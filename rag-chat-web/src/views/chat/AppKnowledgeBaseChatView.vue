<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppChatWorkspace from '@/components/chat/AppChatWorkspace.vue'
import { resolveErrorMessage } from '@/api/http'
import { listKnowledgeBases } from '@/api/knowledge-base'
import type { KnowledgeBaseSummary } from '@/types/knowledge-base'

const route = useRoute()
const router = useRouter()
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

const referenceFocus = computed(() => {
  if (route.query.source !== 'reference') {
    return null
  }
  const chunkIdRaw = route.query.chunkId
  if (typeof chunkIdRaw !== 'string') {
    return null
  }
  const chunkId = Number(chunkIdRaw)
  if (!Number.isFinite(chunkId) || chunkId <= 0) {
    return null
  }

  const chunkNoRaw = route.query.chunkNo
  const documentIdRaw = route.query.documentId
  const chunkNo = typeof chunkNoRaw === 'string' ? Number(chunkNoRaw) : null
  const documentId = typeof documentIdRaw === 'string' ? Number(documentIdRaw) : null

  return {
    documentName: typeof route.query.documentName === 'string' ? route.query.documentName : '未命名文档',
    documentId: documentId !== null && Number.isFinite(documentId) && documentId > 0 ? documentId : null,
    chunkId,
    chunkNo: chunkNo !== null && Number.isFinite(chunkNo) && chunkNo > 0 ? chunkNo : null,
    snippet: typeof route.query.snippet === 'string' ? route.query.snippet : '',
  }
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

async function clearReferenceFocus(): Promise<void> {
  await router.replace({
    path: route.path,
    query: {},
  })
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

  <section v-else-if="knowledgeBase && kbId" class="kb-chat-shell">
    <section v-if="referenceFocus" class="reference-focus-card app-shell-panel">
      <div class="reference-focus-copy">
        <p class="reference-focus-kicker">来源定位</p>
        <h2>{{ referenceFocus.documentName }}</h2>
        <p>
          已从引用结果切换到当前知识库上下文。
          <span v-if="referenceFocus.chunkNo">当前聚焦切片 #{{ referenceFocus.chunkNo }}</span>
          <span v-else>当前聚焦切片 ID {{ referenceFocus.chunkId }}</span>
        </p>
        <blockquote v-if="referenceFocus.snippet">{{ referenceFocus.snippet }}</blockquote>
      </div>
      <el-button text @click="clearReferenceFocus">收起定位</el-button>
    </section>

    <AppChatWorkspace
      scene-type="KNOWLEDGE_BASE"
      :anchor-kb-id="kbId"
      eyebrow="知识库内问答"
      :title="knowledgeBase.kbName"
      :description="knowledgeBase.description || '当前会话固定绑定本知识库，你仍然可以额外追加其它知识库参与本轮检索。'"
    />
  </section>
</template>

<style scoped>
.kb-chat-shell {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

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

.reference-focus-card {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding: 20px 24px;
  border-radius: 28px;
}

.reference-focus-copy {
  max-width: 880px;
}

.reference-focus-kicker {
  margin: 0 0 8px;
  color: var(--text-muted);
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.reference-focus-copy h2 {
  margin: 0;
  font-size: 24px;
  line-height: 1.3;
}

.reference-focus-copy p {
  margin: 10px 0 0;
  color: var(--text-secondary);
  line-height: 1.7;
}

.reference-focus-copy blockquote {
  margin: 14px 0 0;
  padding: 12px 16px;
  border-radius: 18px;
  border-left: 3px solid rgba(157, 91, 47, 0.42);
  background: rgba(255, 251, 245, 0.88);
  color: var(--text-secondary);
}
</style>
