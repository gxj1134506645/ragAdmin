<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElButton, ElEmpty, ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { subscribeTaskEvents, type RealtimeStreamHandle } from '@/api/realtime'
import { listTasks, retryTask } from '@/api/task'
import { resolveErrorMessage } from '@/api/http'
import type { TaskRealtimeEvent } from '@/types/realtime'
import type { TaskRecord } from '@/types/task'
import { buildTaskProgressState } from '@/utils/task-progress'
import { TASK_STATUS_OPTIONS, TASK_TYPE_OPTIONS, formatTaskStatus, formatTaskType } from '@/utils/task'

const router = useRouter()
const loading = ref(false)
const loadError = ref('')
const tasks = ref<TaskRecord[]>([])
const retryingTaskIds = ref<number[]>([])
const pagination = reactive({
  pageNo: 1,
  pageSize: 10,
  total: 0,
})
const query = reactive({
  taskType: '',
  taskStatus: '',
  bizId: '',
})
const realtimeEvents = reactive<Record<number, TaskRealtimeEvent>>({})
let taskRealtimeStream: RealtimeStreamHandle | null = null
let taskRealtimeRefreshTimer: ReturnType<typeof window.setTimeout> | null = null

const hasData = computed(() => tasks.value.length > 0)

const taskStatusOptions = [{ label: '全部状态', value: '' }, ...TASK_STATUS_OPTIONS]

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

function canRetry(status: string): boolean {
  return status === 'FAILED' || status === 'CANCELED'
}

function isRetrying(taskId: number): boolean {
  return retryingTaskIds.value.includes(taskId)
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

function clearTaskRealtimeRefreshTimer(): void {
  if (taskRealtimeRefreshTimer !== null) {
    window.clearTimeout(taskRealtimeRefreshTimer)
    taskRealtimeRefreshTimer = null
  }
}

function closeTaskRealtimeStream(): void {
  taskRealtimeStream?.close()
  taskRealtimeStream = null
}

function taskProgressState(task: TaskRecord) {
  return buildTaskProgressState(task.taskStatus, realtimeEvents[task.taskId])
}

function scheduleTaskRealtimeRefresh(): void {
  clearTaskRealtimeRefreshTimer()
  taskRealtimeRefreshTimer = window.setTimeout(async () => {
    taskRealtimeRefreshTimer = null
    await loadTasks()
  }, 300)
}

function handleTaskRealtimeEvent(event: TaskRealtimeEvent): void {
  if (event.eventType === 'CONNECTED' || !event.taskId) {
    return
  }
  realtimeEvents[event.taskId] = event
  const matchedTask = tasks.value.find((item) => item.taskId === event.taskId)
  if (matchedTask) {
    matchedTask.taskStatus = event.taskStatus || matchedTask.taskStatus
    matchedTask.errorMessage = event.taskStatus === 'FAILED' ? (event.message || matchedTask.errorMessage) : matchedTask.errorMessage
    matchedTask.updatedAt = event.occurredAt || matchedTask.updatedAt
  }
  if (event.terminal) {
    scheduleTaskRealtimeRefresh()
  }
}

function connectTaskRealtimeStream(): void {
  closeTaskRealtimeStream()
  taskRealtimeStream = subscribeTaskEvents({
    onEvent: handleTaskRealtimeEvent,
    onError(error) {
      console.error('任务实时订阅失败', error)
    },
  })
}

function normalizeQuery(): {
  taskType?: string
  taskStatus?: string
  bizId?: string
} {
  return {
    taskType: query.taskType || undefined,
    taskStatus: query.taskStatus || undefined,
    bizId: query.bizId.trim() || undefined,
  }
}

async function loadTasks(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const normalizedQuery = normalizeQuery()
    const response = await listTasks({
      pageNo: pagination.pageNo,
      pageSize: pagination.pageSize,
      ...normalizedQuery,
    })
    tasks.value = response.list
    pagination.total = response.total
  } catch (error) {
    tasks.value = []
    pagination.total = 0
    loadError.value = resolveErrorMessage(error)
  } finally {
    loading.value = false
  }
}

async function handleSearch(): Promise<void> {
  pagination.pageNo = 1
  await loadTasks()
}

async function handleRefresh(): Promise<void> {
  await loadTasks()
}

async function handleReset(): Promise<void> {
  query.taskType = ''
  query.taskStatus = ''
  query.bizId = ''
  pagination.pageNo = 1
  await loadTasks()
}

async function handleCurrentChange(pageNo: number): Promise<void> {
  pagination.pageNo = pageNo
  await loadTasks()
}

async function handleSizeChange(pageSize: number): Promise<void> {
  pagination.pageSize = pageSize
  pagination.pageNo = 1
  await loadTasks()
}

async function handleRetry(task: TaskRecord): Promise<void> {
  if (!canRetry(task.taskStatus) || isRetrying(task.taskId)) {
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定要重新投递任务 #${task.taskId} 吗？`,
      '确认重试',
      {
        confirmButtonText: '确认重试',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  retryingTaskIds.value = [...retryingTaskIds.value, task.taskId]

  try {
    await retryTask(task.taskId)
    ElMessage.success(`任务 #${task.taskId} 已重新投递`)
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error))
  } finally {
    retryingTaskIds.value = retryingTaskIds.value.filter((id) => id !== task.taskId)
    await loadTasks()
  }
}

async function handleDetail(taskId: number): Promise<void> {
  await router.push(`/tasks/${taskId}`)
}

onMounted(async () => {
  await loadTasks()
  connectTaskRealtimeStream()
})

onUnmounted(() => {
  clearTaskRealtimeRefreshTimer()
  closeTaskRealtimeStream()
})
</script>

<template>
  <section class="task-page">
    <section class="filter-panel soft-panel">
      <div class="filter-grid">
        <el-select v-model="query.taskStatus" placeholder="任务状态">
          <el-option
            v-for="item in taskStatusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>

        <el-select v-model="query.taskType" placeholder="任务类型">
          <el-option
            v-for="item in TASK_TYPE_OPTIONS"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>

        <el-input v-model="query.bizId" placeholder="请输入业务编号" clearable />
      </div>

      <div class="filter-actions">
        <el-button @click="handleReset">重置</el-button>
        <el-button type="primary" @click="handleSearch">查询任务</el-button>
        <el-button @click="handleRefresh">刷新任务</el-button>
      </div>
    </section>

    <section class="table-panel soft-panel">
      <section v-if="loadError" class="table-error">
        <el-empty description="任务列表加载失败">
          <template #description>
            <p class="error-text">{{ loadError }}</p>
          </template>
          <el-button type="primary" @click="handleRefresh">重新加载</el-button>
        </el-empty>
      </section>

      <template v-else>
        <el-table :data="tasks" v-loading="loading" empty-text="当前暂无任务数据" stripe>
          <el-table-column prop="taskId" label="任务编号" width="100" />
          <el-table-column label="任务类型" min-width="160">
            <template #default="{ row }">
              {{ formatTaskType(row.taskType) }}
            </template>
          </el-table-column>
          <el-table-column label="任务状态" width="120">
            <template #default="{ row }">
              <el-tag :type="taskStatusType(row.taskStatus)">{{ formatTaskStatus(row.taskStatus) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="执行进度" min-width="220">
            <template #default="{ row }">
              <div class="task-progress-cell">
                <el-progress
                  :percentage="taskProgressState(row).percent"
                  :status="taskProgressState(row).status"
                  :stroke-width="8"
                />
                <small class="task-progress-text">
                  {{ taskProgressState(row).stageLabel }} · {{ taskProgressState(row).message }}
                </small>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="文档名称" min-width="220">
            <template #default="{ row }">
              {{ row.documentName || '暂无' }}
            </template>
          </el-table-column>
          <el-table-column prop="bizId" label="业务编号" width="120" />
          <el-table-column label="重试次数" width="100">
            <template #default="{ row }">
              {{ row.retryCount ?? 0 }}
            </template>
          </el-table-column>
          <el-table-column label="错误摘要" min-width="260">
            <template #default="{ row }">
              {{ row.errorMessage || '无' }}
            </template>
          </el-table-column>
          <el-table-column label="创建时间" min-width="180">
            <template #default="{ row }">
              {{ formatTime(row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column label="更新时间" min-width="180">
            <template #default="{ row }">
              {{ formatTime(row.updatedAt) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button link @click="handleDetail(row.taskId)">详情</el-button>
              <el-button
                v-if="canRetry(row.taskStatus)"
                link
                type="primary"
                :loading="isRetrying(row.taskId)"
                @click="handleRetry(row)"
              >
                重试
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="table-footer" v-if="hasData || pagination.total > 0">
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
      </template>
    </section>
  </section>
</template>

<style scoped>
.task-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.task-head {
  display: flex;
  justify-content: flex-end;
  gap: 16px;
  align-items: center;
}

.filter-panel,
.table-panel {
  padding: 16px;
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
  margin-top: 18px;
}

.task-progress-cell {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.task-progress-text {
  color: var(--ember-text-secondary);
  line-height: 1.5;
}

.table-error {
  padding: 8px 0;
}

.error-text {
  margin: 0;
  color: var(--ember-text-secondary);
}

.table-footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 18px;
}

@media (max-width: 960px) {
  .task-head {
    flex-direction: column;
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
