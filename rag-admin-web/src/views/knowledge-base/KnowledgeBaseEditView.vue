<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElButton, ElEmpty, ElMessage, ElSkeleton } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import KnowledgeBaseForm from '@/components/knowledge-base/KnowledgeBaseForm.vue'
import {
  getKnowledgeBaseDetail,
  listModels,
  updateKnowledgeBase,
} from '@/api/knowledge-base'
import { resolveErrorMessage } from '@/api/http'
import type { KnowledgeBase, KnowledgeBaseUpsertRequest, ModelDefinition } from '@/types/knowledge-base'
import {
  buildKnowledgeBaseModelOptions,
  createEmptyKnowledgeBaseForm,
  fillKnowledgeBaseForm,
  mapKnowledgeBaseToForm,
  normalizeKnowledgeBaseForm,
} from '@/utils/knowledge-base-form'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const submitting = ref(false)
const modelLoading = ref(false)
const modelFallback = ref(false)
const loadError = ref('')
const knowledgeBase = ref<KnowledgeBase | null>(null)
const chatModelOptions = ref<ModelDefinition[]>([])
const embeddingModelOptions = ref<ModelDefinition[]>([])
const form = reactive<KnowledgeBaseUpsertRequest>(createEmptyKnowledgeBaseForm())

const knowledgeBaseId = computed(() => Number(route.params.id))

function applyModelOptions(models: ModelDefinition[]): void {
  const options = buildKnowledgeBaseModelOptions(models, knowledgeBase.value)
  chatModelOptions.value = options.chatModelOptions
  embeddingModelOptions.value = options.embeddingModelOptions
}

async function loadDetail(): Promise<void> {
  const detail = await getKnowledgeBaseDetail(knowledgeBaseId.value)
  knowledgeBase.value = detail
  fillKnowledgeBaseForm(form, mapKnowledgeBaseToForm(detail))
}

async function loadModelOptions(): Promise<void> {
  modelLoading.value = true
  modelFallback.value = false
  try {
    const response = await listModels({
      pageNo: 1,
      pageSize: 100,
      status: 'ENABLED',
    })
    applyModelOptions(response.list)
  } catch {
    modelFallback.value = true
    applyModelOptions([])
  } finally {
    modelLoading.value = false
  }
}

async function initialize(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    await loadDetail()
    await loadModelOptions()
  } catch (error) {
    loadError.value = resolveErrorMessage(error)
  } finally {
    loading.value = false
  }
}

async function handleSubmit(): Promise<void> {
  submitting.value = true
  try {
    await updateKnowledgeBase(knowledgeBaseId.value, normalizeKnowledgeBaseForm(form))
    await router.replace({
      path: '/knowledge-bases',
      query: {
        updated: '1',
      },
    })
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error))
  } finally {
    submitting.value = false
  }
}

async function handleCancel(): Promise<void> {
  await router.push('/knowledge-bases')
}

onMounted(async () => {
  await initialize()
})
</script>

<template>
  <section class="edit-page">
    <el-skeleton v-if="loading" :rows="10" animated class="loading-panel soft-panel" />

    <section v-else-if="loadError" class="error-panel soft-panel">
      <el-empty description="知识库详情加载失败">
        <template #description>
          <p class="error-text">{{ loadError }}</p>
        </template>
        <div class="error-actions">
          <el-button @click="initialize">重新加载</el-button>
          <el-button type="primary" @click="handleCancel">返回列表</el-button>
        </div>
      </el-empty>
    </section>

    <KnowledgeBaseForm
      v-else
      v-model="form"
      :chat-model-options="chatModelOptions"
      :embedding-model-options="embeddingModelOptions"
      :model-fallback="modelFallback"
      :model-loading="modelLoading"
      :submitting="submitting"
      title="编辑知识库"
      description="当前页面复用创建表单，只补齐详情回填、更新提交和列表回跳刷新，避免重复维护两套知识库表单。"
      submit-text="保存修改"
      @submit="handleSubmit"
      @cancel="handleCancel"
    />
  </section>
</template>

<style scoped>
.edit-page {
  display: flex;
  flex-direction: column;
}

.loading-panel,
.error-panel {
  padding: 28px;
}

.error-text {
  margin: 0;
  color: #6d5948;
}

.error-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
}

@media (max-width: 640px) {
  .loading-panel,
  .error-panel {
    padding: 20px;
  }

  .error-actions {
    flex-direction: column;
  }
}
</style>
