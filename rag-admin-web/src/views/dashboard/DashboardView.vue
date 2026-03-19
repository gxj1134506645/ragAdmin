<script setup lang="ts">
import { ChatDotRound, Collection, Connection, List, Plus, Tickets } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'

const router = useRouter()

const quickEntries = [
  {
    key: 'general-chat',
    eyebrow: '问答',
    title: '打开智能问答',
    description: '进入首页通用模型对话，适合做不依赖知识库的即时交流和试问。',
    actionText: '立即对话',
    path: '/chat',
    icon: ChatDotRound,
    accent: 'is-primary',
  },
  {
    key: 'knowledge-bases',
    eyebrow: '知识库',
    title: '进入知识库管理',
    description: '查看知识库列表，进入详情、编辑和文档管理。',
    actionText: '打开列表',
    path: '/knowledge-bases',
    icon: Collection,
    accent: 'is-light',
  },
  {
    key: 'knowledge-base-create',
    eyebrow: '创建',
    title: '新建知识库',
    description: '直接开始创建新的知识库并配置模型与检索参数。',
    actionText: '立即创建',
    path: '/knowledge-bases/create',
    icon: Plus,
    accent: 'is-warm',
  },
  {
    key: 'models',
    eyebrow: '模型',
    title: '进入模型管理',
    description: '维护模型配置、提供方接入信息和探活结果。',
    actionText: '管理模型',
    path: '/models',
    icon: Connection,
    accent: 'is-light',
  },
  {
    key: 'tasks',
    eyebrow: '任务',
    title: '查看任务监控',
    description: '快速检查解析任务状态、错误摘要和重试入口。',
    actionText: '查看任务',
    path: '/tasks',
    icon: List,
    accent: 'is-light',
  },
  {
    key: 'audit-logs',
    eyebrow: '治理',
    title: '进入审计日志',
    description: '查看管理员操作轨迹，并快速筛选问答反馈类审计记录。',
    actionText: '查看审计',
    path: '/audit-logs',
    icon: Tickets,
    accent: 'is-light',
  },
]

async function goTo(path: string): Promise<void> {
  await router.push(path)
}
</script>

<template>
  <section class="dashboard-page">
    <header class="dashboard-head soft-panel">
      <div>
        <p class="dashboard-eyebrow">快捷入口</p>
        <h1 class="page-title">从这里直接进入常用操作</h1>
        <p class="page-subtitle">
          首页只保留高频入口，减少跳转前的阅读成本。
        </p>
      </div>
    </header>

    <section class="entry-grid">
      <button
        v-for="entry in quickEntries"
        :key="entry.key"
        type="button"
        class="entry-card soft-panel"
        :class="entry.accent"
        @click="goTo(entry.path)"
      >
        <div class="entry-top">
          <div class="entry-icon">
            <el-icon><component :is="entry.icon" /></el-icon>
          </div>
          <span class="entry-link">{{ entry.actionText }}</span>
        </div>
        <div class="entry-copy">
          <span class="entry-eyebrow">{{ entry.eyebrow }}</span>
          <strong>{{ entry.title }}</strong>
          <p>{{ entry.description }}</p>
        </div>
      </button>
    </section>
  </section>
</template>

<style scoped>
.dashboard-page {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.dashboard-head {
  position: relative;
  overflow: hidden;
  padding: 30px 34px;
  background:
    radial-gradient(circle at right top, rgba(211, 120, 41, 0.12), transparent 30%),
    linear-gradient(180deg, rgba(255, 251, 246, 0.95), rgba(255, 248, 241, 0.9));
}

.dashboard-eyebrow,
.entry-eyebrow {
  color: #9d7a58;
  font-size: 12px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.dashboard-eyebrow {
  margin: 0 0 10px;
}

.entry-grid {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: 20px;
}

.entry-card {
  position: relative;
  display: flex;
  flex-direction: column;
  grid-column: span 6;
  gap: 20px;
  min-height: 250px;
  padding: 28px;
  border: 1px solid rgba(179, 139, 96, 0.12);
  text-align: left;
  cursor: pointer;
  transition:
    transform 180ms ease,
    box-shadow 180ms ease,
    border-color 180ms ease;
}

.entry-card.is-primary {
  background:
    radial-gradient(circle at top right, rgba(211, 120, 41, 0.24), transparent 34%),
    linear-gradient(160deg, rgba(255, 248, 238, 0.96), rgba(255, 252, 247, 0.9));
}

.entry-card.is-warm {
  background:
    linear-gradient(145deg, rgba(252, 241, 226, 0.94), rgba(255, 250, 244, 0.9));
}

.entry-card.is-light {
  background:
    linear-gradient(180deg, rgba(255, 252, 248, 0.95), rgba(255, 249, 242, 0.88));
}

.entry-card:hover {
  transform: translateY(-4px);
  border-color: rgba(198, 107, 34, 0.22);
  box-shadow: 0 22px 38px rgba(141, 69, 16, 0.1);
}

.entry-card:nth-child(1),
.entry-card:nth-child(2) {
  min-height: 280px;
}

.entry-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.entry-icon {
  display: grid;
  place-items: center;
  width: 58px;
  height: 58px;
  border-radius: 18px;
  background: rgba(198, 107, 34, 0.14);
  color: #8d4510;
  font-size: 24px;
}

.entry-link {
  color: #8d4510;
  font-size: 13px;
  font-weight: 600;
}

.entry-copy {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.entry-copy strong {
  font-family: "Noto Serif SC", serif;
  font-size: 32px;
  line-height: 1.15;
}

.entry-copy p {
  margin: 0;
  color: #6d5948;
  line-height: 1.7;
}

@media (max-width: 960px) {
  .entry-grid {
    grid-template-columns: 1fr;
  }

  .entry-card {
    grid-column: auto;
  }
}

@media (max-width: 640px) {
  .dashboard-head,
  .entry-card {
    padding: 20px;
  }
}
</style>
