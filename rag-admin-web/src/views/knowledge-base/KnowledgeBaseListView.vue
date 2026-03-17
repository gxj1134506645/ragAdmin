<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { KnowledgeBase } from '@/types/knowledge-base'
import { listKnowledgeBases } from '@/api/knowledge-base'
import { resolveErrorMessage } from '@/api/http'

const route = useRoute()
const router = useRouter()
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

async function handleCreate(): Promise<void> {
  await router.push('/knowledge-bases/create')
}

async function handleEdit(id: number): Promise<void> {
  await router.push(`/knowledge-bases/${id}/edit`)
}

async function handleDetail(id: number): Promise<void> {
  await router.push(`/knowledge-bases/${id}`)
}

async function consumeCreatedFlag(): Promise<void> {
  if (route.query.created !== '1') {
    return
  }
  ElMessage.success('知识库创建成功')
  const query = { ...route.query }
  delete query.created
  await router.replace({
    path: route.path,
    query,
  })
}

async function consumeUpdatedFlag(): Promise<void> {
  if (route.query.updated !== '1') {
    return
  }
  ElMessage.success('知识库更新成功')
  const query = { ...route.query }
  delete query.updated
  await router.replace({
    path: route.path,
    query,
  })
}

onMounted(async () => {
  await consumeCreatedFlag()
  await consumeUpdatedFlag()
  await loadList()
})
</script>

<template>
  <section class="kb-page">
    <header class="kb-head">
      <div>
        <h1 class="page-title">知识库管理</h1>
        <p class="page-subtitle">
          支持知识库列表浏览、创建、编辑和详情查看。
        </p>
      </div>
      <div class="head-actions">
        <el-button @click="loadList">刷新列表</el-button>
        <el-button type="primary" @click="handleCreate">新建知识库</el-button>
      </div>
    </header>

    <section class="table-panel soft-panel">
      <el-table :data="list" v-loading="loading" empty-text="暂无知识库数据" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="kbCode" label="知识库编码" min-width="150" />
        <el-table-column prop="kbName" label="知识库名称" min-width="180" />
        <el-table-column label="向量模型" min-width="180">
          <template #default="{ row }">
            {{ modelLabel(row.embeddingModelName) }}
          </template>
        </el-table-column>
        <el-table-column label="聊天模型" min-width="180">
          <template #default="{ row }">
            {{ modelLabel(row.chatModelName) }}
          </template>
        </el-table-column>
        <el-table-column prop="retrieveTopK" label="检索数量" width="100" />
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
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link @click="handleDetail(row.id)">详情</el-button>
            <el-button link type="primary" @click="handleEdit(row.id)">编辑</el-button>
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

.head-actions {
  display: flex;
  gap: 12px;
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

  .head-actions {
    width: 100%;
  }

}

@media (max-width: 640px) {
  .head-actions {
    flex-direction: column;
  }
}
</style>
