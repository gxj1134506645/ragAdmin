<template>
  <section class="dashboard-page">
    <header class="dashboard-head">
      <div>
        <h1 class="page-title">概览</h1>
        <p class="page-subtitle">
          这是首版前端基线的占位首页，先为知识库管理、任务流与健康检查留出统一容器。
        </p>
      </div>
    </header>

    <div class="dashboard-grid">
      <article class="metric-card soft-panel">
        <span>当前完成</span>
        <strong>前端基线初始化</strong>
        <p>已规划登录、Layout、统一请求层与知识库分页表格的整体链路。</p>
      </article>
      <article class="metric-card soft-panel">
        <span>当前联调焦点</span>
        <strong>知识库管理</strong>
        <p>后续创建、详情、文档上传都可以在这个切片上继续垂直推进。</p>
      </article>
      <article class="metric-card soft-panel">
        <span>后续页面</span>
        <strong>模型、文档、任务监控</strong>
        <p>保持同一套导航、鉴权和错误处理基线，不再重复搭脚手架。</p>
      </article>
    </div>
  </section>
</template>

<style scoped>
.dashboard-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}

.metric-card {
  min-height: 220px;
  padding: 24px;
}

.metric-card span {
  color: #9d7a58;
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.metric-card strong {
  display: block;
  margin-top: 14px;
  font-family: "Noto Serif SC", serif;
  font-size: 28px;
  line-height: 1.2;
}

.metric-card p {
  margin: 16px 0 0;
  color: #6d5948;
}

@media (max-width: 960px) {
  .dashboard-grid {
    grid-template-columns: 1fr;
  }
}
</style>
