<script setup lang="ts">
import { useRouter } from 'vue-router'
import { SwitchButton } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { resolveErrorMessage } from '@/api/http'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

async function handleLogout(): Promise<void> {
  try {
    await authStore.logout()
    await router.push('/login')
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error))
  }
}
</script>

<template>
  <div class="chat-layout-shell">
    <div class="layout-actions">
      <el-button text type="danger" :icon="SwitchButton" @click="handleLogout">退出</el-button>
    </div>
    <main class="chat-main">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.chat-layout-shell {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 100vh;
  padding: 20px;
}

.layout-actions {
  display: flex;
  justify-content: space-between;
}

.chat-main {
  min-width: 0;
  flex: 1;
}
</style>
