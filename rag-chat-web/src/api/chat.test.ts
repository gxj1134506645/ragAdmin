import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchEventSource } from '@microsoft/fetch-event-source'
import { streamChatMessage } from '@/api/chat'
import { getAccessToken } from '@/utils/token-storage'

vi.mock('@microsoft/fetch-event-source', () => ({
  fetchEventSource: vi.fn(),
}))

vi.mock('@/utils/token-storage', () => ({
  getAccessToken: vi.fn(),
}))

vi.mock('@/api/http', () => ({
  http: {
    post: vi.fn(),
    get: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
  resolveErrorMessage(error: unknown) {
    return error instanceof Error ? error.message : '请求失败，请稍后重试'
  },
  unwrapResponse: vi.fn(),
}))

const fetchEventSourceMock = vi.mocked(fetchEventSource)
const getAccessTokenMock = vi.mocked(getAccessToken)

describe('streamChatMessage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getAccessTokenMock.mockReturnValue('chat-token')
  })

  it('应携带请求头和请求体，并分发流式事件', () => {
    let capturedOptions: Record<string, unknown> | undefined
    fetchEventSourceMock.mockImplementation((url, options) => {
      expect(url).toBe('/api/app/chat/sessions/22/messages/stream')
      capturedOptions = options as Record<string, unknown>
      return Promise.resolve()
    })

    const onEvent = vi.fn()

    streamChatMessage(
      22,
      {
        question: '请总结本周进展',
        selectedKbIds: [1, 2],
      },
      {
        onEvent,
      },
    )

    expect(fetchEventSourceMock).toHaveBeenCalledTimes(1)
    expect(capturedOptions).toMatchObject({
      method: 'POST',
      openWhenHidden: true,
      headers: {
        Authorization: 'Bearer chat-token',
        Accept: 'text/event-stream',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        question: '请总结本周进展',
        selectedKbIds: [1, 2],
      }),
    })

    const onmessage = capturedOptions?.onmessage as ((message: unknown) => void) | undefined
    expect(onmessage).toBeTypeOf('function')

    onmessage?.({
      data: JSON.stringify({
        eventType: 'DELTA',
        delta: '第一段回答',
        messageId: 88,
      }),
      event: 'ignored_delta',
      id: 'abc',
    })

    expect(onEvent).toHaveBeenCalledWith({
      eventType: 'DELTA',
      delta: '第一段回答',
      messageId: 88,
      answerContentType: 'text/markdown',
    })
  })

  it('在未完成时断流应回调默认错误', async () => {
    fetchEventSourceMock.mockImplementation((_url, options) => {
      try {
        ;(options as { onclose: () => void }).onclose()
        return Promise.resolve()
      } catch (error) {
        return Promise.reject(error)
      }
    })

    const onError = vi.fn()

    streamChatMessage(
      33,
      { question: '为什么失败了' },
      {
        onEvent: vi.fn(),
        onError,
      },
    )

    await new Promise((resolve) => setTimeout(resolve, 0))

    expect(onError).toHaveBeenCalledWith(new Error('流式连接已中断，请重新发送问题'))
  })

  it('收到 COMPLETE 后关闭连接不应再报断流错误', async () => {
    fetchEventSourceMock.mockImplementation((_url, options) => {
      ;(options as { onmessage: (message: unknown) => void }).onmessage({
        data: JSON.stringify({
          eventType: 'COMPLETE',
          delta: null,
          messageId: 103,
          answer: '完整回答',
          references: [],
          usage: null,
          errorMessage: null,
        }),
      })
      ;(options as { onclose: () => void }).onclose()
      return Promise.resolve()
    })

    const onError = vi.fn()
    const onEvent = vi.fn()

    streamChatMessage(
      44,
      { question: '给我最终答案' },
      {
        onEvent,
        onError,
      },
    )

    await new Promise((resolve) => setTimeout(resolve, 0))

    expect(onEvent).toHaveBeenCalledWith({
      eventType: 'COMPLETE',
      delta: null,
      messageId: 103,
      answer: '完整回答',
      answerContentType: 'text/markdown',
      references: [],
      usage: null,
      errorMessage: null,
    })
    expect(onError).not.toHaveBeenCalled()
  })
})
