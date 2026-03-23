<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type {
  ModelCreateRequest,
  ModelDefinition,
  ModelHealthCheck,
  ModelProvider,
  ModelProviderCreateRequest,
  ModelProviderHealthCheck,
  UpdateModelRequest,
} from '@/types/model'
import {
  createModel,
  createModelProvider,
  deleteModel,
  healthCheckModel,
  healthCheckModelProvider,
  listModelProviders,
  listModels,
  setDefaultChatModel,
  updateModel,
} from '@/api/model'
import { resolveErrorMessage } from '@/api/http'

const providerLoading = ref(false)
const providerSubmitting = ref(false)
const providerManagerVisible = ref(false)
const providerDialogVisible = ref(false)
const providerCheckingIds = ref<number[]>([])
const providers = ref<ModelProvider[]>([])
const providerHealthResult = ref<ModelProviderHealthCheck | null>(null)
const activeProviderHealthId = ref<number | null>(null)

const modelLoading = ref(false)
const modelSubmitting = ref(false)
const modelDialogVisible = ref(false)
const modelDialogMode = ref<'create' | 'edit'>('create')
const editingModelId = ref<number | null>(null)
const modelCheckingIds = ref<number[]>([])
const defaultChatModelLoadingId = ref<number | null>(null)
const models = ref<ModelDefinition[]>([])
const modelHealthResult = ref<ModelHealthCheck | null>(null)
const activeModelHealthId = ref<number | null>(null)
const modelLoadError = ref('')

const pagination = reactive({
  pageNo: 1,
  pageSize: 10,
  total: 0,
})

const modelQuery = reactive({
  providerCode: '',
  capabilityType: '',
  status: '',
})

const providerForm = reactive<ModelProviderCreateRequest>({
  providerCode: '',
  providerName: '',
  baseUrl: '',
  apiKeySecretRef: '',
  status: 'ENABLED',
})

const modelForm = reactive<ModelCreateRequest>({
  providerId: null,
  modelCode: '',
  modelName: '',
  capabilityTypes: ['TEXT_GENERATION'],
  modelType: 'CHAT',
  maxTokens: null,
  temperatureDefault: 0.7,
  status: 'ENABLED',
})

const providerStatusOptions = [
  { label: '全部状态', value: '' },
  { label: '启用', value: 'ENABLED' },
  { label: '禁用', value: 'DISABLED' },
]

const providerCreateStatusOptions = providerStatusOptions.filter((item) => item.value)

const capabilityOptions = [
  { label: '全部用途', value: '' },
  { label: '聊天', value: 'TEXT_GENERATION' },
  { label: '向量化', value: 'EMBEDDING' },
]

const modelTypeOptions = [
  { label: '聊天', value: 'CHAT' },
  { label: '向量化', value: 'EMBEDDING' },
]

const hasModelData = computed(() => models.value.length > 0)
const hasDefaultChatModel = computed(() => models.value.some((item) => item.isDefaultChatModel))
const shouldShowDefaultChatModelAlert = computed(() => {
  return !modelLoading.value
    && !modelLoadError.value
    && pagination.pageNo === 1
    && !modelQuery.providerCode
    && !modelQuery.capabilityType
    && !modelQuery.status
    && pagination.total > 0
    && !hasDefaultChatModel.value
})
const showGenerationOptionFields = computed(() => !isEmbeddingModel(modelForm.modelType))
const modelDialogTitle = computed(() => (modelDialogMode.value === 'create' ? '新增模型定义' : '编辑模型定义'))
const modelDialogConfirmText = computed(() => (modelDialogMode.value === 'create' ? '确认创建' : '确认保存'))
const modelPurposeHint = computed(() => {
  if (modelForm.modelType === 'EMBEDDING') {
    return '上传 doc/pdf/png/md 后，系统会先解析或 OCR，再切片并调用这类模型生成向量并写入知识库索引，不用于对话生成。'
  }
  return '用于对话和回答生成，不参与文档向量化。像 deepseek-v3.2 这类模型通常配置为聊天用途。'
})

function statusTagType(status: string): 'success' | 'info' | 'warning' | 'danger' {
  if (status === 'ENABLED' || status === 'UP' || status === 'SUCCESS') {
    return 'success'
  }
  if (status === 'DISABLED' || status === 'UNKNOWN') {
    return 'info'
  }
  if (status === 'DOWN' || status === 'FAILED') {
    return 'danger'
  }
  return 'warning'
}

function providerChecking(providerId: number): boolean {
  return providerCheckingIds.value.includes(providerId)
}

function modelChecking(modelId: number): boolean {
  return modelCheckingIds.value.includes(modelId)
}

function capabilityLabel(capability: string): string {
  if (capability === 'TEXT_GENERATION') {
    return '聊天'
  }
  if (capability === 'EMBEDDING') {
    return '向量化'
  }
  return capability
}

function providerNameById(providerId: number | null): string {
  if (!providerId) {
    return '未绑定'
  }
  return providers.value.find((item) => item.id === providerId)?.providerName ?? `#${providerId}`
}

function isEmbeddingModel(modelType: string): boolean {
  return modelType === 'EMBEDDING'
}

function isChatModel(model: ModelDefinition): boolean {
  return model.modelType === 'CHAT' || model.capabilityTypes.includes('TEXT_GENERATION')
}

function allowedCapabilityTypes(modelType: string): string[] {
  return isEmbeddingModel(modelType) ? ['EMBEDDING'] : ['TEXT_GENERATION']
}

// 前端只暴露“模型用途”，实际提交给后端的 capabilityTypes 仍按用途自动派生。
function normalizeCapabilityTypes(modelType: string, capabilityTypes: string[]): string[] {
  const allowed = allowedCapabilityTypes(modelType)
  const normalized = capabilityTypes.filter((item) => allowed.includes(item))
  return normalized.length > 0 ? normalized : allowed
}

function normalizeRuntimeOptions(
  modelType: string,
  maxTokens: number | null | undefined,
  temperatureDefault: number | null | undefined,
): { maxTokens: number | null; temperatureDefault: number | null } {
  if (isEmbeddingModel(modelType)) {
    return {
      maxTokens: null,
      temperatureDefault: null,
    }
  }
  return {
    maxTokens: maxTokens || null,
    temperatureDefault: temperatureDefault ?? null,
  }
}

function runtimeOptionDisplay(modelType: string, value: number | string | null | undefined): number | string {
  if (isEmbeddingModel(modelType)) {
    return '不适用'
  }
  return value ?? '未配置'
}

function resetProviderForm(): void {
  providerForm.providerCode = ''
  providerForm.providerName = ''
  providerForm.baseUrl = ''
  providerForm.apiKeySecretRef = ''
  providerForm.status = 'ENABLED'
}

function resetModelForm(): void {
  editingModelId.value = null
  modelDialogMode.value = 'create'
  modelForm.providerId = null
  modelForm.modelCode = ''
  modelForm.modelName = ''
  modelForm.capabilityTypes = ['TEXT_GENERATION']
  modelForm.modelType = 'CHAT'
  modelForm.maxTokens = null
  modelForm.temperatureDefault = 0.7
  modelForm.status = 'ENABLED'
}

function openCreateModelDialog(): void {
  resetModelForm()
  modelDialogVisible.value = true
}

function openEditModelDialog(model: ModelDefinition): void {
  const runtimeOptions = normalizeRuntimeOptions(model.modelType, model.maxTokens, model.temperatureDefault)
  modelDialogMode.value = 'edit'
  editingModelId.value = model.id
  modelForm.providerId = model.providerId
  modelForm.modelCode = model.modelCode
  modelForm.modelName = model.modelName
  modelForm.modelType = model.modelType
  modelForm.capabilityTypes = normalizeCapabilityTypes(model.modelType, model.capabilityTypes)
  modelForm.maxTokens = runtimeOptions.maxTokens
  modelForm.temperatureDefault = runtimeOptions.temperatureDefault
  modelForm.status = model.status
  modelDialogVisible.value = true
}

watch(
  () => modelForm.modelType,
  (modelType) => {
    modelForm.capabilityTypes = normalizeCapabilityTypes(modelType, modelForm.capabilityTypes)
    if (isEmbeddingModel(modelType)) {
      modelForm.maxTokens = null
      modelForm.temperatureDefault = null
    }
  },
  { immediate: true },
)

async function loadProviders(): Promise<void> {
  providerLoading.value = true
  try {
    providers.value = await listModelProviders()
  } catch (error) {
    providers.value = []
    ElMessage.error(resolveErrorMessage(error))
  } finally {
    providerLoading.value = false
  }
}

async function loadModels(): Promise<void> {
  modelLoading.value = true
  modelLoadError.value = ''
  try {
    const response = await listModels({
      pageNo: pagination.pageNo,
      pageSize: pagination.pageSize,
      providerCode: modelQuery.providerCode || undefined,
      capabilityType: modelQuery.capabilityType || undefined,
      status: modelQuery.status || undefined,
    })
    models.value = response.list
    pagination.total = response.total
  } catch (error) {
    models.value = []
    pagination.total = 0
    modelLoadError.value = resolveErrorMessage(error)
  } finally {
    modelLoading.value = false
  }
}

async function loadPageData(): Promise<void> {
  await Promise.all([loadProviders(), loadModels()])
}

async function handleProviderHealthCheck(provider: ModelProvider): Promise<void> {
  providerCheckingIds.value = [...providerCheckingIds.value, provider.id]
  try {
    providerHealthResult.value = await healthCheckModelProvider(provider.id)
    activeProviderHealthId.value = provider.id
    ElMessage.success(`${provider.providerName} 探活完成`)
  } catch (error) {
    providerHealthResult.value = null
    activeProviderHealthId.value = null
    ElMessage.error(resolveErrorMessage(error))
  } finally {
    providerCheckingIds.value = providerCheckingIds.value.filter((id) => id !== provider.id)
  }
}

async function handleModelHealthCheck(model: ModelDefinition): Promise<void> {
  modelCheckingIds.value = [...modelCheckingIds.value, model.id]
  try {
    modelHealthResult.value = await healthCheckModel(model.id)
    activeModelHealthId.value = model.id
    ElMessage.success(`${model.modelName} 探活完成`)
  } catch (error) {
    modelHealthResult.value = null
    activeModelHealthId.value = null
    ElMessage.error(resolveErrorMessage(error))
  } finally {
    modelCheckingIds.value = modelCheckingIds.value.filter((id) => id !== model.id)
  }
}

async function handleCreateProvider(): Promise<void> {
  providerSubmitting.value = true
  try {
    await createModelProvider({
      ...providerForm,
      baseUrl: providerForm.baseUrl?.trim() || null,
      apiKeySecretRef: providerForm.apiKeySecretRef?.trim() || null,
    })
    providerDialogVisible.value = false
    resetProviderForm()
    await loadProviders()
    ElMessage.success('模型提供方创建成功')
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error))
  } finally {
    providerSubmitting.value = false
  }
}

function buildModelPayload(): ModelCreateRequest | UpdateModelRequest {
  const runtimeOptions = normalizeRuntimeOptions(
    modelForm.modelType,
    modelForm.maxTokens,
    modelForm.temperatureDefault,
  )
  return {
    ...modelForm,
    modelCode: modelForm.modelCode.trim(),
    modelName: modelForm.modelName.trim(),
    capabilityTypes: normalizeCapabilityTypes(modelForm.modelType, modelForm.capabilityTypes),
    maxTokens: runtimeOptions.maxTokens,
    temperatureDefault: runtimeOptions.temperatureDefault,
  }
}

async function handleSaveModel(): Promise<void> {
  modelSubmitting.value = true
  try {
    const payload = buildModelPayload()
    const editingModel = editingModelId.value
      ? models.value.find((item) => item.id === editingModelId.value)
      : null
    if (
      editingModel?.isDefaultChatModel
      && (payload.modelType !== 'CHAT' || payload.status !== 'ENABLED')
    ) {
      ElMessage.warning('当前模型已是默认聊天模型，请先切换新的默认聊天模型后再修改状态或用途')
      return
    }
    if (modelDialogMode.value === 'create') {
      await createModel(payload)
      ElMessage.success('模型创建成功')
    } else if (editingModelId.value) {
      await updateModel(editingModelId.value, payload)
      ElMessage.success('模型配置已更新')
    }
    modelDialogVisible.value = false
    resetModelForm()
    await loadModels()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error))
  } finally {
    modelSubmitting.value = false
  }
}

async function handleDeleteModel(model: ModelDefinition): Promise<void> {
  if (model.isDefaultChatModel) {
    ElMessage.warning('请先将其他聊天模型设为默认，再删除当前默认聊天模型')
    return
  }

  try {
    await ElMessageBox.confirm(
      `删除后无法恢复，确定删除模型“${model.modelName}”吗？`,
      '删除模型',
      {
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  try {
    await deleteModel(model.id)
    if (activeModelHealthId.value === model.id) {
      activeModelHealthId.value = null
      modelHealthResult.value = null
    }
    if (models.value.length === 1 && pagination.pageNo > 1) {
      pagination.pageNo -= 1
    }
    await loadModels()
    ElMessage.success('模型已删除')
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error))
  }
}

async function handleSetDefaultChatModel(model: ModelDefinition): Promise<void> {
  if (model.isDefaultChatModel) {
    return
  }
  defaultChatModelLoadingId.value = model.id
  try {
    await setDefaultChatModel(model.id)
    await loadModels()
    ElMessage.success(`已将 ${model.modelName} 设为默认聊天模型`)
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error))
  } finally {
    defaultChatModelLoadingId.value = null
  }
}

async function handleSearchModels(): Promise<void> {
  pagination.pageNo = 1
  await loadModels()
}

async function handleResetModels(): Promise<void> {
  modelQuery.providerCode = ''
  modelQuery.capabilityType = ''
  modelQuery.status = ''
  pagination.pageNo = 1
  await loadModels()
}

async function handleCurrentChange(pageNo: number): Promise<void> {
  pagination.pageNo = pageNo
  await loadModels()
}

async function handleSizeChange(pageSize: number): Promise<void> {
  pagination.pageSize = pageSize
  pagination.pageNo = 1
  await loadModels()
}

onMounted(async () => {
  await loadPageData()
})
</script>

<template>
  <section class="model-page">
    <header class="model-head">
      <div>
        <h1 class="page-title">模型管理</h1>
        <p class="page-subtitle">
          支持模型列表、筛选、探活、新增、编辑和删除。提供方维护保留在次级入口，避免影响主流程操作。
        </p>
      </div>
      <div class="head-actions">
        <el-button @click="loadPageData">刷新页面</el-button>
        <el-button plain @click="providerManagerVisible = true">管理提供方</el-button>
        <el-button type="primary" @click="openCreateModelDialog">新增模型</el-button>
      </div>
    </header>

    <section class="model-panel soft-panel">
      <div class="section-head">
        <div>
          <h2 class="section-title">模型定义</h2>
          <p class="section-subtitle">
            聊天模型用于问答生成，向量模型用于文档切片向量化与检索。默认聊天模型只能在本页手动设置，且运行时只允许存在一个启用中的默认聊天模型。
          </p>
        </div>
      </div>

      <el-alert
        v-if="shouldShowDefaultChatModelAlert"
        type="error"
        :closable="false"
        show-icon
        title="当前尚未设置默认聊天模型，问答链路将不可用，请先在本页选择一个启用中的聊天模型设为默认。"
      />

      <section class="filter-panel">
        <div class="filter-grid">
          <el-select v-model="modelQuery.providerCode" placeholder="提供方" clearable>
            <el-option
              v-for="item in providers"
              :key="item.id"
              :label="item.providerName"
              :value="item.providerCode"
            />
          </el-select>

          <el-select v-model="modelQuery.capabilityType" placeholder="模型用途" clearable>
            <el-option
              v-for="item in capabilityOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>

          <el-select v-model="modelQuery.status" placeholder="状态" clearable>
            <el-option
              v-for="item in providerStatusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </div>

        <div class="filter-actions">
          <el-button @click="handleResetModels">重置</el-button>
          <el-button type="primary" @click="handleSearchModels">查询模型</el-button>
        </div>
      </section>

      <section v-if="modelLoadError" class="inline-error">
        {{ modelLoadError }}
      </section>

      <el-table :data="models" v-loading="modelLoading" empty-text="暂无模型定义" stripe>
        <el-table-column prop="modelCode" label="模型编码" min-width="180" />
        <el-table-column label="模型名称" min-width="220">
          <template #default="{ row }">
            <div class="model-name-cell">
              <span>{{ row.modelName }}</span>
              <el-tag
                v-if="row.isDefaultChatModel"
                effect="plain"
                type="warning"
                class="default-model-tag"
              >
                默认聊天模型
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="提供方" min-width="140">
          <template #default="{ row }">
            {{ row.providerName || row.providerCode || '未知' }}
          </template>
        </el-table-column>
        <el-table-column label="模型用途" min-width="200">
          <template #default="{ row }">
            <div class="capability-list">
              <el-tag
                v-for="capability in row.capabilityTypes"
                :key="`${row.id}-${capability}`"
                effect="plain"
              >
                {{ capabilityLabel(capability) }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="最大令牌数" width="120">
          <template #default="{ row }">
            {{ runtimeOptionDisplay(row.modelType, row.maxTokens) }}
          </template>
        </el-table-column>
        <el-table-column label="默认温度" width="120">
          <template #default="{ row }">
            {{ runtimeOptionDisplay(row.modelType, row.temperatureDefault) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="310" fixed="right">
          <template #default="{ row }">
            <div class="action-links">
              <el-button link type="primary" @click="openEditModelDialog(row)">编辑</el-button>
              <el-button
                link
                type="primary"
                :loading="modelChecking(row.id)"
                @click="handleModelHealthCheck(row)"
              >
                探活
              </el-button>
              <el-button
                v-if="isChatModel(row)"
                link
                :type="row.isDefaultChatModel ? 'warning' : 'primary'"
                :loading="defaultChatModelLoadingId === row.id"
                :disabled="row.status !== 'ENABLED' || row.isDefaultChatModel"
                @click="handleSetDefaultChatModel(row)"
              >
                {{ row.isDefaultChatModel ? '默认中' : '设为默认' }}
              </el-button>
              <el-button link type="danger" @click="handleDeleteModel(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer" v-if="hasModelData || pagination.total > 0">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :current-page="pagination.pageNo"
          :page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="pagination.total"
          @current-change="handleCurrentChange"
          @size-change="handleSizeChange"
        />
      </div>

      <section v-if="modelHealthResult" class="health-result">
        <div class="health-header">
          <strong>
            最近一次模型探活结果
            <template v-if="activeModelHealthId">
              / #{{ activeModelHealthId }}
            </template>
          </strong>
          <el-tag :type="statusTagType(modelHealthResult.status)">
            {{ modelHealthResult.status }}
          </el-tag>
        </div>
        <p class="health-message">{{ modelHealthResult.message }}</p>
        <div class="health-detail-list">
          <article
            v-for="item in modelHealthResult.capabilityChecks"
            :key="`${modelHealthResult.modelId}-${item.capabilityType}`"
            class="health-detail-item"
          >
            <div class="health-item-head">
              <el-tag :type="statusTagType(item.status)">
                {{ capabilityLabel(item.capabilityType) }} / {{ item.status }}
              </el-tag>
            </div>
            <p class="health-item-message">{{ item.message }}</p>
          </article>
        </div>
      </section>
    </section>

    <el-dialog v-model="providerManagerVisible" title="管理模型提供方" width="960px">
      <div class="provider-manager-head">
        <p class="provider-manager-tip">
          提供方用于承接模型路由和接入配置，集中放在这里维护。
        </p>
        <el-button type="primary" @click="providerDialogVisible = true">新增提供方</el-button>
      </div>

      <el-table :data="providers" v-loading="providerLoading" empty-text="暂无模型提供方" stripe>
        <el-table-column prop="providerCode" label="提供方编码" min-width="150" />
        <el-table-column prop="providerName" label="提供方名称" min-width="160" />
        <el-table-column label="接入地址" min-width="240">
          <template #default="{ row }">
            {{ row.baseUrl || '未配置' }}
          </template>
        </el-table-column>
        <el-table-column label="密钥引用" min-width="180">
          <template #default="{ row }">
            {{ row.apiKeySecretRef || '未配置' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :loading="providerChecking(row.id)"
              @click="handleProviderHealthCheck(row)"
            >
              探活
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <section v-if="providerHealthResult" class="health-result">
        <div class="health-header">
          <strong>
            最近一次提供方探活结果
            <template v-if="activeProviderHealthId">
              / #{{ activeProviderHealthId }}
            </template>
          </strong>
          <el-tag :type="statusTagType(providerHealthResult.status)">
            {{ providerHealthResult.status }}
          </el-tag>
        </div>
        <p class="health-message">{{ providerHealthResult.message }}</p>
        <div class="health-detail-list">
          <article
            v-for="item in providerHealthResult.capabilityChecks"
            :key="`${providerHealthResult.providerId}-${item.capabilityType}`"
            class="health-detail-item"
          >
            <div class="health-item-head">
              <el-tag :type="statusTagType(item.status)">
                {{ capabilityLabel(item.capabilityType) }} / {{ item.status }}
              </el-tag>
              <span class="health-item-model">
                {{ item.modelCode ? `模型 ${item.modelCode}` : '当前无可用模型' }}
              </span>
            </div>
            <p class="health-item-message">{{ item.message }}</p>
          </article>
        </div>
      </section>
    </el-dialog>

    <el-dialog v-model="providerDialogVisible" title="新增模型提供方" width="560px">
      <el-form label-position="top">
        <el-form-item label="提供方编码">
          <el-input v-model="providerForm.providerCode" placeholder="例如 BAILIAN / OLLAMA / OPENAI" />
        </el-form-item>
        <el-form-item label="提供方名称">
          <el-input v-model="providerForm.providerName" placeholder="请输入提供方名称" />
        </el-form-item>
        <el-form-item label="接入地址">
          <el-input v-model="providerForm.baseUrl" placeholder="可为空，例如 https://dashscope.aliyuncs.com" />
        </el-form-item>
        <el-form-item label="密钥引用">
          <el-input v-model="providerForm.apiKeySecretRef" placeholder="可为空，例如 secret/bailian/api-key" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="providerForm.status">
            <el-radio
              v-for="item in providerCreateStatusOptions"
              :key="item.value"
              :value="item.value"
            >
              {{ item.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="providerDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="providerSubmitting" @click="handleCreateProvider">
            确认创建
          </el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="modelDialogVisible" :title="modelDialogTitle" width="620px" @closed="resetModelForm">
      <el-form label-position="top">
        <el-form-item label="提供方">
          <el-select v-model="modelForm.providerId" placeholder="请选择模型提供方">
            <el-option
              v-for="item in providers"
              :key="item.id"
              :label="item.providerName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="模型编码">
          <el-input v-model="modelForm.modelCode" placeholder="例如 qwen-max / text-embedding-v3 / deepseek-v3.2" />
        </el-form-item>
        <el-form-item label="模型名称">
          <el-input v-model="modelForm.modelName" placeholder="请输入模型展示名称" />
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="模型用途">
            <el-select v-model="modelForm.modelType">
              <el-option
                v-for="item in modelTypeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
            <div class="capability-hint">{{ modelPurposeHint }}</div>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="modelForm.status">
              <el-option
                v-for="item in providerCreateStatusOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </div>
        <div v-if="showGenerationOptionFields" class="form-grid">
          <el-form-item label="最大令牌数">
            <el-input-number v-model="modelForm.maxTokens" :min="1" :step="256" controls-position="right" />
          </el-form-item>
          <el-form-item label="默认温度">
            <el-input-number
              v-model="modelForm.temperatureDefault"
              :min="0"
              :max="2"
              :step="0.1"
              controls-position="right"
            />
          </el-form-item>
        </div>
        <div class="provider-tip">
          当前选择的提供方：<strong>{{ providerNameById(modelForm.providerId) }}</strong>
        </div>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="modelDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="modelSubmitting" @click="handleSaveModel">
            {{ modelDialogConfirmText }}
          </el-button>
        </div>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.model-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.model-head,
.section-head,
.health-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.head-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.model-panel {
  padding: 20px 22px;
}

.provider-manager-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 16px;
}

.provider-manager-tip {
  margin: 0;
  color: #6d5948;
  line-height: 1.7;
}

.section-subtitle,
.health-message,
.provider-tip,
.capability-hint {
  margin: 12px 0 0;
  color: #6d5948;
}

.section-title {
  margin: 0;
  font-family: "Noto Serif SC", serif;
  font-size: 24px;
}

.section-subtitle {
  margin-top: 8px;
}

.filter-panel {
  padding: 18px 0 20px;
}

.filter-grid,
.form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.form-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.filter-actions,
.dialog-footer,
.table-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.filter-actions,
.table-footer {
  margin-top: 18px;
}

.capability-list,
.action-links {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.model-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.default-model-tag {
  flex: none;
}

.health-result {
  margin-top: 18px;
  padding: 16px 18px;
  border-radius: 18px;
  background: rgba(255, 248, 238, 0.88);
}

.health-detail-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 14px;
}

.health-detail-item {
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.72);
}

.health-item-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.health-item-model {
  color: #8a715e;
  font-size: 12px;
}

.health-item-message {
  margin: 10px 0 0;
  color: #6d5948;
  line-height: 1.6;
}

.inline-error {
  margin-bottom: 16px;
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(168, 52, 26, 0.08);
  color: #8d4510;
}

.provider-tip,
.capability-hint {
  font-size: 13px;
}

@media (max-width: 960px) {
  .model-head,
  .section-head,
  .health-header,
  .provider-manager-head {
    flex-direction: column;
  }

  .filter-grid,
  .form-grid {
    grid-template-columns: 1fr;
  }

  .head-actions {
    width: 100%;
    justify-content: flex-start;
  }
}

@media (max-width: 640px) {
  .head-actions,
  .filter-actions,
  .dialog-footer {
    flex-direction: column;
  }
}
</style>
