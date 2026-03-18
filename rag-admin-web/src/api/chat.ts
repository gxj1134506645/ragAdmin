import { fetchEventSource } from '@microsoft/fetch-event-source'
import { http, resolveErrorMessage, unwrapResponse } from '@/api/http'
import type { PageResponse } from '@/types/api'
import type {
  ChatExchange,
  ChatRequest,
  ChatSceneType,
  ChatSession,
  ChatStreamEvent,
  CreateChatSessionRequest,
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

interface StreamChatOptions {
  onEvent: (event: ChatStreamEvent) => void
  onError?: (error: unknown) => void
}

export async function createChatSession(payload: CreateChatSessionRequest): Promise<ChatSession> {
  const response = await http.post('/admin/chat/sessions', payload)
  return unwrapResponse(response.data)
}

export async function listChatSessions(params: ChatSessionQuery): Promise<PageResponse<ChatSession>> {
  const response = await http.get('/admin/chat/sessions', {
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
  const response = await http.get(`/admin/chat/sessions/${sessionId}/messages`)
  return unwrapResponse(response.data)
}

export function streamChatMessage(
  sessionId: number,
  payload: ChatRequest,
  options: StreamChatOptions,
): ChatStreamHandle {
  const controller = new AbortController()
  const token = getAccessToken()

  void fetchEventSource(`/api/admin/chat/sessions/${sessionId}/messages/stream`, {
    method: 'POST',
    signal: controller.signal,
    openWhenHidden: true,
    headers: {
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify({
      ...payload,
      stream: true,
    }),
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
      try {
        options.onEvent(JSON.parse(message.data) as ChatStreamEvent)
      } catch (error) {
        options.onError?.(error)
      }
    },
    onerror(error) {
      options.onError?.(error)
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
