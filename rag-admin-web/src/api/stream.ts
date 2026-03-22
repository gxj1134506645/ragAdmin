import type { EventSourceMessage } from '@microsoft/fetch-event-source'

type StreamDataMessage = Pick<EventSourceMessage, 'data'>

/**
 * 统一只消费 SSE data 中的业务 JSON，不依赖 event/id 等包装字段。
 */
export function parseStreamEventData<T>(message: StreamDataMessage): T | null {
  const raw = message.data.trim()
  if (!raw) {
    return null
  }
  return JSON.parse(raw) as T
}
