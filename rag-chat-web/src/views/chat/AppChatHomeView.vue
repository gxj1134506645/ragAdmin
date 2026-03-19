<script setup lang="ts">
import AppChatWorkspace from '@/components/chat/AppChatWorkspace.vue'
</script>

<template>
  <AppChatWorkspace
    scene-type="GENERAL"
    eyebrow="通用问答"
    title="先和模型对话，再决定是否把知识库拉进来"
    description="首页场景默认不绑定任何知识库。你可以保持纯模型问答，也可以在输入区通过 @知识库 接入多个知识库，把本轮问题切换成临时的多库检索增强模式。"
  />
</template>
