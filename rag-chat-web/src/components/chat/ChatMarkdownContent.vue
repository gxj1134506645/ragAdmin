<script setup lang="ts">
import { computed } from 'vue'
import type { ChatContentType } from '@/types/chat'
import { renderChatContent } from '@/utils/chat-markdown'

interface Props {
  content: string
  contentType?: ChatContentType
}

const props = defineProps<Props>()

// 这里注入的 HTML 只来自受控 Markdown 子集渲染器，不直接信任模型原始字符串。
const renderedHtml = computed(() => renderChatContent(props.content, props.contentType ?? 'text/markdown'))
</script>

<template>
  <div class="chat-markdown" v-html="renderedHtml" />
</template>

<style scoped>
.chat-markdown {
  color: inherit;
  line-height: 1.85;
  word-break: break-word;
}

.chat-markdown :deep(> :first-child) {
  margin-top: 0;
}

.chat-markdown :deep(> :last-child) {
  margin-bottom: 0;
}

.chat-markdown :deep(p),
.chat-markdown :deep(ul),
.chat-markdown :deep(ol),
.chat-markdown :deep(pre),
.chat-markdown :deep(blockquote) {
  margin: 0 0 12px;
}

.chat-markdown :deep(h1),
.chat-markdown :deep(h2),
.chat-markdown :deep(h3) {
  margin: 0 0 10px;
  color: #2f241d;
  font-family: "Noto Serif SC", serif;
  line-height: 1.3;
}

.chat-markdown :deep(h1) {
  font-size: 22px;
}

.chat-markdown :deep(h2) {
  font-size: 18px;
}

.chat-markdown :deep(h3) {
  font-size: 16px;
}

.chat-markdown :deep(ul),
.chat-markdown :deep(ol) {
  padding-left: 20px;
}

.chat-markdown :deep(li + li) {
  margin-top: 4px;
}

.chat-markdown :deep(blockquote) {
  padding: 10px 14px;
  border-left: 3px solid rgba(157, 91, 47, 0.24);
  border-radius: 0 14px 14px 0;
  background: rgba(248, 242, 234, 0.72);
  color: #5d4939;
}

.chat-markdown :deep(code) {
  padding: 2px 6px;
  border-radius: 8px;
  background: rgba(244, 237, 228, 0.9);
  color: #7a4424;
  font-size: 0.92em;
}

.chat-markdown :deep(pre) {
  overflow-x: auto;
  padding: 12px 14px;
  border-radius: 16px;
  background: #2f241d;
  color: #fff7ef;
}

.chat-markdown :deep(pre code) {
  padding: 0;
  background: transparent;
  color: inherit;
}

.chat-markdown :deep(a) {
  color: #9d5b2f;
  text-decoration: underline;
  text-underline-offset: 3px;
}
</style>
