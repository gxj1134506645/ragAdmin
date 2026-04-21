# 纠正记录

## [C-001] 后台问答入口已下线

- 日期：2026-04-21
- 场景：前后台问答接口统一
- 错误表现：早期后台存在 `/api/admin/chat/**` 问答入口
- 根因：问答应统一归前台，后台只管知识库和文档运维
- 纠正动作：后台问答入口下线，所有问答走 `/api/app/chat/**`
- 后续约束：新增问答功能只走 `/api/app` 域
- 适用范围：前端路由、API 设计、验收文档

## [C-002] 分块策略不要硬编码在 DocumentParseProcessor 中

- 日期：2026-04-21
- 场景：分块逻辑重构为策略模式
- 错误表现：早期 splitText/overlapTail 直接写在 DocumentParseProcessor 中，所有文档类型走同一段逻辑
- 根因：缺少策略抽象，无法按文档类型区分分块行为
- 纠正动作：抽取为 `DocumentChunkStrategy` 接口 + 5 个策略实现，通过 `DocumentChunkStrategyResolver` 按优先级选择
- 后续约束：新增分块策略实现 `DocumentChunkStrategy` 接口，不修改 Processor
- 适用范围：文档分块相关改动

## [C-003] ChunkContext 需要携带 parseMode

- 日期：2026-04-21
- 场景：PDF 文档需要区分 OCR 和 TEXT 模式走不同分块策略
- 错误表现：`ChunkContext` 最初只有 document + signals + properties，无法在 `supports()` 中区分 parseMode
- 根因：parseMode 存在于清洗后 Document 的 metadata 中，未传递到 ChunkContext
- 纠正动作：ChunkContext 增加 `parseMode` 字段，由 DocumentParseProcessor 从文档 metadata 提取并传入
- 后续约束：路由所需的信息必须在 ChunkContext 中显式传递
- 适用范围：任何需要新路由字段的场景
