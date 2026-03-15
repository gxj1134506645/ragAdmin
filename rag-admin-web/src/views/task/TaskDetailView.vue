<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElButton, ElEmpty, ElSkeleton } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { getTaskDetail } from '@/api/task'
import { resolveErrorMessage } from '@/api/http'
import type { TaskDetail } from '@/types/task'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const loadError = ref('')
const detail = ref<TaskDetail | null>(null)

const taskId = computed(() => Number(route.params.id))
const linkedDocumentId = computed(() => {
  if (!detail.value?.documentId) {
    return null
  }
  return detail.value.taskType === 'DOCUMENT_PARSE' ? detail.value.documentId : null
})
const timelineSteps = computed(() => {
  if (!detail.value) {
    return []
  }

  const status = detail.value.taskStatus
  const steps = [
    {
      code: 'WAITING',
      label: '等待中',
      reached: true,
      current: status === 'WAITING',
      time: formatTime(detail.value.createdAt),
    },
    {
      code: 'RUNNING',
      label: '运行中',
      reached: ['RUNNING', 'SUCCESS', 'FAILED', 'CANCELED'].includes(status),
      current: status === 'RUNNING',
      time: ['RUNNING', 'SUCCESS', 'FAILED', 'CANCELED'].includes(status)
        ? formatTime(detail.value.startedAt || '')
        : '待发生',
    },
    {
      code: 'SUCCESS',
      label: '成功',
      reached: status === 'SUCCESS',
      current: status === 'SUCCESS',
      time: status === 'SUCCESS' ? formatTime(detail.value.finishedAt || detail.value.updatedAt) : '待发生',
    },
    {
      code: 'FAILED',
      label: '失败',
      reached: status === 'FAILED',
      current: status === 'FAILED',
      time: status === 'FAILED' ? formatTime(detail.value.finishedAt || detail.value.updatedAt) : '待发生',
    },
    {
      code: 'CANCELED',
      label: '已取消',
      reached: status === 'CANCELED',
      current: status === 'CANCELED',
      time: status === 'CANCELED' ? formatTime(detail.value.finishedAt || detail.value.updatedAt) : '待发生',
    },
  ]

  return steps
})

function taskStatusType(status: string): 'warning' | 'success' | 'danger' | 'info' {
  if (status === 'WAITING' || status === 'RUNNING') {
    return 'warning'
  }
  if (status === 'SUCCESS') {
    return 'success'
  }
  if (status === 'FAILED') {
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
  return date.toLocaleString('zh-CN', {
    hour12: false,
  })
}

async function loadDetail(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    detail.value = await getTaskDetail(taskId.value)
  } catch (error) {
    detail.value = null
    loadError.value = resolveErrorMessage(error)
  } finally {
    loading.value = false
  }
}

async function handleBack(): Promise<void> {
  await router.push('/tasks')
}

async function handleLinkedDocumentDetail(): Promise<void> {
  if (!linkedDocumentId.value) {
    return
  }
  await router.push(`/documents/${linkedDocumentId.value}`)
}

onMounted(async () => {
  await loadDetail()
})
</script>

<template>
  <section class="task-detail-page">
    <el-skeleton v-if="loading" :rows="10" animated class="detail-loading soft-panel" />

    <section v-else-if="loadError" class="detail-error soft-panel">
      <el-empty description="任务详情加载失败">
        <template #description>
          <p class="error-text">{{ loadError }}</p>
        </template>
        <div class="error-actions">
          <el-button @click="loadDetail">重新加载</el-button>
          <el-button type="primary" @click="handleBack">返回任务列表</el-button>
        </div>
      </el-empty>
    </section>

    <template v-else-if="detail">
      <header class="detail-head">
        <div>
          <p class="detail-eyebrow">Task / Detail</p>
          <h1 class="page-title">任务 #{{ detail.taskId }}</h1>
          <p class="page-subtitle">
            当前页面已补齐任务状态流转、步骤记录、重试记录和关联文档入口，适合直接用于联调排查。
          </p>
        </div>
        <div class="head-actions">
          <el-button @click="loadDetail">刷新详情</el-button>
          <el-button type="primary" @click="handleBack">返回任务列表</el-button>
        </div>
      </header>

      <section class="overview-grid">
        <article class="overview-card soft-panel">
          <span>任务类型</span>
          <strong>{{ detail.taskType }}</strong>
          <p>用于区分文档解析、索引构建等异步任务类别。</p>
        </article>
        <article class="overview-card soft-panel">
          <span>任务状态</span>
          <strong>
            <el-tag :type="taskStatusType(detail.taskStatus)">{{ detail.taskStatus }}</el-tag>
          </strong>
          <p>以后台真实任务状态为准，前端不对状态流转做推断。</p>
        </article>
        <article class="overview-card soft-panel">
          <span>业务 ID</span>
          <strong>{{ detail.bizId ?? '暂无' }}</strong>
          <p>当前版本先只展示业务 ID，不强行推断业务跳转目标。</p>
        </article>
      </section>

      <section class="detail-panel soft-panel">
        <div class="section-head">
          <div>
            <h2>任务详情</h2>
            <p>当前展示任务基础字段、关联对象、重试信息与文档解析状态。</p>
          </div>
        </div>

        <div class="detail-matrix">
          <article class="detail-item">
            <span>任务 ID</span>
            <strong>{{ detail.taskId }}</strong>
          </article>
          <article class="detail-item">
            <span>业务类型</span>
            <strong>{{ detail.bizType || '暂无' }}</strong>
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
            <span>重试次数</span>
            <strong>{{ detail.retryCount ?? 0 }}</strong>
          </article>
          <article class="detail-item">
            <span>文档解析状态</span>
            <strong>{{ detail.documentParseStatus || '暂无' }}</strong>
          </article>
        </div>
      </section>

      <section class="linkage-panel soft-panel">
        <div class="section-head">
          <div>
            <h2>业务关联</h2>
            <p>文档解析任务已直接关联到具体文档，可从这里进入文档详情继续排查。</p>
          </div>
        </div>

        <div class="detail-matrix">
          <article class="detail-item">
            <span>业务 ID</span>
            <strong>{{ detail.bizId ?? '暂无' }}</strong>
          </article>
          <article class="detail-item">
            <span>业务类型</span>
            <strong>{{ detail.bizType || '暂无' }}</strong>
          </article>
          <article class="detail-item">
            <span>关联文档</span>
            <strong>{{ detail.documentName || linkedDocumentId || '当前无法确定' }}</strong>
          </article>
          <article class="detail-item">
            <span>说明</span>
            <strong>
              {{
                linkedDocumentId
                  ? '该任务当前可识别为文档解析类，关联对象为文档 ID。'
                  : '当前仅保守展示业务标识，后续再补强跳转联动。'
              }}
            </strong>
          </article>
        </div>

        <div class="linkage-actions" v-if="linkedDocumentId">
          <el-button type="primary" @click="handleLinkedDocumentDetail">查看关联文档</el-button>
        </div>
      </section>

      <section class="timeline-panel soft-panel">
        <div class="section-head">
          <div>
            <h2>状态流转</h2>
            <p>时间线优先使用任务真实开始时间和结束时间，缺失时再退化为占位信息。</p>
          </div>
        </div>

        <div class="timeline-list">
          <article
            v-for="step in timelineSteps"
            :key="step.code"
            class="timeline-item"
            :class="{
              'is-reached': step.reached,
              'is-current': step.current,
            }"
          >
            <div class="timeline-marker" />
            <div class="timeline-content">
              <strong>{{ step.label }}</strong>
              <p>{{ step.time }}</p>
            </div>
          </article>
        </div>
      </section>

      <section class="detail-panel soft-panel" v-if="detail.steps?.length">
        <div class="section-head">
          <div>
            <h2>执行步骤</h2>
            <p>展示后端记录的任务步骤状态，便于快速判断卡在哪一段。</p>
          </div>
        </div>

        <el-table :data="detail.steps" stripe>
          <el-table-column prop="stepCode" label="步骤编码" min-width="150" />
          <el-table-column prop="stepName" label="步骤名称" min-width="140" />
          <el-table-column prop="stepStatus" label="状态" width="120" />
          <el-table-column label="开始时间" min-width="180">
            <template #default="{ row }">
              {{ formatTime(row.startedAt || '') }}
            </template>
          </el-table-column>
          <el-table-column label="结束时间" min-width="180">
            <template #default="{ row }">
              {{ formatTime(row.finishedAt || '') }}
            </template>
          </el-table-column>
          <el-table-column prop="errorMessage" label="错误信息" min-width="220" />
        </el-table>
      </section>

      <section class="detail-panel soft-panel" v-if="detail.retryRecords?.length">
        <div class="section-head">
          <div>
            <h2>重试记录</h2>
            <p>保留任务重试轨迹，便于和当前状态一起判断是否需要再次重投。</p>
          </div>
        </div>

        <el-table :data="detail.retryRecords" stripe>
          <el-table-column prop="retryNo" label="重试次数" width="100" />
          <el-table-column prop="retryReason" label="重试原因" min-width="180" />
          <el-table-column prop="retryResult" label="结果" width="120" />
          <el-table-column label="时间" min-width="180">
            <template #default="{ row }">
              {{ formatTime(row.createdAt) }}
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section class="error-panel soft-panel">
        <div class="section-head">
          <div>
            <h2>错误信息</h2>
            <p>若任务失败，可在此查看完整错误内容；成功任务通常为空。</p>
          </div>
        </div>
        <pre class="error-block">{{ detail.errorMessage || '当前任务暂无错误信息。' }}</pre>
      </section>
    </template>
  </section>
</template>

<style scoped>
.task-detail-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.detail-loading,
.detail-error,
.detail-panel,
.linkage-panel,
.timeline-panel,
.error-panel {
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
  font-size: 24px;
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

.error-block {
  margin: 0;
  padding: 18px;
  border-radius: 18px;
  background: rgba(255, 250, 242, 0.72);
  color: #5d4736;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: "Consolas", "Courier New", monospace;
  font-size: 13px;
  line-height: 1.6;
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

.linkage-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}

.timeline-list {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 16px;
}

.timeline-item {
  position: relative;
  padding: 18px;
  border-radius: 18px;
  border: 1px solid rgba(113, 82, 45, 0.12);
  background: rgba(255, 250, 242, 0.58);
}

.timeline-item::after {
  content: "";
  position: absolute;
  top: 30px;
  right: -18px;
  width: 20px;
  height: 2px;
  background: rgba(113, 82, 45, 0.14);
}

.timeline-item:last-child::after {
  display: none;
}

.timeline-item.is-reached {
  border-color: rgba(198, 107, 34, 0.28);
  background: rgba(255, 247, 235, 0.9);
}

.timeline-item.is-current {
  box-shadow: 0 18px 30px rgba(141, 69, 16, 0.12);
}

.timeline-marker {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: rgba(113, 82, 45, 0.18);
}

.timeline-item.is-reached .timeline-marker {
  background: #c66b22;
}

.timeline-content strong {
  display: block;
  margin-top: 14px;
  font-size: 18px;
}

.timeline-content p {
  margin: 8px 0 0;
  color: #6d5948;
  font-size: 13px;
}

@media (max-width: 960px) {
  .detail-head,
  .section-head {
    flex-direction: column;
  }

  .head-actions {
    width: 100%;
  }

  .overview-grid,
  .detail-matrix,
  .timeline-list {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .detail-loading,
  .detail-error,
  .detail-panel,
  .linkage-panel,
  .timeline-panel,
  .error-panel {
    padding: 20px;
  }

  .head-actions,
  .error-actions {
    flex-direction: column;
  }

  .linkage-actions {
    justify-content: stretch;
  }
}
</style>
