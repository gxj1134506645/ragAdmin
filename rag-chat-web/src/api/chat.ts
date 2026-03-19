import { fetchEventSource } from '@microsoft/fetch-event-source'
import { http, resolveErrorMessage, unwrapResponse } from '@/api/http'
import type { PageResponse } from '@/types/api'
import type {
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
  references: ChatExchange['references']
  feedbackType?: ChatExchange['feedbackType']
  feedbackComment?: string | null
}

interface StreamChatOptions {
  onEvent: (event: ChatStreamEvent) => void
  onError?: (error: unknown) => void
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
  return unwrapResponse(response.data)
}

export async function submitChatFeedback(
  messageId: number,
  payload: ChatFeedbackRequest,
): Promise<void> {
  const response = await http.post(`/app/chat/messages/${messageId}/feedback`, payload)
  unwrapResponse(response.data)
}

export function streamChatMessage(
  sessionId: number,
  payload: ChatRequest,
  options: StreamChatOptions,
): ChatStreamHandle {
  const controller = new AbortController()
  const token = getAccessToken()
  let completed = false

  void fetchEventSource(`/api/app/chat/sessions/${sessionId}/messages/stream`, {
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
        throw new Error(errorPayload.message || raw || `流式问答失败，状态码 ${response.status}`)
      } catch (error) {
        if (error instanceof Error) {
          throw error
        }
        throw new Error(raw || `流式问答失败，状态码 ${response.status}`)
      }
    },
    onmessage(message) {
      if (!message.data) {
        return
      }
      const event = JSON.parse(message.data) as ChatStreamEvent
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

export function resolveChatStreamError(error: unknown): string {
  return resolveErrorMessage(error)
}
