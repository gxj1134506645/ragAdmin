<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import KnowledgeBaseForm from '@/components/knowledge-base/KnowledgeBaseForm.vue'
import { createKnowledgeBase, listModels } from '@/api/knowledge-base'
import { resolveErrorMessage } from '@/api/http'
import type { KnowledgeBaseUpsertRequest, ModelDefinition } from '@/types/knowledge-base'
import {
  buildKnowledgeBaseModelOptions,
  createEmptyKnowledgeBaseForm,
  normalizeKnowledgeBaseForm,
} from '@/utils/knowledge-base-form'

const router = useRouter()
const submitting = ref(false)
const modelLoading = ref(false)
const modelFallback = ref(false)
const chatModelOptions = ref<ModelDefinition[]>([])
const embeddingModelOptions = ref<ModelDefinition[]>([])

const form = reactive<KnowledgeBaseUpsertRequest>(createEmptyKnowledgeBaseForm())

async function loadModelOptions(): Promise<void> {
  modelLoading.value = true
  modelFallback.value = false
  try {
    const response = await listModels({
      pageNo: 1,
      pageSize: 100,
      status: 'ENABLED',
    })
    const options = buildKnowledgeBaseModelOptions(response.list)
    chatModelOptions.value = options.chatModelOptions
    embeddingModelOptions.value = options.embeddingModelOptions
  } catch {
    modelFallback.value = true
    chatModelOptions.value = []
    embeddingModelOptions.value = []
  } finally {
    modelLoading.value = false
  }
}

async function handleSubmit(): Promise<void> {
  submitting.value = true
  try {
    await createKnowledgeBase(normalizeKnowledgeBaseForm(form))
    await router.replace({
      path: '/knowledge-bases',
      query: {
        created: '1',
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
  await loadModelOptions()
})
</script>

<template>
  <section class="create-page">
    <KnowledgeBaseForm
      v-model="form"
      :chat-model-options="chatModelOptions"
      :embedding-model-options="embeddingModelOptions"
      :model-fallback="modelFallback"
      :model-loading="modelLoading"
      :submitting="submitting"
      title="新建知识库"
      description="当前页面用于创建知识库并回到列表页，模型项支持真实接口和默认模型兜底双模式。"
      submit-text="创建知识库"
      @submit="handleSubmit"
      @cancel="handleCancel"
    />
  </section>
</template>

<style scoped>
.create-page {
  display: flex;
  flex-direction: column;
}
</style>
