# 工程记忆索引

## 文件说明

- [项目进度快照](project-progress.md) — **新会话必读**，项目整体完成状态与优先级
- [已学规则](learned-rules.md) — 9 条稳定规则（R-001~R-009）
- [纠正记录](corrections.md) — 3 条纠正（后台问答下线、分块策略重构、ChunkContext parseMode）
- [阶段观察](observations.md) — 5 条观察（TextSplitter局限、中文句子检测、分块参数对齐、父子分块可行性、ES IK分词器）
- [反模式清单](anti-patterns.md) — 6 条反模式（A-001~A-006）
- [演化日志](evolution-log.md) — 9 条演化记录（2026-04-14 至 2026-04-22）

## 当前高频主题

- Phase 1 核心管线已完成，Phase 2 大部分完成
- 下一优先：聊天记忆 Redis 层、查询改写接入、语义分块、Cross-Encoder 重排序
- 文档加载已统一为 MinerU 方案
- 三端数据同步（PG + Milvus + ES）已实现
- 前端设计系统迁移完成（Ember + Genesis）