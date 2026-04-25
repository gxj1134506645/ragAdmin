<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listPromptTemplates } from '@/api/prompt-template'
import { resolveErrorMessage } from '@/api/http'
import type { PromptTemplateRecord } from '@/types/prompt-template'

const router = useRouter()
const loading = ref(false)
const loadError = ref('')
const rows = ref<PromptTemplateRecord[]>([])
const pagination = reactive({ pageNo: 1, pageSize: 20, total: 0 })

const query = reactive({
  templateCode: '',
  capabilityType: '',
  status: '',
})

function normalizeQuery() {
  return {
    templateCode: query.templateCode || undefined,
    capabilityType: query.capabilityType || undefined,
    status: query.status || undefined,
  }
}

async function loadData() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await listPromptTemplates({
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
  query.templateCode = ''
  query.capabilityType = ''
  query.status = ''
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

function handleRowClick(row: PromptTemplateRecord) {
  router.push(`/prompt-templates/${row.id}`)
}

function capabilityTagType(type: string): '' | 'success' | 'warning' | 'info' {
  if (type === 'CHAT') return ''
  if (type === 'RETRIEVAL') return 'success'
  return 'info'
}

function capabilityLabel(type: string): string {
  if (type === 'CHAT') return '对话'
  if (type === 'RETRIEVAL') return '检索'
  return type
}

function statusTagType(status: string): 'success' | 'warning' | 'info' {
  if (status === 'ENABLED') return 'success'
  if (status === 'DISABLED') return 'warning'
  return 'info'
}

function statusLabel(status: string): string {
  if (status === 'ENABLED') return '已启用'
  if (status === 'DISABLED') return '已禁用'
  return status
}

function formatTime(value: string): string {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

onMounted(loadData)
</script>

<template>
  <section class="pt-list-page">
    <section class="filter-panel soft-panel">
      <div class="filter-row">
        <div class="filter-grid">
          <el-input v-model="query.templateCode" placeholder="模板编码" clearable />
          <el-select v-model="query.capabilityType" placeholder="能力类型" clearable>
            <el-option label="对话" value="CHAT" />
            <el-option label="检索" value="RETRIEVAL" />
          </el-select>
          <el-select v-model="query.status" placeholder="状态" clearable>
            <el-option label="已启用" value="ENABLED" />
            <el-option label="已禁用" value="DISABLED" />
          </el-select>
        </div>
        <div class="filter-actions">
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleRefresh">刷新</el-button>
        </div>
      </div>
    </section>

    <section class="table-panel soft-panel">
      <el-empty v-if="loadError && !loading" :description="loadError">
        <el-button type="primary" @click="handleRefresh">重试</el-button>
      </el-empty>

      <template v-else>
        <el-table :data="rows" v-loading="loading" stripe empty-text="暂无模板数据" @row-click="handleRowClick" style="cursor: pointer">
          <el-table-column prop="templateCode" label="模板编码" min-width="200" />
          <el-table-column prop="templateName" label="模板名称" min-width="180" />
          <el-table-column label="类型" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="capabilityTagType(row.capabilityType)">
                {{ capabilityLabel(row.capabilityType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="versionNo" label="版本" width="80" align="center" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="statusTagType(row.status)">
                {{ statusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="更新时间" width="170">
            <template #default="{ row }">
              {{ formatTime(row.updatedAt) }}
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
.pt-list-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

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
  grid-template-columns: 200px 140px 140px;
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
