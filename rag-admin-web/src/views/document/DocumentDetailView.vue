<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElAlert, ElButton, ElEmpty, ElMessage, ElMessageBox, ElSkeleton } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { subscribeDocumentEvents, type RealtimeStreamHandle } from '@/api/realtime'
import {
  activateDocumentVersion,
  createDocumentVersion,
  getDocumentDetail,
  getKnowledgeBaseDocumentUploadUrl,
  listDocumentChunks,
  listDocumentVersions,
} from '@/api/knowledge-base'
import { resolveErrorMessage } from '@/api/http'
import type { TaskRealtimeEvent } from '@/types/realtime'
import { buildParseProgressState } from '@/utils/task-progress'
import { formatDocumentParseStatus, formatResourceStatus } from '@/utils/task'
import type {
  CreateDocumentVersionRequest,
  DocumentChunk,
  DocumentDetail,
  DocumentVersion,
  UploadUrlResponse,
} from '@/types/knowledge-base'

const route = useRoute()
const router = useRouter()

const detailLoading = ref(true)
const detailError = ref('')
const detail = ref<DocumentDetail | null>(null)

const versionLoading = ref(false)
const versionError = ref('')
const versions = ref<DocumentVersion[]>([])
const activatingVersionIds = ref<number[]>([])
const versionDialogVisible = ref(false)
const versionFile = ref<File | null>(null)
const versionSubmitting = ref(false)
const versionUploadStage = ref<'idle' | 'getting-url' | 'uploading' | 'registering'>('idle')
const versionUploadError = ref('')
const versionPagination = reactive({
  pageNo: 1,
  pageSize: 10,
  total: 0,
})
const chunkLoading = ref(false)
const chunkError = ref('')
const chunks = ref<DocumentChunk[]>([])
const expandedChunkIds = ref<number[]>([])
const chunkTableWrapperRef = ref<HTMLElement | null>(null)
const chunkPagination = reactive({
  pageNo: 1,
  pageSize: 10,
  total: 0,
})
const realtimeEvent = ref<TaskRealtimeEvent | null>(null)
let documentRealtimeStream: RealtimeStreamHandle | null = null
let documentRealtimeRefreshTimer: ReturnType<typeof window.setTimeout> | null = null

const documentId = computed(() => Number(route.params.id))
const hasVersions = computed(() => versions.value.length > 0)
const hasChunks = computed(() => chunks.value.length > 0)
const parseProgressState = computed(() => buildParseProgressState(detail.value?.parseStatus, realtimeEvent.value))
const routeChunkId = computed(() => parsePositiveInteger(route.query.chunkId))
const routeChunkNo = computed(() => parsePositiveInteger(route.query.chunkNo))
const hasRouteChunkFocus = computed(() => route.query.source === 'chat' && (routeChunkId.value !== null || routeChunkNo.value !== null))

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

function formatTime(value: string | null | undefined): string {
  if (!value) {
    return '暂无时间'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString('zh-CN', { hour12: false })
}

function parsePositiveInteger(value: unknown): number | null {
  if (Array.isArray(value)) {
    return parsePositiveInteger(value[0])
  }
  if (typeof value !== 'string' || !value.trim()) {
    return null
  }
  const parsed = Number(value)
  if (!Number.isInteger(parsed) || parsed <= 0) {
    return null
  }
  return parsed
}

function clearDocumentRealtimeRefreshTimer(): void {
  if (documentRealtimeRefreshTimer !== null) {
    window.clearTimeout(documentRealtimeRefreshTimer)
    documentRealtimeRefreshTimer = null
  }
}

function closeDocumentRealtimeStream(): void {
  documentRealtimeStream?.close()
  documentRealtimeStream = null
}

function scheduleDocumentRealtimeRefresh(): void {
  clearDocumentRealtimeRefreshTimer()
  documentRealtimeRefreshTimer = window.setTimeout(async () => {
    documentRealtimeRefreshTimer = null
    await initialize()
  }, 300)
}

function handleDocumentRealtimeEvent(event: TaskRealtimeEvent): void {
  if (event.eventType === 'CONNECTED') {
    return
  }
  realtimeEvent.value = event
  if (event.terminal) {
    scheduleDocumentRealtimeRefresh()
  }
}

function connectDocumentRealtimeStream(): void {
  closeDocumentRealtimeStream()
  documentRealtimeStream = subscribeDocumentEvents(documentId.value, {
    onEvent: handleDocumentRealtimeEvent,
    onError(error) {
      console.error('文档实时订阅失败', error)
    },
  })
}

function resetVersionUploadState(): void {
  versionFile.value = null
  versionSubmitting.value = false
  versionUploadStage.value = 'idle'
  versionUploadError.value = ''
}

function canActivateVersion(version: DocumentVersion): boolean {
  return !version.active
}

function isActivatingVersion(versionId: number): boolean {
  return activatingVersionIds.value.includes(versionId)
}

function handleSelectVersionFile(uploadFile: { raw?: File }): void {
  versionFile.value = uploadFile.raw ?? null
  versionUploadError.value = ''
}

function handleRemoveVersionFile(): void {
  versionFile.value = null
}

function chunkPreview(chunk: DocumentChunk): string {
  const raw = chunk.contentSnippet || chunk.content || ''
  if (expandedChunkIds.value.includes(chunk.chunkId) || raw.length <= 180) {
    return raw
  }
  return `${raw.slice(0, 180)}...`
}

function canToggleChunk(chunk: DocumentChunk): boolean {
  const raw = chunk.contentSnippet || chunk.content || ''
  return raw.length > 180
}

function isChunkExpanded(chunkId: number): boolean {
  return expandedChunkIds.value.includes(chunkId)
}

function toggleChunk(chunkId: number): void {
  if (expandedChunkIds.value.includes(chunkId)) {
    expandedChunkIds.value = expandedChunkIds.value.filter((id) => id !== chunkId)
    return
  }
  expandedChunkIds.value = [...expandedChunkIds.value, chunkId]
}

function chunkRowClassName({ row }: { row: DocumentChunk }): string {
  if (routeChunkId.value !== null && row.chunkId === routeChunkId.value) {
    return `chunk-row chunk-row-${row.chunkId} is-route-highlight`
  }
  return `chunk-row chunk-row-${row.chunkId}`
}

async function scrollToChunkRow(chunkId: number): Promise<void> {
  await nextTick()
  const row = chunkTableWrapperRef.value?.querySelector(`.chunk-row-${chunkId}`) as HTMLElement | null
  row?.scrollIntoView({
    behavior: 'smooth',
    block: 'center',
  })
}

async function alignChunkViewToRoute(): Promise<void> {
  const targetChunkId = routeChunkId.value
  const targetChunkNo = routeChunkNo.value
  if (targetChunkId === null && targetChunkNo === null) {
    return
  }

  if (targetChunkNo !== null) {
    const targetPageNo = Math.max(1, Math.ceil(targetChunkNo / chunkPagination.pageSize))
    if (chunkPagination.pageNo !== targetPageNo) {
      chunkPagination.pageNo = targetPageNo
      await loadChunks()
    }
  }

  if (targetChunkId !== null && chunks.value.some((item) => item.chunkId === targetChunkId)) {
    if (!expandedChunkIds.value.includes(targetChunkId)) {
      expandedChunkIds.value = [...expandedChunkIds.value, targetChunkId]
    }
    await scrollToChunkRow(targetChunkId)
  }
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

async function loadDetail(): Promise<void> {
  detailLoading.value = true
  detailError.value = ''
  try {
    detail.value = await getDocumentDetail(documentId.value)
  } catch (error) {
    detail.value = null
    detailError.value = resolveErrorMessage(error)
  } finally {
    detailLoading.value = false
  }
}

async function loadVersions(): Promise<void> {
  versionLoading.value = true
  versionError.value = ''
  try {
    const response = await listDocumentVersions(documentId.value, {
      pageNo: versionPagination.pageNo,
      pageSize: versionPagination.pageSize,
    })
    versions.value = response.list
    versionPagination.total = response.total
  } catch (error) {
    versions.value = []
    versionPagination.total = 0
    versionError.value = resolveErrorMessage(error)
  } finally {
    versionLoading.value = false
  }
}

async function loadChunks(): Promise<void> {
  chunkLoading.value = true
  chunkError.value = ''
  try {
    const response = await listDocumentChunks(documentId.value, {
      pageNo: chunkPagination.pageNo,
      pageSize: chunkPagination.pageSize,
    })
    chunks.value = response.list
    chunkPagination.total = response.total
    expandedChunkIds.value = []
  } catch (error) {
    chunks.value = []
    chunkPagination.total = 0
    chunkError.value = resolveErrorMessage(error)
  } finally {
    chunkLoading.value = false
  }
}

async function initialize(): Promise<void> {
  await loadDetail()
  if (!detailError.value) {
    await loadVersions()
    await loadChunks()
    await alignChunkViewToRoute()
  }
}

async function handleBack(): Promise<void> {
  if (detail.value?.kbId) {
    await router.push(`/knowledge-bases/${detail.value.kbId}`)
    return
  }
  await router.push('/tasks')
}

async function handleRetryDetail(): Promise<void> {
  await initialize()
}

async function handleRetryVersions(): Promise<void> {
  await loadVersions()
}

function handleOpenVersionDialog(): void {
  resetVersionUploadState()
  versionDialogVisible.value = true
}

function handleCloseVersionDialog(): void {
  versionDialogVisible.value = false
  resetVersionUploadState()
}

async function handleActivateVersion(version: DocumentVersion): Promise<void> {
  if (!canActivateVersion(version) || isActivatingVersion(version.versionId)) {
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定将版本 #${version.versionId} 设为当前生效版本吗？`,
      '确认激活版本',
      {
        confirmButtonText: '确认切换',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  activatingVersionIds.value = [...activatingVersionIds.value, version.versionId]

  try {
    await activateDocumentVersion(documentId.value, version.versionId)
    ElMessage.success(`版本 #${version.versionId} 已设为当前生效版本`)
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error))
  } finally {
    activatingVersionIds.value = activatingVersionIds.value.filter((id) => id !== version.versionId)
    await loadDetail()
    await loadVersions()
  }
}

async function handleCreateVersion(): Promise<void> {
  if (!versionFile.value) {
    versionUploadError.value = '请先选择版本文件'
    return
  }

  versionSubmitting.value = true
  versionUploadError.value = ''

  try {
    versionUploadStage.value = 'getting-url'
    const file = versionFile.value
    const upload = await getKnowledgeBaseDocumentUploadUrl({
      fileName: file.name,
      contentType: file.type || 'application/octet-stream',
      bizType: 'KB_DOCUMENT',
    })

    versionUploadStage.value = 'uploading'
    await uploadToObjectStorage(upload, file)

    versionUploadStage.value = 'registering'
    const payload: CreateDocumentVersionRequest = {
      storageBucket: upload.bucket,
      storageObjectKey: upload.objectKey,
      contentHash: null,
      fileSize: file.size,
    }
    await createDocumentVersion(documentId.value, payload)

    ElMessage.success('文档新版本已创建')
    versionDialogVisible.value = false
    resetVersionUploadState()
    versionPagination.pageNo = 1
    await loadDetail()
    await loadVersions()
  } catch (error) {
    if (versionUploadStage.value === 'getting-url') {
      versionUploadError.value = `获取上传凭证失败：${resolveErrorMessage(error)}`
    } else if (versionUploadStage.value === 'uploading') {
      versionUploadError.value = `文件上传失败：${resolveErrorMessage(error)}`
    } else if (versionUploadStage.value === 'registering') {
      versionUploadError.value = `新版本登记失败：${resolveErrorMessage(error)}`
    } else {
      versionUploadError.value = resolveErrorMessage(error)
    }
  } finally {
    versionSubmitting.value = false
    if (versionDialogVisible.value) {
      versionUploadStage.value = 'idle'
    }
  }
}

async function handleRetryChunks(): Promise<void> {
  await loadChunks()
}

async function handleCurrentChange(pageNo: number): Promise<void> {
  versionPagination.pageNo = pageNo
  await loadVersions()
}

async function handleSizeChange(pageSize: number): Promise<void> {
  versionPagination.pageSize = pageSize
  versionPagination.pageNo = 1
  await loadVersions()
}

async function handleChunkCurrentChange(pageNo: number): Promise<void> {
  chunkPagination.pageNo = pageNo
  await loadChunks()
}

async function handleChunkSizeChange(pageSize: number): Promise<void> {
  chunkPagination.pageSize = pageSize
  chunkPagination.pageNo = 1
  await loadChunks()
}

onMounted(async () => {
  await initialize()
  if (!detailError.value) {
    connectDocumentRealtimeStream()
  }
})

watch(
  () => [documentId.value, routeChunkId.value, routeChunkNo.value],
  async ([nextDocumentId, nextChunkId, nextChunkNo], [prevDocumentId, prevChunkId, prevChunkNo]) => {
    if (nextDocumentId !== prevDocumentId) {
      realtimeEvent.value = null
      closeDocumentRealtimeStream()
      await initialize()
      if (!detailError.value) {
        connectDocumentRealtimeStream()
      }
      return
    }
    if (nextChunkId !== prevChunkId || nextChunkNo !== prevChunkNo) {
      await alignChunkViewToRoute()
    }
  },
)

onUnmounted(() => {
  clearDocumentRealtimeRefreshTimer()
  closeDocumentRealtimeStream()
})
</script>

<template>
  <section class="document-detail-page">
    <el-skeleton v-if="detailLoading" :rows="12" animated class="detail-loading soft-panel" />

    <section v-else-if="detailError" class="detail-error soft-panel">
      <el-empty description="文档详情加载失败">
        <template #description>
          <p class="error-text">{{ detailError }}</p>
        </template>
        <div class="error-actions">
          <el-button @click="handleRetryDetail">重新加载</el-button>
          <el-button type="primary" @click="handleBack">返回上级页面</el-button>
        </div>
      </el-empty>
    </section>

    <template v-else-if="detail">
      <header class="detail-head">
        <div>
          <p class="detail-eyebrow">文档详情</p>
          <h1 class="page-title">{{ detail.docName }}</h1>
          <p class="page-subtitle">
            查看文档基础信息、版本记录和切片内容，便于检查解析结果。
          </p>
        </div>
        <div class="head-actions">
          <el-button @click="loadDetail">刷新详情</el-button>
          <el-button type="primary" @click="handleBack">返回上级页面</el-button>
        </div>
      </header>

      <section class="overview-grid">
        <article class="overview-card soft-panel">
          <span>文档编号</span>
          <strong>{{ detail.documentId }}</strong>
          <p>当前文档的唯一业务标识。</p>
        </article>
        <article class="overview-card soft-panel">
          <span>解析状态</span>
          <strong>
            <el-tag :type="parseStatusType(detail.parseStatus)">{{ formatDocumentParseStatus(detail.parseStatus) }}</el-tag>
          </strong>
          <p>解析链路当前状态以后台任务执行结果为准。</p>
        </article>
        <article class="overview-card soft-panel">
          <span>所属知识库</span>
          <strong>{{ detail.kbName || detail.kbId || '暂无' }}</strong>
          <p>文档当前所属的知识库上下文信息。</p>
        </article>
      </section>

      <section class="progress-panel soft-panel">
        <div class="section-head">
          <div>
            <h2>解析进度</h2>
            <p>后台异步执行时，页面会实时接收阶段更新；完成后自动刷新详情、版本和切片。</p>
          </div>
        </div>

        <div class="progress-summary">
          <article class="progress-card">
            <span>当前阶段</span>
            <strong>{{ parseProgressState.stageLabel }}</strong>
            <p>{{ parseProgressState.message }}</p>
          </article>
          <article class="progress-card">
            <span>当前进度</span>
            <strong>{{ parseProgressState.percent }}%</strong>
            <el-progress
              :percentage="parseProgressState.percent"
              :status="parseProgressState.status"
              :stroke-width="10"
            />
          </article>
        </div>
      </section>

      <section class="detail-panel soft-panel">
        <div class="section-head">
          <div>
            <h2>文档信息</h2>
            <p>这里展示文档基础信息、存储位置和当前解析状态。</p>
          </div>
        </div>

        <div class="detail-matrix">
          <article class="detail-item">
            <span>文档类型</span>
            <strong>{{ detail.docType }}</strong>
          </article>
          <article class="detail-item">
            <span>启停状态</span>
            <strong>{{ detail.enabled ? formatResourceStatus('ENABLED') : formatResourceStatus('DISABLED') }}</strong>
          </article>
          <article class="detail-item">
            <span>创建时间</span>
            <strong>{{ formatTime(detail.createdAt) }}</strong>
          </article>
          <article class="detail-item">
            <span>更新时间</span>
            <strong>{{ formatTime(detail.updatedAt) }}</strong>
          </article>
          <article class="detail-item">
            <span>当前版本</span>
            <strong>{{ detail.currentVersion ?? '暂无' }}</strong>
          </article>
          <article class="detail-item">
            <span>文件大小</span>
            <strong>{{ detail.fileSize ? `${(detail.fileSize / 1024).toFixed(1)} KB` : '暂无' }}</strong>
          </article>
        </div>

        <div class="storage-panel">
          <article class="storage-item">
            <span>存储桶</span>
            <strong>{{ detail.storageBucket || '暂无' }}</strong>
          </article>
          <article class="storage-item">
            <span>对象键</span>
            <strong>{{ detail.storageObjectKey || '暂无' }}</strong>
          </article>
          <article class="storage-item">
            <span>内容哈希</span>
            <strong>{{ detail.contentHash || '暂无' }}</strong>
          </article>
        </div>
      </section>

      <section class="version-panel soft-panel">
        <div class="section-head">
          <div>
            <h2>版本列表</h2>
            <p>支持浏览版本、切换生效版本以及上传新的文档版本。</p>
          </div>
          <div class="version-actions">
            <el-button :loading="versionLoading" @click="handleRetryVersions">刷新版本列表</el-button>
            <el-button type="primary" @click="handleOpenVersionDialog">新增版本</el-button>
          </div>
        </div>

        <section v-if="versionError" class="version-error">
          <el-empty description="版本列表加载失败">
            <template #description>
              <p class="error-text">{{ versionError }}</p>
            </template>
            <el-button type="primary" @click="handleRetryVersions">重新加载</el-button>
          </el-empty>
        </section>

        <template v-else>
          <el-table :data="versions" v-loading="versionLoading" empty-text="当前文档暂无版本数据" stripe>
            <el-table-column prop="versionId" label="版本 ID" width="100" />
            <el-table-column prop="storageObjectKey" label="对象键" min-width="280" />
            <el-table-column label="内容哈希" min-width="220">
              <template #default="{ row }">
                {{ row.contentHash || '暂无' }}
              </template>
            </el-table-column>
            <el-table-column label="激活状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.active ? 'success' : 'info'">{{ row.active ? '当前生效' : '历史版本' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" min-width="180">
              <template #default="{ row }">
                {{ formatTime(row.createdAt) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="canActivateVersion(row)"
                  link
                  type="primary"
                  :loading="isActivatingVersion(row.versionId)"
                  @click="handleActivateVersion(row)"
                >
                  设为生效版本
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="table-footer" v-if="hasVersions || versionPagination.total > 0">
            <el-pagination
              background
              layout="total, sizes, prev, pager, next"
              :current-page="versionPagination.pageNo"
              :page-size="versionPagination.pageSize"
              :page-sizes="[10, 20, 50]"
              :total="versionPagination.total"
              @current-change="handleCurrentChange"
              @size-change="handleSizeChange"
            />
          </div>
        </template>
      </section>

      <section class="chunk-panel soft-panel">
        <div class="section-head">
          <div>
            <h2>切片浏览</h2>
            <p>切片区支持分页、刷新和行内展开，可直接观察解析与切片质量。</p>
          </div>
          <el-button :loading="chunkLoading" @click="handleRetryChunks">刷新切片</el-button>
        </div>

        <el-alert
          v-if="hasRouteChunkFocus"
          type="info"
          :closable="false"
          show-icon
          class="chunk-focus-alert"
          title="当前页面已按问答引用定位到命中切片。"
        >
          <template #default>
            <p class="chunk-focus-text">
              系统会自动切到对应分页，并高亮目标切片，方便你直接核对问答引用原文。
            </p>
          </template>
        </el-alert>

        <section v-if="chunkError" class="chunk-error">
          <el-empty description="切片列表加载失败">
            <template #description>
              <p class="error-text">{{ chunkError }}</p>
            </template>
            <el-button type="primary" @click="handleRetryChunks">重新加载</el-button>
          </el-empty>
        </section>

        <template v-else>
          <div ref="chunkTableWrapperRef">
            <el-table
              :data="chunks"
              v-loading="chunkLoading"
              empty-text="当前文档暂无切片数据"
              stripe
              row-key="chunkId"
              :row-class-name="chunkRowClassName"
            >
            <el-table-column prop="chunkId" label="切片 ID" width="100" />
            <el-table-column label="序号" width="100">
              <template #default="{ row, $index }">
                {{ row.chunkNo ?? $index + 1 + (chunkPagination.pageNo - 1) * chunkPagination.pageSize }}
              </template>
            </el-table-column>
            <el-table-column label="内容" min-width="360">
              <template #default="{ row }">
                <div class="chunk-content">
                  <p>{{ chunkPreview(row) || '暂无内容' }}</p>
                  <el-button
                    v-if="canToggleChunk(row)"
                    link
                    type="primary"
                    @click="toggleChunk(row.chunkId)"
                  >
                    {{ isChunkExpanded(row.chunkId) ? '收起' : '展开' }}
                  </el-button>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="得分" width="100">
              <template #default="{ row }">
                {{ row.score ?? '-' }}
              </template>
            </el-table-column>
            <el-table-column label="令牌数" width="100">
              <template #default="{ row }">
                {{ row.tokenCount ?? '-' }}
              </template>
            </el-table-column>
            <el-table-column label="字符数" width="100">
              <template #default="{ row }">
                {{ row.charCount ?? '-' }}
              </template>
            </el-table-column>
            </el-table>
          </div>

          <div class="table-footer" v-if="hasChunks || chunkPagination.total > 0">
            <el-pagination
              background
              layout="total, sizes, prev, pager, next"
              :current-page="chunkPagination.pageNo"
              :page-size="chunkPagination.pageSize"
              :page-sizes="[10, 20, 50]"
              :total="chunkPagination.total"
              @current-change="handleChunkCurrentChange"
              @size-change="handleChunkSizeChange"
            />
          </div>
        </template>
      </section>
    </template>

    <el-dialog
      v-model="versionDialogVisible"
      title="新增版本"
      width="560px"
      destroy-on-close
      @close="handleCloseVersionDialog"
    >
      <section class="upload-dialog">
        <p class="upload-hint">
          目标文档：
          <strong>{{ detail?.docName }}</strong>
        </p>

        <el-upload
          class="upload-box"
          drag
          :auto-upload="false"
          :limit="1"
          :show-file-list="true"
          :on-change="handleSelectVersionFile"
          :on-remove="handleRemoveVersionFile"
        >
          <el-icon class="el-icon--upload"><i class="el-icon-upload" /></el-icon>
          <div class="el-upload__text">将新版本文件拖到此处，或 <em>点击选择</em></div>
          <template #tip>
            <div class="el-upload__tip">
              新版本创建后不会自动切换为生效版本，可在版本列表中手动激活。
            </div>
          </template>
        </el-upload>

        <div class="upload-meta soft-panel" v-if="versionFile">
          <article class="upload-meta-item">
            <span>文件名</span>
            <strong>{{ versionFile.name }}</strong>
          </article>
          <article class="upload-meta-item">
            <span>文件大小</span>
            <strong>{{ (versionFile.size / 1024 / 1024).toFixed(2) }} MB</strong>
          </article>
        </div>

        <el-alert
          v-if="versionUploadError"
          type="error"
          :closable="false"
          show-icon
          :title="versionUploadError"
        />
      </section>

      <template #footer>
        <div class="dialog-actions">
          <el-button :disabled="versionSubmitting" @click="handleCloseVersionDialog">取消</el-button>
          <el-button type="primary" :loading="versionSubmitting" @click="handleCreateVersion">
            开始上传
          </el-button>
        </div>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.document-detail-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.detail-loading,
.detail-error,
.detail-panel,
.progress-panel,
.version-panel {
  padding: 24px;
}

.chunk-panel {
  padding: 24px;
}

.detail-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.detail-eyebrow {
  margin: 0 0 8px;
  color: #9b7755;
  font-size: 12px;
  letter-spacing: 0.22em;
  text-transform: uppercase;
}

.head-actions {
  display: flex;
  gap: 12px;
}

.version-actions {
  display: flex;
  gap: 12px;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}

.overview-card {
  padding: 22px;
}

.overview-card span,
.detail-item span,
.storage-item span {
  color: #9d7a58;
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.overview-card strong,
.detail-item strong,
.storage-item strong {
  display: block;
  margin-top: 12px;
  font-family: "Noto Serif SC", serif;
  font-size: 22px;
  word-break: break-word;
}

.overview-card p {
  margin: 12px 0 0;
  color: #6d5948;
}

.section-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 18px;
}

.section-head h2 {
  margin: 0;
  font-family: "Noto Serif SC", serif;
  font-size: 24px;
}

.section-head p {
  margin: 8px 0 0;
  color: #6d5948;
}

.detail-matrix {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
}

.detail-item,
.storage-item {
  padding: 18px;
  border-radius: 18px;
  background: rgba(255, 250, 242, 0.72);
}

.progress-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.progress-card {
  padding: 18px;
  border-radius: 18px;
  background: rgba(255, 250, 242, 0.72);
}

.progress-card span {
  color: #9d7a58;
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.progress-card strong {
  display: block;
  margin-top: 12px;
  font-family: "Noto Serif SC", serif;
  font-size: 28px;
}

.progress-card p {
  margin: 12px 0 0;
  color: #6d5948;
}

.storage-panel {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  margin-top: 18px;
}

.version-error {
  padding: 8px 0;
}

.chunk-error {
  padding: 8px 0;
}

.chunk-focus-alert {
  margin-bottom: 16px;
}

.chunk-focus-text {
  margin: 0;
  color: #6d5948;
}

.chunk-content p {
  margin: 0;
  color: #5d4736;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

:deep(.el-table .chunk-row.is-route-highlight > td.el-table__cell) {
  background:
    linear-gradient(90deg, rgba(226, 160, 87, 0.22), rgba(255, 246, 232, 0.92));
}

:deep(.el-table .chunk-row.is-route-highlight .cell) {
  color: #4e361f;
  font-weight: 600;
}

.upload-dialog {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.upload-hint {
  margin: 0;
  color: #6d5948;
}

.upload-box {
  width: 100%;
}

.upload-meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  padding: 18px;
}

.upload-meta-item {
  padding: 14px;
  border-radius: 16px;
  background: rgba(255, 250, 242, 0.72);
}

.upload-meta-item span {
  display: block;
  color: #9d7a58;
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.upload-meta-item strong {
  display: block;
  margin-top: 10px;
  color: #2f241d;
  word-break: break-word;
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.table-footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 18px;
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

@media (max-width: 960px) {
  .detail-head,
  .section-head {
    flex-direction: column;
  }

  .head-actions {
    width: 100%;
  }

  .version-actions,
  .overview-grid,
  .progress-summary,
  .detail-matrix,
  .storage-panel {
    grid-template-columns: 1fr;
  }

  .upload-meta {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .detail-loading,
  .detail-error,
  .detail-panel,
  .progress-panel,
  .version-panel,
  .chunk-panel {
    padding: 20px;
  }

  .head-actions,
  .version-actions,
  .dialog-actions,
  .error-actions {
    flex-direction: column;
  }
}
</style>
