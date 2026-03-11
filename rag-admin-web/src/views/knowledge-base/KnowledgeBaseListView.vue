<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { KnowledgeBase } from '@/types/knowledge-base'
import { listKnowledgeBases } from '@/api/knowledge-base'
import { resolveErrorMessage } from '@/api/http'

const loading = ref(false)
const list = ref<KnowledgeBase[]>([])
const pagination = reactive({
  pageNo: 1,
  pageSize: 10,
  total: 0,
})

const hasData = computed(() => list.value.length > 0)

function statusType(status: string): 'success' | 'info' | 'warning' {
  if (status === 'ENABLED') {
    return 'success'
  }
  if (status === 'DISABLED') {
    return 'info'
  }
  return 'warning'
}

function modelLabel(name: string | null): string {
  return name || '平台默认模型'
}

async function loadList(): Promise<void> {
  loading.value = true
  try {
    const response = await listKnowledgeBases({
      pageNo: pagination.pageNo,
      pageSize: pagination.pageSize,
    })
    list.value = response.list
    pagination.total = response.total
  } catch (error) {
    list.value = []
    pagination.total = 0
    ElMessage.error(resolveErrorMessage(error))
  } finally {
    loading.value = false
  }
}

async function handleCurrentChange(pageNo: number): Promise<void> {
  pagination.pageNo = pageNo
  await loadList()
}

async function handleSizeChange(pageSize: number): Promise<void> {
  pagination.pageSize = pageSize
  pagination.pageNo = 1
  await loadList()
}

onMounted(async () => {
  await loadList()
})
</script>

<template>
  <section class="kb-page">
    <header class="kb-head">
      <div>
        <h1 class="page-title">知识库管理</h1>
        <p class="page-subtitle">
          首版页面只承载分页列表与基础状态展示，用于验证登录态、接口结构与后台布局联调。
        </p>
      </div>
      <el-button type="primary" @click="loadList">刷新列表</el-button>
    </header>

    <div class="summary-strip">
      <article class="summary-card soft-panel">
        <span>列表总量</span>
        <strong>{{ pagination.total }}</strong>
        <p>与后端分页接口保持同一来源。</p>
      </article>
      <article class="summary-card soft-panel">
        <span>当前页</span>
        <strong>{{ pagination.pageNo }}</strong>
        <p>首版仅保留分页，不提前加入筛选条件。</p>
      </article>
      <article class="summary-card soft-panel">
        <span>默认模型回退</span>
        <strong>已兼容</strong>
        <p>当知识库未绑定模型时，前端展示为平台默认模型。</p>
      </article>
    </div>

    <section class="table-panel soft-panel">
      <el-table :data="list" v-loading="loading" empty-text="暂无知识库数据" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="kbCode" label="知识库编码" min-width="150" />
        <el-table-column prop="kbName" label="知识库名称" min-width="180" />
        <el-table-column label="Embedding 模型" min-width="180">
          <template #default="{ row }">
            {{ modelLabel(row.embeddingModelName) }}
          </template>
        </el-table-column>
        <el-table-column label="聊天模型" min-width="180">
          <template #default="{ row }">
            {{ modelLabel(row.chatModelName) }}
          </template>
        </el-table-column>
        <el-table-column prop="retrieveTopK" label="TopK" width="90" />
        <el-table-column label="重排" width="90">
          <template #default="{ row }">
            <el-tag :type="row.rerankEnabled ? 'warning' : 'info'">
              {{ row.rerankEnabled ? '开启' : '关闭' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="说明" min-width="220">
          <template #default="{ row }">
            {{ row.description || '暂无描述' }}
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
    </section>
  </section>
</template>

<style scoped>
.kb-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.kb-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.summary-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}

.summary-card {
  padding: 20px 22px;
}

.summary-card span {
  color: #9d7a58;
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.summary-card strong {
  display: block;
  margin-top: 12px;
  font-family: "Noto Serif SC", serif;
  font-size: 30px;
}

.summary-card p {
  margin: 12px 0 0;
  color: #6d5948;
}

.table-panel {
  padding: 18px;
}

.table-footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 18px;
}

@media (max-width: 960px) {
  .kb-head {
    flex-direction: column;
  }

  .summary-strip {
    grid-template-columns: 1fr;
  }
}
</style>
