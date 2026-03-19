<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChatDotRound, Collection, RefreshRight, SwitchButton } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { listKnowledgeBases } from '@/api/knowledge-base'
import { resolveErrorMessage } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import type { KnowledgeBaseSummary } from '@/types/knowledge-base'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const loading = ref(false)
const keyword = ref('')
const loadError = ref('')
const knowledgeBases = ref<KnowledgeBaseSummary[]>([])

const activeKbId = computed<number | null>(() => {
  const raw = route.params.kbId
  if (typeof raw !== 'string') {
    return null
  }
  const parsed = Number(raw)
  return Number.isFinite(parsed) ? parsed : null
})

const filteredKnowledgeBases = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  if (!query) {
    return knowledgeBases.value
  }
  return knowledgeBases.value.filter((item) => {
    return item.kbName.toLowerCase().includes(query) || item.kbCode.toLowerCase().includes(query)
  })
})

async function loadPortalData(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const response = await listKnowledgeBases({ pageNo: 1, pageSize: 200 })
    knowledgeBases.value = response.list
  } catch (error) {
    loadError.value = resolveErrorMessage(error)
  } finally {
    loading.value = false
  }
}

async function handleLogout(): Promise<void> {
  try {
    await authStore.logout()
    await router.push('/login')
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error))
  }
}

function openGeneralChat(): void {
  void router.push('/chat')
}

function openKnowledgeBaseChat(kbId: number): void {
  void router.push(`/knowledge-bases/${kbId}/chat`)
}

onMounted(async () => {
  await loadPortalData()
})
</script>

<template>
  <div class="chat-layout-shell">
    <aside class="chat-sidebar app-shell-panel">
      <section class="brand-card">
        <p class="brand-kicker">ragAdmin Chat</p>
        <h1 class="serif-title">组织知识问答入口</h1>
        <p>
          与模型直接对话，或把多个知识库临时拉入本轮检索范围。管理后台维护模型与知识库，这里只负责面向用户的问答体验。
        </p>
      </section>

      <section class="sidebar-section">
        <button
          type="button"
          class="nav-entry"
          :class="{ 'is-active': route.name === 'app-chat-home' }"
          @click="openGeneralChat"
        >
          <el-icon><ChatDotRound /></el-icon>
          <div>
            <strong>通用问答</strong>
            <span>不绑定知识库，按需临时勾选</span>
          </div>
        </button>
      </section>

      <section class="sidebar-section">
        <div class="section-head">
          <div>
            <p class="section-kicker">知识库</p>
            <h2>进入知识库内问答</h2>
          </div>
          <el-button text :icon="RefreshRight" @click="loadPortalData">刷新</el-button>
        </div>
        <el-input v-model="keyword" placeholder="搜索知识库编码或名称" clearable />
        <div class="kb-list thin-scrollbar">
          <div v-if="loading" class="section-placeholder">正在加载知识库列表...</div>
          <div v-else-if="loadError" class="section-placeholder is-error">
            <span>{{ loadError }}</span>
          </div>
          <button
            v-for="knowledgeBase in filteredKnowledgeBases"
            :key="knowledgeBase.id"
            type="button"
            class="kb-entry"
            :class="{ 'is-active': activeKbId === knowledgeBase.id }"
            @click="openKnowledgeBaseChat(knowledgeBase.id)"
          >
            <div class="kb-entry-main">
              <strong>{{ knowledgeBase.kbName }}</strong>
              <span>{{ knowledgeBase.kbCode }}</span>
            </div>
            <small>{{ knowledgeBase.chatModelName || '默认模型' }}</small>
          </button>
          <div v-if="!loading && !loadError && filteredKnowledgeBases.length === 0" class="section-placeholder">
            当前没有可见知识库
          </div>
        </div>
      </section>

      <section class="sidebar-footer">
        <div class="user-card">
          <div class="user-meta">
            <p>{{ authStore.displayName }}</p>
            <span>{{ authStore.currentUser?.username || 'anonymous' }}</span>
          </div>
          <div class="user-actions">
            <el-button text :icon="Collection" @click="openGeneralChat">首页</el-button>
            <el-button text type="danger" :icon="SwitchButton" @click="handleLogout">退出</el-button>
          </div>
        </div>
      </section>
    </aside>

    <main class="chat-main">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.chat-layout-shell {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: var(--layout-gap);
  min-height: 100vh;
  padding: 20px;
}

.chat-sidebar {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 20px;
  border-radius: var(--radius-xl);
}

.brand-card {
  padding: 18px;
  border-radius: 22px;
  background:
    linear-gradient(150deg, rgba(255, 248, 240, 0.94), rgba(246, 235, 220, 0.82)),
    rgba(255, 255, 255, 0.4);
  border: 1px solid rgba(122, 89, 53, 0.12);
}

.brand-kicker,
.section-kicker {
  margin: 0 0 8px;
  color: var(--text-muted);
  font-size: 12px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.brand-card h1 {
  margin: 0;
  font-size: 30px;
  line-height: 1.25;
}

.brand-card p:last-child {
  margin: 14px 0 0;
  color: var(--text-secondary);
  line-height: 1.8;
}

.sidebar-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.section-head h2 {
  margin: 0;
  font-size: 18px;
}

.nav-entry,
.kb-entry {
  width: 100%;
  border: 1px solid rgba(122, 89, 53, 0.1);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.58);
  color: inherit;
  cursor: pointer;
  transition:
    transform 180ms ease,
    border-color 180ms ease,
    box-shadow 180ms ease;
}

.nav-entry:hover,
.kb-entry:hover,
.nav-entry.is-active,
.kb-entry.is-active {
  transform: translateY(-1px);
  border-color: rgba(157, 91, 47, 0.26);
  box-shadow: 0 14px 28px rgba(91, 58, 24, 0.08);
}

.nav-entry {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 18px;
  text-align: left;
}

.nav-entry strong,
.nav-entry span,
.kb-entry strong,
.kb-entry span,
.kb-entry small {
  display: block;
}

.nav-entry strong,
.kb-entry strong {
  font-size: 15px;
}

.nav-entry span,
.kb-entry span,
.kb-entry small {
  margin-top: 6px;
  color: var(--text-muted);
  font-size: 12px;
}

.kb-list {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 10px;
  min-height: 220px;
  max-height: 50vh;
  overflow-y: auto;
  padding-right: 4px;
}

.kb-entry {
  padding: 14px 16px;
  text-align: left;
}

.kb-entry-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.section-placeholder {
  display: grid;
  place-items: center;
  min-height: 120px;
  padding: 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.42);
  color: var(--text-muted);
  text-align: center;
}

.section-placeholder.is-error {
  background: rgba(255, 247, 244, 0.85);
  color: #b04d35;
}

.sidebar-footer {
  margin-top: auto;
}

.user-card {
  padding: 16px;
  border-radius: 20px;
  background: rgba(255, 249, 243, 0.84);
  border: 1px solid rgba(122, 89, 53, 0.1);
}

.user-meta p,
.user-meta span {
  margin: 0;
}

.user-meta span {
  display: block;
  margin-top: 6px;
  color: var(--text-muted);
  font-size: 13px;
}

.user-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
}

.chat-main {
  min-width: 0;
}

@media (max-width: 1100px) {
  .chat-layout-shell {
    grid-template-columns: 1fr;
  }

  .kb-list {
    max-height: 280px;
  }
}
</style>
