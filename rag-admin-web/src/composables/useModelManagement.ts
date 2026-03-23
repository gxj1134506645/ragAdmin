import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type {
  ModelBatchDeleteResult,
  ModelCreateRequest,
  ModelDefinition,
  ModelHealthCheck,
  ModelProvider,
  ModelProviderCreateRequest,
  ModelProviderHealthCheck,
  UpdateModelRequest,
} from '@/types/model'
import {
  batchDeleteModels,
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
import {
  buildModelDraftFromDefinition,
  createEmptyModelForm,
  detectModelScene,
  isChatModel,
  isEmbeddingModel,
  isArchitectureReady,
  sanitizeModelPayload,
} from '@/utils/model-management'

export function useModelManagement() {
  const providerLoading = ref(false)
  const providerSubmitting = ref(false)
  const providerDrawerVisible = ref(false)
  const providerFormDialogVisible = ref(false)
  const providerCheckingIds = ref<number[]>([])
  const providers = ref<ModelProvider[]>([])
  const providerHealthResult = ref<ModelProviderHealthCheck | null>(null)
  const activeProviderHealthId = ref<number | null>(null)

  const modelLoading = ref(false)
  const modelSubmitting = ref(false)
  const batchDeleteSubmitting = ref(false)
  const modelDialogVisible = ref(false)
  const modelDialogMode = ref<'create' | 'edit'>('create')
  const editingModelId = ref<number | null>(null)
  const modelCheckingIds = ref<number[]>([])
  const defaultChatModelLoadingId = ref<number | null>(null)
  const models = ref<ModelDefinition[]>([])
  const selectedModels = ref<ModelDefinition[]>([])
  const modelHealthResult = ref<ModelHealthCheck | null>(null)
  const activeModelHealthId = ref<number | null>(null)
  const modelLoadError = ref('')
  const modelDialogDraft = ref<ModelCreateRequest>(createEmptyModelForm())

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

  const hasModelData = computed(() => models.value.length > 0)
  const hasSelectedModels = computed(() => selectedModels.value.length > 0)
  const selectedModelCount = computed(() => selectedModels.value.length)
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

  const currentPageEmbeddingCount = computed(() => {
    return models.value.filter((item) => isEmbeddingModel(item.modelType)).length
  })

  const currentPageUnsupportedEmbeddingCount = computed(() => {
    return models.value.filter((item) => {
      if (!isEmbeddingModel(item.modelType)) {
        return false
      }
      return !isArchitectureReady(detectModelScene(item.modelType, item.modelCode))
    }).length
  })

  function providerChecking(providerId: number): boolean {
    return providerCheckingIds.value.includes(providerId)
  }

  function modelChecking(modelId: number): boolean {
    return modelCheckingIds.value.includes(modelId)
  }

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
      selectedModels.value = []
    } catch (error) {
      models.value = []
      selectedModels.value = []
      pagination.total = 0
      modelLoadError.value = resolveErrorMessage(error)
    } finally {
      modelLoading.value = false
    }
  }

  async function loadPageData(): Promise<void> {
    await Promise.all([loadProviders(), loadModels()])
  }

  function openProviderDrawer(): void {
    providerDrawerVisible.value = true
  }

  function openCreateProviderDialog(): void {
    providerFormDialogVisible.value = true
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

  async function handleCreateProvider(payload: ModelProviderCreateRequest): Promise<void> {
    providerSubmitting.value = true
    try {
      await createModelProvider({
        ...payload,
        baseUrl: payload.baseUrl?.trim() || null,
        apiKeySecretRef: payload.apiKeySecretRef?.trim() || null,
      })
      providerFormDialogVisible.value = false
      await loadProviders()
      ElMessage.success('模型提供方创建成功')
    } catch (error) {
      ElMessage.error(resolveErrorMessage(error))
    } finally {
      providerSubmitting.value = false
    }
  }

  function openCreateModelDialog(): void {
    editingModelId.value = null
    modelDialogMode.value = 'create'
    modelDialogDraft.value = createEmptyModelForm()
    modelDialogVisible.value = true
  }

  function openEditModelDialog(model: ModelDefinition): void {
    editingModelId.value = model.id
    modelDialogMode.value = 'edit'
    modelDialogDraft.value = buildModelDraftFromDefinition(model)
    modelDialogVisible.value = true
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

  async function handleSaveModel(payload: ModelCreateRequest | UpdateModelRequest): Promise<void> {
    modelSubmitting.value = true
    try {
      const sanitizedPayload = sanitizeModelPayload(payload)
      const editingModel = editingModelId.value
        ? models.value.find((item) => item.id === editingModelId.value)
        : null
      if (
        editingModel?.isDefaultChatModel
        && (sanitizedPayload.modelType !== 'CHAT' || sanitizedPayload.status !== 'ENABLED')
      ) {
        ElMessage.warning('当前模型已是默认聊天模型，请先切换新的默认聊天模型后再修改状态或用途')
        return
      }

      if (modelDialogMode.value === 'create') {
        await createModel(sanitizedPayload)
        ElMessage.success('模型创建成功')
      } else if (editingModelId.value) {
        await updateModel(editingModelId.value, sanitizedPayload)
        ElMessage.success('模型配置已更新')
      }

      modelDialogVisible.value = false
      modelDialogDraft.value = createEmptyModelForm()
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

    await deleteSingleModel(model)
  }

  async function deleteSingleModel(model: ModelDefinition): Promise<void> {
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
      ElMessage.success(`模型 ${model.modelName} 已删除`)
    } catch (error) {
      ElMessage.error(resolveErrorMessage(error))
    }
  }

  function handleSelectionChange(selection: ModelDefinition[]): void {
    selectedModels.value = selection
  }

  async function handleBatchDeleteModels(): Promise<void> {
    if (!hasSelectedModels.value || batchDeleteSubmitting.value) {
      return
    }

    try {
      await ElMessageBox.confirm(
        `确定批量删除 ${selectedModelCount.value} 个模型吗？默认聊天模型或被业务引用的模型会保留并记为失败。`,
        '确认批量删除',
        {
          confirmButtonText: '确认删除',
          cancelButtonText: '取消',
          type: 'warning',
        },
      )
    } catch {
      return
    }

    batchDeleteSubmitting.value = true
    const targetModels = [...selectedModels.value]
    const selectedIds = targetModels.map((item) => item.id)

    try {
      const response = await batchDeleteModels({
        modelIds: selectedIds,
      })

      if (
        response.successCount > 0
        && response.successCount === targetModels.length
        && targetModels.length === models.value.length
        && pagination.pageNo > 1
      ) {
        pagination.pageNo -= 1
      }

      if (activeModelHealthId.value != null && response.deletedIds.includes(activeModelHealthId.value)) {
        activeModelHealthId.value = null
        modelHealthResult.value = null
      }

      await loadModels()

      if (response.failedCount === 0) {
        ElMessage.success(`已删除 ${response.successCount} 个模型`)
        return
      }

      ElMessage.warning(buildBatchDeleteSummary(response))
    } finally {
      batchDeleteSubmitting.value = false
    }
  }

  async function handleSetDefaultChatModel(model: ModelDefinition): Promise<void> {
    if (model.isDefaultChatModel || !isChatModel(model)) {
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

  return {
    providerLoading,
    providerSubmitting,
    providerDrawerVisible,
    providerFormDialogVisible,
    providerCheckingIds,
    providers,
    providerHealthResult,
    activeProviderHealthId,
    modelLoading,
    modelSubmitting,
    batchDeleteSubmitting,
    modelDialogVisible,
    modelDialogMode,
    editingModelId,
    modelCheckingIds,
    defaultChatModelLoadingId,
    models,
    selectedModels,
    modelHealthResult,
    activeModelHealthId,
    modelLoadError,
    modelDialogDraft,
    pagination,
    modelQuery,
    hasModelData,
    hasSelectedModels,
    selectedModelCount,
    shouldShowDefaultChatModelAlert,
    currentPageEmbeddingCount,
    currentPageUnsupportedEmbeddingCount,
    providerChecking,
    modelChecking,
    loadProviders,
    loadModels,
    loadPageData,
    openProviderDrawer,
    openCreateProviderDialog,
    handleProviderHealthCheck,
    handleCreateProvider,
    openCreateModelDialog,
    openEditModelDialog,
    handleModelHealthCheck,
    handleSaveModel,
    handleDeleteModel,
    handleSelectionChange,
    handleBatchDeleteModels,
    handleSetDefaultChatModel,
    handleSearchModels,
    handleResetModels,
    handleCurrentChange,
    handleSizeChange,
  }
}

function buildBatchDeleteSummary(result: ModelBatchDeleteResult): string {
  if (result.failedCount === 0) {
    return `已删除 ${result.successCount} 个模型`
  }
  const detail = result.failedItems
    .slice(0, 2)
    .map((item) => `${item.modelName}：${item.message}`)
    .join('；')
  return `批量删除完成，成功 ${result.successCount} 个，失败 ${result.failedCount} 个${detail ? `。${detail}` : ''}`
}
