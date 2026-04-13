<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { resolveErrorMessage } from '@/api/http'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive({
  loginId: 'admin',
  password: 'Admin@123456',
})

const rules: FormRules<typeof form> = {
  loginId: [{ required: true, message: '请输入账号或手机号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  submitting.value = true
  try {
    await authStore.login(form)
    ElMessage.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
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
    <section class="hero-copy">
      <div class="hero-copy-top">
        <p class="eyebrow">RAG ADMIN</p>
        <h1>知识库后台管理</h1>
        <p class="description">统一进入知识库、模型、文档与任务管理入口。</p>
      </div>
    </section>

    <section class="login-card soft-panel">
      <div class="card-head">
        <p>管理端登录</p>
        <h2>进入知识工坊</h2>
      </div>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @keyup.enter="submit"
      >
        <el-form-item label="账号或手机号" prop="loginId">
          <el-input v-model="form.loginId" :prefix-icon="User" placeholder="请输入 admin 或手机号" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            :prefix-icon="Lock"
            show-password
            placeholder="请输入密码"
          />
        </el-form-item>
        <el-button class="submit-button" type="primary" :loading="submitting" @click="submit">
          登录并进入后台
        </el-button>
      </el-form>
      <div class="hint-block">
        <span>默认本地账号</span>
        <strong>admin / Admin@123456</strong>
      </div>
    </section>
  </div>
</template>

<style scoped>
.login-page {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(360px, 460px);
  gap: 28px;
  align-items: stretch;
  min-height: 100vh;
  padding: 28px;
}

.hero-copy {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 28px 18px;
}

.hero-copy-top {
  display: flex;
  flex-direction: column;
  max-width: 720px;
  margin: 0 auto;
}

.eyebrow {
  margin: 0 0 18px;
  color: #9b7755;
  font-size: 12px;
  letter-spacing: 0.28em;
}

.hero-copy h1 {
  max-width: 720px;
  margin: 0;
  font-family: "Noto Serif SC", serif;
  font-size: clamp(42px, 5vw, 72px);
  line-height: 1.08;
}

.description {
  margin: 18px 0 0;
  color: #6d5948;
  font-size: 17px;
}

.login-card {
  align-self: center;
  padding: 28px;
}

.card-head p {
  margin: 0;
  color: #9b7755;
  font-size: 12px;
  letter-spacing: 0.22em;
  text-transform: uppercase;
}

.card-head h2 {
  margin: 8px 0 0;
  font-family: "Noto Serif SC", serif;
  font-size: 32px;
}

.submit-button {
  width: 100%;
  margin-top: 10px;
  height: 46px;
  border: none;
  background: linear-gradient(135deg, #d37829 0%, #8d4510 100%);
}

.hint-block {
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px dashed rgba(113, 82, 45, 0.18);
  color: #6d5948;
}

.hint-block span {
  display: block;
  margin-bottom: 6px;
  font-size: 12px;
}

@media (max-width: 960px) {
  .login-page {
    grid-template-columns: 1fr;
    min-height: auto;
  }

  .hero-copy {
    justify-content: flex-start;
    padding: 10px 8px 0;
  }

  .hero-copy-top {
    margin: 0;
  }
}

@media (max-width: 640px) {
  .login-page {
    padding: 16px;
  }

  .feature-grid {
    grid-template-columns: 1fr;
  }
}
</style>
