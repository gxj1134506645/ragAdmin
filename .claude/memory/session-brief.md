# 会话启动摘要

> 最后更新：2026-04-25
>
> 本文件用于 SessionStart hook 的轻量注入，只保留宏观项目状态和记忆读取策略。完整进度以 `project-progress.md` 为准。

## 宏观进度

- 当前阶段：Phase 1 基本完成，Phase 2 大部分完成，Phase 3 未启动
- 已完成主线：认证授权、用户管理、模型管理、文档处理管线、混合检索、RAG SSE 聊天、后台治理、管理前端、聊天前端
- 进行中重点：聊天记忆 Redis 短期层、查询改写接入检索管线、语义/父子分块、Cross-Encoder 重排序、Dashboard 扩展、分块策略 YAML 参数覆写
- 未启动重点：内部 MCP 工具平台、多模型路由 / 智能切换、多模态能力、组织 / SSO 集成

## 读取策略

- 简单配置核查或单文件小改动：优先按任务作用域读取必要文件，不强制加载完整进度
- 中等及以上任务、跨模块任务、阶段规划、重大功能收口：先读取 `project-progress.md`，再按类型读取相关 `rules`、`memory` 和架构文档
- 大块功能模块完成、阶段性提交或路线变化后：主动更新 `project-progress.md`，并同步更新本摘要

## 事实源

- 完整进度：`.claude/memory/project-progress.md`
- 执行规则：`.claude/rules`
- 稳定架构事实源：`docs/architectures`
