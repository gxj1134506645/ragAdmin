<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElButton, ElEmpty } from 'element-plus'
import { listAuditLogs } from '@/api/audit'
import { resolveErrorMessage } from '@/api/http'
import type { AuditLogRecord } from '@/types/audit'

const loading = ref(false)
const loadError = ref('')
const rows = ref<AuditLogRecord[]>([])
const timeRange = ref<[Date, Date] | null>(null)
const pagination = reactive({
  pageNo: 1,
  pageSize: 20,
  total: 0,
})
const query = reactive({
  operator: '',
  bizType: '',
})

const bizTypeOptions = [
  { label: '全部业务类型', value: '' },
  { label: '登录认证', value: 'AUTH' },
  { label: '知识库', value: 'KNOWLEDGE_BASE' },
  { label: '文档', value: 'DOCUMENT' },
  { label: '任务', value: 'TASK' },
  { label: '问答', value: 'CHAT' },
  { label: '问答反馈', value: 'CHAT_FEEDBACK' },
  { label: '模型', value: 'MODEL' },
  { label: '审计', value: 'AUDIT' },
  { label: '系统', value: 'SYSTEM' },
]

const hasData = computed(() => rows.value.length > 0)
const summary = computed(() => {
  return rows.value.reduce(
    (result, item) => {
      result.totalCount += 1
      if (item.success) {
        result.successCount += 1
      } else {
        result.failureCount += 1
      }
      if (item.bizType === 'CHAT_FEEDBACK') {
        result.feedbackCount += 1
      }
      return result
    },
    {
      totalCount: 0,
      successCount: 0,
      failureCount: 0,
      feedbackCount: 0,
    },
  )
})

function bizTypeLabel(value: string): string {
  return bizTypeOptions.find((item) => item.value === value)?.label ?? value
}

function successTagType(success: boolean): 'success' | 'danger' {
  return success ? 'success' : 'danger'
}

function bizTypeTagType(bizType: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  if (bizType === 'CHAT_FEEDBACK') {
    return 'warning'
  }
  if (bizType === 'CHAT') {
    return 'primary'
  }
  if (bizType === 'DOCUMENT' || bizType === 'KNOWLEDGE_BASE') {
    return 'success'
  }
  if (bizType === 'TASK') {
    return 'danger'
  }
  return 'info'
}

function formatTime(value: string): string {
  if (!value) {
    return '暂无时间'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString('zh-CN', { hour12: false })
}

function formatDateTimeForQuery(date: Date): string {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function normalizeQuery(): {
  operator?: string
  bizType?: string
  startTime?: string
  endTime?: string
} {
  const [startTime, endTime] = timeRange.value ?? []
  return {
    operator: query.operator.trim() || undefined,
    bizType: query.bizType || undefined,
    startTime: startTime ? formatDateTimeForQuery(startTime) : undefined,
    endTime: endTime ? formatDateTimeForQuery(endTime) : undefined,
  }
}

async function loadData(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const response = await listAuditLogs({
      ...normalizeQuery(),
      pageNo: pagination.pageNo,
      pageSize: pagination.pageSize,
    })
    rows.value = response.list
    pagination.total = response.total
  } catch (error) {
    rows.value = []
    pagination.total = 0
    loadError.value = resolveErrorMessage(error)
  } finally {
    loading.value = false
  }
}

async function handleSearch(): Promise<void> {
  pagination.pageNo = 1
  await loadData()
}

async function handleRefresh(): Promise<void> {
  await loadData()
}

async function handleReset(): Promise<void> {
  query.operator = ''
  query.bizType = ''
  timeRange.value = null
  pagination.pageNo = 1
  await loadData()
}

async function handleCurrentChange(pageNo: number): Promise<void> {
  pagination.pageNo = pageNo
  await loadData()
}

async function handleSizeChange(pageSize: number): Promise<void> {
  pagination.pageSize = pageSize
  pagination.pageNo = 1
  await loadData()
}

onMounted(async () => {
  await loadData()
})
</script>

<template>
  <section class="audit-page">
    <header class="audit-head soft-panel">
      <div class="head-stats">
        <span class="stat"><em>{{ summary.totalCount }}</em>总记录</span>
        <span class="stat is-success"><em>{{ summary.successCount }}</em>成功</span>
        <span class="stat is-danger"><em>{{ summary.failureCount }}</em>失败</span>
        <span class="stat is-warm"><em>{{ summary.feedbackCount }}</em>反馈</span>
      </div>
      <el-button size="small" @click="handleRefresh">刷新</el-button>
    </header>

    <section class="filter-panel soft-panel">
      <div class="filter-row">
        <div class="filter-grid">
          <el-input
            v-model="query.operator"
            placeholder="操作人用户名"
            clearable
            @keyup.enter="handleSearch"
          />
          <el-select v-model="query.bizType" placeholder="业务类型">
            <el-option
              v-for="item in bizTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            clearable
          />
        </div>
        <div class="filter-actions">
          <el-button @click="handleReset">重置</el-button>
          <el-button @click="handleRefresh">刷新</el-button>
          <el-button type="primary" @click="handleSearch">查询</el-button>
        </div>
      </div>
    </section>

    <section class="table-panel soft-panel">

      <section v-if="loadError" class="table-error">
        <el-empty description="审计日志加载失败">
          <template #description>
            <p class="error-text">{{ loadError }}</p>
          </template>
          <el-button type="primary" @click="handleRefresh">重新加载</el-button>
        </el-empty>
      </section>

      <template v-else>
        <el-table :data="rows" v-loading="loading" stripe empty-text="当前没有匹配的审计记录">
          <el-table-column prop="id" label="日志 ID" width="100" />
          <el-table-column label="操作人" min-width="160">
            <template #default="{ row }">
              <div class="operator-cell">
                <strong>{{ row.operatorUsername || '匿名/系统' }}</strong>
                <span v-if="row.operatorUserId">UID {{ row.operatorUserId }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="业务类型" width="140">
            <template #default="{ row }">
              <el-tag :type="bizTypeTagType(row.bizType)" effect="plain">{{ bizTypeLabel(row.bizType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="动作" width="120">
            <template #default="{ row }">
              <span class="method-badge">{{ row.actionType }}</span>
            </template>
          </el-table-column>
          <el-table-column label="请求路径" min-width="320">
            <template #default="{ row }">
              <div class="path-cell">
                <code>{{ row.requestPath }}</code>
                <span v-if="row.bizId">业务 ID {{ row.bizId }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="响应" width="120">
            <template #default="{ row }">
              <div class="response-cell">
                <el-tag :type="successTagType(row.success)">{{ row.success ? '成功' : '失败' }}</el-tag>
                <span>{{ row.responseCode || '-' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="来源 IP" width="150">
            <template #default="{ row }">
              {{ row.requestIp || '-' }}
            </template>
          </el-table-column>
          <el-table-column label="发生时间" min-width="180">
            <template #default="{ row }">
              {{ formatTime(row.createdAt) }}
            </template>
          </el-table-column>
        </el-table>

        <div v-if="hasData || pagination.total > 0" class="table-footer">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :current-page="pagination.pageNo"
            :page-size="pagination.pageSize"
            :page-sizes="[20, 50, 100]"
            :total="pagination.total"
            @current-change="handleCurrentChange"
            @size-change="handleSizeChange"
          />
        </div>
      </template>
    </section>
  </section>
</template>

<style scoped>
.audit-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.audit-head {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 10px 20px;
  background:
    radial-gradient(circle at right top, rgba(198, 107, 34, 0.12), transparent 32%),
    linear-gradient(180deg, rgba(255, 251, 246, 0.96), rgba(255, 248, 241, 0.9));
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
  color: #8f7159;
  font-size: 13px;
}

.head-stats .stat em {
  font-style: normal;
  font-weight: 700;
  color: #2f241d;
  font-size: 16px;
  margin-right: 2px;
}

.head-stats .is-success em { color: #529b2e; }
.head-stats .is-danger em { color: #c45656; }
.head-stats .is-warm em { color: #b88230; }

.filter-panel,
.table-panel {
  padding: 14px 20px;
}

.filter-row {
  display: flex;
  align-items: flex-end;
  gap: 12px;
}

.filter-grid {
  flex: 1;
  display: grid;
  grid-template-columns: 1fr 1fr 2fr;
  gap: 12px;
}

.filter-actions {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
}

.table-error {
  padding: 12px 0;
}

.operator-cell,
.path-cell,
.response-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.operator-cell strong,
.method-badge {
  color: #2f241d;
}

.operator-cell span,
.path-cell span,
.response-cell span {
  color: #8f7159;
  font-size: 12px;
}

.path-cell code {
  color: #684d39;
  white-space: pre-wrap;
  word-break: break-all;
}

.method-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: fit-content;
  min-width: 64px;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(255, 248, 238, 0.88);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.table-footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 14px;
}

.error-text {
  margin: 0;
  color: #6d5948;
}

@media (max-width: 1080px) {
  .filter-row {
    flex-wrap: wrap;
  }

  .filter-grid {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 760px) {
  .audit-head {
    flex-wrap: wrap;
  }

  .head-stats {
    margin-left: 0;
    gap: 12px;
  }

  .filter-row {
    flex-direction: column;
    align-items: stretch;
  }

  .filter-grid {
    grid-template-columns: 1fr;
  }

  .filter-actions {
    justify-content: flex-end;
  }
}
</style>
