<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { resolveErrorMessage } from '@/api/http'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const submitting = ref(false)
const form = reactive({
  loginId: '',
  password: '',
})

async function handleLogin(): Promise<void> {
  if (!form.loginId.trim() || !form.password) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  submitting.value = true
  try {
    await authStore.login({
      loginId: form.loginId.trim(),
      password: form.password,
    })
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/chat'
    await router.replace(redirect)
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error))
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <section class="login-copy">
      <p class="copy-kicker">RAG CHAT PORTAL</p>
      <h1 class="serif-title">让模型在正确的知识边界内回答问题</h1>
      <p>
        这里不是后台管理台，而是面向组织成员的独立问答入口。你可以直接提问，也可以切入某个知识库内部发起更聚焦的检索增强问答。
      </p>
      <ul class="copy-points">
        <li>支持首页通用聊天与知识库内聊天两种场景</li>
        <li>支持运行时切换聊天模型、开启或关闭联网</li>
        <li>支持多知识库临时联查，不污染后台管理页面</li>
      </ul>
    </section>

    <section class="login-card app-shell-panel">
      <div class="login-card-head">
        <p class="card-kicker">统一登录</p>
        <h2 class="serif-title">进入 ragAdmin Chat</h2>
        <span>复用后台维护的用户账号，不单独注册。</span>
      </div>

      <el-form label-position="top" @submit.prevent="handleLogin">
        <el-form-item label="登录账号">
          <el-input v-model="form.loginId" placeholder="用户名 / 手机号" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="请输入密码"
            autocomplete="current-password"
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-button type="primary" class="submit-button" :loading="submitting" @click="handleLogin">
          {{ submitting ? '正在进入' : '进入问答工作台' }}
        </el-button>
      </el-form>
    </section>
  </div>
</template>

<style scoped>
.login-page {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(340px, 420px);
  gap: 28px;
  min-height: 100vh;
  padding: 24px;
  align-items: stretch;
}

.login-copy,
.login-card {
  border-radius: 32px;
}

.login-copy {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: clamp(28px, 7vw, 68px);
  background:
    linear-gradient(145deg, rgba(255, 251, 245, 0.72), rgba(245, 233, 214, 0.58)),
    rgba(255, 255, 255, 0.24);
  border: 1px solid rgba(110, 84, 54, 0.1);
}

.copy-kicker,
.card-kicker {
  margin: 0 0 10px;
  color: var(--text-muted);
  font-size: 12px;
  letter-spacing: 0.22em;
  text-transform: uppercase;
}

.login-copy h1 {
  margin: 0;
  font-size: clamp(38px, 5vw, 68px);
  line-height: 1.12;
}

.login-copy p {
  margin: 18px 0 0;
  max-width: 720px;
  color: var(--text-secondary);
  line-height: 1.9;
  font-size: 16px;
}

.copy-points {
  display: grid;
  gap: 12px;
  margin: 28px 0 0;
  padding: 0;
  list-style: none;
}

.copy-points li {
  padding: 16px 18px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.46);
  border: 1px solid rgba(110, 84, 54, 0.08);
}

.login-card {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 32px;
}

.login-card-head h2 {
  margin: 0;
  font-size: 34px;
}

.login-card-head span {
  display: block;
  margin-top: 10px;
  color: var(--text-muted);
}

.submit-button {
  width: 100%;
  margin-top: 8px;
}

@media (max-width: 980px) {
  .login-page {
    grid-template-columns: 1fr;
  }
}
</style>
