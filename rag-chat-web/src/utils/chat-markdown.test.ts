import { describe, expect, it } from 'vitest'
import { renderChatContent } from '@/utils/chat-markdown'

describe('renderChatContent', () => {
  it('应渲染受控 Markdown 子集', () => {
    const html = renderChatContent('## 处理建议\n\n- 第一项\n- **第二项**')

    expect(html).toContain('<h2>处理建议</h2>')
    expect(html).toContain('<li>第一项</li>')
    expect(html).toContain('<strong>第二项</strong>')
  })

  it('应阻断原始 html 注入', () => {
    const html = renderChatContent('<script>alert(1)</script>\n\n<div>内容</div>')

    expect(html).not.toContain('<script>')
    expect(html).not.toContain('<div>')
    expect(html).toContain('&lt;script&gt;alert(1)&lt;/script&gt;')
  })

  it('应禁用图片与表格等未开放能力', () => {
    const html = renderChatContent('| 列1 | 列2 |\n| --- | --- |\n| A | B |\n\n![x](https://example.com/a.png)')

    expect(html).not.toContain('<table>')
    expect(html).not.toContain('<img')
  })

  it('纯文本模式应保留换行并做 html 转义', () => {
    const html = renderChatContent('第一行\n第二行\n\n<script>x</script>', 'text/plain')

    expect(html).toContain('<br />')
    expect(html).toContain('&lt;script&gt;x&lt;/script&gt;')
    expect(html).not.toContain('<script>')
  })
})
