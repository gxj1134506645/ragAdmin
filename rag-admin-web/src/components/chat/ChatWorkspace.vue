<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { CollectionTag, Plus, Promotion, RefreshRight, Tickets } from '@element-plus/icons-vue'
import { ElButton, ElEmpty, ElInput, ElMessage, ElSkeleton } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  createChatSession,
  listChatMessages,
  listChatSessions,
  resolveChatStreamError,
  submitChatFeedback,
  streamChatMessage,
  type ChatStreamHandle,
} from '@/api/chat'
import type { ChatExchange, ChatFeedbackType, ChatReference, ChatSceneType, ChatSession } from '@/types/chat'

interface Props {
  sceneType: ChatSceneType
  kbId?: number | null
  title: string
  description: string
  emptyTitle: string
  emptyDescription: string
}

interface PendingExchange {
  question: string
  answer: string
  references: ChatReference[]
  status: 'STREAMING' | 'FAILED'
  errorMessage: string | null
}

const props = defineProps<Props>()
const router = useRouter()

const sessionLoading = ref(false)
const messageLoading = ref(false)
const loadingError = ref('')
const sessions = ref<ChatSession[]>([])
const messages = ref<ChatExchange[]>([])
const activeSessionId = ref<number | null>(null)
const draftQuestion = ref('')
const streaming = ref(false)
const pendingExchange = ref<PendingExchange | null>(null)
const conversationBodyRef = ref<HTMLElement | null>(null)
const expandedReferenceMessageIds = ref<number[]>([])
const feedbackSubmittingMessageIds = ref<number[]>([])

let streamHandle: ChatStreamHandle | null = null

const isKnowledgeBaseScene = computed(() => props.sceneType === 'KNOWLEDGE_BASE')
const hasSessionSidebar = computed(() => isKnowledgeBaseScene.value)
const hasConversation = computed(() => messages.value.length > 0 || pendingExchange.value !== null)
const composerPlaceholder = computed(() =>
  isKnowledgeBaseScene.value
    ? '输入问题后，会基于当前知识库进行检索增强回答'
    : '输入问题后，将直接与默认聊天模型对话',
)

function resetConversationState(): void {
  messages.value = []
  pendingExchange.value = null
  expandedReferenceMessageIds.value = []
  loadingError.value = ''
}

function closeStream(): void {
  streamHandle?.close()
  streamHandle = null
  streaming.value = false
}

async function scrollToBottom(): Promise<void> {
  await nextTick()
  if (!conversationBodyRef.value) {
    return
  }
  conversationBodyRef.value.scrollTo({
    top: conversationBodyRef.value.scrollHeight,
    behavior: 'smooth',
  })
}

function buildSessionName(question: string): string {
  const normalized = question.trim().replace(/\s+/g, ' ')
  if (!normalized) {
    return isKnowledgeBaseScene.value ? '知识库新会话' : '首页通用会话'
  }
  return normalized.length <= 18 ? normalized : `${normalized.slice(0, 18)}...`
}

async function loadMessages(sessionId: number): Promise<void> {
  messageLoading.value = true
  loadingError.value = ''
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

async function ensureGeneralSession(): Promise<void> {
  if (sessions.value.length > 0) {
    return
  }
  const session = await createChatSession({
    sceneType: 'GENERAL',
    sessionName: '首页通用会话',
  })
  sessions.value = [session]
}

async function loadSessions(): Promise<void> {
  sessionLoading.value = true
  loadingError.value = ''
  try {
    const response = await listChatSessions({
      kbId: isKnowledgeBaseScene.value ? props.kbId ?? undefined : undefined,
      sceneType: props.sceneType,
      pageNo: 1,
      pageSize: 50,
    })
    sessions.value = response.list

    if (!isKnowledgeBaseScene.value) {
      await ensureGeneralSession()
    }

    if (activeSessionId.value !== null && sessions.value.some((item) => item.id === activeSessionId.value)) {
      await loadMessages(activeSessionId.value)
      return
    }

    const firstSession = sessions.value[0]
    if (firstSession) {
      activeSessionId.value = firstSession.id
      await loadMessages(firstSession.id)
      return
    }

    activeSessionId.value = null
    resetConversationState()
  } catch (error) {
    sessions.value = []
    activeSessionId.value = null
    resetConversationState()
    loadingError.value = resolveChatStreamError(error)
  } finally {
    sessionLoading.value = false
  }
}

async function initialize(): Promise<void> {
  closeStream()
  resetConversationState()
  await loadSessions()
}

async function handleSelectSession(sessionId: number): Promise<void> {
  if (sessionId === activeSessionId.value || streaming.value) {
    return
  }
  activeSessionId.value = sessionId
  resetConversationState()
  await loadMessages(sessionId)
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

function isFeedbackSubmitting(messageId: number): boolean {
  return feedbackSubmittingMessageIds.value.includes(messageId)
}

function markPendingExchangeFailed(errorMessage: string): void {
  if (!pendingExchange.value) {
    return
  }
  pendingExchange.value.status = 'FAILED'
  pendingExchange.value.errorMessage = errorMessage
}

function handleStartNewSession(): void {
  if (!isKnowledgeBaseScene.value || streaming.value) {
    return
  }
  activeSessionId.value = null
  resetConversationState()
}

async function ensureSessionForQuestion(question: string): Promise<number> {
  if (activeSessionId.value !== null) {
    return activeSessionId.value
  }

  const session = await createChatSession({
    sceneType: props.sceneType,
    kbId: isKnowledgeBaseScene.value ? props.kbId ?? undefined : undefined,
    sessionName: buildSessionName(question),
  })

  sessions.value = [session, ...sessions.value.filter((item) => item.id !== session.id)]
  activeSessionId.value = session.id
  return session.id
}

async function handleSendQuestion(questionOverride?: string): Promise<void> {
  const question = (questionOverride ?? draftQuestion.value).trim()
  if (!question || streaming.value) {
    return
  }
  if (isKnowledgeBaseScene.value && !props.kbId) {
    ElMessage.warning('当前知识库标识缺失，暂时无法发起问答')
    return
  }

  loadingError.value = ''
  if (!questionOverride) {
    draftQuestion.value = ''
  }
  closeStream()

  let sessionId: number
  try {
    sessionId = await ensureSessionForQuestion(question)
  } catch (error) {
    ElMessage.error(resolveChatStreamError(error))
    return
  }

  pendingExchange.value = {
    question,
    answer: '',
    references: [],
    status: 'STREAMING',
    errorMessage: null,
  }
  streaming.value = true
  await scrollToBottom()

  streamHandle = streamChatMessage(
    sessionId,
    {
      question,
      kbId: isKnowledgeBaseScene.value ? props.kbId ?? undefined : undefined,
    },
    {
      onEvent(event) {
        if (event.eventType === 'DELTA') {
          if (!pendingExchange.value) {
            return
          }
          pendingExchange.value.answer += event.delta ?? ''
          void scrollToBottom()
          return
        }

        if (event.eventType === 'COMPLETE') {
          const answerText = event.answer ?? pendingExchange.value?.answer ?? ''
          messages.value = [
            ...messages.value,
            {
              id: event.messageId ?? Date.now(),
              questionText: question,
              answerText,
              references: event.references ?? [],
              feedbackType: null,
              feedbackComment: null,
            },
          ]
          pendingExchange.value = null
          closeStream()
          void scrollToBottom()
          return
        }

        if (event.eventType === 'ERROR') {
          markPendingExchangeFailed(event.errorMessage || '流式问答失败')
          closeStream()
          ElMessage.error(event.errorMessage || '流式问答失败')
        }
      },
      onError(error) {
        const errorMessage = resolveChatStreamError(error)
        markPendingExchangeFailed(errorMessage)
        closeStream()
        ElMessage.error(errorMessage)
      },
    },
  )
}

function handleRetryPendingExchange(): void {
  if (!pendingExchange.value || pendingExchange.value.status !== 'FAILED') {
    return
  }
  void handleSendQuestion(pendingExchange.value.question)
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
    ElMessage.success(feedbackType === 'LIKE' ? '已记录为有帮助' : '已记录为待改进')
  } catch (error) {
    ElMessage.error(resolveChatStreamError(error))
  } finally {
    feedbackSubmittingMessageIds.value = feedbackSubmittingMessageIds.value.filter((id) => id !== messageId)
  }
}

async function handleOpenReference(reference: ChatReference): Promise<void> {
  if (!reference.documentId) {
    ElMessage.warning('当前引用未关联具体文档，暂时无法跳转')
    return
  }
  await router.push({
    name: 'document-detail',
    params: {
      id: reference.documentId,
    },
    query: {
      chunkId: String(reference.chunkId),
      ...(reference.chunkNo ? { chunkNo: String(reference.chunkNo) } : {}),
      source: 'chat',
    },
  })
}

function handleComposerKeydown(event: Event | KeyboardEvent): void {
  if (!(event instanceof KeyboardEvent)) {
    return
  }
  if (event.key !== 'Enter' || event.shiftKey) {
    return
  }
  event.preventDefault()
  void handleSendQuestion()
}

function handleSendButtonClick(): void {
  void handleSendQuestion()
}

watch(
  () => [props.sceneType, props.kbId],
  async () => {
    await initialize()
  },
)

onMounted(async () => {
  await initialize()
})

onUnmounted(() => {
  closeStream()
})
</script>

<template>
  <section class="chat-workspace soft-panel">
    <header class="chat-head">
      <div>
        <p class="chat-eyebrow">{{ isKnowledgeBaseScene ? '知识库问答' : '通用问答' }}</p>
        <h2>{{ title }}</h2>
        <p>{{ description }}</p>
      </div>
      <div class="chat-head-actions">
        <el-button :icon="RefreshRight" text @click="initialize">刷新会话</el-button>
        <el-button
          v-if="isKnowledgeBaseScene"
          :icon="Plus"
          type="primary"
          plain
          :disabled="streaming"
          @click="handleStartNewSession"
        >
          新建会话
        </el-button>
      </div>
    </header>

    <div class="chat-layout" :class="{ 'is-single': !hasSessionSidebar }">
      <aside v-if="hasSessionSidebar" class="session-panel">
        <div class="session-panel-head">
          <span>会话列表</span>
          <small>{{ sessions.length }} 个</small>
        </div>
        <el-skeleton v-if="sessionLoading" :rows="6" animated />
        <div v-else class="session-list">
          <button
            v-for="session in sessions"
            :key="session.id"
            type="button"
            class="session-item"
            :class="{ 'is-active': session.id === activeSessionId }"
            @click="handleSelectSession(session.id)"
          >
            <strong>{{ session.sessionName }}</strong>
            <span>{{ session.sceneType === 'KNOWLEDGE_BASE' ? '知识库上下文' : '通用上下文' }}</span>
          </button>
          <div v-if="sessions.length === 0" class="session-empty">
            <el-empty :description="emptyTitle">
              <template #description>
                <p>{{ emptyDescription }}</p>
              </template>
            </el-empty>
          </div>
        </div>
      </aside>

      <section class="conversation-panel">
        <div ref="conversationBodyRef" class="conversation-body">
          <el-skeleton v-if="messageLoading" :rows="8" animated />

          <template v-else-if="hasConversation">
            <div
              v-for="message in messages"
              :key="message.id"
              class="conversation-group"
            >
              <article class="bubble bubble-user">
                <span class="bubble-role">你</span>
                <p>{{ message.questionText }}</p>
              </article>
              <article class="bubble bubble-assistant">
                <span class="bubble-role">助手</span>
                <p>{{ message.answerText || '模型没有返回内容。' }}</p>
                <div class="assistant-actions">
                  <button
                    v-if="message.references.length > 0"
                    type="button"
                    class="assistant-action assistant-reference-toggle"
                    @click="toggleReferencePanel(message.id)"
                  >
                    <el-icon><CollectionTag /></el-icon>
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
                <p v-if="message.feedbackType" class="feedback-hint">
                  {{ message.feedbackType === 'LIKE' ? '你已标记这条回答有帮助。' : '你已标记这条回答需要改进。' }}
                </p>
                <div
                  v-if="message.references.length > 0 && isReferenceExpanded(message.id)"
                  class="reference-list"
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
                        查看原文
                      </el-button>
                      <span v-if="reference.chunkNo" class="reference-meta">定位切片 #{{ reference.chunkNo }}</span>
                    </div>
                  </div>
                </div>
              </article>
            </div>

            <div v-if="pendingExchange" class="conversation-group">
              <article class="bubble bubble-user">
                <span class="bubble-role">你</span>
                <p>{{ pendingExchange.question }}</p>
              </article>
              <article
                class="bubble bubble-assistant"
                :class="{
                  'is-streaming': pendingExchange.status === 'STREAMING',
                  'is-failed': pendingExchange.status === 'FAILED',
                }"
              >
                <span class="bubble-role">助手</span>
                <p>{{ pendingExchange.answer || '正在组织回答...' }}</p>
                <p v-if="pendingExchange.errorMessage" class="pending-error">{{ pendingExchange.errorMessage }}</p>
                <div v-if="pendingExchange.status === 'FAILED'" class="pending-actions">
                  <el-button text :icon="RefreshRight" @click="handleRetryPendingExchange">重新发送上一问</el-button>
                </div>
              </article>
            </div>
          </template>

          <div v-else class="conversation-empty">
            <el-empty :description="emptyTitle">
              <template #description>
                <p>{{ emptyDescription }}</p>
              </template>
            </el-empty>
          </div>
        </div>

        <footer class="composer-panel">
          <div class="composer-hint">
            <el-icon><Tickets /></el-icon>
            <span>{{ composerPlaceholder }}</span>
          </div>
          <el-input
            v-model="draftQuestion"
            type="textarea"
            :rows="4"
            resize="none"
            :placeholder="composerPlaceholder"
            @keydown="handleComposerKeydown"
          />
          <div class="composer-actions">
            <p v-if="loadingError" class="composer-error">{{ loadingError }}</p>
            <span class="composer-tip">Enter 发送，Shift + Enter 换行</span>
            <el-button
              type="primary"
              :icon="Promotion"
              :loading="streaming"
              :disabled="!draftQuestion.trim()"
              @click="handleSendButtonClick"
            >
              {{ streaming ? '生成中' : '发送问题' }}
            </el-button>
          </div>
        </footer>
      </section>
    </div>
  </section>
</template>

<style scoped>
.chat-workspace {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 24px;
  background:
    radial-gradient(circle at top right, rgba(211, 120, 41, 0.12), transparent 28%),
    linear-gradient(180deg, rgba(255, 252, 247, 0.96), rgba(255, 248, 240, 0.9));
}

.chat-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
}

.chat-eyebrow {
  margin: 0 0 8px;
  color: #9d7a58;
  font-size: 12px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.chat-head h2 {
  margin: 0;
  font-family: "Noto Serif SC", serif;
  font-size: 28px;
}

.chat-head p:last-child {
  margin: 10px 0 0;
  color: #6d5948;
  line-height: 1.7;
}

.chat-head-actions {
  display: flex;
  gap: 10px;
}

.chat-layout {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: 16px;
  min-height: 560px;
}

.chat-layout.is-single {
  grid-template-columns: minmax(0, 1fr);
}

.session-panel,
.conversation-panel {
  border-radius: 20px;
  background: rgba(255, 250, 242, 0.78);
}

.session-panel {
  display: flex;
  flex-direction: column;
  padding: 16px;
  border: 1px solid rgba(179, 139, 96, 0.12);
}

.session-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  color: #7b634f;
}

.session-list {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 10px;
}

.session-item {
  padding: 14px 16px;
  border: 1px solid rgba(179, 139, 96, 0.1);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.55);
  text-align: left;
  cursor: pointer;
  transition:
    transform 180ms ease,
    border-color 180ms ease,
    box-shadow 180ms ease;
}

.session-item:hover,
.session-item.is-active {
  transform: translateY(-2px);
  border-color: rgba(198, 107, 34, 0.24);
  box-shadow: 0 16px 30px rgba(141, 69, 16, 0.08);
}

.session-item strong,
.session-item span {
  display: block;
}

.session-item strong {
  color: #2f241d;
}

.session-item span {
  margin-top: 8px;
  color: #8f7159;
  font-size: 12px;
}

.session-empty {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
}

.conversation-panel {
  display: grid;
  grid-template-rows: minmax(0, 1fr) auto;
  min-height: 0;
  overflow: hidden;
  border: 1px solid rgba(179, 139, 96, 0.12);
}

.conversation-body {
  overflow-y: auto;
  padding: 22px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.conversation-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.bubble {
  max-width: 88%;
  padding: 16px 18px;
  border-radius: 20px;
  line-height: 1.7;
  box-shadow: 0 12px 24px rgba(141, 69, 16, 0.05);
}

.bubble p {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}

.bubble-user {
  align-self: flex-end;
  background: linear-gradient(150deg, #c66b22, #8d4510);
  color: #fff8f1;
}

.bubble-assistant {
  align-self: flex-start;
  background: rgba(255, 255, 255, 0.78);
  color: #3a2c21;
}

.bubble-assistant.is-streaming {
  border: 1px dashed rgba(198, 107, 34, 0.28);
}

.bubble-assistant.is-failed {
  border: 1px solid rgba(192, 57, 43, 0.22);
  background: rgba(255, 247, 244, 0.9);
}

.bubble-role {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  color: inherit;
  opacity: 0.78;
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.assistant-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid rgba(168, 127, 88, 0.14);
}

.assistant-action {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border: 0;
  border-radius: 999px;
  background: rgba(246, 236, 224, 0.78);
  color: #805d3f;
  cursor: pointer;
  transition:
    transform 180ms ease,
    background-color 180ms ease;
}

.assistant-action:hover {
  transform: translateY(-1px);
  background: rgba(240, 224, 207, 0.92);
}

.assistant-action strong {
  color: #c66b22;
  font-size: 12px;
}

.assistant-feedback {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.feedback-hint,
.pending-error {
  margin: 12px 0 0;
  color: #8d6a4d;
  font-size: 13px;
}

.pending-error {
  color: #b0493d;
}

.pending-actions {
  margin-top: 8px;
}

.reference-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 14px;
}

.reference-item {
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(246, 236, 224, 0.72);
  border-left: 3px solid rgba(198, 107, 34, 0.42);
}

.reference-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #6f563f;
}

.reference-item p {
  margin: 8px 0 0;
  color: #5f4a38;
  font-size: 13px;
}

.reference-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
}

.reference-meta {
  color: #9d7a58;
  font-size: 12px;
}

.conversation-empty {
  display: grid;
  place-items: center;
  min-height: 100%;
}

.composer-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 18px 22px 22px;
  border-top: 1px solid rgba(179, 139, 96, 0.12);
  background: rgba(255, 250, 242, 0.92);
}

.composer-hint {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: #8d6d52;
  font-size: 13px;
}

.composer-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.composer-error {
  margin: 0;
  color: #c0392b;
}

.composer-tip {
  margin-left: auto;
  color: #9d7a58;
  font-size: 12px;
}

@media (max-width: 1080px) {
  .chat-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .chat-workspace,
  .session-panel,
  .composer-panel,
  .conversation-body {
    padding: 18px;
  }

  .chat-head,
  .chat-head-actions,
  .composer-actions,
  .reference-head,
  .assistant-actions {
    flex-direction: column;
    align-items: flex-start;
  }

  .bubble {
    max-width: 100%;
  }

  .composer-tip {
    margin-left: 0;
  }
}
</style>
