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
  { label: '全部能力', value: '' },
  { label: '文本生成', value: 'TEXT_GENERATION' },
  { label: '向量生成', value: 'EMBEDDING' },
]

const modelTypeOptions = [
  { label: '聊天模型', value: 'CHAT' },
  { label: '向量模型', value: 'EMBEDDING' },
]

const modelCapabilityCreateOptions = capabilityOptions.filter((item) => item.value)

const hasModelData = computed(() => models.value.length > 0)
const modelDialogTitle = computed(() => (modelDialogMode.value === 'create' ? '新增模型定义' : '编辑模型定义'))
const modelDialogConfirmText = computed(() => (modelDialogMode.value === 'create' ? '确认创建' : '确认保存'))
const modelCapabilityHint = computed(() => {
  if (modelForm.modelType === 'EMBEDDING') {
    return '向量模型只允许保留“向量生成”能力。'
  }
  return '聊天模型只允许保留“文本生成”能力，像 deepseek-v3.2 这类模型不要勾选向量生成。'
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
    return '文本生成'
  }
  if (capability === 'EMBEDDING') {
    return '向量生成'
  }
  return capability
}

function modelTypeLabel(modelType: string): string {
  if (modelType === 'CHAT') {
    return '聊天模型'
  }
  if (modelType === 'EMBEDDING') {
    return '向量模型'
  }
  return modelType
}

function providerNameById(providerId: number | null): string {
  if (!providerId) {
    return '未绑定'
  }
  return providers.value.find((item) => item.id === providerId)?.providerName ?? `#${providerId}`
}

function allowedCapabilityTypes(modelType: string): string[] {
  return modelType === 'EMBEDDING' ? ['EMBEDDING'] : ['TEXT_GENERATION']
}

function normalizeCapabilityTypes(modelType: string, capabilityTypes: string[]): string[] {
  const allowed = allowedCapabilityTypes(modelType)
  const normalized = capabilityTypes.filter((item) => allowed.includes(item))
  return normalized.length > 0 ? normalized : allowed
}

function capabilityDisabled(capability: string): boolean {
  return !allowedCapabilityTypes(modelForm.modelType).includes(capability)
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
  modelDialogMode.value = 'edit'
  editingModelId.value = model.id
  modelForm.providerId = model.providerId
  modelForm.modelCode = model.modelCode
  modelForm.modelName = model.modelName
  modelForm.modelType = model.modelType
  modelForm.capabilityTypes = normalizeCapabilityTypes(model.modelType, model.capabilityTypes)
  modelForm.maxTokens = model.maxTokens ?? null
  modelForm.temperatureDefault = model.temperatureDefault ?? 0.7
  modelForm.status = model.status
  modelDialogVisible.value = true
}

watch(
  () => modelForm.modelType,
  (modelType) => {
    modelForm.capabilityTypes = normalizeCapabilityTypes(modelType, modelForm.capabilityTypes)
  },
  { immediate: true },
)

watch(
  () => modelForm.capabilityTypes.join(','),
  () => {
    const normalized = normalizeCapabilityTypes(modelForm.modelType, modelForm.capabilityTypes)
    if (normalized.join(',') !== modelForm.capabilityTypes.join(',')) {
      modelForm.capabilityTypes = normalized
    }
  },
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
  return {
    ...modelForm,
    modelCode: modelForm.modelCode.trim(),
    modelName: modelForm.modelName.trim(),
    capabilityTypes: normalizeCapabilityTypes(modelForm.modelType, modelForm.capabilityTypes),
    maxTokens: modelForm.maxTokens || null,
    temperatureDefault: modelForm.temperatureDefault ?? null,
  }
}

async function handleSaveModel(): Promise<void> {
  modelSubmitting.value = true
  try {
    const payload = buildModelPayload()
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
          <p class="section-subtitle">支持按提供方、能力和状态筛选，并提供新增、编辑、删除和探活能力。</p>
        </div>
      </div>

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

          <el-select v-model="modelQuery.capabilityType" placeholder="能力类型" clearable>
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
        <el-table-column prop="modelName" label="模型名称" min-width="180" />
        <el-table-column label="提供方" min-width="140">
          <template #default="{ row }">
            {{ row.providerName || row.providerCode || '未知' }}
          </template>
        </el-table-column>
        <el-table-column label="能力类型" min-width="200">
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
        <el-table-column label="模型类型" width="120">
          <template #default="{ row }">
            {{ modelTypeLabel(row.modelType) }}
          </template>
        </el-table-column>
        <el-table-column label="最大令牌数" width="120">
          <template #default="{ row }">
            {{ row.maxTokens ?? '未配置' }}
          </template>
        </el-table-column>
        <el-table-column label="默认温度" width="120">
          <template #default="{ row }">
            {{ row.temperatureDefault ?? '未配置' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
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
        <el-form-item label="能力类型">
          <el-checkbox-group v-model="modelForm.capabilityTypes">
            <el-checkbox
              v-for="item in modelCapabilityCreateOptions"
              :key="item.value"
              :label="item.value"
              :disabled="capabilityDisabled(item.value)"
            >
              {{ item.label }}
            </el-checkbox>
          </el-checkbox-group>
          <div class="capability-hint">{{ modelCapabilityHint }}</div>
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="模型类型">
            <el-select v-model="modelForm.modelType">
              <el-option
                v-for="item in modelTypeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
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
        <div class="form-grid">
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
