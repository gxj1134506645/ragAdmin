<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppChatWorkspace from '@/components/chat/AppChatWorkspace.vue'
import { resolveErrorMessage } from '@/api/http'
import { listKnowledgeBases } from '@/api/knowledge-base'
import type { KnowledgeBaseSummary } from '@/types/knowledge-base'

const route = useRoute()
const loading = ref(false)
const loadError = ref('')
const knowledgeBase = ref<KnowledgeBaseSummary | null>(null)

const kbId = computed<number | null>(() => {
  const raw = route.params.kbId
  if (typeof raw !== 'string') {
    return null
  }
  const parsed = Number(raw)
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return null
  }
  return parsed
})

async function loadCurrentKnowledgeBase(): Promise<void> {
  if (!kbId.value) {
    knowledgeBase.value = null
    loadError.value = '知识库标识不合法'
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    const response = await listKnowledgeBases({ pageNo: 1, pageSize: 200 })
    knowledgeBase.value = response.list.find((item) => item.id === kbId.value) ?? null
    if (!knowledgeBase.value) {
      loadError.value = '未找到该知识库，或当前用户不可见'
    }
  } catch (error) {
    loadError.value = resolveErrorMessage(error)
  } finally {
    loading.value = false
  }
}

watch(kbId, async () => {
  await loadCurrentKnowledgeBase()
})

onMounted(async () => {
  await loadCurrentKnowledgeBase()
})
</script>

<template>
  <section v-if="loading" class="kb-page-placeholder app-shell-panel">
    正在加载知识库信息...
  </section>

  <section v-else-if="loadError" class="kb-page-placeholder app-shell-panel is-error">
    {{ loadError }}
  </section>

  <AppChatWorkspace
    v-else-if="knowledgeBase && kbId"
    scene-type="KNOWLEDGE_BASE"
    :anchor-kb-id="kbId"
    eyebrow="知识库内问答"
    :title="knowledgeBase.kbName"
    :description="knowledgeBase.description || '当前会话固定绑定本知识库，你仍然可以额外追加其它知识库参与本轮检索。'"
  />
</template>

<style scoped>
.kb-page-placeholder {
  display: grid;
  place-items: center;
  min-height: calc(100vh - 40px);
  border-radius: 32px;
  padding: 32px;
  color: var(--text-secondary);
}

.kb-page-placeholder.is-error {
  color: #b04d35;
}
</style>
