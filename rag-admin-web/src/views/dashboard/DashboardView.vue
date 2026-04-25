<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Collection, Connection, DataAnalysis, List, Plus, Tickets } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { hasPermission } from '@/utils/permission'
import { resolveErrorMessage } from '@/api/http'
import { getSystemHealth } from '@/api/system'
import { listModelCallStatistics } from '@/api/statistics'
import { getTaskSummary } from '@/api/task'
import type { DependencyHealth, HealthCheckResponse } from '@/types/system'
import type { ModelCallStatistics } from '@/types/statistics'
import type { TaskSummary } from '@/types/task'

const router = useRouter()
const authStore = useAuthStore()

// ── Permission guards ──
const canViewTasks = computed(() => hasPermission(authStore.currentUser, 'TASK_VIEW'))
const canViewStatistics = computed(() => hasPermission(authStore.currentUser, 'STATISTICS_VIEW'))

// ── System Health ──
const healthLoading = ref(false)
const healthError = ref('')
const health = ref<HealthCheckResponse | null>(null)

// ── Task Summary ──
const taskLoading = ref(false)
const taskError = ref('')
const taskSummary = ref<TaskSummary | null>(null)

// ── Model Call Statistics ──
const modelLoading = ref(false)
const modelError = ref('')
const modelStats = ref<ModelCallStatistics[]>([])

const lastRefreshed = ref('')

// ── Dependency metadata ──
const DEPENDENCY_KEYS: { key: keyof HealthCheckResponse; label: string }[] = [
  { key: 'postgres', label: 'PostgreSQL' },
  { key: 'redis', label: 'Redis' },
  { key: 'minio', label: 'MinIO' },
  { key: 'bailian', label: '百炼' },
  { key: 'ollama', label: 'Ollama' },
  { key: 'milvus', label: 'Milvus' },
  { key: 'tavily', label: 'Tavily' },
  { key: 'mineru', label: 'MinerU' },
  { key: 'elasticsearch', label: 'Elasticsearch' },
]

// ── Quick navigation entries ──
const quickEntries = [
  { key: 'knowledge-bases', eyebrow: '知识库', title: '知识库管理', path: '/knowledge-bases', icon: Collection, permission: 'KB_MANAGE' },
  { key: 'kb-create', eyebrow: '创建', title: '新建知识库', path: '/knowledge-bases/create', icon: Plus, permission: 'KB_MANAGE' },
  { key: 'models', eyebrow: '模型', title: '模型管理', path: '/models', icon: Connection, permission: 'MODEL_MANAGE' },
  { key: 'tasks', eyebrow: '任务', title: '任务监控', path: '/tasks', icon: List, permission: 'TASK_VIEW' },
  { key: 'vector-indexes', eyebrow: '统计', title: '向量索引', path: '/vector-indexes', icon: DataAnalysis, permission: 'STATISTICS_VIEW' },
  { key: 'audit-logs', eyebrow: '治理', title: '审计日志', path: '/audit-logs', icon: Tickets, permission: 'AUDIT_VIEW' },
]

const visibleEntries = computed(() =>
  quickEntries.filter(e => hasPermission(authStore.currentUser, e.permission)),
)

// ── Computed: flat health dependency list ──
const healthDeps = computed(() => {
  if (!health.value) return []
  const h = health.value as Record<string, DependencyHealth>
  return DEPENDENCY_KEYS.map(d => {
    const dep = h[d.key]
    return {
      key: d.key,
      label: d.label,
      status: dep?.status ?? 'UNKNOWN',
      message: dep?.message ?? '未检查',
    }
  })
})

// ── Helpers ──
function tagType(status: string): 'success' | 'danger' | 'warning' | 'info' {
  if (status === 'UP') return 'success'
  if (status === 'DOWN') return 'danger'
  if (status === 'DEGRADED') return 'warning'
  return 'info'
}

function statusLabel(status: string): string {
  if (status === 'UP') return '正常'
  if (status === 'DOWN') return '异常'
  if (status === 'DEGRADED') return '降级'
  return '未知'
}

function dotClass(status: string): string {
  if (status === 'UP') return 'is-up'
  if (status === 'DOWN') return 'is-down'
  if (status === 'DEGRADED') return 'is-degraded'
  return 'is-unknown'
}

function fmtNum(n: number): string {
  if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + 'M'
  if (n >= 1_000) return (n / 1_000).toFixed(1) + 'K'
  return String(n)
}

function fmtMs(ms: number): string {
  return ms >= 1000 ? (ms / 1000).toFixed(1) + 's' : ms + 'ms'
}

// ── Data loaders ──
async function loadHealth(): Promise<void> {
  healthLoading.value = true
  healthError.value = ''
  try {
    health.value = await getSystemHealth()
  } catch (e) {
    health.value = null
    healthError.value = resolveErrorMessage(e)
  } finally {
    healthLoading.value = false
  }
}

async function loadTaskSummary(): Promise<void> {
  if (!canViewTasks.value) return
  taskLoading.value = true
  taskError.value = ''
  try {
    taskSummary.value = await getTaskSummary({})
  } catch (e) {
    taskSummary.value = null
    taskError.value = resolveErrorMessage(e)
  } finally {
    taskLoading.value = false
  }
}

async function loadModelStats(): Promise<void> {
  if (!canViewStatistics.value) return
  modelLoading.value = true
  modelError.value = ''
  try {
    modelStats.value = await listModelCallStatistics()
  } catch (e) {
    modelStats.value = []
    modelError.value = resolveErrorMessage(e)
  } finally {
    modelLoading.value = false
  }
}

function updateTimestamp(): void {
  lastRefreshed.value = new Date().toLocaleString('zh-CN', { hour12: false })
}

async function loadAll(): Promise<void> {
  await Promise.allSettled([loadHealth(), loadTaskSummary(), loadModelStats()])
  updateTimestamp()
}

async function goTo(path: string): Promise<void> {
  await router.push(path)
}

onMounted(loadAll)
</script>

<template>
  <section class="dashboard-page">

    <!-- HEADER -->
    <header class="dashboard-head soft-panel">
      <div class="head-left">
        <h1 class="page-title">概览</h1>
        <span v-if="lastRefreshed" class="head-timestamp">更新于 {{ lastRefreshed }}</span>
      </div>
      <el-button size="small" :loading="healthLoading" @click="loadAll">刷新</el-button>
    </header>

    <!-- SYSTEM HEALTH -->
    <section class="health-section soft-panel">
      <div class="section-header">
        <h2 class="section-title">系统健康</h2>
        <el-tag
          v-if="health"
          :type="tagType(health.status)"
          size="large"
          effect="dark"
        >
          {{ statusLabel(health.status) }}
        </el-tag>
      </div>

      <p v-if="healthError" class="error-text">{{ healthError }}</p>

      <div v-else class="health-grid" v-loading="healthLoading">
        <div v-for="dep in healthDeps" :key="dep.key" class="health-tile">
          <div class="health-tile-head">
            <span class="health-dot" :class="dotClass(dep.status)" />
            <span class="health-tile-label">{{ dep.label }}</span>
          </div>
          <el-tag size="small" :type="tagType(dep.status)">
            {{ statusLabel(dep.status) }}
          </el-tag>
        </div>
      </div>
    </section>

    <!-- TASKS + QUICK NAV -->
    <section class="middle-grid">

      <!-- Task Summary -->
      <section class="task-panel soft-panel">
        <h2 class="section-title">任务概览</h2>

        <p v-if="taskError" class="error-text">{{ taskError }}</p>

        <template v-else-if="canViewTasks">
          <div class="task-body" v-loading="taskLoading">
            <div class="task-row task-total">
              <span class="task-label">总计</span>
              <strong class="task-value">{{ taskSummary?.total ?? '-' }}</strong>
            </div>
            <div class="task-row">
              <el-tag type="warning" size="small">运行中</el-tag>
              <strong>{{ taskSummary?.running ?? '-' }}</strong>
            </div>
            <div class="task-row">
              <el-tag size="small">等待中</el-tag>
              <strong>{{ taskSummary?.waiting ?? '-' }}</strong>
            </div>
            <div class="task-row">
              <el-tag type="success" size="small">成功</el-tag>
              <strong>{{ taskSummary?.success ?? '-' }}</strong>
            </div>
            <div class="task-row">
              <el-tag type="danger" size="small">失败</el-tag>
              <strong>{{ taskSummary?.failed ?? '-' }}</strong>
            </div>
            <div class="task-row">
              <el-tag type="info" size="small">已取消</el-tag>
              <strong>{{ taskSummary?.canceled ?? '-' }}</strong>
            </div>
          </div>
          <div v-if="canViewTasks" class="panel-footer">
            <el-button link type="primary" @click="goTo('/tasks')">查看全部任务</el-button>
          </div>
        </template>
        <p v-else class="muted-text">无任务查看权限</p>
      </section>

      <!-- Quick Navigation -->
      <section class="nav-panel soft-panel">
        <h2 class="section-title">快捷入口</h2>
        <div class="nav-grid">
          <button
            v-for="entry in visibleEntries"
            :key="entry.key"
            type="button"
            class="nav-item"
            @click="goTo(entry.path)"
          >
            <div class="nav-icon">
              <el-icon :size="20"><component :is="entry.icon" /></el-icon>
            </div>
            <div class="nav-text">
              <span class="nav-eyebrow">{{ entry.eyebrow }}</span>
              <strong>{{ entry.title }}</strong>
            </div>
          </button>
        </div>
      </section>
    </section>

    <!-- MODEL CALL STATISTICS -->
    <section v-if="canViewStatistics" class="model-section soft-panel">
      <div class="section-header">
        <h2 class="section-title">模型调用统计</h2>
      </div>

      <p v-if="modelError" class="error-text">{{ modelError }}</p>

      <el-table
        v-else
        :data="modelStats"
        v-loading="modelLoading"
        empty-text="暂无模型调用数据"
        stripe
      >
        <el-table-column label="模型" min-width="200">
          <template #default="{ row }">
            <div class="model-cell">
              <strong>{{ row.modelName }}</strong>
              <small>{{ row.modelCode }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="调用次数" width="120" sortable>
          <template #default="{ row }">
            {{ row.callCount?.toLocaleString() ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column label="Prompt Tokens" width="150">
          <template #default="{ row }">
            {{ fmtNum(row.totalPromptTokens) }}
          </template>
        </el-table-column>
        <el-table-column label="Completion Tokens" width="160">
          <template #default="{ row }">
            {{ fmtNum(row.totalCompletionTokens) }}
          </template>
        </el-table-column>
        <el-table-column label="平均延迟" width="120" sortable>
          <template #default="{ row }">
            {{ row.averageLatencyMs ? fmtMs(row.averageLatencyMs) : '-' }}
          </template>
        </el-table-column>
      </el-table>
    </section>

  </section>
</template>

<style scoped>
.dashboard-page {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

/* Header */
.dashboard-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  background:
    radial-gradient(circle at top right, rgba(194, 65, 12, 0.06), transparent 34%),
    var(--ember-surface);
}

.head-left {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.head-timestamp {
  color: var(--ember-neutral);
  font-size: 12px;
}

/* Shared */
.section-title {
  margin: 0 0 14px;
  font-family: var(--ember-font-body);
  font-size: 15px;
  font-weight: 600;
  color: var(--ember-text-primary);
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.error-text {
  margin: 0;
  color: var(--ember-text-secondary);
  font-size: 13px;
}

.muted-text {
  color: var(--ember-neutral);
  font-size: 13px;
}

/* Health Grid */
.health-section {
  padding: 16px 20px;
}

.health-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 12px;
}

.health-tile {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 12px;
  border: 1px solid var(--ember-border);
  border-radius: var(--ember-radius-md);
  background: var(--ember-background);
}

.health-tile-head {
  display: flex;
  align-items: center;
  gap: 6px;
}

.health-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--ember-neutral);
  flex-shrink: 0;
}

.health-dot.is-up { background: var(--ember-success); }
.health-dot.is-down { background: var(--ember-error); }
.health-dot.is-degraded { background: var(--ember-warning); }
.health-dot.is-unknown { background: var(--ember-neutral); }

.health-tile-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--ember-text-secondary);
}

/* Middle Grid: Tasks + Nav */
.middle-grid {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: 22px;
}

.task-panel {
  grid-column: span 5;
  padding: 16px 20px;
}

.task-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 160px;
}

.task-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 4px 0;
}

.task-row strong {
  font-size: 16px;
}

.task-total {
  padding-bottom: 8px;
  margin-bottom: 4px;
  border-bottom: 1px solid var(--ember-border);
}

.task-total .task-label {
  font-weight: 600;
  font-size: 14px;
}

.task-total .task-value {
  font-size: 22px;
  color: var(--ember-primary);
}

.panel-footer {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid var(--ember-border);
}

.nav-panel {
  grid-column: span 7;
  padding: 16px 20px;
}

.nav-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 12px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--ember-border);
  border-radius: var(--ember-radius-md);
  background: var(--ember-background);
  cursor: pointer;
  text-align: left;
  transition: border-color 150ms ease, box-shadow 150ms ease;
}

.nav-item:hover {
  border-color: var(--ember-primary);
  box-shadow: var(--ember-shadow-md);
}

.nav-icon {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: var(--ember-radius-md);
  background: var(--ember-primary-light);
  color: var(--ember-primary);
  flex-shrink: 0;
}

.nav-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nav-eyebrow {
  font-size: 11px;
  color: var(--ember-neutral);
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.nav-text strong {
  font-size: 13px;
  font-weight: 600;
}

/* Model Stats */
.model-section {
  padding: 16px 20px;
}

.model-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.model-cell small {
  color: var(--ember-text-secondary);
}

/* Responsive */
@media (max-width: 960px) {
  .middle-grid {
    grid-template-columns: 1fr;
  }
  .task-panel,
  .nav-panel {
    grid-column: auto;
  }
  .health-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 640px) {
  .dashboard-head,
  .health-section,
  .task-panel,
  .nav-panel,
  .model-section {
    padding: 12px 14px;
  }
  .health-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .nav-grid {
    grid-template-columns: 1fr;
  }
}
</style>
