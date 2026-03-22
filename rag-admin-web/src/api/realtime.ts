import { fetchEventSource } from '@microsoft/fetch-event-source'
import type { TaskRealtimeEvent } from '@/types/realtime'
import { parseStreamEventData } from '@/api/stream'
import { getAccessToken } from '@/utils/token-storage'

export interface RealtimeStreamHandle {
  close: () => void
}

interface SubscribeOptions {
  onEvent: (event: TaskRealtimeEvent) => void
  onError?: (error: unknown) => void
}

function subscribe(
  url: string,
  options: SubscribeOptions,
): RealtimeStreamHandle {
  const controller = new AbortController()
  const token = getAccessToken()

  void fetchEventSource(url, {
    method: 'GET',
    signal: controller.signal,
    openWhenHidden: true,
    headers: token
      ? {
          Authorization: `Bearer ${token}`,
          Accept: 'text/event-stream',
        }
      : {
          Accept: 'text/event-stream',
        },
    onmessage(message) {
      try {
        const event = parseStreamEventData<TaskRealtimeEvent>(message)
        if (!event) {
          return
        }
        options.onEvent(event)
      } catch (error) {
        options.onError?.(error)
      }
    },
    onerror(error) {
      options.onError?.(error)
      throw error
    },
  })

  return {
    close() {
      controller.abort()
    },
  }
}

export function subscribeKnowledgeBaseDocumentEvents(
  kbId: number,
  options: SubscribeOptions,
): RealtimeStreamHandle {
  return subscribe(`/api/admin/events/knowledge-bases/${kbId}/documents`, options)
}

export function subscribeDocumentEvents(
  documentId: number,
  options: SubscribeOptions,
): RealtimeStreamHandle {
  return subscribe(`/api/admin/events/documents/${documentId}`, options)
}

export function subscribeTaskEvents(options: SubscribeOptions): RealtimeStreamHandle {
  return subscribe('/api/admin/events/tasks', options)
}
