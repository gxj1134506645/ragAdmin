<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listVectorIndexes } from '@/api/statistics'
import { resolveErrorMessage } from '@/api/http'
import type { VectorIndexOverview } from '@/types/statistics'

const router = useRouter()
const loading = ref(false)
const loadError = ref('')
const rows = ref<VectorIndexOverview[]>([])

const query = reactive({
  keyword: '',
  status: '',
  milvusStatus: '',
})

const kbStatusOptions = [
  { label: '全部知识库状态', value: '' },
  { label: '启用', value: 'ENABLED' },
  { label: '禁用', value: 'DISABLED' },
]

const milvusStatusOptions = [
  { label: '全部 Milvus 状态', value: '' },
  { label: '已加载', value: 'UP' },
  { label: '未加载', value: 'NOT_LOADED' },
  { label: '未建索引', value: 'EMPTY' },
  { label: '异常', value: 'DOWN' },
  { label: '未知', value: 'UNKNOWN' },
]

const filteredRows = computed(() => {
  if (!query.milvusStatus) {
    return rows.value
  }
  return rows.value.filter((item) => item.milvusStatus === query.milvusStatus)
})

const summary = computed(() => {
  const list = filteredRows.value
  const totalKnowledgeBaseCount = list.length
  const indexedKnowledgeBaseCount = list.filter((item) => item.vectorRefCount > 0).length
  const totalVectorCount = list.reduce((sum, item) => sum + item.vectorRefCount, 0)
  const issueKnowledgeBaseCount = list.filter((item) => hasIssue(item)).length
  return {
    totalKnowledgeBaseCount,
    indexedKnowledgeBaseCount,
    totalVectorCount,
    issueKnowledgeBaseCount,
  }
})

function hasIssue(item: VectorIndexOverview): boolean {
  if (item.milvusStatus === 'DOWN' || item.milvusStatus === 'NOT_LOADED') {
    return true
  }
  return item.chunkCount > 0 && item.vectorRefCount < item.chunkCount
}

function kbStatusTagType(status: string): 'success' | 'info' {
  return status === 'ENABLED' ? 'success' : 'info'
}

function kbStatusLabel(status: string): string {
  return status === 'ENABLED' ? '启用' : '禁用'
}

function milvusStatusTagType(status: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'UP') {
    return 'success'
  }
  if (status === 'NOT_LOADED') {
    return 'warning'
  }
  if (status === 'DOWN') {
    return 'danger'
  }
  return 'info'
}

function milvusStatusLabel(status: string): string {
  if (status === 'UP') {
    return '已加载'
  }
  if (status === 'NOT_LOADED') {
    return '未加载'
  }
  if (status === 'EMPTY') {
    return '未建索引'
  }
  if (status === 'DOWN') {
    return '异常'
  }
  return '未知'
}

function modelSourceLabel(source?: string | null): string {
  if (source === 'UNSET') {
    return '未绑定'
  }
  return '知识库绑定'
}

function coveragePercent(item: VectorIndexOverview): number {
  if (item.chunkCount <= 0) {
    return 0
  }
  return Math.min(100, Math.round((item.vectorRefCount / item.chunkCount) * 100))
}

function coverageStatus(item: VectorIndexOverview): '' | 'success' | 'warning' | 'exception' {
  if (item.chunkCount <= 0) {
    return ''
  }
  if (item.vectorRefCount === 0) {
    return 'exception'
  }
  if (item.vectorRefCount >= item.chunkCount) {
    return 'success'
  }
  return 'warning'
}

function formatTime(value?: string | null): string {
  if (!value) {
    return '暂无'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString('zh-CN', {
    hour12: false,
  })
}

async function loadData(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    rows.value = await listVectorIndexes({
      keyword: query.keyword.trim() || undefined,
      status: query.status || undefined,
    })
  } catch (error) {
    rows.value = []
    loadError.value = resolveErrorMessage(error)
  } finally {
    loading.value = false
  }
}

async function handleSearch(): Promise<void> {
  await loadData()
}

async function handleRefresh(): Promise<void> {
  await loadData()
}

async function handleReset(): Promise<void> {
  query.keyword = ''
  query.status = ''
  query.milvusStatus = ''
  await loadData()
}

async function openKnowledgeBase(kbId: number): Promise<void> {
  await router.push(`/knowledge-bases/${kbId}`)
}

onMounted(async () => {
  await loadData()
})
</script>

<template>
  <section class="vector-page">
    <header class="vector-head soft-panel">
      <div class="head-stats">
        <span class="stat"><em>{{ summary.totalKnowledgeBaseCount }}</em>知识库总数</span>
        <span class="stat is-success"><em>{{ summary.indexedKnowledgeBaseCount }}</em>已建索引</span>
        <span class="stat"><em>{{ summary.totalVectorCount }}</em>向量总数</span>
        <span class="stat is-warning"><em>{{ summary.issueKnowledgeBaseCount }}</em>待处理</span>
      </div>
    </header>

    <section class="filter-panel soft-panel">
      <div class="filter-grid">
        <el-input v-model="query.keyword" placeholder="知识库编码或名称" clearable />

        <el-select v-model="query.status" placeholder="知识库状态">
          <el-option
            v-for="item in kbStatusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>

        <el-select v-model="query.milvusStatus" placeholder="Milvus 状态">
          <el-option
            v-for="item in milvusStatusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </div>

      <div class="filter-actions">
        <el-button @click="handleReset">重置</el-button>
        <el-button type="primary" @click="handleSearch">查询概览</el-button>
        <el-button @click="handleRefresh">刷新</el-button>
      </div>
    </section>

    <section class="table-panel soft-panel">
      <section v-if="loadError" class="table-error">
        <el-empty description="向量索引概览加载失败">
          <template #description>
            <p class="error-text">{{ loadError }}</p>
          </template>
          <el-button type="primary" @click="handleRefresh">重新加载</el-button>
        </el-empty>
      </section>

      <template v-else>
        <el-table :data="filteredRows" v-loading="loading" empty-text="当前没有向量索引数据" stripe>
          <el-table-column label="知识库" min-width="220">
            <template #default="{ row }">
              <div class="kb-cell">
                <strong>{{ row.kbName }}</strong>
                <small>{{ row.kbCode }}</small>
                <el-tag size="small" :type="kbStatusTagType(row.kbStatus)">
                  {{ kbStatusLabel(row.kbStatus) }}
                </el-tag>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="向量模型" min-width="220">
            <template #default="{ row }">
              <div class="model-cell">
                <strong>{{ row.embeddingModelSource === 'UNSET' ? '未绑定向量模型' : (row.embeddingModelName || '未解析') }}</strong>
                <small>{{ row.embeddingModelSource === 'UNSET' ? '请先在知识库中绑定向量模型' : (row.embeddingModelCode || '暂无模型编码') }}</small>
                <el-tag size="small" effect="plain">
                  {{ modelSourceLabel(row.embeddingModelSource) }}
                </el-tag>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="集合名称" min-width="220">
            <template #default="{ row }">
              <code v-if="row.collectionName" class="mono-text">{{ row.collectionName }}</code>
              <span v-else class="muted-text">未建索引</span>
            </template>
          </el-table-column>

          <el-table-column label="文档 / 成功文档" width="140">
            <template #default="{ row }">
              {{ row.documentCount }} / {{ row.successDocumentCount }}
            </template>
          </el-table-column>

          <el-table-column label="分块数" width="100">
            <template #default="{ row }">
              {{ row.chunkCount }}
            </template>
          </el-table-column>

          <el-table-column label="向量数" width="100">
            <template #default="{ row }">
              {{ row.vectorRefCount }}
            </template>
          </el-table-column>

          <el-table-column label="覆盖率" min-width="220">
            <template #default="{ row }">
              <div v-if="row.chunkCount > 0" class="coverage-cell">
                <el-progress
                  :percentage="coveragePercent(row)"
                  :status="coverageStatus(row)"
                  :stroke-width="8"
                />
                <small>{{ row.vectorRefCount }} / {{ row.chunkCount }}</small>
              </div>
              <span v-else class="muted-text">暂无切片</span>
            </template>
          </el-table-column>

          <el-table-column label="维度" width="90">
            <template #default="{ row }">
              {{ row.embeddingDim ?? '-' }}
            </template>
          </el-table-column>

          <el-table-column label="Milvus 状态" min-width="220">
            <template #default="{ row }">
              <div class="milvus-cell">
                <el-tag :type="milvusStatusTagType(row.milvusStatus)">
                  {{ milvusStatusLabel(row.milvusStatus) }}
                </el-tag>
                <small>{{ row.milvusMessage }}</small>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="最近写入" min-width="170">
            <template #default="{ row }">
              {{ formatTime(row.latestVectorizedAt) }}
            </template>
          </el-table-column>

          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openKnowledgeBase(row.kbId)">
                查看知识库
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </section>
  </section>
</template>

<style scoped>
.vector-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.vector-head {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 10px 20px;
  background:
    radial-gradient(circle at right top, var(--ember-primary-light), transparent 32%),
    linear-gradient(180deg, var(--ember-background), var(--ember-surface));
}

.head-stats {
  display: flex;
  gap: 18px;
  margin-left: auto;
}

.head-stats .stat {
  display: flex;
  align-items: baseline;
  gap: 4px;
  color: var(--ember-text-secondary);
  font-size: 13px;
}

.head-stats .stat em {
  font-style: normal;
  font-weight: 700;
  color: var(--ember-text-primary);
  font-size: 16px;
  margin-right: 2px;
}

.head-stats .is-success em { color: var(--ember-success); }
.head-stats .is-warning em { color: var(--ember-warning); }

.filter-panel,
.table-panel {
  padding: 14px 20px;
}

.filter-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.filter-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 14px;
}

.table-error {
  padding: 8px 0;
}

.error-text {
  margin: 0;
  color: var(--ember-text-secondary);
}

.kb-cell,
.model-cell,
.milvus-cell,
.coverage-cell {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.kb-cell small,
.model-cell small,
.milvus-cell small,
.coverage-cell small {
  color: var(--ember-text-secondary);
  line-height: 1.5;
}

.mono-text {
  color: var(--ember-primary);
  font-family: var(--ember-font-code);
  font-size: 12px;
  word-break: break-all;
}

.muted-text {
  color: var(--ember-text-muted);
}

@media (max-width: 960px) {
  .vector-head {
    flex-wrap: wrap;
  }

  .head-stats {
    gap: 12px;
    margin-left: 0;
  }

  .filter-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .filter-panel,
  .table-panel {
    padding: 14px;
  }

  .filter-actions {
    flex-direction: column;
  }
}
</style>
