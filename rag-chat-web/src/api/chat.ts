import { fetchEventSource } from '@microsoft/fetch-event-source'
import { http, resolveErrorMessage, unwrapResponse } from '@/api/http'
import { parseStreamEventData } from '@/api/stream'
import type { PageResponse } from '@/types/api'
import type {
  ChatContentType,
  ChatExchange,
  ChatFeedbackRequest,
  ChatRequest,
  ChatResponse,
  ChatSceneType,
  ChatSession,
  ChatStreamEvent,
  CreateChatSessionRequest,
  UpdateChatSessionRequest,
  UpdateSessionKnowledgeBasesRequest,
} from '@/types/chat'
import { getAccessToken } from '@/utils/token-storage'

export interface ChatStreamHandle {
  close: () => void
}

interface ChatSessionQuery {
  kbId?: number
  sceneType?: ChatSceneType
  pageNo?: number
  pageSize?: number
}

interface ChatMessagePayload {
  messageId: number
  question: string
  answer: string
  answerContentType?: ChatContentType | string | null
  references: ChatExchange['references']
  feedbackType?: ChatExchange['feedbackType']
  feedbackComment?: string | null
}

interface StreamChatOptions {
  onEvent: (event: ChatStreamEvent) => void
  onError?: (error: unknown) => void
}

export interface RegenerateChatMessageRequest {
  chatModelId?: number
  selectedKbIds?: number[]
  webSearchEnabled?: boolean
}

function normalizeChatErrorMessage(message: string): string {
  const normalized = message.trim()
  if (!normalized) {
    return '请求失败，请稍后重试'
  }
  const lowered = normalized.toLowerCase()
  if (
    lowered.includes('arrearage')
    || lowered.includes('overdue-payment')
    || lowered.includes('good standing')
  ) {
    return '当前模型提供方账户可能已欠费或额度异常，请联系管理员处理后重试。'
  }
  return normalized
}

function normalizeAnswerContentType(value?: string | null): ChatContentType {
  return value === 'text/plain' ? 'text/plain' : 'text/markdown'
}

export async function createChatSession(payload: CreateChatSessionRequest): Promise<ChatSession> {
  const response = await http.post('/app/chat/sessions', payload)
  return unwrapResponse(response.data)
}

export async function listChatSessions(params: ChatSessionQuery): Promise<PageResponse<ChatSession>> {
  const response = await http.get('/app/chat/sessions', {
    params: {
      kbId: params.kbId,
      sceneType: params.sceneType,
      pageNo: params.pageNo ?? 1,
      pageSize: params.pageSize ?? 50,
    },
  })
  return unwrapResponse(response.data)
}

export async function listChatMessages(sessionId: number): Promise<ChatExchange[]> {
  const response = await http.get(`/app/chat/sessions/${sessionId}/messages`)
  return unwrapResponse<ChatMessagePayload[]>(response.data).map((item) => ({
    id: item.messageId,
    questionText: item.question,
    answerText: item.answer,
    answerContentType: normalizeAnswerContentType(item.answerContentType),
    references: item.references ?? [],
    feedbackType: item.feedbackType ?? null,
    feedbackComment: item.feedbackComment ?? null,
  }))
}

export async function updateChatSession(
  sessionId: number,
  payload: UpdateChatSessionRequest,
): Promise<ChatSession> {
  const response = await http.put(`/app/chat/sessions/${sessionId}`, payload)
  return unwrapResponse(response.data)
}

export async function deleteChatSession(sessionId: number): Promise<void> {
  const response = await http.delete(`/app/chat/sessions/${sessionId}`)
  unwrapResponse(response.data)
}

export async function updateSessionKnowledgeBases(
  sessionId: number,
  payload: UpdateSessionKnowledgeBasesRequest,
): Promise<ChatSession> {
  const response = await http.put(`/app/chat/sessions/${sessionId}/knowledge-bases`, payload)
  return unwrapResponse(response.data)
}

export async function chat(sessionId: number, payload: ChatRequest): Promise<ChatResponse> {
  const response = await http.post(`/app/chat/sessions/${sessionId}/messages`, payload)
  const result = unwrapResponse<ChatResponse & { answerContentType?: string | null }>(response.data)
  return {
    ...result,
    answerContentType: normalizeAnswerContentType(result.answerContentType),
  }
}

export async function submitChatFeedback(
  messageId: number,
  payload: ChatFeedbackRequest,
): Promise<void> {
  const response = await http.post(`/app/chat/messages/${messageId}/feedback`, payload)
  unwrapResponse(response.data)
}

function createStreamHandle(
  url: string,
  payload: object,
  options: StreamChatOptions,
): ChatStreamHandle {
  const controller = new AbortController()
  const token = getAccessToken()
  let completed = false

  void fetchEventSource(url, {
    method: 'POST',
    signal: controller.signal,
    openWhenHidden: true,
    headers: {
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(payload),
    async onopen(response) {
      if (response.ok) {
        return
      }
      const raw = await response.text()
      try {
        const errorPayload = JSON.parse(raw) as { message?: string }
        throw new Error(normalizeChatErrorMessage(errorPayload.message || raw || `流式问答失败，状态码 ${response.status}`))
      } catch (error) {
        if (error instanceof Error) {
          throw error
        }
        throw new Error(normalizeChatErrorMessage(raw || `流式问答失败，状态码 ${response.status}`))
      }
    },
    onmessage(message) {
      const event = parseStreamEventData<ChatStreamEvent>(message)
      if (!event) {
        return
      }
      event.answerContentType = normalizeAnswerContentType(event.answerContentType)
      if (event.eventType === 'COMPLETE' || event.eventType === 'ERROR') {
        completed = true
      }
      options.onEvent(event)
    },
    onclose() {
      if (!completed && !controller.signal.aborted) {
        throw new Error('流式连接已中断，请重新发送问题')
      }
    },
    onerror(error) {
      throw error
    },
  }).catch((error) => {
    if (controller.signal.aborted) {
      return
    }
    options.onError?.(error)
  })

  return {
    close() {
      controller.abort()
    },
  }
}

export function streamChatMessage(
  sessionId: number,
  payload: ChatRequest,
  options: StreamChatOptions,
): ChatStreamHandle {
  return createStreamHandle(`/api/app/chat/sessions/${sessionId}/messages/stream`, payload, options)
}

export function streamRegenerateChatMessage(
  messageId: number,
  payload: RegenerateChatMessageRequest,
  options: StreamChatOptions,
): ChatStreamHandle {
  return createStreamHandle(`/api/app/chat/messages/${messageId}/regenerate/stream`, payload, options)
}

export function resolveChatStreamError(error: unknown): string {
  return normalizeChatErrorMessage(resolveErrorMessage(error))
}
