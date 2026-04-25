<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElButton, ElEmpty, ElMessage, ElMessageBox, ElSkeleton } from 'element-plus'
import type { UploadFile, UploadFiles, UploadUserFile } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { subscribeKnowledgeBaseDocumentEvents, type RealtimeStreamHandle } from '@/api/realtime'
import {
  createKnowledgeBaseDocument,
  deleteKnowledgeBaseDocument,
  getKnowledgeBaseDetail,
  getKnowledgeBaseDocumentUploadCapability,
  getKnowledgeBaseDocumentUploadUrl,
  listKnowledgeBaseDocuments,
  triggerDocumentParse,
} from '@/api/knowledge-base'
import { resolveErrorMessage } from '@/api/http'
import type { TaskRealtimeEvent } from '@/types/realtime'
import { DOCUMENT_UPLOAD_ACCEPT, inferDocumentType, isOcrImageDocumentType, isPdfDocumentType } from '@/utils/document-upload'
import { buildParseProgressState } from '@/utils/task-progress'
import { DOCUMENT_PARSE_STATUS_OPTIONS, formatDocumentParseStatus, formatResourceStatus } from '@/utils/task'
import type {
  CreateKnowledgeBaseDocumentRequest,
  DocumentUploadCapability,
  KnowledgeBase,
  KnowledgeBaseDocument,
  UploadUrlResponse,
} from '@/types/knowledge-base'

interface UploadResultItem {
  key: string
  name: string
  status: 'success' | 'warning' | 'error'
  message: string
}

interface SelectedUploadFileSummary {
  key: string
  name: string
  sizeLabel: string
  docType: string
}

const route = useRoute()
const router = useRouter()

const detailLoading = ref(true)
const detailError = ref('')
const knowledgeBase = ref<KnowledgeBase | null>(null)

const documentLoading = ref(false)
const documentError = ref('')
const documents = ref<KnowledgeBaseDocument[]>([])
const selectedDocuments = ref<KnowledgeBaseDocument[]>([])
const documentFilters = reactive({
  keyword: '',
  parseStatus: '',
  enabled: '',
})
const documentPagination = reactive({
  pageNo: 1,
  pageSize: 10,
  total: 0,
})
const uploadDialogVisible = ref(false)
const uploadFileList = ref<UploadUserFile[]>([])
const uploadSubmitting = ref(false)
const uploadError = ref('')
const uploadResults = ref<UploadResultItem[]>([])
const uploadCapability = ref<DocumentUploadCapability | null>(null)
const uploadCapabilityLoading = ref(false)
const uploadCapabilityError = ref('')
const autoParseAfterUpload = ref(true)
const parsingDocumentIds = ref<number[]>([])
const deletingDocumentIds = ref<number[]>([])
const batchRetrySubmitting = ref(false)
const batchDeleteSubmitting = ref(false)
const uploadProgress = reactive({
  total: 0,
  completed: 0,
  currentFileName: '',
})
const documentTableRef = ref<{ clearSelection?: () => void } | null>(null)
let documentAutoRefreshTimer: ReturnType<typeof window.setTimeout> | null = null
let documentRealtimeRefreshTimer: ReturnType<typeof window.setTimeout> | null = null
let knowledgeBaseRealtimeStream: RealtimeStreamHandle | null = null
const documentRealtimeEvents = reactive<Record<number, TaskRealtimeEvent>>({})

const knowledgeBaseId = computed(() => Number(route.params.id))
const hasDocuments = computed(() => documents.value.length > 0)
const selectedDocumentCount = computed(() => selectedDocuments.value.length)
const hasSelectedDocuments = computed(() => selectedDocumentCount.value > 0)
const selectedRetryableDocuments = computed(() =>
  selectedDocuments.value.filter((item) => canTriggerParse(item.parseStatus)),
)
const selectedRetryableDocumentCount = computed(() => selectedRetryableDocuments.value.length)
const selectedNonRetryableDocumentCount = computed(
  () => selectedDocumentCount.value - selectedRetryableDocumentCount.value,
)
const hasActiveParseDocuments = computed(() =>
  documents.value.some((item) => isActiveParseStatus(item.parseStatus)),
)
const selectedFiles = computed<File[]>(() =>
  uploadFileList.value.flatMap((item) => (item.raw instanceof File ? [item.raw] : [])),
)
const hasSelectedFiles = computed(() => selectedFiles.value.length > 0)
const selectedFileSummaries = computed<SelectedUploadFileSummary[]>(() =>
  selectedFiles.value.map((file) => ({
    key: buildFileKey(file),
    name: file.name,
    sizeLabel: formatFileSize(file.size),
    docType: inferDocumentType(file.name),
  })),
)
const hasPdfSelection = computed(() =>
  selectedFileSummaries.value.some((item) => isPdfDocumentType(item.docType)),
)
const hasOcrImageSelection = computed(() =>
  selectedFileSummaries.value.some((item) => isOcrImageDocumentType(item.docType)),
)
const supportedDocTypesText = computed(() => {
  const docTypes = uploadCapability.value?.supportedDocTypes ?? [
    'TXT',
    'MD',
    'MARKDOWN',
    'PDF',
    'DOCX',
    'PPTX',
    'XLSX',
    'PNG',
    'JPG',
    'JPEG',
    'WEBP',
  ]
  const labelByDocType: Record<string, string> = {
    TXT: 'TXT',
    MD: 'Markdown（.md）',
    MARKDOWN: 'Markdown（.md / .markdown）',
    PDF: 'PDF',
    DOCX: 'DOCX',
    PPTX: 'PPTX',
    XLSX: 'XLSX',
    PNG: 'PNG',
    JPG: 'JPG',
    JPEG: 'JPEG',
    WEBP: 'WEBP',
  }
  const normalized = docTypes.includes('MARKDOWN') ? docTypes.filter((item) => item !== 'MD') : docTypes
  return normalized.map((item) => labelByDocType[item] ?? item).join(' / ')
})

const uploadOcrAlertType = computed<'success' | 'warning' | 'info'>(() => {
  if (!uploadCapability.value?.ocrEnabled) {
    return 'info'
  }
  return uploadCapability.value.ocrAvailable ? 'success' : 'warning'
})

function embeddingModelLabel(name: string | null): string {
  return name || '已绑定模型待解析'
}

function statusType(status: string): 'success' | 'info' | 'warning' {
  if (status === 'ENABLED') {
    return 'success'
  }
  if (status === 'DISABLED') {
    return 'info'
  }
  return 'warning'
}

function parseStatusType(status: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'SUCCESS') {
    return 'success'
  }
  if (status === 'PROCESSING' || status === 'PENDING') {
    return 'warning'
  }
  if (status === 'FAILED') {
    return 'danger'
  }
  return 'info'
}

function enabledLabel(enabled: boolean): string {
  return enabled ? '启用' : '停用'
}

function canTriggerParse(status: string): boolean {
  return status === 'PENDING' || status === 'FAILED'
}

function isActiveParseStatus(status: string): boolean {
  return status === 'PENDING' || status === 'PROCESSING'
}

function clearDocumentSelection(): void {
  selectedDocuments.value = []
  documentTableRef.value?.clearSelection?.()
}

function clearDocumentAutoRefreshTimer(): void {
  if (documentAutoRefreshTimer !== null) {
    window.clearTimeout(documentAutoRefreshTimer)
    documentAutoRefreshTimer = null
  }
}

function clearDocumentRealtimeRefreshTimer(): void {
  if (documentRealtimeRefreshTimer !== null) {
    window.clearTimeout(documentRealtimeRefreshTimer)
    documentRealtimeRefreshTimer = null
  }
}

function closeKnowledgeBaseRealtimeStream(): void {
  knowledgeBaseRealtimeStream?.close()
  knowledgeBaseRealtimeStream = null
}

function getDocumentRealtimeEvent(documentId: number): TaskRealtimeEvent | null {
  return documentRealtimeEvents[documentId] ?? null
}

function progressStateOfDocument(document: KnowledgeBaseDocument) {
  return buildParseProgressState(document.parseStatus, getDocumentRealtimeEvent(document.documentId))
}

function scheduleRealtimeDocumentRefresh(): void {
  clearDocumentRealtimeRefreshTimer()
  documentRealtimeRefreshTimer = window.setTimeout(async () => {
    documentRealtimeRefreshTimer = null
    await loadDocuments()
  }, 300)
}

function handleKnowledgeBaseRealtimeEvent(event: TaskRealtimeEvent): void {
  if (event.eventType === 'CONNECTED' || !event.documentId) {
    return
  }
  documentRealtimeEvents[event.documentId] = event
  if (event.terminal) {
    scheduleRealtimeDocumentRefresh()
  }
}

function connectKnowledgeBaseRealtimeStream(): void {
  closeKnowledgeBaseRealtimeStream()
  knowledgeBaseRealtimeStream = subscribeKnowledgeBaseDocumentEvents(knowledgeBaseId.value, {
    onEvent: handleKnowledgeBaseRealtimeEvent,
    onError(error) {
      console.error('知识库文档实时订阅失败', error)
    },
  })
}

function canAutoRefreshDocuments(): boolean {
  return (
    hasActiveParseDocuments.value &&
    !hasSelectedDocuments.value &&
    !uploadDialogVisible.value &&
    !uploadSubmitting.value &&
    !batchRetrySubmitting.value &&
    !batchDeleteSubmitting.value
  )
}

function scheduleDocumentAutoRefresh(): void {
  clearDocumentAutoRefreshTimer()
  if (!canAutoRefreshDocuments()) {
    return
  }

  // 有文档仍在排队或处理中时，自动刷新列表，避免页面停留在旧状态。
  documentAutoRefreshTimer = window.setTimeout(async () => {
    documentAutoRefreshTimer = null
    if (!canAutoRefreshDocuments()) {
      scheduleDocumentAutoRefresh()
      return
    }
    await loadDocuments()
  }, 5000)
}

function isParsing(documentId: number): boolean {
  return parsingDocumentIds.value.includes(documentId)
}

function isDeletingDocument(documentId: number): boolean {
  return deletingDocumentIds.value.includes(documentId)
}

function formatTime(value: string): string {
  if (!value) {
    return '暂无时间'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString('zh-CN', {
    hour12: false,
  })
}

function formatFileSize(size: number): string {
  if (size >= 1024 * 1024) {
    return `${(size / 1024 / 1024).toFixed(2)} MB`
  }
  if (size >= 1024) {
    return `${(size / 1024).toFixed(2)} KB`
  }
  return `${size} B`
}

function buildFileKey(file: File): string {
  return `${file.name}_${file.size}_${file.lastModified}`
}

function resetUploadState(): void {
  uploadFileList.value = []
  uploadSubmitting.value = false
  uploadError.value = ''
  uploadResults.value = []
  autoParseAfterUpload.value = true
  uploadProgress.total = 0
  uploadProgress.completed = 0
  uploadProgress.currentFileName = ''
}

function handleSelectFile(_: UploadFile, fileList: UploadFiles): void {
  uploadFileList.value = [...fileList]
  uploadError.value = ''
}

function handleRemoveFile(_: UploadFile, fileList: UploadFiles): void {
  uploadFileList.value = [...fileList]
}

async function uploadToObjectStorage(upload: UploadUrlResponse, file: File): Promise<void> {
  const response = await fetch(upload.uploadUrl, {
    method: 'PUT',
    headers: {
      'Content-Type': file.type || 'application/octet-stream',
    },
    body: file,
  })

  if (!response.ok) {
    throw new Error(`文件上传失败，状态码 ${response.status}`)
  }
}

async function uploadSingleFile(file: File): Promise<UploadResultItem> {
  const key = buildFileKey(file)
  const docType = inferDocumentType(file.name)

  if (docType === 'UNKNOWN') {
    return {
      key,
      name: file.name,
      status: 'error',
      message: '文件类型暂不支持，请上传 Markdown、TXT、PDF、DOCX、PPTX、XLSX 或图片文件',
    }
  }
  if (isOcrImageDocumentType(docType) && !uploadCapability.value?.ocrAvailable) {
    return {
      key,
      name: file.name,
      status: 'error',
      message: uploadCapability.value?.ocrMessage || '当前图片解析依赖 OCR，请先完成 OCR 环境配置',
    }
  }

  let stepLabel = '获取上传凭证'
  try {
    const upload = await getKnowledgeBaseDocumentUploadUrl({
      fileName: file.name,
      contentType: file.type || 'application/octet-stream',
      bizType: 'KB_DOCUMENT',
    })

    stepLabel = '上传文件'
    await uploadToObjectStorage(upload, file)

    stepLabel = '登记文档'
    const payload: CreateKnowledgeBaseDocumentRequest = {
      docName: file.name,
      docType,
      storageBucket: upload.bucket,
      storageObjectKey: upload.objectKey,
      fileSize: file.size,
      contentHash: null,
    }
    const documentId = await createKnowledgeBaseDocument(knowledgeBaseId.value, payload)

    if (autoParseAfterUpload.value && documentId) {
      try {
        await triggerDocumentParse(documentId)
        return {
          key,
          name: file.name,
          status: 'success',
          message: '上传成功，已进入解析队列',
        }
      } catch (error) {
        return {
          key,
          name: file.name,
          status: 'warning',
          message: `上传成功，但自动解析提交失败：${resolveErrorMessage(error)}`,
        }
      }
    }

    return {
      key,
      name: file.name,
      status: 'success',
      message: '上传成功，已接入当前知识库',
    }
  } catch (error) {
    return {
      key,
      name: file.name,
      status: 'error',
      message: `${stepLabel}失败：${resolveErrorMessage(error)}`,
    }
  }
}

async function handleUpload(): Promise<void> {
  if (!hasSelectedFiles.value) {
    uploadError.value = '请先选择待上传文件'
    return
  }

  uploadSubmitting.value = true
  uploadError.value = ''
  uploadResults.value = []
  uploadProgress.total = selectedFiles.value.length
  uploadProgress.completed = 0

  const files = [...selectedFiles.value]
  const successKeys = new Set<string>()
  const results: UploadResultItem[] = []

  try {
    for (const file of files) {
      uploadProgress.currentFileName = file.name
      const result = await uploadSingleFile(file)
      results.push(result)
      uploadProgress.completed += 1
      if (result.status !== 'error') {
        successKeys.add(result.key)
      }
    }

    uploadResults.value = results
    uploadFileList.value = uploadFileList.value.filter(
      (item) => !(item.raw instanceof File) || !successKeys.has(buildFileKey(item.raw)),
    )

    const successCount = results.filter((item) => item.status !== 'error').length
    const warningCount = results.filter((item) => item.status === 'warning').length
    const failedCount = results.length - successCount

    if (successCount > 0) {
      documentPagination.pageNo = 1
      await loadDocuments()
    }

    if (failedCount === 0) {
      const summary =
        warningCount > 0
          ? `已完成 ${results.length} 个文件上传，其中 ${warningCount} 个需要手动补提解析`
          : `已完成 ${results.length} 个文件上传`
      ElMessage.success(summary)
      uploadDialogVisible.value = false
      resetUploadState()
      return
    }

    uploadError.value = '部分文件上传失败，已保留失败文件，可修正后直接重试'
    ElMessage.warning(`本次上传完成，成功 ${successCount} 个，失败 ${failedCount} 个`)
  } finally {
    uploadSubmitting.value = false
    uploadProgress.currentFileName = ''
  }
}

async function loadUploadCapability(): Promise<void> {
  uploadCapabilityLoading.value = true
  uploadCapabilityError.value = ''
  try {
    uploadCapability.value = await getKnowledgeBaseDocumentUploadCapability()
  } catch (error) {
    uploadCapability.value = null
    uploadCapabilityError.value = resolveErrorMessage(error)
  } finally {
    uploadCapabilityLoading.value = false
  }
}

async function handleTriggerParse(document: KnowledgeBaseDocument): Promise<void> {
  if (!canTriggerParse(document.parseStatus) || isParsing(document.documentId)) {
    return
  }

  parsingDocumentIds.value = [...parsingDocumentIds.value, document.documentId]

  try {
    await triggerDocumentParse(document.documentId)
    ElMessage.success(`已提交《${document.docName}》的解析任务`)
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error))
  } finally {
    parsingDocumentIds.value = parsingDocumentIds.value.filter((id) => id !== document.documentId)
    await loadDocuments()
  }
}

function handleSelectionChange(rows: KnowledgeBaseDocument[]): void {
  selectedDocuments.value = rows
  scheduleDocumentAutoRefresh()
}

async function handleBatchRetryParse(): Promise<void> {
  if (!hasSelectedDocuments.value || batchRetrySubmitting.value) {
    return
  }
  if (selectedRetryableDocumentCount.value === 0) {
    ElMessage.warning('当前所选文档都不支持解析，请选择失败或待处理文档')
    return
  }

  const skippedCount = selectedNonRetryableDocumentCount.value
  try {
    await ElMessageBox.confirm(
      skippedCount > 0
        ? `将批量解析 ${selectedRetryableDocumentCount.value} 个失败或待处理文档，另外 ${skippedCount} 个成功或处理中文档会自动跳过，是否继续？`
        : `确定批量解析 ${selectedRetryableDocumentCount.value} 个文档吗？`,
      '确认批量解析',
      {
        confirmButtonText: '确认解析',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  batchRetrySubmitting.value = true
  let successCount = 0
  let failedCount = 0

  try {
    for (const document of selectedRetryableDocuments.value) {
      if (isParsing(document.documentId)) {
        continue
      }
      parsingDocumentIds.value = [...parsingDocumentIds.value, document.documentId]
      try {
        await triggerDocumentParse(document.documentId)
        successCount += 1
      } catch (error) {
        failedCount += 1
      } finally {
        parsingDocumentIds.value = parsingDocumentIds.value.filter((id) => id !== document.documentId)
      }
    }

    await loadDocuments()
    clearDocumentSelection()

    if (failedCount === 0) {
      ElMessage.success(
        skippedCount > 0
          ? `已提交 ${successCount} 个文档的解析任务，跳过 ${skippedCount} 个`
          : `已提交 ${successCount} 个文档的解析任务`,
      )
      return
    }

    ElMessage.warning(
      `批量解析完成，成功 ${successCount} 个，失败 ${failedCount} 个${skippedCount > 0 ? `，跳过 ${skippedCount} 个` : ''}`,
    )
  } finally {
    batchRetrySubmitting.value = false
  }
}

async function handleDeleteDocument(document: KnowledgeBaseDocument): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定删除文档《${document.docName}》吗？删除后将同时清理解析任务、切片和引用关系。`,
      '确认删除文档',
      {
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  deletingDocumentIds.value = [...deletingDocumentIds.value, document.documentId]

  try {
    await deleteKnowledgeBaseDocument(document.documentId)
    if (documents.value.length === 1 && documentPagination.pageNo > 1) {
      documentPagination.pageNo -= 1
    }
    await loadDocuments()
    ElMessage.success(`文档《${document.docName}》已删除`)
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error))
  } finally {
    deletingDocumentIds.value = deletingDocumentIds.value.filter((id) => id !== document.documentId)
  }
}

async function handleBatchDeleteDocuments(): Promise<void> {
  if (!hasSelectedDocuments.value || batchDeleteSubmitting.value) {
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定批量删除 ${selectedDocumentCount.value} 个文档吗？删除后将同时清理解析任务、切片和引用关系。`,
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
  let successCount = 0
  let failedCount = 0
  const targetCount = selectedDocuments.value.length

  try {
    for (const document of [...selectedDocuments.value]) {
      if (isDeletingDocument(document.documentId)) {
        continue
      }
      deletingDocumentIds.value = [...deletingDocumentIds.value, document.documentId]
      try {
        await deleteKnowledgeBaseDocument(document.documentId)
        successCount += 1
      } catch (error) {
        failedCount += 1
      } finally {
        deletingDocumentIds.value = deletingDocumentIds.value.filter((id) => id !== document.documentId)
      }
    }

    if (successCount > 0 && successCount === documents.value.length && documentPagination.pageNo > 1) {
      documentPagination.pageNo -= 1
    }
    await loadDocuments()
    clearDocumentSelection()

    if (failedCount === 0) {
      ElMessage.success(`已删除 ${targetCount} 个文档`)
      return
    }

    ElMessage.warning(`批量删除完成，成功 ${successCount} 个，失败 ${failedCount} 个`)
  } finally {
    batchDeleteSubmitting.value = false
  }
}

async function loadDetail(): Promise<void> {
  detailLoading.value = true
  detailError.value = ''
  try {
    knowledgeBase.value = await getKnowledgeBaseDetail(knowledgeBaseId.value)
  } catch (error) {
    knowledgeBase.value = null
    detailError.value = resolveErrorMessage(error)
  } finally {
    detailLoading.value = false
  }
}

async function loadDocuments(): Promise<void> {
  documentLoading.value = true
  documentError.value = ''
  clearDocumentAutoRefreshTimer()
  clearDocumentRealtimeRefreshTimer()
  try {
    const response = await listKnowledgeBaseDocuments(knowledgeBaseId.value, {
      keyword: documentFilters.keyword.trim() || undefined,
      parseStatus: documentFilters.parseStatus || undefined,
      enabled:
        documentFilters.enabled === ''
          ? undefined
          : documentFilters.enabled === 'true',
      pageNo: documentPagination.pageNo,
      pageSize: documentPagination.pageSize,
    })
    documents.value = response.list
    documentPagination.total = response.total
    const activeDocumentIds = new Set(
      response.list
        .filter((item) => item.parseStatus === 'PENDING' || item.parseStatus === 'PROCESSING')
        .map((item) => item.documentId),
    )
    Object.keys(documentRealtimeEvents).forEach((key) => {
      const documentId = Number(key)
      if (!activeDocumentIds.has(documentId)) {
        delete documentRealtimeEvents[documentId]
      }
    })
  } catch (error) {
    documents.value = []
    documentPagination.total = 0
    documentError.value = resolveErrorMessage(error)
  } finally {
    documentLoading.value = false
    clearDocumentSelection()
    scheduleDocumentAutoRefresh()
  }
}

async function initialize(): Promise<void> {
  await Promise.all([loadDetail(), loadUploadCapability()])
  if (!detailError.value) {
    await loadDocuments()
  }
}

async function handleRetryDetail(): Promise<void> {
  await initialize()
}

async function handleRetryDocuments(): Promise<void> {
  await loadDocuments()
}

async function handleSearchDocuments(): Promise<void> {
  documentPagination.pageNo = 1
  await loadDocuments()
}

async function handleResetDocuments(): Promise<void> {
  documentFilters.keyword = ''
  documentFilters.parseStatus = ''
  documentFilters.enabled = ''
  documentPagination.pageNo = 1
  await loadDocuments()
}

function handleOpenUploadDialog(): void {
  resetUploadState()
  uploadDialogVisible.value = true
  scheduleDocumentAutoRefresh()
}

function handleCloseUploadDialog(): void {
  uploadDialogVisible.value = false
  resetUploadState()
  scheduleDocumentAutoRefresh()
}

async function handleBack(): Promise<void> {
  await router.push('/knowledge-bases')
}

async function handleEdit(): Promise<void> {
  await router.push(`/knowledge-bases/${knowledgeBaseId.value}/edit`)
}

async function handleDocumentDetail(documentId: number): Promise<void> {
  await router.push(`/documents/${documentId}`)
}

async function consumeEntryFlags(): Promise<void> {
  const created = route.query.created === '1'
  const openUpload = route.query.openUpload === '1'
  if (!created && !openUpload) {
    return
  }

  if (created) {
    ElMessage.success('知识库创建成功，请继续上传文档')
  }
  if (openUpload) {
    handleOpenUploadDialog()
  }

  const query = { ...route.query }
  delete query.created
  delete query.openUpload
  await router.replace({
    path: route.path,
    query,
  })
}

async function handleCurrentChange(pageNo: number): Promise<void> {
  documentPagination.pageNo = pageNo
  await loadDocuments()
}

async function handleSizeChange(pageSize: number): Promise<void> {
  documentPagination.pageSize = pageSize
  documentPagination.pageNo = 1
  await loadDocuments()
}

onMounted(async () => {
  await initialize()
  await consumeEntryFlags()
  if (!detailError.value) {
    connectKnowledgeBaseRealtimeStream()
  }
})

onUnmounted(() => {
  clearDocumentAutoRefreshTimer()
  clearDocumentRealtimeRefreshTimer()
  closeKnowledgeBaseRealtimeStream()
})
</script>

<template>
  <section class="detail-page">
    <el-skeleton v-if="detailLoading" :rows="12" animated class="detail-loading soft-panel" />

    <section v-else-if="detailError" class="detail-error soft-panel">
      <el-empty description="知识库详情加载失败">
        <template #description>
          <p class="error-text">{{ detailError }}</p>
        </template>
        <div class="error-actions">
          <el-button @click="handleRetryDetail">重新加载</el-button>
          <el-button type="primary" @click="handleBack">返回列表</el-button>
        </div>
      </el-empty>
    </section>

    <template v-else-if="knowledgeBase">
      <header class="detail-head soft-panel">
        <h1 class="page-title">{{ knowledgeBase.kbName }}</h1>
        <div class="head-actions">
          <el-button type="primary" plain @click="handleOpenUploadDialog">上传文档</el-button>
          <el-button @click="loadDetail">刷新详情</el-button>
          <el-button @click="handleBack">返回列表</el-button>
          <el-button type="primary" @click="handleEdit">编辑知识库</el-button>
        </div>
      </header>

      <section class="overview-grid">
        <article class="overview-card soft-panel">
          <div class="overview-card-head">
            <span>知识库编码</span>
            <el-tag size="small" effect="plain" type="info">核心标识</el-tag>
          </div>
          <strong class="overview-card-value">{{ knowledgeBase.kbCode }}</strong>
        </article>
        <article class="overview-card soft-panel">
          <div class="overview-card-head">
            <span>回答模型策略</span>
            <el-tag size="small" effect="plain" type="warning">问答生成</el-tag>
          </div>
          <strong class="overview-card-value">平台默认聊天模型</strong>
        </article>
        <article class="overview-card soft-panel">
          <div class="overview-card-head">
            <span>向量模型</span>
            <el-tag size="small" effect="plain" type="success">检索构建</el-tag>
          </div>
          <strong class="overview-card-value">{{ embeddingModelLabel(knowledgeBase.embeddingModelName) }}</strong>
        </article>
      </section>

      <section class="detail-panel soft-panel">
        <div class="section-head">
          <h2>知识库配置</h2>
        </div>

        <div class="detail-matrix">
          <article class="detail-item">
            <span>知识库编号</span>
            <strong class="detail-item-value">{{ knowledgeBase.id }}</strong>
          </article>
          <article class="detail-item">
            <span>状态</span>
            <div class="detail-item-content">
              <el-tag :type="statusType(knowledgeBase.status)">{{ formatResourceStatus(knowledgeBase.status) }}</el-tag>
            </div>
          </article>
          <article class="detail-item">
            <span>检索数量</span>
            <strong class="detail-item-value">{{ knowledgeBase.retrieveTopK }}</strong>
          </article>
          <article class="detail-item">
            <span>重排开关</span>
            <div class="detail-item-content">
              <el-tag :type="knowledgeBase.rerankEnabled ? 'warning' : 'info'">
                {{ knowledgeBase.rerankEnabled ? '开启' : '关闭' }}
              </el-tag>
            </div>
          </article>
        </div>
      </section>

      <section class="document-panel soft-panel">
        <div class="section-head">
          <h2>知识库文档</h2>
        </div>

        <el-alert
          :type="uploadOcrAlertType"
          :closable="false"
          show-icon
          class="capability-alert"
          :title="`支持格式：${supportedDocTypesText}`"
        >
          <template #default>
            <p class="capability-text">
              OCR 状态：
              {{
                uploadCapabilityLoading
                  ? '正在检查 OCR 环境...'
                  : uploadCapability?.ocrMessage || uploadCapabilityError || '未获取到 OCR 状态'
              }}
            </p>
            <p class="capability-text">
              图片文件依赖 OCR；扫描版 PDF 在普通文本抽取为空时会自动尝试 OCR。
            </p>
          </template>
        </el-alert>

        <section class="filter-panel">
          <div class="filter-grid">
            <el-input
              v-model="documentFilters.keyword"
              placeholder="请输入文档名称或关键词"
              clearable
            />
            <el-select v-model="documentFilters.parseStatus" placeholder="解析状态">
              <el-option label="全部状态" value="" />
              <el-option
                v-for="item in DOCUMENT_PARSE_STATUS_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
            <el-select v-model="documentFilters.enabled" placeholder="启停状态">
              <el-option label="全部状态" value="" />
              <el-option label="启用" value="true" />
              <el-option label="停用" value="false" />
            </el-select>
          </div>
          <el-alert
            v-if="hasActiveParseDocuments"
            type="info"
            :closable="false"
            show-icon
            class="parse-progress-alert"
            title="存在待处理或处理中任务，页面会实时同步解析进度。"
          >
            <template #default>
              <p class="capability-text">待处理表示已入队等待执行，处理中表示后台正在解析与向量化。</p>
            </template>
          </el-alert>
          <div class="filter-toolbar">
            <div class="filter-toolbar-left">
              <span v-if="hasSelectedDocuments" class="selection-summary">
                已选 {{ selectedDocumentCount }} 个文档
                <template v-if="selectedNonRetryableDocumentCount > 0">
                  ，本次可解析 {{ selectedRetryableDocumentCount }} 个，自动跳过 {{ selectedNonRetryableDocumentCount }} 个成功或处理中
                </template>
              </span>
              <div class="filter-actions">
                <el-button
                  type="primary"
                  plain
                  :disabled="!hasSelectedDocuments || selectedRetryableDocumentCount === 0"
                  :loading="batchRetrySubmitting"
                  @click="handleBatchRetryParse"
                >
                  批量解析
                </el-button>
                <el-button
                  type="danger"
                  plain
                  :disabled="!hasSelectedDocuments"
                  :loading="batchDeleteSubmitting"
                  @click="handleBatchDeleteDocuments"
                >
                  批量删除
                </el-button>
                <el-button @click="handleResetDocuments">重置</el-button>
                <el-button type="primary" @click="handleSearchDocuments">查询</el-button>
                <el-button :loading="documentLoading" @click="handleRetryDocuments">刷新</el-button>
              </div>
            </div>
            <div class="filter-toolbar-right">
              <el-button type="primary" @click="handleOpenUploadDialog">上传文档</el-button>
            </div>
          </div>
        </section>

        <section v-if="documentError" class="document-error">
          <el-empty description="文档列表加载失败">
            <template #description>
              <p class="error-text">{{ documentError }}</p>
            </template>
            <el-button type="primary" @click="handleRetryDocuments">重新加载</el-button>
          </el-empty>
        </section>

        <template v-else>
          <el-table
            ref="documentTableRef"
            :data="documents"
            v-loading="documentLoading"
            empty-text="当前知识库暂无文档"
            stripe
            @selection-change="handleSelectionChange"
          >
            <template #empty>
              <el-empty description="当前知识库还没有文档">
                <template #description>
                  <p class="error-text">先上传 PDF、Word、Markdown 或图片文件，再开始解析和向量化。</p>
                </template>
                <el-button type="primary" @click="handleOpenUploadDialog">上传第一份文档</el-button>
              </el-empty>
            </template>
            <el-table-column type="selection" width="52" />
            <el-table-column prop="documentId" label="文档编号" width="100" />
            <el-table-column prop="docName" label="文档名称" min-width="220" />
            <el-table-column prop="docType" label="类型" width="100" />
            <el-table-column label="解析状态" width="180">
              <template #default="{ row }">
                <div class="parse-progress-cell">
                  <el-tag :type="parseStatusType(row.parseStatus)">{{ formatDocumentParseStatus(row.parseStatus) }}</el-tag>
                  <el-progress
                    v-if="progressStateOfDocument(row).active"
                    :percentage="progressStateOfDocument(row).percent"
                    :status="progressStateOfDocument(row).status"
                    :stroke-width="6"
                    :show-text="false"
                  />
                  <small v-if="progressStateOfDocument(row).active" class="parse-progress-text">
                    {{ progressStateOfDocument(row).stageLabel }} · {{ progressStateOfDocument(row).percent }}%
                  </small>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="启停" width="100">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'">{{ enabledLabel(row.enabled) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" min-width="180">
              <template #default="{ row }">
                {{ formatTime(row.createdAt) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="260" fixed="right">
              <template #default="{ row }">
                <el-button link @click="handleDocumentDetail(row.documentId)">详情</el-button>
                <el-button
                  link
                  type="primary"
                  :disabled="!canTriggerParse(row.parseStatus)"
                  :loading="isParsing(row.documentId)"
                  @click="handleTriggerParse(row)"
                >
                  开始解析
                </el-button>
                <el-button
                  link
                  type="danger"
                  :loading="isDeletingDocument(row.documentId)"
                  @click="handleDeleteDocument(row)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="table-footer" v-if="hasDocuments || documentPagination.total > 0">
            <el-pagination
              background
              layout="total, sizes, prev, pager, next"
              :current-page="documentPagination.pageNo"
              :page-size="documentPagination.pageSize"
              :page-sizes="[10, 20, 50]"
              :total="documentPagination.total"
              @current-change="handleCurrentChange"
              @size-change="handleSizeChange"
            />
          </div>
        </template>
      </section>
    </template>

    <el-dialog
      v-model="uploadDialogVisible"
      title="上传文档"
      width="560px"
      destroy-on-close
      @close="handleCloseUploadDialog"
    >
      <section class="upload-dialog">
        <p class="upload-hint">
          目标知识库：
            <strong>{{ knowledgeBase?.kbName }}</strong>
        </p>

        <el-upload
          class="upload-box"
          drag
          v-model:file-list="uploadFileList"
          multiple
          :accept="DOCUMENT_UPLOAD_ACCEPT"
          :auto-upload="false"
          :show-file-list="true"
          :on-change="handleSelectFile"
          :on-remove="handleRemoveFile"
        >
          <el-icon class="el-icon--upload"><i class="el-icon-upload" /></el-icon>
          <div class="el-upload__text">将文件拖到此处，或 <em>点击选择</em></div>
          <template #tip>
            <div class="el-upload__tip">
              <div class="upload-tip-line">支持格式：{{ supportedDocTypesText }}</div>
              <div class="upload-tip-line">
                当前支持批量上传，系统会逐个获取凭证、上传并登记文档。图片与扫描版 PDF 依赖 OCR。
              </div>
            </div>
          </template>
        </el-upload>

        <el-alert
          v-if="hasPdfSelection && uploadCapability?.ocrEnabled && !uploadCapability?.ocrAvailable"
          type="warning"
          :closable="false"
          show-icon
          title="当前 OCR 未就绪，扫描版 PDF 可能无法抽取到文本。"
        />
        <el-alert
          v-else-if="hasOcrImageSelection && uploadCapability?.ocrEnabled && !uploadCapability?.ocrAvailable"
          type="error"
          :closable="false"
          show-icon
          :title="uploadCapability?.ocrMessage || '当前图片解析依赖 OCR，请先完成 OCR 环境配置。'"
        />

        <div class="upload-progress soft-panel" v-if="uploadSubmitting || uploadProgress.completed > 0">
          <article class="upload-progress-item">
            <span>上传进度</span>
            <strong>{{ uploadProgress.completed }} / {{ uploadProgress.total }}</strong>
          </article>
          <article class="upload-progress-item">
            <span>当前文件</span>
            <strong>{{ uploadProgress.currentFileName || '等待中' }}</strong>
          </article>
        </div>

        <div class="upload-meta soft-panel" v-if="selectedFileSummaries.length">
          <article
            v-for="file in selectedFileSummaries"
            :key="file.key"
            class="upload-meta-item"
          >
            <span>文件名</span>
            <strong>{{ file.name }}</strong>
            <small>大小：{{ file.sizeLabel }}</small>
            <small>类型：{{ file.docType }}</small>
          </article>
        </div>

        <label class="upload-option">
          <el-checkbox v-model="autoParseAfterUpload">上传后立即解析</el-checkbox>
          <span>推荐开启，上传完成后系统会自动提交解析任务。</span>
        </label>

        <div class="upload-result-list soft-panel" v-if="uploadResults.length">
          <article
            v-for="item in uploadResults"
            :key="`${item.key}-${item.status}`"
            class="upload-result-item"
          >
            <div class="upload-result-head">
              <strong>{{ item.name }}</strong>
              <el-tag :type="item.status === 'success' ? 'success' : item.status === 'warning' ? 'warning' : 'danger'">
                {{
                  item.status === 'success'
                    ? '成功'
                    : item.status === 'warning'
                      ? '部分成功'
                      : '失败'
                }}
              </el-tag>
            </div>
            <p class="upload-result-message">{{ item.message }}</p>
          </article>
        </div>

        <el-alert
          v-if="uploadError"
          type="error"
          :closable="false"
          show-icon
          :title="uploadError"
        />
      </section>

      <template #footer>
        <div class="dialog-actions">
          <el-button :disabled="uploadSubmitting" @click="handleCloseUploadDialog">取消</el-button>
          <el-button type="primary" :loading="uploadSubmitting" @click="handleUpload">
            开始批量上传
          </el-button>
        </div>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.detail-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-loading,
.detail-error,
.detail-panel,
.document-panel {
  padding: 14px 18px;
}

.detail-head {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: center;
  padding: 10px 18px;
}

.head-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 10px;
  padding: 10px 12px;
  border-radius: var(--ember-radius-lg);
  background: var(--ember-surface);
  box-shadow: inset 0 0 0 1px var(--ember-border-light);
}

.capability-alert {
  margin-bottom: 18px;
}

.capability-text {
  margin: 0;
  line-height: 1.7;
}

.capability-text + .capability-text {
  margin-top: 6px;
}

.parse-progress-alert {
  margin-top: 14px;
}

.filter-panel {
  margin-bottom: 18px;
  padding: 18px;
  border-radius: var(--ember-radius-lg);
  background: var(--ember-surface);
}

.filter-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.filter-toolbar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-top: 16px;
}

.filter-toolbar-left {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
}

.filter-toolbar-right {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}

.filter-actions {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  flex-wrap: wrap;
  gap: 12px;
}

.selection-summary {
  color: var(--ember-text-secondary);
  font-size: 13px;
}

.parse-progress-cell {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.parse-progress-text {
  color: var(--ember-text-secondary);
  line-height: 1.4;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.overview-card {
  padding: 12px 14px;
  min-height: 0;
}

.overview-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.overview-card span,
.detail-item span {
  color: var(--ember-neutral);
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.overview-card-value,
.detail-item-value {
  display: block;
  margin-top: 6px;
  font-family: var(--ember-font-heading);
  font-size: 18px;
  line-height: 1.25;
  color: var(--ember-text-primary);
  word-break: break-word;
}

.section-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  margin-bottom: 10px;
}

.section-head h2 {
  margin: 0;
  font-family: var(--ember-font-heading);
  font-size: 18px;
}

.section-head p {
  margin: 6px 0 0;
  color: var(--ember-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.detail-matrix {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.detail-item {
  padding: 14px 16px;
  border-radius: var(--ember-radius-lg);
  background: var(--ember-surface);
  min-height: 0;
}

.detail-item-content {
  display: flex;
  align-items: center;
  min-height: 34px;
  margin-top: 10px;
}

.document-error {
  padding: 8px 0;
}

.table-footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 18px;
}

.error-text {
  margin: 0;
  color: var(--ember-text-secondary);
}

.error-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
}

.upload-dialog {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.upload-hint {
  margin: 0;
  color: var(--ember-text-secondary);
}

.upload-box {
  width: 100%;
}

.upload-tip-line + .upload-tip-line {
  margin-top: 4px;
}

.upload-meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  padding: 18px;
  max-height: 280px;
  overflow-y: auto;
}

.upload-meta-item {
  padding: 14px;
  border-radius: var(--ember-radius-lg);
  background: var(--ember-surface);
}

.upload-meta-item span {
  display: block;
  color: var(--ember-neutral);
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.upload-meta-item strong {
  display: block;
  margin-top: 10px;
  color: var(--ember-text-primary);
  word-break: break-word;
}

.upload-meta-item small {
  display: block;
  margin-top: 8px;
  color: var(--ember-text-secondary);
  line-height: 1.5;
}

.upload-progress {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  padding: 18px;
}

.upload-progress-item {
  padding: 14px;
  border-radius: var(--ember-radius-lg);
  background: var(--ember-surface);
}

.upload-progress-item span {
  display: block;
  color: var(--ember-neutral);
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.upload-progress-item strong {
  display: block;
  margin-top: 10px;
  color: var(--ember-text-primary);
  word-break: break-word;
}

.upload-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border-radius: var(--ember-radius-lg);
  background: var(--ember-surface);
  color: var(--ember-text-secondary);
}

.upload-result-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 18px;
  max-height: 240px;
  overflow-y: auto;
}

.upload-result-item {
  padding: 14px 16px;
  border-radius: var(--ember-radius-lg);
  background: var(--ember-surface);
}

.upload-result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.upload-result-head strong {
  color: var(--ember-text-primary);
  word-break: break-word;
}

.upload-result-message {
  margin: 10px 0 0;
  color: var(--ember-text-secondary);
  line-height: 1.6;
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

@media (max-width: 1200px) {
  .overview-grid,
  .detail-matrix {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 960px) {
  .detail-loading,
  .detail-error,
  .detail-panel,
  .document-panel {
    padding: 18px;
  }

  .detail-head,
  .section-head,
  .filter-toolbar {
    flex-direction: column;
  }

  .head-actions {
    width: 100%;
    justify-content: flex-start;
  }

  .filter-grid,
  .upload-meta,
  .upload-progress {
    grid-template-columns: 1fr;
  }

  .filter-toolbar-right {
    justify-content: flex-start;
    flex-wrap: wrap;
  }
}

@media (max-width: 720px) {
  .overview-grid,
  .detail-matrix {
    grid-template-columns: 1fr;
  }

  .detail-loading,
  .detail-error,
  .detail-panel,
  .document-panel {
    padding: 20px;
  }

  .head-actions,
  .filter-toolbar-right,
  .filter-actions,
  .dialog-actions,
  .error-actions,
  .upload-option,
  .upload-result-head {
    flex-direction: column;
  }

  .filter-toolbar-left,
  .filter-toolbar-right,
  .filter-actions {
    width: 100%;
    align-items: stretch;
  }

  .upload-option {
    align-items: flex-start;
  }

  .selection-summary {
    width: 100%;
  }
}
</style>
