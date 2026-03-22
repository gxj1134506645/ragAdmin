import { describe, expect, it } from 'vitest'
import { parseStreamEventData } from '@/api/stream'

describe('parseStreamEventData', () => {
  it('在 data 为空时返回 null', () => {
    expect(parseStreamEventData({ data: '' })).toBeNull()
  })

  it('只解析 data 中的 JSON 数据，不依赖 SSE 包装字段', () => {
    const message = {
      data: JSON.stringify({
        eventType: 'TASK_STARTED',
        taskId: 101,
        message: '开始处理文档',
      }),
      event: 'conflicting_event',
      id: '999',
    } as unknown as { data: string }

    const payload = parseStreamEventData<{
      eventType: string
      taskId: number
      message: string
    }>(message)

    expect(payload).toEqual({
      eventType: 'TASK_STARTED',
      taskId: 101,
      message: '开始处理文档',
    })
  })
})
