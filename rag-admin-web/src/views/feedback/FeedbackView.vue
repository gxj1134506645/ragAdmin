<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { listFeedback } from '@/api/feedback'
import { resolveErrorMessage } from '@/api/http'
import type { FeedbackRecord } from '@/types/feedback'

const loading = ref(false)
const loadError = ref('')
const rows = ref<FeedbackRecord[]>([])
const pagination = reactive({ pageNo: 1, pageSize: 20, total: 0 })

const query = reactive({
  feedbackType: '',
})
const timeRange = ref<[Date, Date] | null>(null)

const summary = computed(() => {
  const like = rows.value.filter(r => r.feedbackType === 'LIKE').length
  const dislike = rows.value.filter(r => r.feedbackType === 'DISLIKE').length
  return { like, dislike, total: rows.value.length }
})

function normalizeQuery() {
  return {
    feedbackType: query.feedbackType || undefined,
    startTime: timeRange.value?.[0]?.toISOString(),
    endTime: timeRange.value?.[1]?.toISOString(),
  }
}

async function loadData() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await listFeedback({
      ...normalizeQuery(),
      pageNo: pagination.pageNo,
      pageSize: pagination.pageSize,
    })
    rows.value = res.list
    pagination.total = res.total
  } catch (e) {
    rows.value = []
    loadError.value = resolveErrorMessage(e)
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.pageNo = 1
  loadData()
}

function handleRefresh() {
  loadData()
}

function handleReset() {
  query.feedbackType = ''
  timeRange.value = null
  pagination.pageNo = 1
  loadData()
}

function handleCurrentChange(page: number) {
  pagination.pageNo = page
  loadData()
}

function handleSizeChange(size: number) {
  pagination.pageSize = size
  pagination.pageNo = 1
  loadData()
}

function feedbackTagType(type: string): 'success' | 'danger' | 'info' {
  if (type === 'LIKE') return 'success'
  if (type === 'DISLIKE') return 'danger'
  return 'info'
}

function feedbackLabel(type: string): string {
  if (type === 'LIKE') return '有帮助'
  if (type === 'DISLIKE') return '待改进'
  return type
}

function formatTime(value: string): string {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

onMounted(loadData)
</script>

<template>
  <section class="feedback-page">

    <header class="feedback-head soft-panel">
      <div class="head-stats">
        <span class="stat-item"><em>{{ summary.total }}</em>条反馈</span>
        <span class="stat-sep">|</span>
        <span class="stat-item stat-like"><em>{{ summary.like }}</em>有帮助</span>
        <span class="stat-sep">|</span>
        <span class="stat-item stat-dislike"><em>{{ summary.dislike }}</em>待改进</span>
      </div>
    </header>

    <section class="filter-panel soft-panel">
      <div class="filter-row">
        <div class="filter-grid">
          <el-select v-model="query.feedbackType" placeholder="反馈类型" clearable>
            <el-option label="有帮助" value="LIKE" />
            <el-option label="待改进" value="DISLIKE" />
          </el-select>
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
          />
        </div>
        <div class="filter-actions">
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleRefresh" :loading="loading">刷新</el-button>
        </div>
      </div>
    </section>

    <section class="table-panel soft-panel">
      <el-empty v-if="loadError && !loading" :description="loadError">
        <el-button type="primary" @click="handleRefresh">重试</el-button>
      </el-empty>

      <template v-else>
        <el-table :data="rows" v-loading="loading" stripe empty-text="暂无反馈数据">
          <el-table-column label="反馈类型" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="feedbackTagType(row.feedbackType)">
                {{ feedbackLabel(row.feedbackType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="用户" width="120">
            <template #default="{ row }">
              {{ row.username || '-' }}
            </template>
          </el-table-column>
          <el-table-column label="问题摘要" min-width="200">
            <template #default="{ row }">
              <span class="text-ellipsis" :title="row.questionSummary ?? undefined">
                {{ row.questionSummary || '-' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="答案摘要" min-width="200">
            <template #default="{ row }">
              <span class="text-ellipsis" :title="row.answerSummary ?? undefined">
                {{ row.answerSummary || '-' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="评论" width="160">
            <template #default="{ row }">
              {{ row.commentText || '-' }}
            </template>
          </el-table-column>
          <el-table-column label="时间" width="170">
            <template #default="{ row }">
              {{ formatTime(row.createdAt) }}
            </template>
          </el-table-column>
        </el-table>
        <div class="table-footer">
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
.feedback-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.feedback-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
}

.head-stats {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--ember-text-secondary);
}

.head-stats em {
  font-style: normal;
  font-weight: 700;
  color: var(--ember-text-primary);
  margin-right: 2px;
}

.stat-sep {
  color: var(--ember-border);
}

.stat-like em { color: var(--ember-success); }
.stat-dislike em { color: var(--ember-error); }

.filter-panel {
  padding: 14px 20px;
}

.filter-row {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}

.filter-grid {
  display: grid;
  grid-template-columns: 160px 1fr;
  gap: 12px;
  flex: 1;
}

.filter-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.table-panel {
  padding: 16px 20px;
}

.text-ellipsis {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.5;
}

.table-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

@media (max-width: 760px) {
  .filter-row {
    flex-direction: column;
    align-items: stretch;
  }
  .filter-grid {
    grid-template-columns: 1fr;
  }
}
</style>
