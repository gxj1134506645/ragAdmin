# 工程记忆索引

## 文件说明

- [已学规则](learned-rules.md) — 6 条稳定规则（笔记路径、职责边界、策略模式、Embedding约束、认证方案、向量方案）
- [纠正记录](corrections.md) — 3 条纠正（后台问答下线、分块策略重构、ChunkContext parseMode）
- [阶段观察](observations.md) — 4 条观察（TextSplitter局限、中文句子检测、分块参数对齐、父子分块可行性）
- [反模式清单](anti-patterns.md) — 5 条反模式（职责混淆、全量历史灌模型、conversationId、敏感日志、过期plan堆积）
- [演化日志](evolution-log.md) — 8 条演化记录（2026-04-14 至 2026-04-21）

## 当前高频主题

- 文档导入三件套（loading → cleaning → chunking）已全部完成
- 分块策略不自建在框架接口上，自定义 DocumentChunkStrategy
- 知识库主链路仅支持同步 Embedding，向量存储使用 Milvus
- 前后台统一用户源 + sa-token 认证授权
