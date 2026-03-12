<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElButton, ElEmpty, ElMessage, ElSkeleton } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import {
  createKnowledgeBaseDocument,
  getKnowledgeBaseDetail,
  getKnowledgeBaseDocumentUploadUrl,
  listKnowledgeBaseDocuments,
  triggerDocumentParse,
} from '@/api/knowledge-base'
import { resolveErrorMessage } from '@/api/http'
import type {
  CreateKnowledgeBaseDocumentRequest,
  KnowledgeBase,
  KnowledgeBaseDocument,
  UploadUrlResponse,
} from '@/types/knowledge-base'

const route = useRoute()
const router = useRouter()

const detailLoading = ref(true)
const detailError = ref('')
const knowledgeBase = ref<KnowledgeBase | null>(null)

const documentLoading = ref(false)
const documentError = ref('')
const documents = ref<KnowledgeBaseDocument[]>([])
const documentPagination = reactive({
  pageNo: 1,
  pageSize: 10,
  total: 0,
})
const uploadDialogVisible = ref(false)
const selectedFile = ref<File | null>(null)
const uploadSubmitting = ref(false)
const uploadStage = ref<'idle' | 'getting-url' | 'uploading' | 'registering'>('idle')
const uploadError = ref('')
const parsingDocumentIds = ref<number[]>([])

const knowledgeBaseId = computed(() => Number(route.params.id))
const hasDocuments = computed(() => documents.value.length > 0)
const uploadDocType = computed(() => inferDocType(selectedFile.value?.name ?? ''))

function inferDocType(fileName: string): string {
  const extension = fileName.split('.').pop()?.toLowerCase() ?? ''
  if (extension === 'pdf') {
    return 'PDF'
  }
  if (extension === 'doc' || extension === 'docx') {
    return 'DOC'
  }
  if (extension === 'md' || extension === 'markdown') {
    return 'MARKDOWN'
  }
  if (extension === 'txt') {
    return 'TEXT'
  }
  return 'UNKNOWN'
}

function modelLabel(name: string | null): string {
  return name || '平台默认模型'
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

function isParsing(documentId: number): boolean {
  return parsingDocumentIds.value.includes(documentId)
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

function resetUploadState(): void {
  selectedFile.value = null
  uploadSubmitting.value = false
  uploadStage.value = 'idle'
  uploadError.value = ''
}

function handleSelectFile(uploadFile: { raw?: File }): void {
  selectedFile.value = uploadFile.raw ?? null
  uploadError.value = ''
}

function handleRemoveFile(): void {
  selectedFile.value = null
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

async function handleUpload(): Promise<void> {
  if (!selectedFile.value) {
    uploadError.value = '请先选择待上传文件'
    return
  }

  uploadSubmitting.value = true
  uploadError.value = ''

  try {
    uploadStage.value = 'getting-url'
    const file = selectedFile.value
    const upload = await getKnowledgeBaseDocumentUploadUrl({
      fileName: file.name,
      contentType: file.type || 'application/octet-stream',
      bizType: 'KB_DOCUMENT',
    })

    uploadStage.value = 'uploading'
    await uploadToObjectStorage(upload, file)

    uploadStage.value = 'registering'
    const payload: CreateKnowledgeBaseDocumentRequest = {
      docName: file.name,
      docType: uploadDocType.value,
      storageBucket: upload.bucket,
      storageObjectKey: upload.objectKey,
    }
    await createKnowledgeBaseDocument(knowledgeBaseId.value, payload)

    ElMessage.success('文档已接入当前知识库')
    uploadDialogVisible.value = false
    resetUploadState()
    documentPagination.pageNo = 1
    await loadDocuments()
  } catch (error) {
    if (uploadStage.value === 'getting-url') {
      uploadError.value = `获取上传凭证失败：${resolveErrorMessage(error)}`
    } else if (uploadStage.value === 'uploading') {
      uploadError.value = `文件上传失败：${resolveErrorMessage(error)}`
    } else if (uploadStage.value === 'registering') {
      uploadError.value = `文件已上传，但文档登记失败：${resolveErrorMessage(error)}`
    } else {
      uploadError.value = resolveErrorMessage(error)
    }
  } finally {
    uploadSubmitting.value = false
    if (uploadDialogVisible.value) {
      uploadStage.value = 'idle'
    }
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
  try {
    const response = await listKnowledgeBaseDocuments(knowledgeBaseId.value, {
      pageNo: documentPagination.pageNo,
      pageSize: documentPagination.pageSize,
    })
    documents.value = response.list
    documentPagination.total = response.total
  } catch (error) {
    documents.value = []
    documentPagination.total = 0
    documentError.value = resolveErrorMessage(error)
  } finally {
    documentLoading.value = false
  }
}

async function initialize(): Promise<void> {
  await loadDetail()
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

function handleOpenUploadDialog(): void {
  resetUploadState()
  uploadDialogVisible.value = true
}

function handleCloseUploadDialog(): void {
  uploadDialogVisible.value = false
  resetUploadState()
}

async function handleBack(): Promise<void> {
  await router.push('/knowledge-bases')
}

async function handleEdit(): Promise<void> {
  await router.push(`/knowledge-bases/${knowledgeBaseId.value}/edit`)
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
      <header class="detail-head">
        <div>
          <p class="detail-eyebrow">Knowledge Base / Detail</p>
          <h1 class="page-title">{{ knowledgeBase.kbName }}</h1>
          <p class="page-subtitle">
            {{ knowledgeBase.description || '当前知识库暂无补充说明，可继续通过编辑页完善描述。' }}
          </p>
        </div>
        <div class="head-actions">
          <el-button @click="loadDetail">刷新详情</el-button>
          <el-button @click="handleBack">返回列表</el-button>
          <el-button type="primary" @click="handleEdit">编辑知识库</el-button>
        </div>
      </header>

      <section class="overview-grid">
        <article class="overview-card soft-panel">
          <span>知识库编码</span>
          <strong>{{ knowledgeBase.kbCode }}</strong>
          <p>用于接口、路由和后台治理中的稳定标识。</p>
        </article>
        <article class="overview-card soft-panel">
          <span>聊天模型</span>
          <strong>{{ modelLabel(knowledgeBase.chatModelName) }}</strong>
          <p>未显式绑定时由平台默认聊天模型兜底。</p>
        </article>
        <article class="overview-card soft-panel">
          <span>Embedding 模型</span>
          <strong>{{ modelLabel(knowledgeBase.embeddingModelName) }}</strong>
          <p>向量构建链路使用的 Embedding 配置。</p>
        </article>
      </section>

      <section class="detail-panel soft-panel">
        <div class="section-head">
          <div>
            <h2>知识库配置</h2>
            <p>首版详情页聚焦基础配置与文档列表浏览，不提前引入写操作。</p>
          </div>
        </div>

        <div class="detail-matrix">
          <article class="detail-item">
            <span>知识库 ID</span>
            <strong>{{ knowledgeBase.id }}</strong>
          </article>
          <article class="detail-item">
            <span>状态</span>
            <el-tag :type="statusType(knowledgeBase.status)">{{ knowledgeBase.status }}</el-tag>
          </article>
          <article class="detail-item">
            <span>检索 TopK</span>
            <strong>{{ knowledgeBase.retrieveTopK }}</strong>
          </article>
          <article class="detail-item">
            <span>重排开关</span>
            <el-tag :type="knowledgeBase.rerankEnabled ? 'warning' : 'info'">
              {{ knowledgeBase.rerankEnabled ? '开启' : '关闭' }}
            </el-tag>
          </article>
        </div>
      </section>

      <section class="document-panel soft-panel">
        <div class="section-head">
          <div>
            <h2>知识库文档</h2>
            <p>当前已接入单文件上传入口，后续再补解析触发、详情查看和版本管理。</p>
          </div>
          <div class="document-actions">
            <el-button :loading="documentLoading" @click="handleRetryDocuments">刷新文档列表</el-button>
            <el-button type="primary" @click="handleOpenUploadDialog">上传文档</el-button>
          </div>
        </div>

        <section v-if="documentError" class="document-error">
          <el-empty description="文档列表加载失败">
            <template #description>
              <p class="error-text">{{ documentError }}</p>
            </template>
            <el-button type="primary" @click="handleRetryDocuments">重新加载</el-button>
          </el-empty>
        </section>

        <template v-else>
          <el-table :data="documents" v-loading="documentLoading" empty-text="当前知识库暂无文档" stripe>
            <el-table-column prop="documentId" label="文档 ID" width="100" />
            <el-table-column prop="docName" label="文档名称" min-width="220" />
            <el-table-column prop="docType" label="类型" width="100" />
            <el-table-column label="解析状态" width="130">
              <template #default="{ row }">
                <el-tag :type="parseStatusType(row.parseStatus)">{{ row.parseStatus }}</el-tag>
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
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button
                  link
                  type="primary"
                  :disabled="!canTriggerParse(row.parseStatus)"
                  :loading="isParsing(row.documentId)"
                  @click="handleTriggerParse(row)"
                >
                  开始解析
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
          当前上传目标知识库：
          <strong>{{ knowledgeBase?.kbName }}</strong>
        </p>

        <el-upload
          class="upload-box"
          drag
          :auto-upload="false"
          :limit="1"
          :show-file-list="true"
          :on-change="handleSelectFile"
          :on-remove="handleRemoveFile"
        >
          <el-icon class="el-icon--upload"><i class="el-icon-upload" /></el-icon>
          <div class="el-upload__text">将文件拖到此处，或 <em>点击选择</em></div>
          <template #tip>
            <div class="el-upload__tip">
              首版支持单文件上传，文档类型将按文件扩展名自动推断。
            </div>
          </template>
        </el-upload>

        <div class="upload-meta soft-panel" v-if="selectedFile">
          <article class="upload-meta-item">
            <span>文件名</span>
            <strong>{{ selectedFile.name }}</strong>
          </article>
          <article class="upload-meta-item">
            <span>文件大小</span>
            <strong>{{ (selectedFile.size / 1024 / 1024).toFixed(2) }} MB</strong>
          </article>
          <article class="upload-meta-item">
            <span>文档类型</span>
            <strong>{{ uploadDocType }}</strong>
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
            开始上传
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
  gap: 20px;
}

.detail-loading,
.detail-error,
.detail-panel,
.document-panel {
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

.document-actions {
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
.detail-item span {
  color: #9d7a58;
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.overview-card strong,
.detail-item strong {
  display: block;
  margin-top: 12px;
  font-family: "Noto Serif SC", serif;
  font-size: 28px;
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

.detail-item {
  padding: 18px;
  border-radius: 18px;
  background: rgba(255, 250, 242, 0.72);
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
  color: #6d5948;
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
  color: #6d5948;
}

.upload-box {
  width: 100%;
}

.upload-meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
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

@media (max-width: 960px) {
  .detail-head,
  .section-head {
    flex-direction: column;
  }

  .head-actions {
    width: 100%;
  }

  .document-actions,
  .overview-grid,
  .detail-matrix {
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
  .document-panel {
    padding: 20px;
  }

  .head-actions,
  .document-actions,
  .dialog-actions,
  .error-actions {
    flex-direction: column;
  }
}
</style>
