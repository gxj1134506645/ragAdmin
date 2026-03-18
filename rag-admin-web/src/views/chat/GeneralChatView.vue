<script setup lang="ts">
import ChatWorkspace from '@/components/chat/ChatWorkspace.vue'
</script>

<template>
  <section class="general-chat-page">
    <ChatWorkspace
      scene-type="GENERAL"
      title="首页通用问答"
      description="这里不绑定任何知识库，直接与当前默认聊天模型对话。会话记忆按用户独立隔离。"
      empty-title="开始首页通用会话"
      empty-description="发送第一条问题后，会自动为你创建默认会话。"
    />
  </section>
</template>

<style scoped>
.general-chat-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
</style>
