import MarkdownIt from 'markdown-it'
import type { ChatContentType } from '@/types/chat'

const markdownRenderer = new MarkdownIt({
  html: false,
  breaks: true,
  linkify: false,
  typographer: false,
})

// 只保留当前前端已声明支持的 Markdown 子集，避免渲染能力无边界扩张。
markdownRenderer.disable([
  'image',
  'table',
  'html_block',
  'html_inline',
  'lheading',
])

const defaultLinkOpenRenderer = markdownRenderer.renderer.rules.link_open

type LinkOpenRenderer = NonNullable<typeof markdownRenderer.renderer.rules.link_open>

markdownRenderer.renderer.rules.link_open = (...args: Parameters<LinkOpenRenderer>) => {
  const [tokens, idx, options, env, self] = args
  const currentToken = tokens[idx]
  if (!currentToken) {
    return self.renderToken(tokens, idx, options)
  }
  currentToken.attrSet('target', '_blank')
  currentToken.attrSet('rel', 'noopener noreferrer nofollow')
  if (defaultLinkOpenRenderer) {
    return defaultLinkOpenRenderer(tokens, idx, options, env, self)
  }
  return self.renderToken(tokens, idx, options)
}

export function renderChatContent(content: string, contentType: ChatContentType = 'text/markdown'): string {
  const normalized = content.trim()
  if (!normalized) {
    return '<p></p>'
  }
  if (contentType === 'text/plain') {
    return renderPlainText(normalized)
  }
  return markdownRenderer.render(normalized)
}

function renderPlainText(content: string): string {
  return content
    .split(/\n{2,}/)
    .map((paragraph) => `<p>${escapeHtml(paragraph).replace(/\n/g, '<br />')}</p>`)
    .join('')
}

function escapeHtml(content: string): string {
  return content
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}
