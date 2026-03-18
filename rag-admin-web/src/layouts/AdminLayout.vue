<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Collection, Connection, DataAnalysis, Histogram, List, SwitchButton } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { resolveErrorMessage } from '@/api/http'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const activeMenu = computed(() => route.path)

const menuItems = [
  { index: '/dashboard', label: '概览', icon: Histogram },
  { index: '/chat', label: '智能问答', icon: ChatDotRound },
  { index: '/knowledge-bases', label: '知识库管理', icon: Collection },
  { index: '/models', label: '模型管理', icon: Connection },
  { index: '/vector-indexes', label: '向量索引', icon: DataAnalysis },
  { index: '/tasks', label: '任务监控', icon: List },
]

async function handleLogout(): Promise<void> {
  try {
    await authStore.logout()
    ElMessage.success('已退出登录')
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error))
  } finally {
    await router.replace('/login')
  }
}
</script>

<template>
  <div class="admin-shell">
    <aside class="side-panel soft-panel">
      <div class="brand-block">
        <div class="brand-sign">RA</div>
        <div>
          <p class="brand-caption">RAG ADMIN</p>
          <h1>知识工坊控制台</h1>
        </div>
      </div>
      <el-menu
        :default-active="activeMenu"
        class="nav-menu"
        router
        background-color="transparent"
        text-color="#6b5746"
        active-text-color="#8d4510"
      >
        <el-menu-item
          v-for="item in menuItems"
          :key="item.index"
          :index="item.index"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
    </aside>

    <div class="content-shell">
      <header class="topbar soft-panel">
        <div>
          <p class="topbar-label">知识工坊</p>
          <strong>{{ route.meta.title || '管理台' }}</strong>
        </div>
        <div class="topbar-actions">
          <div class="user-badge">
            <span class="user-badge-label">当前用户</span>
            <strong>{{ authStore.displayName }}</strong>
          </div>
          <el-button :icon="SwitchButton" text @click="handleLogout">
            退出登录
          </el-button>
        </div>
      </header>

      <main class="main-panel">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin-shell {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 20px;
  min-height: 100vh;
  padding: 20px;
}

.side-panel {
  display: flex;
  flex-direction: column;
  gap: 24px;
  padding: 28px 22px;
}

.brand-block {
  display: flex;
  gap: 16px;
  align-items: center;
}

.brand-sign {
  display: grid;
  place-items: center;
  width: 58px;
  height: 58px;
  border-radius: 18px;
  background: linear-gradient(160deg, #d37829 0%, #8d4510 100%);
  color: #fff6ec;
  font-family: "Noto Serif SC", serif;
  font-size: 24px;
  font-weight: 700;
  box-shadow: 0 18px 30px rgba(141, 69, 16, 0.2);
}

.brand-caption {
  margin: 0 0 6px;
  color: #97765a;
  font-size: 12px;
  letter-spacing: 0.28em;
}

.brand-block h1 {
  margin: 0;
  font-family: "Noto Serif SC", serif;
  font-size: 24px;
}

.nav-menu {
  border-right: none;
}

:deep(.nav-menu .el-menu-item) {
  height: 48px;
  margin-bottom: 8px;
  border-radius: 14px;
}

:deep(.nav-menu .el-menu-item.is-active) {
  background: rgba(198, 107, 34, 0.12);
}

.content-shell {
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-width: 0;
}

.topbar {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  padding: 18px 24px;
}

.topbar-label {
  margin: 0 0 6px;
  color: #8a715e;
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.topbar-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.user-badge {
  min-width: 140px;
  padding: 10px 14px;
  border-radius: 16px;
  background: rgba(255, 250, 242, 0.86);
}

.user-badge-label {
  display: block;
  margin-bottom: 4px;
  color: #9d7a58;
  font-size: 12px;
}

.main-panel {
  min-width: 0;
}

@media (max-width: 960px) {
  .admin-shell {
    grid-template-columns: 1fr;
  }

  .side-panel {
    gap: 18px;
  }
}

@media (max-width: 640px) {
  .admin-shell {
    padding: 14px;
  }

  .topbar {
    flex-direction: column;
    align-items: flex-start;
  }

  .topbar-actions {
    width: 100%;
    justify-content: space-between;
  }
}
</style>
