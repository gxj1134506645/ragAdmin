<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ChatDotRound, Compass, Connection, MoreFilled, Plus, RefreshRight } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createChatSession,
  deleteChatSession,
  listChatMessages,
  listChatSessions,
  resolveChatStreamError,
  submitChatFeedback,
  streamChatMessage,
  type ChatStreamHandle,
  updateChatSession,
  updateSessionKnowledgeBases,
} from '@/api/chat'
import { listKnowledgeBases } from '@/api/knowledge-base'
import { listModels } from '@/api/model'
import KnowledgeBaseSelector from '@/components/chat/KnowledgeBaseSelector.vue'
import ModelSelector from '@/components/chat/ModelSelector.vue'
import type { ChatExchange, ChatFeedbackType, ChatReference, ChatSceneType, ChatSession, ChatStreamEvent } from '@/types/chat'
import type { KnowledgeBaseSummary } from '@/types/knowledge-base'
import type { ModelSummary } from '@/types/model'

interface Props {
  sceneType: ChatSceneType
  anchorKbId?: number | null
  eyebrow: string
  title: string
  description: string
}

interface PendingExchange {
  question: string
  answer: string
  errorMessage: string | null
}

interface KnowledgeBaseMentionState {
  keyword: string
  start: number
  end: number
}

interface SessionMetadataPayload {
  sessionId: number
  sessionName: string
  chatModelId: number | null
  webSearchEnabled: boolean
}

interface SessionKnowledgeBasePayload {
  sessionId: number
  selectedKbIds: number[]
}

const props = defineProps<Props>()
const router = useRouter()
const KNOWLEDGE_BASE_MENTION_STOP_PATTERN = /[\s@,，.。!！?？:：;；()（）\[\]【】]/

const messageContainerRef = ref<HTMLElement | null>(null)
const composerInputRef = ref<{ textarea?: HTMLTextAreaElement | null } | null>(null)
const optionLoading = ref(false)
const sessionLoading = ref(false)
const messageLoading = ref(false)
const sessions = ref<ChatSession[]>([])
const messages = ref<ChatExchange[]>([])
const availableKnowledgeBases = ref<KnowledgeBaseSummary[]>([])
const availableModels = ref<ModelSummary[]>([])
const activeSessionId = ref<number | null>(null)
const selectedKbIds = ref<number[]>([])
const selectedModelId = ref<number | undefined>(undefined)
const webSearchEnabled = ref(false)
const draftQuestion = ref('')
const pendingExchange = ref<PendingExchange | null>(null)
const loadingError = ref('')
const streaming = ref(false)
const sessionActionLoadingId = ref<number | null>(null)
const feedbackSubmittingMessageIds = ref<number[]>([])
const expandedReferenceMessageIds = ref<number[]>([])
const knowledgeBasePickerVisible = ref(false)
const knowledgeBasePickerKeyword = ref('')
const knowledgeBaseMentionState = ref<KnowledgeBaseMentionState | null>(null)
const knowledgeBaseMentionActiveIndex = ref(0)
const preferencePersistencePauseDepth = ref(0)

let streamHandle: ChatStreamHandle | null = null
let composerBlurTimer: number | null = null
let sessionMetadataSyncTask: Promise<void> = Promise.resolve()
let sessionKnowledgeBaseSyncTask: Promise<void> = Promise.resolve()

const isKnowledgeBaseScene = computed(() => props.sceneType === 'KNOWLEDGE_BASE')
const sessionPanelTitle = computed(() => {
  return isKnowledgeBaseScene.value ? '知识库会话' : '首页会话'
})
const sessionPanelHint = computed(() => {
  return isKnowledgeBaseScene.value ? '切换同一知识库内的历史上下文' : '切换首页里的独立对话线程'
})
const sessionEmptyText = computed(() => {
  return isKnowledgeBaseScene.value ? '当前知识库还没有聊天会话' : '还没有首页会话，发送第一条问题后会自动创建'
})

const selectedKnowledgeBaseNames = computed<string[]>(() => {
  return selectedKbIds.value.flatMap((id) => {
    const match = availableKnowledgeBases.value.find((item) => item.id === id)
    return match ? [match.kbName] : []
  })
})

const selectedKnowledgeBases = computed<KnowledgeBaseSummary[]>(() => {
  return selectedKbIds.value.flatMap((id) => {
    const match = availableKnowledgeBases.value.find((item) => item.id === id)
    return match ? [match] : []
  })
})

const filteredKnowledgeBases = computed<KnowledgeBaseSummary[]>(() => {
  const keyword = knowledgeBasePickerKeyword.value.trim().toLowerCase()
  const selectedIdSet = new Set(selectedKbIds.value)

  return availableKnowledgeBases.value
    .filter((item) => {
      if (!keyword) {
        return true
      }
      return item.kbName.toLowerCase().includes(keyword) || item.kbCode.toLowerCase().includes(keyword)
    })
    .sort((left, right) => {
      const leftSelected = selectedIdSet.has(left.id) ? 1 : 0
      const rightSelected = selectedIdSet.has(right.id) ? 1 : 0
      return rightSelected - leftSelected
    })
})

const mentionKnowledgeBases = computed<KnowledgeBaseSummary[]>(() => {
  if (isKnowledgeBaseScene.value || knowledgeBaseMentionState.value === null) {
    return []
  }

  const keyword = knowledgeBaseMentionState.value.keyword.trim().toLowerCase()
  const selectedIdSet = new Set(selectedKbIds.value)

  return availableKnowledgeBases.value
    .filter((item) => {
      if (!keyword) {
        return true
      }
      return item.kbName.toLowerCase().includes(keyword) || item.kbCode.toLowerCase().includes(keyword)
    })
    .sort((left, right) => {
      const leftSelected = selectedIdSet.has(left.id) ? 1 : 0
      const rightSelected = selectedIdSet.has(right.id) ? 1 : 0
      return leftSelected - rightSelected
    })
})

const mentionPanelVisible = computed(() => {
  return !isKnowledgeBaseScene.value && knowledgeBaseMentionState.value !== null
})

const currentModelName = computed(() => {
  if (!selectedModelId.value) {
    return '系统默认模型'
  }
  return availableModels.value.find((item) => item.id === selectedModelId.value)?.modelName || '指定模型'
})

const hasConversation = computed(() => {
  return messages.value.length > 0 || pendingExchange.value !== null
})

const activeSession = computed<ChatSession | null>(() => {
  if (activeSessionId.value === null) {
    return null
  }
  return sessions.value.find((item) => item.id === activeSessionId.value) ?? null
})

const composerPlaceholder = computed(() => {
  return isKnowledgeBaseScene.value
    ? '输入问题后，将在当前知识库基础上执行检索增强问答'
    : '输入问题后，可以纯模型回答，也可以临时联合多个知识库检索'
})

function normalizeSelectedKbIds(value?: number[] | null): number[] {
  const ordered = new Set<number>()
  if (typeof props.anchorKbId === 'number') {
    ordered.add(props.anchorKbId)
  }
  value?.forEach((item) => {
    if (typeof item === 'number' && Number.isFinite(item)) {
      ordered.add(item)
    }
  })
  return Array.from(ordered)
}

function resetPreferenceInputs(): void {
  selectedKbIds.value = normalizeSelectedKbIds([])
  selectedModelId.value = undefined
  webSearchEnabled.value = false
}

function resetConversationViewState(): void {
  expandedReferenceMessageIds.value = []
}

function resetKnowledgeBasePickerState(): void {
  knowledgeBasePickerVisible.value = false
  knowledgeBasePickerKeyword.value = ''
  knowledgeBaseMentionState.value = null
  knowledgeBaseMentionActiveIndex.value = 0
}

function suspendPreferencePersistence(mutator: () => void): void {
  preferencePersistencePauseDepth.value += 1
  mutator()
  void nextTick(() => {
    preferencePersistencePauseDepth.value = Math.max(0, preferencePersistencePauseDepth.value - 1)
  })
}

function applySessionPreferences(session: ChatSession | null): void {
  suspendPreferencePersistence(() => {
    if (!session) {
      resetPreferenceInputs()
      return
    }
    selectedKbIds.value = normalizeSelectedKbIds(session.selectedKbIds)
    selectedModelId.value = session.chatModelId ?? undefined
    webSearchEnabled.value = Boolean(session.webSearchEnabled)
  })
}

function replaceSessionLocal(sessionId: number, patch: Partial<ChatSession>): void {
  sessions.value = sessions.value.map((item) => {
    if (item.id !== sessionId) {
      return item
    }
    return {
      ...item,
      ...patch,
    }
  })
}

function closeStream(): void {
  streamHandle?.close()
  streamHandle = null
  streaming.value = false
}

function isSameNumberArray(left: number[], right: number[]): boolean {
  if (left.length !== right.length) {
    return false
  }
  return left.every((value, index) => value === right[index])
}

function buildSessionMetadataPayload(): SessionMetadataPayload | null {
  const session = activeSession.value
  if (!session) {
    return null
  }
  return {
    sessionId: session.id,
    sessionName: session.sessionName,
    chatModelId: selectedModelId.value ?? null,
    webSearchEnabled: webSearchEnabled.value,
  }
}

function buildSessionKnowledgeBasePayload(): SessionKnowledgeBasePayload | null {
  const session = activeSession.value
  if (!session) {
    return null
  }
  return {
    sessionId: session.id,
    selectedKbIds: [...selectedKbIds.value],
  }
}

async function persistSessionMetadata(payload: SessionMetadataPayload): Promise<void> {
  try {
    const updated = await updateChatSession(payload.sessionId, {
      sessionName: payload.sessionName,
      chatModelId: payload.chatModelId,
      webSearchEnabled: payload.webSearchEnabled,
    })
    replaceSessionLocal(payload.sessionId, {
      sessionName: updated.sessionName,
      chatModelId: updated.chatModelId,
      webSearchEnabled: updated.webSearchEnabled,
    })
  } catch (error) {
    ElMessage.error(resolveChatStreamError(error))
  }
}

async function persistSessionKnowledgeBases(payload: SessionKnowledgeBasePayload): Promise<void> {
  try {
    const updated = await updateSessionKnowledgeBases(payload.sessionId, {
      selectedKbIds: payload.selectedKbIds,
    })
    replaceSessionLocal(payload.sessionId, {
      selectedKbIds: [...updated.selectedKbIds],
    })
  } catch (error) {
    ElMessage.error(resolveChatStreamError(error))
  }
}

function queueSessionMetadataPersistence(): void {
  const payload = buildSessionMetadataPayload()
  if (!payload) {
    return
  }
  sessionMetadataSyncTask = sessionMetadataSyncTask
    .catch(() => undefined)
    .then(() => persistSessionMetadata(payload))
}

function queueSessionKnowledgeBasePersistence(): void {
  const payload = buildSessionKnowledgeBasePayload()
  if (!payload) {
    return
  }
  sessionKnowledgeBaseSyncTask = sessionKnowledgeBaseSyncTask
    .catch(() => undefined)
    .then(() => persistSessionKnowledgeBases(payload))
}

async function scrollToBottom(): Promise<void> {
  await nextTick()
  if (!messageContainerRef.value) {
    return
  }
  messageContainerRef.value.scrollTo({
    top: messageContainerRef.value.scrollHeight,
    behavior: 'smooth',
  })
}

function buildSessionName(question: string): string {
  const normalized = question.trim().replace(/\s+/g, ' ')
  if (!normalized) {
    return isKnowledgeBaseScene.value ? '知识库会话' : '通用会话'
  }
  return normalized.length <= 20 ? normalized : `${normalized.slice(0, 20)}...`
}

async function loadPortalOptions(): Promise<void> {
  optionLoading.value = true
  try {
    const [knowledgeBasePage, modelPage] = await Promise.all([
      listKnowledgeBases({ pageNo: 1, pageSize: 200 }),
      listModels({ pageNo: 1, pageSize: 200 }),
    ])
    availableKnowledgeBases.value = knowledgeBasePage.list
    availableModels.value = modelPage.list
  } finally {
    optionLoading.value = false
  }
}

async function loadMessages(sessionId: number): Promise<void> {
  messageLoading.value = true
  loadingError.value = ''
  resetConversationViewState()
  try {
    messages.value = await listChatMessages(sessionId)
  } catch (error) {
    messages.value = []
    loadingError.value = resolveChatStreamError(error)
  } finally {
    messageLoading.value = false
    await scrollToBottom()
  }
}

async function loadSessions(): Promise<void> {
  sessionLoading.value = true
  loadingError.value = ''
  try {
    const response = await listChatSessions({
      kbId: isKnowledgeBaseScene.value ? props.anchorKbId ?? undefined : undefined,
      sceneType: props.sceneType,
      pageNo: 1,
      pageSize: 50,
    })
    sessions.value = response.list

    const nextSession = activeSessionId.value == null
      ? (sessions.value[0] ?? null)
      : (sessions.value.find((item) => item.id === activeSessionId.value) ?? sessions.value[0] ?? null)

    if (!nextSession) {
      activeSessionId.value = null
      messages.value = []
      applySessionPreferences(null)
      return
    }

    activeSessionId.value = nextSession.id
    applySessionPreferences(nextSession)
    await loadMessages(nextSession.id)
  } catch (error) {
    sessions.value = []
    activeSessionId.value = null
    messages.value = []
    applySessionPreferences(null)
    loadingError.value = resolveChatStreamError(error)
  } finally {
    sessionLoading.value = false
  }
}

async function initialize(): Promise<void> {
  closeStream()
  messages.value = []
  pendingExchange.value = null
  resetConversationViewState()
  await loadPortalOptions()
  await loadSessions()
}

async function handleSelectSession(session: ChatSession): Promise<void> {
  if (streaming.value || session.id === activeSessionId.value) {
    return
  }
  activeSessionId.value = session.id
  messages.value = []
  pendingExchange.value = null
  resetConversationViewState()
  applySessionPreferences(session)
  await loadMessages(session.id)
}

async function ensureSession(question: string): Promise<number> {
  if (activeSessionId.value !== null) {
    return activeSessionId.value
  }

  const created = await createChatSession({
    kbId: isKnowledgeBaseScene.value ? props.anchorKbId ?? undefined : undefined,
    sceneType: props.sceneType,
    sessionName: buildSessionName(question),
    chatModelId: selectedModelId.value,
    webSearchEnabled: webSearchEnabled.value,
    selectedKbIds: selectedKbIds.value,
  })
  sessions.value = [created, ...sessions.value.filter((item) => item.id !== created.id)]
  activeSessionId.value = created.id
  applySessionPreferences(created)
  return created.id
}

function applyStreamComplete(question: string, event: ChatStreamEvent): void {
  messages.value = [
    ...messages.value,
    {
      id: event.messageId ?? Date.now(),
      questionText: question,
      answerText: event.answer ?? pendingExchange.value?.answer ?? '',
      references: event.references ?? [],
      feedbackType: null,
      feedbackComment: null,
      usage: event.usage,
    },
  ]
}

async function handleSendQuestion(questionOverride?: string): Promise<void> {
  const question = (questionOverride ?? draftQuestion.value).trim()
  if (!question || streaming.value) {
    return
  }

  loadingError.value = ''
  draftQuestion.value = ''

  let sessionId: number
  try {
    sessionId = await ensureSession(question)
    replaceSessionLocal(sessionId, {
      chatModelId: selectedModelId.value ?? null,
      webSearchEnabled: webSearchEnabled.value,
      selectedKbIds: [...selectedKbIds.value],
    })
  } catch (error) {
    ElMessage.error(resolveChatStreamError(error))
    return
  }

  closeStream()
  pendingExchange.value = {
    question,
    answer: '',
    errorMessage: null,
  }
  streaming.value = true
  await scrollToBottom()

  streamHandle = streamChatMessage(
    sessionId,
    {
      question,
      chatModelId: selectedModelId.value,
      selectedKbIds: selectedKbIds.value,
      webSearchEnabled: webSearchEnabled.value,
    },
    {
      onEvent(event) {
        if (event.eventType === 'DELTA') {
          if (pendingExchange.value) {
            pendingExchange.value.answer += event.delta ?? ''
            void scrollToBottom()
          }
          return
        }

        if (event.eventType === 'COMPLETE') {
          applyStreamComplete(question, event)
          pendingExchange.value = null
          closeStream()
          void scrollToBottom()
          return
        }

        pendingExchange.value = pendingExchange.value
          ? {
              ...pendingExchange.value,
              errorMessage: event.errorMessage || '流式问答失败',
            }
          : null
        closeStream()
        ElMessage.error(event.errorMessage || '流式问答失败')
      },
      onError(error) {
        const message = resolveChatStreamError(error)
        if (pendingExchange.value) {
          pendingExchange.value = {
            ...pendingExchange.value,
            errorMessage: message,
          }
        }
        closeStream()
        ElMessage.error(message)
      },
    },
  )
}

function handleRetryPendingExchange(): void {
  const question = pendingExchange.value?.question
  if (!question || streaming.value) {
    return
  }
  void handleSendQuestion(question)
}

function handleStartNewSession(): void {
  if (streaming.value) {
    return
  }
  activeSessionId.value = null
  messages.value = []
  pendingExchange.value = null
  resetConversationViewState()
  resetPreferenceInputs()
}

function handleClearView(): void {
  messages.value = []
  pendingExchange.value = null
  resetConversationViewState()
}

function toggleKnowledgeBaseSelection(kbId: number): void {
  if (isKnowledgeBaseScene.value) {
    return
  }
  if (selectedKbIds.value.includes(kbId)) {
    selectedKbIds.value = normalizeSelectedKbIds(selectedKbIds.value.filter((id) => id !== kbId))
    return
  }
  selectedKbIds.value = normalizeSelectedKbIds([...selectedKbIds.value, kbId])
}

function removeSelectedKnowledgeBase(kbId: number): void {
  if (isKnowledgeBaseScene.value) {
    return
  }
  selectedKbIds.value = normalizeSelectedKbIds(selectedKbIds.value.filter((id) => id !== kbId))
}

function clearSelectedKnowledgeBases(): void {
  if (isKnowledgeBaseScene.value) {
    return
  }
  selectedKbIds.value = []
}

function isKnowledgeBaseMentionBoundary(value?: string): boolean {
  return !value || KNOWLEDGE_BASE_MENTION_STOP_PATTERN.test(value)
}

function extractKnowledgeBaseMentionSuffix(value: string): string {
  let index = 0
  while (index < value.length && !isKnowledgeBaseMentionBoundary(value[index])) {
    index += 1
  }
  return value.slice(0, index)
}

function getComposerTextarea(): HTMLTextAreaElement | null {
  return composerInputRef.value?.textarea ?? null
}

function clearComposerBlurTimer(): void {
  if (composerBlurTimer === null) {
    return
  }
  window.clearTimeout(composerBlurTimer)
  composerBlurTimer = null
}

// 只把独立 token 里的 @ 视为知识库 mention，避免邮箱等普通文本被误识别。
function resolveKnowledgeBaseMentionState(question: string, cursorPosition: number): KnowledgeBaseMentionState | null {
  if (isKnowledgeBaseScene.value) {
    return null
  }

  const safeCursorPosition = Math.min(Math.max(cursorPosition, 0), question.length)
  const prefix = question.slice(0, safeCursorPosition)
  const mentionStart = prefix.lastIndexOf('@')

  if (mentionStart < 0 || !isKnowledgeBaseMentionBoundary(question[mentionStart - 1])) {
    return null
  }

  const tokenPrefix = question.slice(mentionStart + 1, safeCursorPosition)
  if ([...tokenPrefix].some((char) => isKnowledgeBaseMentionBoundary(char))) {
    return null
  }

  const suffix = extractKnowledgeBaseMentionSuffix(question.slice(safeCursorPosition))
  return {
    keyword: `${tokenPrefix}${suffix}`,
    start: mentionStart,
    end: safeCursorPosition + suffix.length,
  }
}

function syncKnowledgeBaseMentionState(cursorPosition?: number | null): void {
  if (isKnowledgeBaseScene.value) {
    knowledgeBaseMentionState.value = null
    knowledgeBaseMentionActiveIndex.value = 0
    return
  }

  const textarea = getComposerTextarea()
  const nextCursorPosition = typeof cursorPosition === 'number'
    ? cursorPosition
    : (textarea?.selectionStart ?? draftQuestion.value.length)

  knowledgeBaseMentionState.value = resolveKnowledgeBaseMentionState(draftQuestion.value, nextCursorPosition)
  if (knowledgeBaseMentionState.value === null) {
    knowledgeBaseMentionActiveIndex.value = 0
    return
  }
  knowledgeBasePickerVisible.value = false
}

function focusComposerTextarea(cursorPosition?: number): void {
  void nextTick(() => {
    const textarea = getComposerTextarea()
    if (!textarea) {
      return
    }
    textarea.focus()
    if (typeof cursorPosition === 'number') {
      textarea.setSelectionRange(cursorPosition, cursorPosition)
    }
  })
}

// 选中知识库后只移除当前 mention token，本轮真实问题文本保持原位继续编辑。
function buildQuestionWithoutMention(question: string, mentionState: KnowledgeBaseMentionState): { value: string; cursorPosition: number } {
  const before = question.slice(0, mentionState.start)
  const after = question.slice(mentionState.end)

  if (!before && /^\s+/.test(after)) {
    return {
      value: after.replace(/^\s+/, ''),
      cursorPosition: 0,
    }
  }

  if (/\s$/.test(before) && /^\s/.test(after)) {
    return {
      value: `${before}${after.replace(/^\s+/, '')}`,
      cursorPosition: before.length,
    }
  }

  if (before && after && !/\s$/.test(before) && !/^\s/.test(after)) {
    return {
      value: `${before} ${after}`,
      cursorPosition: before.length + 1,
    }
  }

  return {
    value: `${before}${after}`,
    cursorPosition: before.length,
  }
}

function moveMentionActiveIndex(offset: number): void {
  if (mentionKnowledgeBases.value.length === 0) {
    return
  }

  const total = mentionKnowledgeBases.value.length
  knowledgeBaseMentionActiveIndex.value = (knowledgeBaseMentionActiveIndex.value + offset + total) % total
}

function selectKnowledgeBaseFromMention(knowledgeBase: KnowledgeBaseSummary): void {
  const mentionState = knowledgeBaseMentionState.value
  selectedKbIds.value = normalizeSelectedKbIds([...selectedKbIds.value, knowledgeBase.id])

  if (mentionState === null) {
    focusComposerTextarea()
    return
  }

  const nextQuestion = buildQuestionWithoutMention(draftQuestion.value, mentionState)
  draftQuestion.value = nextQuestion.value
  knowledgeBaseMentionState.value = null
  knowledgeBaseMentionActiveIndex.value = 0
  focusComposerTextarea(nextQuestion.cursorPosition)
}

function handleComposerCursorChange(event?: Event): void {
  clearComposerBlurTimer()
  const target = event?.target as HTMLTextAreaElement | null
  syncKnowledgeBaseMentionState(target?.selectionStart ?? null)
}

function handleComposerFocus(): void {
  clearComposerBlurTimer()
  syncKnowledgeBaseMentionState()
}

function handleComposerBlur(): void {
  clearComposerBlurTimer()
  composerBlurTimer = window.setTimeout(() => {
    knowledgeBaseMentionState.value = null
    knowledgeBaseMentionActiveIndex.value = 0
    composerBlurTimer = null
  }, 120)
}

function isFeedbackSubmitting(messageId: number): boolean {
  return feedbackSubmittingMessageIds.value.includes(messageId)
}

function isReferenceExpanded(messageId: number): boolean {
  return expandedReferenceMessageIds.value.includes(messageId)
}

function toggleReferencePanel(messageId: number): void {
  if (isReferenceExpanded(messageId)) {
    expandedReferenceMessageIds.value = expandedReferenceMessageIds.value.filter((id) => id !== messageId)
    return
  }
  expandedReferenceMessageIds.value = [...expandedReferenceMessageIds.value, messageId]
}

async function handleSubmitFeedback(messageId: number, feedbackType: ChatFeedbackType): Promise<void> {
  const targetMessage = messages.value.find((item) => item.id === messageId)
  if (!targetMessage || isFeedbackSubmitting(messageId) || targetMessage.feedbackType === feedbackType) {
    return
  }

  feedbackSubmittingMessageIds.value = [...feedbackSubmittingMessageIds.value, messageId]
  try {
    await submitChatFeedback(messageId, { feedbackType })
    targetMessage.feedbackType = feedbackType
    targetMessage.feedbackComment = null
    ElMessage.success(feedbackType === 'LIKE' ? '已记录为有帮助' : '已记录为待改进')
  } catch (error) {
    ElMessage.error(resolveChatStreamError(error))
  } finally {
    feedbackSubmittingMessageIds.value = feedbackSubmittingMessageIds.value.filter((id) => id !== messageId)
  }
}

async function handleOpenReference(reference: ChatReference): Promise<void> {
  if (!reference.kbId) {
    ElMessage.warning('当前引用未关联知识库，暂时无法定位来源')
    return
  }
  await router.push({
    name: 'app-knowledge-base-chat',
    params: {
      kbId: reference.kbId,
    },
    query: {
      source: 'reference',
      ...(reference.documentName ? { documentName: reference.documentName } : {}),
      ...(reference.documentId ? { documentId: String(reference.documentId) } : {}),
      chunkId: String(reference.chunkId),
      ...(reference.chunkNo ? { chunkNo: String(reference.chunkNo) } : {}),
      ...(reference.contentSnippet ? { snippet: reference.contentSnippet } : {}),
    },
  })
}

async function handleRenameSession(session: ChatSession): Promise<void> {
  if (streaming.value || sessionActionLoadingId.value !== null) {
    return
  }
  try {
    const { value } = await ElMessageBox.prompt('请输入新的会话名称', '重命名会话', {
      confirmButtonText: '保存',
      cancelButtonText: '取消',
      inputValue: session.sessionName,
      inputValidator: (inputValue) => inputValue.trim().length > 0 || '会话名称不能为空',
    })
    const normalizedName = value.trim()
    if (!normalizedName || normalizedName === session.sessionName) {
      return
    }
    sessionActionLoadingId.value = session.id
    const updated = await updateChatSession(session.id, {
      sessionName: normalizedName,
      chatModelId: session.id === activeSessionId.value ? (selectedModelId.value ?? null) : session.chatModelId,
      webSearchEnabled: session.id === activeSessionId.value ? webSearchEnabled.value : session.webSearchEnabled,
    })
    replaceSessionLocal(session.id, {
      sessionName: updated.sessionName,
      chatModelId: updated.chatModelId,
      webSearchEnabled: updated.webSearchEnabled,
    })
    ElMessage.success('会话已重命名')
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    ElMessage.error(resolveChatStreamError(error))
  } finally {
    sessionActionLoadingId.value = null
  }
}

async function handleDeleteSession(session: ChatSession): Promise<void> {
  if (streaming.value || sessionActionLoadingId.value !== null) {
    return
  }
  try {
    await ElMessageBox.confirm(`删除后该会话的历史消息与知识库选择会一并清理，确定删除“${session.sessionName}”吗？`, '删除会话', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      confirmButtonClass: 'el-button--danger',
    })
    sessionActionLoadingId.value = session.id
    await deleteChatSession(session.id)
    if (session.id === activeSessionId.value) {
      messages.value = []
      pendingExchange.value = null
    }
    await loadSessions()
    ElMessage.success('会话已删除')
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    ElMessage.error(resolveChatStreamError(error))
  } finally {
    sessionActionLoadingId.value = null
  }
}

function handleSessionCommand(session: ChatSession, command: unknown): void {
  if (command === 'rename') {
    void handleRenameSession(session)
    return
  }
  void handleDeleteSession(session)
}

function handleComposerKeydown(event: KeyboardEvent): void {
  if (mentionPanelVisible.value) {
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      moveMentionActiveIndex(1)
      return
    }

    if (event.key === 'ArrowUp') {
      event.preventDefault()
      moveMentionActiveIndex(-1)
      return
    }

    if (event.key === 'Escape') {
      event.preventDefault()
      knowledgeBaseMentionState.value = null
      knowledgeBaseMentionActiveIndex.value = 0
      return
    }

    if (!event.shiftKey && (event.key === 'Enter' || event.key === 'Tab')) {
      const activeKnowledgeBase = mentionKnowledgeBases.value[knowledgeBaseMentionActiveIndex.value]
      if (activeKnowledgeBase) {
        event.preventDefault()
        selectKnowledgeBaseFromMention(activeKnowledgeBase)
        return
      }
    }
  }

  if (event.key !== 'Enter' || event.shiftKey) {
    return
  }
  event.preventDefault()
  void handleSendQuestion()
}

watch(
  () => [props.sceneType, props.anchorKbId],
  async () => {
    resetKnowledgeBasePickerState()
    await initialize()
  },
)

watch(
  () => [selectedModelId.value ?? null, webSearchEnabled.value] as const,
  ([nextModelId, nextWebSearchEnabled], [prevModelId, prevWebSearchEnabled]) => {
    if (preferencePersistencePauseDepth.value > 0 || activeSessionId.value === null) {
      return
    }
    if (nextModelId === prevModelId && nextWebSearchEnabled === prevWebSearchEnabled) {
      return
    }
    replaceSessionLocal(activeSessionId.value, {
      chatModelId: nextModelId,
      webSearchEnabled: nextWebSearchEnabled,
    })
    queueSessionMetadataPersistence()
  },
)

watch(
  selectedKbIds,
  (nextValue, prevValue) => {
    if (preferencePersistencePauseDepth.value > 0 || activeSessionId.value === null) {
      return
    }
    if (isSameNumberArray(nextValue, prevValue ?? [])) {
      return
    }
    replaceSessionLocal(activeSessionId.value, {
      selectedKbIds: [...nextValue],
    })
    queueSessionKnowledgeBasePersistence()
  },
)

watch(draftQuestion, () => {
  void nextTick(() => {
    syncKnowledgeBaseMentionState()
  })
})

watch(knowledgeBasePickerVisible, (visible) => {
  if (visible) {
    knowledgeBaseMentionState.value = null
    knowledgeBaseMentionActiveIndex.value = 0
    return
  }
  knowledgeBasePickerKeyword.value = ''
})

watch(mentionKnowledgeBases, (list) => {
  if (list.length === 0) {
    knowledgeBaseMentionActiveIndex.value = 0
    return
  }

  if (knowledgeBaseMentionActiveIndex.value >= list.length) {
    knowledgeBaseMentionActiveIndex.value = 0
  }
})

onMounted(async () => {
  await initialize()
})

onUnmounted(() => {
  closeStream()
  clearComposerBlurTimer()
})
</script>

<template>
  <section class="workspace-shell">
    <header class="workspace-hero app-shell-panel">
      <div class="hero-copy">
        <p class="hero-kicker">{{ props.eyebrow }}</p>
        <h1 class="serif-title">{{ props.title }}</h1>
        <p>{{ props.description }}</p>
      </div>
      <div class="hero-summary">
        <article>
          <span>当前模型</span>
          <strong>{{ currentModelName }}</strong>
        </article>
        <article>
          <span>知识库范围</span>
          <strong>{{ selectedKbIds.length }} 个</strong>
        </article>
        <article>
          <span>联网开关</span>
          <strong>{{ webSearchEnabled ? '已开启' : '已关闭' }}</strong>
        </article>
      </div>
    </header>

    <section class="control-strip app-shell-panel" :class="{ 'is-general-scene': !isKnowledgeBaseScene }">
      <div class="control-block">
        <ModelSelector v-model="selectedModelId" :options="availableModels" :loading="optionLoading" />
      </div>
      <div v-if="isKnowledgeBaseScene" class="control-block is-wide">
        <KnowledgeBaseSelector
          v-model="selectedKbIds"
          :options="availableKnowledgeBases"
          :loading="optionLoading"
          :locked-kb-id="props.anchorKbId ?? null"
        />
      </div>
      <div v-else class="control-block control-summary">
        <div class="control-summary-head">
          <span>@知识库选择</span>
          <small>{{ selectedKbIds.length }} 个已接入</small>
        </div>
        <p class="control-summary-copy">
          首页场景不固定绑定知识库。你可以在输入区点击 `@知识库`，把本轮问题切换成临时多库检索模式。
        </p>
        <div v-if="selectedKnowledgeBases.length > 0" class="control-summary-tags">
          <el-tag
            v-for="knowledgeBase in selectedKnowledgeBases.slice(0, 4)"
            :key="knowledgeBase.id"
            effect="plain"
            type="info"
          >
            @{{ knowledgeBase.kbName }}
          </el-tag>
          <span v-if="selectedKnowledgeBases.length > 4" class="control-summary-more">
            +{{ selectedKnowledgeBases.length - 4 }}
          </span>
        </div>
      </div>
      <div class="control-block control-toggle">
        <div class="toggle-head">
          <span>联网搜索</span>
          <small>Provider 不可用时服务端会优雅降级</small>
        </div>
        <el-switch v-model="webSearchEnabled" />
      </div>
    </section>

    <section class="conversation-grid has-session-list">
      <aside class="session-list-card app-shell-panel">
        <div class="session-list-head">
          <div>
            <p>{{ sessionPanelTitle }}</p>
            <strong>{{ sessions.length }} 个</strong>
            <span class="session-list-subtitle">{{ sessionPanelHint }}</span>
          </div>
          <el-button text :icon="Plus" :disabled="streaming" @click="handleStartNewSession">新会话</el-button>
        </div>
        <div class="session-list thin-scrollbar">
          <div v-if="sessionLoading" class="session-placeholder">正在加载会话...</div>
          <div
            v-for="session in sessions"
            :key="session.id"
            class="session-item"
            :class="{ 'is-active': session.id === activeSessionId }"
          >
            <button
              type="button"
              class="session-main"
              :disabled="streaming"
              @click="handleSelectSession(session)"
            >
              <strong>{{ session.sessionName }}</strong>
              <span>
                {{
                  session.selectedKbIds.length > 0
                    ? `${session.selectedKbIds.length} 个知识库参与检索`
                    : '纯模型上下文'
                }}
              </span>
            </button>
            <div class="session-item-tools">
              <el-dropdown
                trigger="click"
                :teleported="false"
                :disabled="streaming || sessionActionLoadingId === session.id"
                @command="handleSessionCommand(session, $event)"
              >
                <el-button
                  text
                  circle
                  class="session-action-trigger"
                  :disabled="streaming || sessionActionLoadingId === session.id"
                  @click.stop
                >
                  <el-icon><MoreFilled /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="rename">重命名</el-dropdown-item>
                    <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>
          <div v-if="!sessionLoading && sessions.length === 0" class="session-placeholder">
            {{ sessionEmptyText }}
          </div>
        </div>
      </aside>

      <section class="conversation-card app-shell-panel">
        <div class="conversation-toolbar">
          <div class="toolbar-tags">
            <el-tag type="warning" effect="plain">
              <el-icon><Compass /></el-icon>
              <span>{{ props.sceneType === 'GENERAL' ? '首页通用场景' : '知识库内场景' }}</span>
            </el-tag>
            <el-tag v-if="selectedKnowledgeBaseNames.length > 0" type="info" effect="plain">
              {{ selectedKnowledgeBaseNames.join(' / ') }}
            </el-tag>
          </div>
          <el-button text :icon="RefreshRight" @click="initialize">刷新</el-button>
        </div>

        <div ref="messageContainerRef" class="conversation-body page-scrollbar">
          <div v-if="messageLoading" class="conversation-placeholder">
            正在加载历史消息...
          </div>

          <template v-else-if="hasConversation">
            <div
              v-for="message in messages"
              :key="message.id"
              class="message-group"
            >
              <article class="message-bubble message-bubble-user">
                <span class="message-role">你</span>
                <p>{{ message.questionText }}</p>
              </article>

              <article class="message-bubble message-bubble-assistant">
                <span class="message-role">助手</span>
                <p>{{ message.answerText || '模型没有返回内容' }}</p>
                <div class="assistant-actions">
                  <button
                    v-if="message.references.length > 0"
                    type="button"
                    class="assistant-action assistant-reference-toggle"
                    @click="toggleReferencePanel(message.id)"
                  >
                    <span>{{ isReferenceExpanded(message.id) ? '收起引用' : '查看引用' }}</span>
                    <strong>{{ message.references.length }}</strong>
                  </button>
                  <div class="assistant-feedback">
                    <el-button
                      text
                      size="small"
                      :type="message.feedbackType === 'LIKE' ? 'primary' : undefined"
                      :disabled="isFeedbackSubmitting(message.id)"
                      @click="handleSubmitFeedback(message.id, 'LIKE')"
                    >
                      有帮助
                    </el-button>
                    <el-button
                      text
                      size="small"
                      :type="message.feedbackType === 'DISLIKE' ? 'danger' : undefined"
                      :disabled="isFeedbackSubmitting(message.id)"
                      @click="handleSubmitFeedback(message.id, 'DISLIKE')"
                    >
                      待改进
                    </el-button>
                  </div>
                </div>
                <div
                  v-if="message.references.length > 0 && isReferenceExpanded(message.id)"
                  class="reference-block"
                >
                  <div
                    v-for="reference in message.references"
                    :key="`${message.id}-${reference.chunkId}`"
                    class="reference-item"
                  >
                    <div class="reference-head">
                      <strong>{{ reference.documentName || '未命名文档' }}</strong>
                      <span>相似度 {{ reference.score.toFixed(2) }}</span>
                    </div>
                    <p>{{ reference.contentSnippet }}</p>
                    <div class="reference-actions">
                      <el-button link type="primary" @click="handleOpenReference(reference)">
                        进入知识库
                      </el-button>
                      <span v-if="reference.chunkNo" class="reference-meta">定位切片 #{{ reference.chunkNo }}</span>
                    </div>
                  </div>
                </div>
                <p v-if="message.feedbackType" class="feedback-hint">
                  {{ message.feedbackType === 'LIKE' ? '你已标记这条回答有帮助。' : '你已标记这条回答需要改进。' }}
                </p>
                <div v-if="message.usage" class="message-usage">
                  Prompt {{ message.usage.promptTokens ?? 0 }} · Completion {{ message.usage.completionTokens ?? 0 }}
                </div>
              </article>
            </div>

            <div v-if="pendingExchange" class="message-group">
              <article class="message-bubble message-bubble-user">
                <span class="message-role">你</span>
                <p>{{ pendingExchange.question }}</p>
              </article>
              <article
                class="message-bubble message-bubble-assistant"
                :class="{ 'is-error': pendingExchange.errorMessage }"
              >
                <span class="message-role">助手</span>
                <p>{{ pendingExchange.answer || '正在生成回答...' }}</p>
                <p v-if="pendingExchange.errorMessage" class="pending-error">{{ pendingExchange.errorMessage }}</p>
                <el-button
                  v-if="pendingExchange.errorMessage"
                  text
                  :icon="RefreshRight"
                  @click="handleRetryPendingExchange"
                >
                  重新发送
                </el-button>
              </article>
            </div>
          </template>

          <div v-else class="conversation-empty">
            <el-empty description="还没有聊天内容">
              <template #description>
                <p>输入你的第一个问题，当前选择会在发送时生效。</p>
              </template>
            </el-empty>
          </div>
        </div>

        <footer class="composer-card">
          <div class="composer-head">
            <div class="composer-hint">
              <el-icon><Connection /></el-icon>
              <span>{{ composerPlaceholder }}</span>
            </div>
            <div class="composer-tools">
              <el-popover
                v-if="!isKnowledgeBaseScene"
                v-model:visible="knowledgeBasePickerVisible"
                placement="top-end"
                :width="380"
                trigger="click"
                popper-class="composer-kb-popper"
              >
                <template #reference>
                  <el-button plain size="small">@知识库</el-button>
                </template>
                <div class="composer-kb-picker">
                  <div class="composer-kb-picker-head">
                    <div>
                      <strong>选择本轮检索知识库</strong>
                      <span>支持多选，不选则走纯模型回答</span>
                    </div>
                    <el-button
                      text
                      size="small"
                      :disabled="selectedKbIds.length === 0"
                      @click="clearSelectedKnowledgeBases"
                    >
                      清空
                    </el-button>
                  </div>
                  <el-input
                    v-model="knowledgeBasePickerKeyword"
                    clearable
                    placeholder="搜索知识库编码或名称"
                  />
                  <div class="composer-kb-picker-list thin-scrollbar">
                    <button
                      v-for="knowledgeBase in filteredKnowledgeBases"
                      :key="knowledgeBase.id"
                      type="button"
                      class="composer-kb-picker-item"
                      :class="{ 'is-selected': selectedKbIds.includes(knowledgeBase.id) }"
                      @click="toggleKnowledgeBaseSelection(knowledgeBase.id)"
                    >
                      <div class="composer-kb-picker-copy">
                        <strong>{{ knowledgeBase.kbName }}</strong>
                        <span>{{ knowledgeBase.kbCode }}</span>
                      </div>
                      <small>{{ selectedKbIds.includes(knowledgeBase.id) ? '已选择' : '点击接入' }}</small>
                    </button>
                    <div v-if="filteredKnowledgeBases.length === 0" class="composer-kb-picker-empty">
                      未找到匹配知识库
                    </div>
                  </div>
                </div>
              </el-popover>
              <span class="composer-tip">输入 @ 可接入知识库，Enter 发送，Shift + Enter 换行</span>
            </div>
          </div>
          <div v-if="!isKnowledgeBaseScene" class="composer-kb-strip">
            <span class="composer-kb-strip-label">@ 当前知识库</span>
            <div v-if="selectedKnowledgeBases.length > 0" class="composer-kb-strip-tags">
              <el-tag
                v-for="knowledgeBase in selectedKnowledgeBases"
                :key="knowledgeBase.id"
                closable
                effect="plain"
                type="info"
                @close="removeSelectedKnowledgeBase(knowledgeBase.id)"
              >
                @{{ knowledgeBase.kbName }}
              </el-tag>
            </div>
            <span v-else class="composer-kb-strip-empty">未选择，当前将走纯模型问答</span>
          </div>
          <div class="composer-input-shell">
            <div v-if="mentionPanelVisible" class="composer-mention-panel">
              <div class="composer-mention-head">
                <strong>@知识库</strong>
                <span>
                  {{ knowledgeBaseMentionState?.keyword ? `匹配“${knowledgeBaseMentionState.keyword}”` : '输入名称或编码快速接入' }}
                </span>
              </div>
              <div v-if="mentionKnowledgeBases.length > 0" class="composer-mention-list thin-scrollbar">
                <button
                  v-for="(knowledgeBase, index) in mentionKnowledgeBases"
                  :key="`mention-${knowledgeBase.id}`"
                  type="button"
                  class="composer-mention-item"
                  :class="{
                    'is-active': index === knowledgeBaseMentionActiveIndex,
                    'is-selected': selectedKbIds.includes(knowledgeBase.id),
                  }"
                  @mousedown.prevent="selectKnowledgeBaseFromMention(knowledgeBase)"
                  @mouseenter="knowledgeBaseMentionActiveIndex = index"
                >
                  <div class="composer-mention-copy">
                    <strong>{{ knowledgeBase.kbName }}</strong>
                    <span>{{ knowledgeBase.kbCode }}</span>
                  </div>
                  <small>{{ selectedKbIds.includes(knowledgeBase.id) ? '已接入' : '按 Enter 选择' }}</small>
                </button>
              </div>
              <div v-else class="composer-mention-empty">
                未找到匹配知识库，继续输入可缩小范围
              </div>
            </div>
            <el-input
              ref="composerInputRef"
              v-model="draftQuestion"
              type="textarea"
              :rows="5"
              resize="none"
              :placeholder="composerPlaceholder"
              @blur="handleComposerBlur"
              @click="handleComposerCursorChange"
              @focus="handleComposerFocus"
              @keydown="handleComposerKeydown"
              @keyup="handleComposerCursorChange"
            />
          </div>
          <div class="composer-actions">
            <p v-if="loadingError" class="composer-error">{{ loadingError }}</p>
            <div class="composer-action-buttons">
              <el-button plain :icon="ChatDotRound" :disabled="streaming" @click="handleClearView">
                清空视图
              </el-button>
              <el-button
                type="primary"
                :loading="streaming"
                :disabled="!draftQuestion.trim()"
                @click="handleSendQuestion"
              >
                {{ streaming ? '生成中...' : '发送问题' }}
              </el-button>
            </div>
          </div>
        </footer>
      </section>
    </section>
  </section>
</template>

<style scoped>
.workspace-shell {
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-height: calc(100vh - 40px);
}

.workspace-hero,
.control-strip,
.session-list-card,
.conversation-card {
  border-radius: 30px;
}

.workspace-hero {
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  gap: 20px;
  padding: 28px;
}

.hero-copy {
  max-width: 760px;
}

.hero-kicker {
  margin: 0 0 8px;
  color: var(--text-muted);
  font-size: 12px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.hero-copy h1 {
  margin: 0;
  font-size: clamp(30px, 3vw, 46px);
  line-height: 1.18;
}

.hero-copy p:last-child {
  margin: 16px 0 0;
  color: var(--text-secondary);
  line-height: 1.8;
}

.hero-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(120px, 1fr));
  gap: 12px;
  min-width: 360px;
}

.hero-summary article {
  padding: 16px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.54);
  border: 1px solid rgba(122, 89, 53, 0.08);
}

.hero-summary span,
.session-list-head p {
  display: block;
  color: var(--text-muted);
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.hero-summary strong,
.session-list-head strong {
  display: block;
  margin-top: 10px;
  font-size: 18px;
}

.control-strip {
  display: grid;
  grid-template-columns: minmax(220px, 0.9fr) minmax(260px, 1.2fr) 220px;
  gap: 16px;
  padding: 18px 20px;
}

.control-block {
  padding: 14px;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.48);
  border: 1px solid rgba(122, 89, 53, 0.08);
}

.control-toggle {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.control-summary {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.control-summary-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.control-summary-head span {
  font-weight: 600;
}

.control-summary-copy,
.control-summary-head small,
.control-summary-more {
  color: var(--text-muted);
}

.control-summary-copy {
  margin: 0;
  line-height: 1.7;
}

.control-summary-tags {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.toggle-head span {
  display: block;
  font-weight: 600;
}

.toggle-head small {
  display: block;
  margin-top: 8px;
  color: var(--text-muted);
  line-height: 1.6;
}

.conversation-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 18px;
  min-height: 0;
  flex: 1;
}

.conversation-grid.has-session-list {
  grid-template-columns: 280px minmax(0, 1fr);
}

.session-list-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px;
}

.session-list-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.session-list-subtitle {
  display: block;
  margin-top: 8px;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.6;
}

.session-list {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 10px;
  min-height: 420px;
  max-height: calc(100vh - 340px);
  overflow-y: auto;
  padding-right: 4px;
}

.session-item {
  display: flex;
  align-items: stretch;
  gap: 8px;
  padding: 10px;
  border-radius: 18px;
  border: 1px solid rgba(122, 89, 53, 0.08);
  background: rgba(255, 255, 255, 0.58);
  transition:
    transform 180ms ease,
    border-color 180ms ease,
    box-shadow 180ms ease;
}

.session-item:hover,
.session-item.is-active {
  transform: translateY(-1px);
  border-color: rgba(157, 91, 47, 0.26);
  box-shadow: 0 14px 28px rgba(91, 58, 24, 0.08);
}

.session-main {
  flex: 1;
  min-width: 0;
  padding: 6px;
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  text-align: left;
}

.session-main:disabled {
  cursor: not-allowed;
}

.session-main strong,
.session-main span {
  display: block;
}

.session-main strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-main span {
  margin-top: 8px;
  color: var(--text-muted);
  font-size: 12px;
}

.session-item-tools {
  display: flex;
  align-items: flex-start;
}

.session-action-trigger {
  margin-top: 2px;
  color: var(--text-muted);
}

.session-action-trigger:hover,
.session-item.is-active .session-action-trigger {
  color: var(--brand-strong);
}

.session-placeholder {
  display: grid;
  place-items: center;
  min-height: 140px;
  padding: 18px;
  color: var(--text-muted);
  text-align: center;
}

.conversation-card {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  min-height: 0;
  overflow: hidden;
}

.conversation-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 18px 20px;
  border-bottom: 1px solid rgba(122, 89, 53, 0.08);
}

.toolbar-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.conversation-body {
  display: flex;
  flex-direction: column;
  gap: 18px;
  overflow-y: auto;
  padding: 24px;
  min-height: 0;
}

.conversation-placeholder,
.conversation-empty {
  display: grid;
  place-items: center;
  min-height: 100%;
  color: var(--text-muted);
}

.message-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.message-bubble {
  max-width: min(84%, 920px);
  padding: 16px 18px;
  border-radius: 22px;
  box-shadow: 0 16px 30px rgba(76, 51, 23, 0.06);
  line-height: 1.8;
}

.message-bubble p {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}

.message-bubble-user {
  align-self: flex-end;
  color: #fffaf4;
  background: linear-gradient(150deg, #9d5b2f, #7f431b);
}

.message-bubble-assistant {
  align-self: flex-start;
  background: rgba(255, 255, 255, 0.8);
}

.message-bubble-assistant.is-error {
  border: 1px solid rgba(176, 77, 53, 0.22);
  background: rgba(255, 247, 244, 0.88);
}

.message-role {
  display: inline-flex;
  margin-bottom: 10px;
  color: inherit;
  opacity: 0.8;
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.reference-block {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid rgba(122, 89, 53, 0.1);
}

.reference-item {
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(243, 234, 222, 0.86);
}

.reference-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.reference-head span,
.message-usage,
.pending-error,
.composer-tip {
  color: var(--text-muted);
  font-size: 12px;
}

.reference-item p {
  margin-top: 8px;
  color: var(--text-secondary);
}

.reference-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
}

.reference-meta {
  color: var(--text-muted);
  font-size: 12px;
}

.assistant-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 12px;
}

.assistant-action {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 6px 12px;
  border: 1px solid rgba(157, 91, 47, 0.16);
  border-radius: 999px;
  background: rgba(255, 251, 245, 0.92);
  color: var(--text-secondary);
  cursor: pointer;
  transition:
    border-color 180ms ease,
    color 180ms ease,
    background 180ms ease;
}

.assistant-action strong {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 22px;
  padding: 1px 6px;
  border-radius: 999px;
  background: rgba(157, 91, 47, 0.12);
  color: var(--brand-strong);
  font-size: 12px;
}

.assistant-action:hover {
  border-color: rgba(157, 91, 47, 0.3);
  color: var(--brand-strong);
  background: rgba(255, 247, 238, 0.96);
}

.assistant-reference-toggle {
  font-size: 12px;
}

.assistant-feedback {
  display: inline-flex;
  gap: 6px;
  padding: 4px;
  border-radius: 999px;
  background: rgba(243, 234, 222, 0.82);
}

.feedback-hint,
.message-usage {
  margin-top: 12px;
}

.feedback-hint {
  color: var(--text-muted);
  font-size: 12px;
}

.message-usage {
  margin-top: 12px;
}

.pending-error {
  margin-top: 12px;
  color: #b04d35;
}

.composer-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 18px 20px 20px;
  border-top: 1px solid rgba(122, 89, 53, 0.08);
  background: rgba(255, 252, 248, 0.92);
}

.composer-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.composer-tools {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.composer-hint {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--text-secondary);
}

.composer-kb-strip {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  flex-wrap: wrap;
  padding: 12px 14px;
  border-radius: 18px;
  background: rgba(255, 250, 244, 0.88);
  border: 1px dashed rgba(157, 91, 47, 0.22);
}

.composer-kb-strip-label {
  color: var(--text-secondary);
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.composer-kb-strip-tags {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.composer-kb-strip-empty {
  color: var(--text-muted);
  font-size: 12px;
}

.composer-kb-picker {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.composer-kb-picker-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.composer-kb-picker-head strong {
  display: block;
}

.composer-kb-picker-head span {
  display: block;
  margin-top: 6px;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.6;
}

.composer-kb-picker-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 280px;
  overflow-y: auto;
  padding-right: 4px;
}

.composer-kb-picker-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  padding: 12px 14px;
  border: 1px solid rgba(122, 89, 53, 0.1);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.9);
  color: inherit;
  cursor: pointer;
  text-align: left;
  transition:
    border-color 180ms ease,
    box-shadow 180ms ease,
    transform 180ms ease;
}

.composer-kb-picker-item:hover,
.composer-kb-picker-item.is-selected {
  transform: translateY(-1px);
  border-color: rgba(157, 91, 47, 0.24);
  box-shadow: 0 10px 24px rgba(91, 58, 24, 0.08);
}

.composer-kb-picker-item small {
  color: var(--text-muted);
  font-size: 12px;
}

.composer-kb-picker-copy {
  min-width: 0;
}

.composer-kb-picker-copy strong,
.composer-kb-picker-copy span {
  display: block;
}

.composer-kb-picker-copy span {
  margin-top: 4px;
  color: var(--text-muted);
  font-size: 12px;
}

.composer-kb-picker-empty {
  display: grid;
  place-items: center;
  min-height: 120px;
  color: var(--text-muted);
  font-size: 12px;
}

.composer-input-shell {
  position: relative;
}

.composer-mention-panel {
  margin-bottom: 10px;
  padding: 14px;
  border: 1px solid rgba(157, 91, 47, 0.16);
  border-radius: 20px;
  background: rgba(255, 250, 244, 0.96);
  box-shadow: 0 18px 36px rgba(91, 58, 24, 0.1);
}

.composer-mention-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.composer-mention-head strong {
  display: block;
}

.composer-mention-head span {
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.6;
}

.composer-mention-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 220px;
  margin-top: 12px;
  overflow-y: auto;
  padding-right: 4px;
}

.composer-mention-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  padding: 12px 14px;
  border: 1px solid rgba(122, 89, 53, 0.1);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.94);
  color: inherit;
  cursor: pointer;
  text-align: left;
  transition:
    border-color 180ms ease,
    background 180ms ease,
    box-shadow 180ms ease,
    transform 180ms ease;
}

.composer-mention-item:hover,
.composer-mention-item.is-active,
.composer-mention-item.is-selected {
  transform: translateY(-1px);
  border-color: rgba(157, 91, 47, 0.26);
  box-shadow: 0 12px 24px rgba(91, 58, 24, 0.08);
}

.composer-mention-item.is-active {
  background: rgba(255, 245, 234, 0.96);
}

.composer-mention-item.is-selected {
  background: rgba(246, 237, 226, 0.96);
}

.composer-mention-item small {
  color: var(--text-muted);
  font-size: 12px;
}

.composer-mention-copy {
  min-width: 0;
}

.composer-mention-copy strong,
.composer-mention-copy span {
  display: block;
}

.composer-mention-copy span {
  margin-top: 4px;
  color: var(--text-muted);
  font-size: 12px;
}

.composer-mention-empty {
  display: grid;
  place-items: center;
  min-height: 84px;
  margin-top: 12px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.84);
  color: var(--text-muted);
  font-size: 12px;
}

.composer-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.composer-error {
  margin: 0;
  color: #b04d35;
}

.composer-action-buttons {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-left: auto;
}

@media (max-width: 1280px) {
  .hero-summary,
  .control-strip,
  .conversation-grid.has-session-list {
    grid-template-columns: 1fr;
  }

  .workspace-hero,
  .composer-head,
  .composer-actions,
  .conversation-toolbar {
    flex-direction: column;
    align-items: flex-start;
  }
}

@media (max-width: 900px) {
  .message-bubble {
    max-width: 100%;
  }
}
</style>
