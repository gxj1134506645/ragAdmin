<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowDown, ChatDotRound, MoreFilled, Plus, RefreshRight, SwitchButton } from '@element-plus/icons-vue'
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
import { resolveErrorMessage } from '@/api/http'
import { listKnowledgeBases } from '@/api/knowledge-base'
import { listModels } from '@/api/model'
import { useAuthStore } from '@/stores/auth'
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

type PreferenceSyncStatus = 'IDLE' | 'SAVING' | 'SAVED' | 'FAILED'

interface StreamRecoveryNotice {
  tone: 'connection' | 'model'
  title: string
  description: string
}

const props = defineProps<Props>()
const router = useRouter()
const authStore = useAuthStore()
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
const modelPickerVisible = ref(false)
const knowledgeBasePickerKeyword = ref('')
const knowledgeBaseMentionState = ref<KnowledgeBaseMentionState | null>(null)
const knowledgeBaseMentionActiveIndex = ref(0)
const preferencePersistencePauseDepth = ref(0)
const preferenceSyncStatus = ref<PreferenceSyncStatus>('IDLE')
const preferenceSyncErrorMessage = ref('')
const preferenceSyncPendingCount = ref(0)

let streamHandle: ChatStreamHandle | null = null
let composerBlurTimer: number | null = null
let sessionMetadataSyncTask: Promise<void> = Promise.resolve()
let sessionKnowledgeBaseSyncTask: Promise<void> = Promise.resolve()
let preferenceSyncResetTimer: number | null = null

const isKnowledgeBaseScene = computed(() => props.sceneType === 'KNOWLEDGE_BASE')
const activeKnowledgeBase = computed<KnowledgeBaseSummary | null>(() => {
  if (!isKnowledgeBaseScene.value || typeof props.anchorKbId !== 'number') {
    return null
  }
  return availableKnowledgeBases.value.find((item) => item.id === props.anchorKbId) ?? null
})
const workspaceTitle = computed(() => {
  return isKnowledgeBaseScene.value
    ? (activeKnowledgeBase.value?.kbName || '知识库问答')
    : '通用问答'
})
const sessionPanelTitle = computed(() => {
  return isKnowledgeBaseScene.value ? '知识库会话' : '通用会话'
})
const sessionEmptyText = computed(() => {
  return isKnowledgeBaseScene.value ? '当前知识库还没有聊天会话' : '还没有通用会话，发送第一条问题后会自动创建'
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
  return !streaming.value && !isKnowledgeBaseScene.value && knowledgeBaseMentionState.value !== null
})

const sidebarKnowledgeBases = computed<KnowledgeBaseSummary[]>(() => {
  return availableKnowledgeBases.value
})

const defaultChatModel = computed<ModelSummary | null>(() => {
  return availableModels.value.find((item) => item.isDefaultChatModel) ?? null
})

const currentModelName = computed(() => {
  if (!selectedModelId.value) {
    return defaultChatModel.value?.modelName || '系统默认模型'
  }
  return availableModels.value.find((item) => item.id === selectedModelId.value)?.modelName || '指定模型'
})
const accountInitial = computed(() => {
  const source = authStore.displayName || authStore.currentUser?.username || 'U'
  return source.slice(0, 1).toUpperCase()
})

function handleOpenGeneralChat(): void {
  if (streaming.value || !isKnowledgeBaseScene.value) {
    return
  }
  void router.push({ name: 'app-chat-home' })
}

function handleOpenKnowledgeBase(kbId: number): void {
  if (streaming.value) {
    return
  }
  if (isKnowledgeBaseScene.value && props.anchorKbId === kbId) {
    return
  }
  void router.push({
    name: 'app-knowledge-base-chat',
    params: {
      kbId: String(kbId),
    },
  })
}

function handleSelectModel(modelId?: number): void {
  if (streaming.value) {
    return
  }
  selectedModelId.value = modelId
  modelPickerVisible.value = false
}

async function handleLogout(): Promise<void> {
  try {
    await authStore.logout()
    await router.push('/login')
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error))
  }
}

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

const showPreferenceSyncNotice = computed(() => {
  return activeSession.value !== null && preferenceSyncStatus.value !== 'IDLE'
})

const preferenceSyncNoticeTitle = computed(() => {
  switch (preferenceSyncStatus.value) {
    case 'SAVING':
      return '偏好保存中'
    case 'SAVED':
      return '偏好已保存'
    case 'FAILED':
      return '偏好保存失败'
    default:
      return ''
  }
})

const preferenceSyncNoticeText = computed(() => {
  switch (preferenceSyncStatus.value) {
    case 'SAVING':
      return '正在同步当前会话的模型、联网和知识库选择。'
    case 'SAVED':
      return '当前会话偏好已同步到服务端。'
    case 'FAILED':
      return preferenceSyncErrorMessage.value || '本次偏好同步失败，请稍后重试。'
    default:
      return ''
  }
})

const preferenceSyncNoticeClass = computed(() => {
  switch (preferenceSyncStatus.value) {
    case 'SAVING':
      return 'is-saving'
    case 'SAVED':
      return 'is-saved'
    case 'FAILED':
      return 'is-failed'
    default:
      return ''
  }
})

const pendingStreamRecoveryNotice = computed<StreamRecoveryNotice | null>(() => {
  const errorMessage = pendingExchange.value?.errorMessage?.trim()
  if (!errorMessage) {
    return null
  }

  if (isConnectionInterruptedMessage(errorMessage)) {
    return {
      tone: 'connection',
      title: '连接已中断',
      description: '当前回答尚未完整返回。你可以点击“重新连接并发送”，或等待网络恢复后再次提问。',
    }
  }

  return {
    tone: 'model',
    title: '模型返回失败',
    description: '这次失败来自模型或服务端返回。建议检查模型配置、联网开关或知识库范围后再重试。',
  }
})

const retryPendingExchangeLabel = computed(() => {
  return pendingStreamRecoveryNotice.value?.tone === 'connection' ? '重新连接并发送' : '重新发送'
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

function clearPreferenceSyncResetTimer(): void {
  if (preferenceSyncResetTimer === null) {
    return
  }
  window.clearTimeout(preferenceSyncResetTimer)
  preferenceSyncResetTimer = null
}

function resetPreferenceSyncState(): void {
  clearPreferenceSyncResetTimer()
  preferenceSyncPendingCount.value = 0
  preferenceSyncStatus.value = 'IDLE'
  preferenceSyncErrorMessage.value = ''
}

function beginPreferenceSync(sessionId: number): void {
  if (activeSessionId.value !== sessionId) {
    return
  }
  clearPreferenceSyncResetTimer()
  preferenceSyncPendingCount.value += 1
  preferenceSyncStatus.value = 'SAVING'
  preferenceSyncErrorMessage.value = ''
}

function completePreferenceSyncSuccess(sessionId: number): void {
  if (activeSessionId.value !== sessionId) {
    return
  }

  preferenceSyncPendingCount.value = Math.max(0, preferenceSyncPendingCount.value - 1)
  if (preferenceSyncPendingCount.value > 0) {
    preferenceSyncStatus.value = 'SAVING'
    return
  }

  preferenceSyncStatus.value = 'SAVED'
  preferenceSyncResetTimer = window.setTimeout(() => {
    if (activeSessionId.value === sessionId && preferenceSyncStatus.value === 'SAVED') {
      preferenceSyncStatus.value = 'IDLE'
    }
    preferenceSyncResetTimer = null
  }, 1800)
}

function completePreferenceSyncFailure(sessionId: number, errorMessage: string): void {
  if (activeSessionId.value !== sessionId) {
    return
  }
  clearPreferenceSyncResetTimer()
  preferenceSyncPendingCount.value = Math.max(0, preferenceSyncPendingCount.value - 1)
  preferenceSyncStatus.value = 'FAILED'
  preferenceSyncErrorMessage.value = errorMessage
}

function suspendPreferencePersistence(mutator: () => void): void {
  preferencePersistencePauseDepth.value += 1
  mutator()
  void nextTick(() => {
    preferencePersistencePauseDepth.value = Math.max(0, preferencePersistencePauseDepth.value - 1)
  })
}

function applySessionPreferences(session: ChatSession | null): void {
  resetPreferenceSyncState()
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

function isConnectionInterruptedMessage(message: string): boolean {
  return /流式连接已中断|连接已中断|网络|network|fetch|timeout|timed out|连接失败|断开/i.test(message)
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
  beginPreferenceSync(payload.sessionId)
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
    completePreferenceSyncSuccess(payload.sessionId)
  } catch (error) {
    completePreferenceSyncFailure(payload.sessionId, resolveChatStreamError(error))
  }
}

async function persistSessionKnowledgeBases(payload: SessionKnowledgeBasePayload): Promise<void> {
  beginPreferenceSync(payload.sessionId)
  try {
    const updated = await updateSessionKnowledgeBases(payload.sessionId, {
      selectedKbIds: payload.selectedKbIds,
    })
    replaceSessionLocal(payload.sessionId, {
      selectedKbIds: [...updated.selectedKbIds],
    })
    completePreferenceSyncSuccess(payload.sessionId)
  } catch (error) {
    completePreferenceSyncFailure(payload.sessionId, resolveChatStreamError(error))
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
  resetPreferenceSyncState()
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
  if (streaming.value || isKnowledgeBaseScene.value) {
    return
  }
  if (selectedKbIds.value.includes(kbId)) {
    selectedKbIds.value = normalizeSelectedKbIds(selectedKbIds.value.filter((id) => id !== kbId))
    return
  }
  selectedKbIds.value = normalizeSelectedKbIds([...selectedKbIds.value, kbId])
}

function removeSelectedKnowledgeBase(kbId: number): void {
  if (streaming.value || isKnowledgeBaseScene.value) {
    return
  }
  selectedKbIds.value = normalizeSelectedKbIds(selectedKbIds.value.filter((id) => id !== kbId))
}

function clearSelectedKnowledgeBases(): void {
  if (streaming.value || isKnowledgeBaseScene.value) {
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
  if (streaming.value) {
    return
  }
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
  clearPreferenceSyncResetTimer()
})
</script>

<template>
  <section class="workspace-shell">
    <aside class="workspace-sidebar app-shell-panel">
      <div class="sidebar-section">
        <el-button
          class="sidebar-primary-action"
          type="primary"
          :icon="Plus"
          :disabled="streaming"
          @click="handleStartNewSession"
        >
          新会话
        </el-button>
        <button
          type="button"
          class="sidebar-entry sidebar-entry-summary"
          :class="{ 'is-active': !isKnowledgeBaseScene }"
          :disabled="streaming"
          @click="handleOpenGeneralChat"
        >
          <div class="sidebar-entry-copy">
            <strong>通用会话</strong>
            <small>默认纯模型对话，按需临时接入知识库。</small>
          </div>
        </button>
      </div>

      <div class="sidebar-section sidebar-knowledge">
        <div class="sidebar-section-head">
          <div>
            <p>知识库</p>
          </div>
          <small v-if="sidebarKnowledgeBases.length > 0">{{ sidebarKnowledgeBases.length }}</small>
        </div>
        <div class="sidebar-list thin-scrollbar">
          <div v-if="optionLoading" class="sidebar-placeholder">正在加载知识库...</div>
          <button
            v-for="knowledgeBase in sidebarKnowledgeBases"
            :key="knowledgeBase.id"
            type="button"
            class="sidebar-entry"
            :class="{ 'is-active': isKnowledgeBaseScene && props.anchorKbId === knowledgeBase.id }"
            :disabled="streaming"
            @click="handleOpenKnowledgeBase(knowledgeBase.id)"
          >
            <strong>{{ knowledgeBase.kbName }}</strong>
            <span>{{ knowledgeBase.kbCode }}</span>
          </button>
          <div v-if="!optionLoading && sidebarKnowledgeBases.length === 0" class="sidebar-placeholder">
            暂无知识库
          </div>
        </div>
      </div>

      <div class="sidebar-section sidebar-sessions">
        <div class="sidebar-section-head">
          <div>
            <p>{{ sessionPanelTitle }}</p>
          </div>
          <small>{{ sessions.length }}</small>
        </div>
        <div class="sidebar-list thin-scrollbar">
          <div v-if="sessionLoading" class="sidebar-placeholder">正在加载会话...</div>
          <div
            v-for="session in sessions"
            :key="session.id"
            class="session-entry"
            :class="{ 'is-active': session.id === activeSessionId }"
          >
            <button
              type="button"
              class="session-entry-main"
              :disabled="streaming"
              @click="handleSelectSession(session)"
            >
              <strong>{{ session.sessionName }}</strong>
            </button>
            <el-dropdown
              trigger="click"
              :teleported="false"
              :disabled="streaming || sessionActionLoadingId === session.id"
              @command="handleSessionCommand(session, $event)"
            >
              <el-button
                text
                circle
                class="session-entry-trigger"
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
          <div v-if="!sessionLoading && sessions.length === 0" class="sidebar-placeholder">
            {{ sessionEmptyText }}
          </div>
        </div>
      </div>

      <div class="sidebar-footer">
        <el-dropdown
          class="sidebar-account-dropdown"
          placement="top-start"
          trigger="click"
          :teleported="false"
          @command="handleLogout"
        >
          <button type="button" class="sidebar-account-button">
            <span class="sidebar-account-avatar">{{ accountInitial }}</span>
            <span class="sidebar-account-copy">
              <strong>{{ authStore.displayName }}</strong>
              <small>个人中心</small>
            </span>
            <el-icon class="sidebar-account-arrow"><ArrowDown /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">
                <el-icon><SwitchButton /></el-icon>
                退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </aside>

    <section class="workspace-main app-shell-panel">
      <header class="workspace-toolbar">
        <div class="toolbar-copy">
          <h1>{{ workspaceTitle }}</h1>
        </div>
        <div class="toolbar-actions">
          <el-button text :icon="ChatDotRound" :disabled="streaming" @click="handleClearView">清空视图</el-button>
          <el-button text :icon="RefreshRight" :disabled="streaming" @click="initialize">刷新</el-button>
        </div>
      </header>

      <div v-if="streaming || showPreferenceSyncNotice || loadingError" class="workspace-status">
        <div v-if="streaming" class="status-pill is-streaming">
          <strong>回答生成中</strong>
          <span>当前会话已锁定，避免中途切换模型、联网或知识库范围。</span>
        </div>
        <div v-if="showPreferenceSyncNotice" class="status-pill" :class="preferenceSyncNoticeClass">
          <strong>{{ preferenceSyncNoticeTitle }}</strong>
          <span>{{ preferenceSyncNoticeText }}</span>
        </div>
        <div v-if="loadingError" class="status-pill is-error">
          <strong>加载失败</strong>
          <span>{{ loadingError }}</span>
        </div>
      </div>

      <div ref="messageContainerRef" class="conversation-body page-scrollbar">
        <div v-if="messageLoading" class="conversation-placeholder">正在加载历史消息...</div>

        <template v-else-if="hasConversation">
          <div v-for="message in messages" :key="message.id" class="message-thread">
            <article class="message-row is-user">
              <div class="message-card is-user">
                <span class="message-role">你</span>
                <p>{{ message.questionText }}</p>
              </div>
            </article>

            <article class="message-row is-assistant">
              <div class="message-card is-assistant">
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
              </div>
            </article>
          </div>

          <div v-if="pendingExchange" class="message-thread">
            <article class="message-row is-user">
              <div class="message-card is-user">
                <span class="message-role">你</span>
                <p>{{ pendingExchange.question }}</p>
              </div>
            </article>
            <article class="message-row is-assistant">
              <div class="message-card is-assistant" :class="{ 'is-error': pendingExchange.errorMessage }">
                <span class="message-role">助手</span>
                <p>{{ pendingExchange.answer || '正在生成回答...' }}</p>
                <p v-if="pendingExchange.errorMessage" class="pending-error">{{ pendingExchange.errorMessage }}</p>
                <div
                  v-if="pendingStreamRecoveryNotice"
                  class="pending-recovery-card"
                  :class="`is-${pendingStreamRecoveryNotice.tone}`"
                >
                  <strong>{{ pendingStreamRecoveryNotice.title }}</strong>
                  <span>{{ pendingStreamRecoveryNotice.description }}</span>
                </div>
                <el-button v-if="pendingExchange.errorMessage" text :icon="RefreshRight" @click="handleRetryPendingExchange">
                  {{ retryPendingExchangeLabel }}
                </el-button>
              </div>
            </article>
          </div>
        </template>

        <div v-else class="conversation-empty">
          <h2>{{ workspaceTitle }}</h2>
          <p>{{ isKnowledgeBaseScene ? '围绕当前知识库开始提问' : '开始一轮新的提问' }}</p>
        </div>
      </div>

      <footer class="composer-card">
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
                :disabled="streaming"
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
            :rows="4"
            resize="none"
            :placeholder="composerPlaceholder"
            @blur="handleComposerBlur"
            @click="handleComposerCursorChange"
            @focus="handleComposerFocus"
            @keydown="handleComposerKeydown"
            @keyup="handleComposerCursorChange"
          />
          <div class="composer-bottom-bar">
            <div class="composer-bottom-left">
              <div class="composer-toolbelt">
                <el-popover
                  v-model:visible="modelPickerVisible"
                  placement="top-start"
                  :width="320"
                  trigger="click"
                  :teleported="false"
                  :disabled="streaming"
                  popper-class="workspace-picker-popper"
                >
                  <template #reference>
                    <button type="button" class="composer-tool-button" :disabled="streaming">
                      <span>模型</span>
                      <strong>{{ currentModelName }}</strong>
                    </button>
                  </template>
                  <div class="picker-panel">
                    <div class="picker-panel-head">
                      <strong>选择模型</strong>
                      <span>不选择时使用当前默认聊天模型</span>
                    </div>
                    <div class="picker-option-list thin-scrollbar">
                      <button
                        type="button"
                        class="picker-option"
                        :class="{ 'is-active': !selectedModelId }"
                        @click="handleSelectModel()"
                      >
                        <strong>系统默认模型</strong>
                        <span>
                          {{ defaultChatModel ? `当前为 ${defaultChatModel.modelName} / ${defaultChatModel.modelCode}` : '由服务端默认配置决定' }}
                        </span>
                      </button>
                      <button
                        v-for="model in availableModels"
                        :key="model.id"
                        type="button"
                        class="picker-option"
                        :class="{ 'is-active': selectedModelId === model.id }"
                        @click="handleSelectModel(model.id)"
                      >
                        <strong>{{ model.isDefaultChatModel ? `${model.modelName} · 默认` : model.modelName }}</strong>
                        <span>{{ model.modelCode }}</span>
                      </button>
                    </div>
                  </div>
                </el-popover>

                <el-popover
                  v-if="!isKnowledgeBaseScene"
                  v-model:visible="knowledgeBasePickerVisible"
                  placement="top-start"
                  :width="360"
                  trigger="click"
                  :disabled="streaming"
                  :teleported="false"
                  popper-class="workspace-picker-popper"
                >
                  <template #reference>
                    <button type="button" class="composer-tool-button" :disabled="streaming">
                      <span>知识库</span>
                      <strong>{{ selectedKbIds.length > 0 ? `${selectedKbIds.length} 个已选` : '多选知识库' }}</strong>
                    </button>
                  </template>
                  <div class="range-picker">
                    <div class="range-picker-head">
                      <div>
                        <strong>选择本轮检索知识库</strong>
                        <span>支持多选，不选则走纯模型回答</span>
                      </div>
                      <el-button
                        text
                        size="small"
                        :disabled="streaming || selectedKbIds.length === 0"
                        @click="clearSelectedKnowledgeBases"
                      >
                        清空
                      </el-button>
                    </div>
                    <el-input
                      v-model="knowledgeBasePickerKeyword"
                      clearable
                      :disabled="streaming"
                      placeholder="搜索知识库编码或名称"
                    />
                    <div class="range-picker-list thin-scrollbar">
                      <button
                        v-for="knowledgeBase in filteredKnowledgeBases"
                        :key="knowledgeBase.id"
                        type="button"
                        class="range-picker-item"
                        :class="{ 'is-selected': selectedKbIds.includes(knowledgeBase.id) }"
                        :disabled="streaming"
                        @click="toggleKnowledgeBaseSelection(knowledgeBase.id)"
                      >
                        <div class="range-picker-copy">
                          <strong>{{ knowledgeBase.kbName }}</strong>
                          <span>{{ knowledgeBase.kbCode }}</span>
                        </div>
                        <small>{{ selectedKbIds.includes(knowledgeBase.id) ? '已选择' : '点击接入' }}</small>
                      </button>
                      <div v-if="filteredKnowledgeBases.length === 0" class="range-picker-empty">
                        未找到匹配知识库
                      </div>
                    </div>
                  </div>
                </el-popover>

                <button
                  type="button"
                  class="composer-tool-button"
                  :class="{ 'is-active': webSearchEnabled }"
                  :disabled="streaming"
                  @click="webSearchEnabled = !webSearchEnabled"
                >
                  <span>联网</span>
                  <strong>{{ webSearchEnabled ? '开启' : '关闭' }}</strong>
                </button>
              </div>

              <div v-if="!isKnowledgeBaseScene" class="composer-footer-tags">
                <el-tag
                  v-for="knowledgeBase in selectedKnowledgeBases"
                  :key="knowledgeBase.id"
                  :closable="!isKnowledgeBaseScene && !streaming"
                  effect="plain"
                  type="info"
                  @close="removeSelectedKnowledgeBase(knowledgeBase.id)"
                >
                  @{{ knowledgeBase.kbName }}
                </el-tag>
                <span v-if="selectedKnowledgeBases.length === 0" class="composer-footer-hint">
                  输入 @ 接入知识库，Enter 发送
                </span>
              </div>
            </div>

            <div class="composer-footer-actions">
              <el-tag
                v-if="isKnowledgeBaseScene"
                effect="plain"
                type="warning"
              >
                当前知识库已固定接入
              </el-tag>
              <el-button
                type="primary"
                :loading="streaming"
                :disabled="!draftQuestion.trim()"
                @click="handleSendQuestion"
              >
                {{ streaming ? '生成中...' : '发送' }}
              </el-button>
            </div>
          </div>
        </div>
      </footer>
    </section>
  </section>
</template>

<style scoped>
.workspace-shell {
  --surface-base: rgba(255, 252, 248, 0.94);
  --surface-muted: rgba(248, 244, 238, 0.92);
  --surface-strong: rgba(255, 255, 255, 0.78);
  --border-soft: rgba(122, 89, 53, 0.08);
  --border-medium: rgba(122, 89, 53, 0.14);
  --accent-soft: rgba(157, 91, 47, 0.1);
  --accent-medium: rgba(157, 91, 47, 0.24);
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 16px;
  flex: 1;
  height: calc(100vh - 40px);
  min-height: calc(100vh - 40px);
}

.workspace-sidebar,
.workspace-main {
  min-height: 0;
  border-radius: 28px;
  overflow: hidden;
}

.workspace-sidebar {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px 12px 14px;
  background:
    linear-gradient(180deg, rgba(252, 248, 242, 0.96), rgba(248, 243, 236, 0.92));
}

.sidebar-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.sidebar-sessions {
  flex: 1;
  min-height: 0;
}

.sidebar-knowledge {
  min-height: 0;
}

.sidebar-primary-action {
  width: 100%;
}

.sidebar-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 8px;
}

.sidebar-section-head p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.sidebar-section-head small {
  color: var(--text-muted);
  font-size: 12px;
}

.sidebar-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 0;
  overflow-y: auto;
  padding-right: 4px;
}

.sidebar-knowledge .sidebar-list {
  max-height: 220px;
}

.sidebar-sessions .sidebar-list {
  flex: 1;
  min-height: 240px;
}

.sidebar-footer {
  padding-top: 6px;
  border-top: 1px solid var(--border-soft);
}

.sidebar-account-dropdown {
  display: block;
  width: 100%;
}

.sidebar-footer :deep(.el-dropdown) {
  display: block;
  width: 100%;
}

.sidebar-entry,
.session-entry {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 11px 12px 11px 14px;
  border: 1px solid transparent;
  border-radius: 16px;
  background: transparent;
  color: inherit;
  transition:
    background 180ms ease,
    border-color 180ms ease,
    transform 180ms ease;
}

.sidebar-entry {
  cursor: pointer;
  text-align: left;
}

.sidebar-entry-summary {
  align-items: flex-start;
}

.sidebar-entry:hover,
.sidebar-entry.is-active,
.session-entry:hover,
.session-entry.is-active {
  transform: translateY(-1px);
  border-color: rgba(122, 89, 53, 0.1);
  background: rgba(255, 255, 255, 0.52);
}

.sidebar-entry.is-active,
.session-entry.is-active {
  border-color: var(--accent-medium);
  background: rgba(255, 247, 238, 0.82);
}

.sidebar-entry:disabled,
.session-entry-main:disabled {
  cursor: not-allowed;
}

.sidebar-entry strong,
.sidebar-entry span,
.session-entry-main strong,
.session-entry-main span {
  display: block;
}

.sidebar-entry-copy {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 3px;
}

.sidebar-entry-copy strong,
.sidebar-entry-copy small {
  display: block;
  min-width: 0;
}

.sidebar-entry-copy strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  font-weight: 600;
}

.sidebar-entry-copy small {
  color: var(--text-muted);
  font-size: 10px;
  line-height: 1.45;
}

.sidebar-entry strong,
.session-entry-main strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  font-weight: 600;
}

.sidebar-entry span,
.session-entry-main span {
  margin-top: 2px;
  color: var(--text-muted);
  font-size: 11px;
  line-height: 1.5;
}

.session-entry {
  padding-right: 6px;
}

.session-entry-main {
  flex: 1;
  min-width: 0;
  padding: 0;
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  text-align: left;
}

.session-entry-trigger {
  color: var(--text-muted);
}

.session-entry.is-active .session-entry-trigger,
.session-entry-trigger:hover {
  color: var(--brand-strong);
}

.sidebar-placeholder {
  display: grid;
  place-items: center;
  min-height: 96px;
  padding: 16px;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.6;
  text-align: center;
}

.sidebar-account-button {
  display: flex;
  align-items: center;
  width: 100%;
  gap: 10px;
  padding: 10px 10px 10px 12px;
  border: 1px solid transparent;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.52);
  color: inherit;
  cursor: pointer;
  text-align: left;
  transition:
    background 180ms ease,
    border-color 180ms ease,
    transform 180ms ease;
}

.sidebar-account-button:hover {
  transform: translateY(-1px);
  border-color: rgba(122, 89, 53, 0.1);
  background: rgba(255, 255, 255, 0.68);
}

.sidebar-account-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 999px;
  background: rgba(157, 91, 47, 0.14);
  color: var(--brand-strong);
  font-size: 12px;
  font-weight: 700;
  flex: none;
}

.sidebar-account-copy {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 2px;
}

.sidebar-account-copy strong,
.sidebar-account-copy small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sidebar-account-copy strong {
  font-size: 13px;
  font-weight: 600;
}

.sidebar-account-copy small {
  color: var(--text-muted);
  font-size: 11px;
}

.sidebar-account-arrow {
  color: var(--text-muted);
  flex: none;
}

.workspace-main {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.68), rgba(255, 252, 248, 0.92));
}

.workspace-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 24px 12px;
}

.toolbar-copy {
  display: flex;
  flex-direction: column;
  gap: 0;
  max-width: 760px;
}

.toolbar-copy h1 {
  margin: 0;
  font-size: clamp(24px, 3vw, 34px);
  line-height: 1.04;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.range-picker {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.range-picker-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.range-picker-head strong {
  display: block;
  color: var(--text-secondary);
  font-size: 14px;
}

.range-picker-head span {
  display: block;
  margin-top: 4px;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.6;
}

.range-picker-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 280px;
  overflow-y: auto;
  padding-right: 4px;
}

.range-picker-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  padding: 12px 14px;
  border: 1px solid var(--border-soft);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.92);
  color: inherit;
  cursor: pointer;
  text-align: left;
  transition:
    transform 180ms ease,
    border-color 180ms ease,
    box-shadow 180ms ease;
}

.range-picker-item:hover,
.range-picker-item.is-selected {
  transform: translateY(-1px);
  border-color: var(--accent-medium);
  box-shadow: 0 14px 28px rgba(91, 58, 24, 0.08);
}

.range-picker-copy {
  min-width: 0;
}

.range-picker-copy strong,
.range-picker-copy span {
  display: block;
}

.range-picker-copy span,
.range-picker-item small {
  margin-top: 4px;
  color: var(--text-muted);
  font-size: 12px;
}

.range-picker-empty {
  display: grid;
  place-items: center;
  min-height: 100px;
  color: var(--text-muted);
  font-size: 12px;
}

.workspace-status {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 0 24px 12px;
}

.status-pill {
  display: flex;
  flex: 1;
  align-items: flex-start;
  gap: 10px;
  min-width: min(320px, 100%);
  padding: 12px 14px;
  border: 1px solid var(--border-medium);
  border-radius: 18px;
  background: var(--surface-base);
}

.status-pill strong {
  flex: none;
  color: var(--text-secondary);
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.status-pill span {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.7;
}

.status-pill.is-streaming {
  border-color: rgba(157, 91, 47, 0.18);
  background: rgba(255, 246, 235, 0.92);
}

.status-pill.is-saving {
  border-color: rgba(150, 117, 56, 0.18);
  background: rgba(255, 250, 238, 0.92);
}

.status-pill.is-saved {
  border-color: rgba(79, 132, 86, 0.18);
  background: rgba(244, 250, 244, 0.94);
}

.status-pill.is-failed,
.status-pill.is-error {
  border-color: rgba(176, 77, 53, 0.22);
  background: rgba(255, 244, 240, 0.94);
}

.conversation-body {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  gap: 22px;
  min-height: 0;
  overflow-y: auto;
  padding: 22px 28px;
}

.conversation-placeholder,
.conversation-empty {
  display: grid;
  place-items: center;
  min-height: 100%;
  color: var(--text-muted);
}

.conversation-empty {
  gap: 6px;
  padding: 24px;
  text-align: center;
}

.conversation-empty h2 {
  margin: 0;
  font-size: clamp(22px, 3vw, 30px);
  line-height: 1.06;
}

.conversation-empty p:last-child {
  max-width: 420px;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.7;
}

.message-thread {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.message-row {
  display: flex;
}

.message-row.is-user {
  justify-content: flex-end;
}

.message-row.is-assistant {
  justify-content: flex-start;
}

.message-card {
  max-width: min(88%, 860px);
  padding: 14px 16px;
  border-radius: 20px;
  line-height: 1.8;
}

.message-card p {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}

.message-card.is-user {
  color: #fffaf4;
  background: linear-gradient(150deg, #9d5b2f, #77411b);
}

.message-card.is-assistant {
  border: 1px solid var(--border-soft);
  background: var(--surface-strong);
}

.message-card.is-assistant.is-error {
  border-color: rgba(176, 77, 53, 0.24);
  background: rgba(255, 244, 240, 0.92);
}

.message-role {
  display: inline-flex;
  margin-bottom: 8px;
  opacity: 0.78;
  font-size: 10px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.assistant-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 12px;
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

.assistant-action {
  display: flex;
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

.reference-head,
.reference-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.reference-item p {
  margin-top: 8px;
  color: var(--text-secondary);
}

.reference-head span,
.reference-meta,
.feedback-hint,
.message-usage,
.pending-error {
  color: var(--text-muted);
  font-size: 12px;
}

.reference-actions {
  margin-top: 10px;
}

.feedback-hint,
.message-usage {
  margin-top: 12px;
}

.pending-error {
  margin-top: 12px;
  color: #b04d35;
}

.pending-recovery-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 12px;
  padding: 12px 14px;
  border: 1px solid var(--border-medium);
  border-radius: 16px;
  background: var(--surface-base);
}

.pending-recovery-card strong {
  color: var(--text-secondary);
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.pending-recovery-card span {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.7;
}

.pending-recovery-card.is-connection {
  border-color: rgba(157, 91, 47, 0.18);
  background: rgba(255, 246, 235, 0.92);
}

.pending-recovery-card.is-model {
  border-color: rgba(176, 77, 53, 0.22);
  background: rgba(255, 244, 240, 0.94);
}

.composer-card {
  position: sticky;
  bottom: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: auto;
  padding: 12px 20px 18px;
  border-top: 1px solid var(--border-soft);
  background:
    linear-gradient(180deg, rgba(255, 252, 248, 0), rgba(255, 252, 248, 0.9) 22%, rgba(255, 252, 248, 0.96));
  z-index: 3;
}

.composer-input-shell {
  position: relative;
  padding: 8px 10px 10px;
  border: 1px solid rgba(122, 89, 53, 0.12);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 10px 24px rgba(91, 58, 24, 0.04);
}

.composer-input-shell :deep(.el-textarea__inner) {
  min-height: 96px;
  padding: 6px 4px 0;
  border: none;
  box-shadow: none;
  background: transparent;
  color: inherit;
  font-size: 14px;
  line-height: 1.75;
}

.composer-input-shell :deep(.el-textarea__inner:focus) {
  box-shadow: none;
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

.composer-bottom-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid rgba(122, 89, 53, 0.08);
}

.composer-bottom-left {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  gap: 8px;
}

.composer-toolbelt {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.composer-tool-button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border: 1px solid rgba(122, 89, 53, 0.12);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.84);
  color: inherit;
  cursor: pointer;
  transition:
    border-color 180ms ease,
    background 180ms ease,
    color 180ms ease;
}

.composer-tool-button:hover {
  border-color: var(--accent-medium);
  color: var(--brand-strong);
  background: rgba(255, 248, 240, 0.92);
}

.composer-tool-button:disabled {
  cursor: not-allowed;
  opacity: 0.68;
}

.composer-tool-button.is-active {
  border-color: var(--accent-medium);
  background: rgba(255, 244, 232, 0.92);
  color: var(--brand-strong);
}

.composer-tool-button span,
.composer-tool-button strong {
  display: block;
}

.composer-tool-button span {
  color: var(--text-muted);
  font-size: 11px;
}

.composer-tool-button strong {
  font-size: 11px;
  font-weight: 600;
}

.picker-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.picker-panel-head strong {
  display: block;
  color: var(--text-secondary);
  font-size: 14px;
}

.picker-panel-head span {
  display: block;
  margin-top: 4px;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.6;
}

.picker-option-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 280px;
  overflow-y: auto;
  padding-right: 4px;
}

.picker-option {
  display: flex;
  width: 100%;
  flex-direction: column;
  gap: 4px;
  padding: 12px 14px;
  border: 1px solid var(--border-soft);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.92);
  color: inherit;
  cursor: pointer;
  text-align: left;
  transition:
    transform 180ms ease,
    border-color 180ms ease,
    box-shadow 180ms ease;
}

.picker-option:hover,
.picker-option.is-active {
  transform: translateY(-1px);
  border-color: var(--accent-medium);
  box-shadow: 0 12px 24px rgba(91, 58, 24, 0.08);
}

.picker-option strong {
  font-size: 14px;
  font-weight: 600;
}

.picker-option span {
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.6;
}

.composer-footer-tags {
  display: flex;
  flex: 1;
  flex-wrap: wrap;
  gap: 6px;
  min-height: 28px;
}

.composer-footer-hint {
  color: var(--text-muted);
  font-size: 11px;
  line-height: 1.6;
}

.composer-footer-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  align-self: flex-end;
}

@media (max-width: 1280px) {
  .workspace-shell {
    grid-template-columns: 1fr;
  }

  .workspace-sidebar {
    max-height: none;
  }
}

@media (max-width: 960px) {
  .workspace-toolbar,
  .composer-bottom-bar {
    flex-direction: column;
    align-items: flex-start;
  }

  .conversation-body {
    padding: 20px 18px;
  }

  .workspace-toolbar,
  .workspace-status,
  .composer-card {
    padding-left: 18px;
    padding-right: 18px;
  }

  .message-card {
    max-width: 100%;
  }

  .composer-toolbelt,
  .assistant-actions,
  .reference-head,
  .reference-actions {
    align-items: flex-start;
    flex-direction: column;
  }

  .composer-footer-actions {
    align-self: stretch;
    justify-content: space-between;
  }
}

@media (max-width: 680px) {
  .workspace-sidebar,
  .workspace-main {
    border-radius: 22px;
  }

  .sidebar-section-head,
  .range-picker-head,
  .composer-mention-head {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
