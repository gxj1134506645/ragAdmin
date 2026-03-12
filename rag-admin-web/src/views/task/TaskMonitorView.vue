<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElButton, ElEmpty, ElMessage, ElMessageBox } from 'element-plus'
import { listTasks, retryTask } from '@/api/task'
import { resolveErrorMessage } from '@/api/http'
import type { TaskRecord } from '@/types/task'

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

const hasData = computed(() => tasks.value.length > 0)

const taskStatusOptions = [
  { label: '全部状态', value: '' },
  { label: '等待中', value: 'WAITING' },
  { label: '运行中', value: 'RUNNING' },
  { label: '成功', value: 'SUCCESS' },
  { label: '失败', value: 'FAILED' },
  { label: '已取消', value: 'CANCELED' },
]

const taskTypeOptions = [
  { label: '全部类型', value: '' },
  { label: '文档解析', value: 'DOCUMENT_PARSE' },
  { label: '索引构建', value: 'INDEX_BUILD' },
]

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
    const response = await listTasks({
      pageNo: pagination.pageNo,
      pageSize: pagination.pageSize,
      ...normalizeQuery(),
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

onMounted(async () => {
  await loadTasks()
})
</script>

<template>
  <section class="task-page">
    <header class="task-head">
      <div>
        <h1 class="page-title">任务监控</h1>
        <p class="page-subtitle">
          用于观察文档解析等异步任务的当前状态，本轮先提供基础筛选、分页和错误摘要。
        </p>
      </div>
      <el-button @click="handleRefresh">刷新任务</el-button>
    </header>

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
            v-for="item in taskTypeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>

        <el-input v-model="query.bizId" placeholder="请输入业务 ID" clearable />
      </div>

      <div class="filter-actions">
        <el-button @click="handleReset">重置</el-button>
        <el-button type="primary" @click="handleSearch">查询任务</el-button>
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
          <el-table-column prop="taskId" label="任务 ID" width="100" />
          <el-table-column prop="taskType" label="任务类型" min-width="160" />
          <el-table-column label="任务状态" width="120">
            <template #default="{ row }">
              <el-tag :type="taskStatusType(row.taskStatus)">{{ row.taskStatus }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="bizId" label="业务 ID" width="120" />
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
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
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
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.filter-panel,
.table-panel {
  padding: 20px;
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

.table-error {
  padding: 8px 0;
}

.error-text {
  margin: 0;
  color: #6d5948;
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
    padding: 18px;
  }

  .filter-actions {
    flex-direction: column;
  }
}
</style>
