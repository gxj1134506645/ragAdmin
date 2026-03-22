import { describe, expect, it } from 'vitest'
import { parseStreamEventData } from '@/api/stream'

describe('parseStreamEventData', () => {
  it('在 data 为空时返回 null', () => {
    expect(parseStreamEventData({ data: '' })).toBeNull()
  })

  it('只解析 data 中的 JSON 数据，不依赖 SSE 包装字段', () => {
    const message = {
      data: JSON.stringify({
        eventType: 'DELTA',
        delta: '你好',
        messageId: 88,
      }),
      event: 'complete',
      id: '123',
    } as unknown as { data: string }

    const payload = parseStreamEventData<{
      eventType: string
      delta: string
      messageId: number
    }>(message)

    expect(payload).toEqual({
      eventType: 'DELTA',
      delta: '你好',
      messageId: 88,
    })
  })
})
