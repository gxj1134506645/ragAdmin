<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { ChatDotRound, Compass, Connection, Plus, RefreshRight } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
  createChatSession,
  listChatMessages,
  listChatSessions,
  resolveChatStreamError,
  streamChatMessage,
  type ChatStreamHandle,
} from '@/api/chat'
import { listKnowledgeBases } from '@/api/knowledge-base'
import { listModels } from '@/api/model'
import KnowledgeBaseSelector from '@/components/chat/KnowledgeBaseSelector.vue'
import ModelSelector from '@/components/chat/ModelSelector.vue'
import type { ChatExchange, ChatSceneType, ChatSession, ChatStreamEvent } from '@/types/chat'
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

const props = defineProps<Props>()

const messageContainerRef = ref<HTMLElement | null>(null)
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

let streamHandle: ChatStreamHandle | null = null

const isKnowledgeBaseScene = computed(() => props.sceneType === 'KNOWLEDGE_BASE')

const selectedKnowledgeBaseNames = computed<string[]>(() => {
  return selectedKbIds.value.flatMap((id) => {
    const match = availableKnowledgeBases.value.find((item) => item.id === id)
    return match ? [match.kbName] : []
  })
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

function applySessionPreferences(session: ChatSession | null): void {
  if (!session) {
    resetPreferenceInputs()
    return
  }
  selectedKbIds.value = normalizeSelectedKbIds(session.selectedKbIds)
  selectedModelId.value = session.chatModelId ?? undefined
  webSearchEnabled.value = Boolean(session.webSearchEnabled)
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
  if (!isKnowledgeBaseScene.value || streaming.value) {
    return
  }
  activeSessionId.value = null
  messages.value = []
  pendingExchange.value = null
  resetPreferenceInputs()
}

function handleClearView(): void {
  messages.value = []
  pendingExchange.value = null
}

function handleComposerKeydown(event: KeyboardEvent): void {
  if (event.key !== 'Enter' || event.shiftKey) {
    return
  }
  event.preventDefault()
  void handleSendQuestion()
}

watch(
  () => [props.sceneType, props.anchorKbId],
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

    <section class="control-strip app-shell-panel">
      <div class="control-block">
        <ModelSelector v-model="selectedModelId" :options="availableModels" :loading="optionLoading" />
      </div>
      <div class="control-block is-wide">
        <KnowledgeBaseSelector
          v-model="selectedKbIds"
          :options="availableKnowledgeBases"
          :loading="optionLoading"
          :locked-kb-id="props.anchorKbId ?? null"
        />
      </div>
      <div class="control-block control-toggle">
        <div class="toggle-head">
          <span>联网搜索</span>
          <small>Provider 不可用时服务端会优雅降级</small>
        </div>
        <el-switch v-model="webSearchEnabled" />
      </div>
    </section>

    <section class="conversation-grid" :class="{ 'has-session-list': isKnowledgeBaseScene }">
      <aside v-if="isKnowledgeBaseScene" class="session-list-card app-shell-panel">
        <div class="session-list-head">
          <div>
            <p>知识库会话</p>
            <strong>{{ sessions.length }} 个</strong>
          </div>
          <el-button text :icon="Plus" :disabled="streaming" @click="handleStartNewSession">新会话</el-button>
        </div>
        <div class="session-list thin-scrollbar">
          <div v-if="sessionLoading" class="session-placeholder">正在加载会话...</div>
          <button
            v-for="session in sessions"
            :key="session.id"
            type="button"
            class="session-item"
            :class="{ 'is-active': session.id === activeSessionId }"
            @click="handleSelectSession(session)"
          >
            <strong>{{ session.sessionName }}</strong>
            <span>{{ session.selectedKbIds.length }} 个知识库参与检索</span>
          </button>
          <div v-if="!sessionLoading && sessions.length === 0" class="session-placeholder">
            当前知识库还没有聊天会话
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
                <div v-if="message.references.length > 0" class="reference-block">
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
                  </div>
                </div>
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
            <span class="composer-tip">Enter 发送，Shift + Enter 换行</span>
          </div>
          <el-input
            v-model="draftQuestion"
            type="textarea"
            :rows="5"
            resize="none"
            :placeholder="composerPlaceholder"
            @keydown="handleComposerKeydown"
          />
          <div class="composer-actions">
            <p v-if="loadingError" class="composer-error">{{ loadingError }}</p>
            <div class="composer-action-buttons">
              <el-button
                v-if="!isKnowledgeBaseScene"
                plain
                :icon="ChatDotRound"
                :disabled="streaming"
                @click="handleClearView"
              >
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
  padding: 16px;
  border-radius: 18px;
  border: 1px solid rgba(122, 89, 53, 0.08);
  background: rgba(255, 255, 255, 0.58);
  color: inherit;
  cursor: pointer;
  text-align: left;
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

.session-item strong,
.session-item span {
  display: block;
}

.session-item span {
  margin-top: 8px;
  color: var(--text-muted);
  font-size: 12px;
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

.composer-hint {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--text-secondary);
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
