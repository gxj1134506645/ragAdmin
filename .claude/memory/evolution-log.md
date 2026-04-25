# 演化日志

## 2026-04-14

- 变更：引入运行目录作为 Agent 执行层
- 原因：将项目级宪法、执行细则、工程记忆和子代理职责分层维护
- 影响：顶层入口文件负责稳定原则，运行目录负责执行细则与沉淀
- 后续观察点：逐步校准 memory 写入密度和子代理分工粒度

## 2026-04-17

- 变更：前台独立问答前端 `rag-chat-web` 建立，后台问答入口下线
- 原因：问答应统一归前台，后台只管知识库和文档运维
- 影响：所有问答接口走 `/api/app/chat/**`，后台不再提供问答页面

## 2026-04-18

- 变更：Tavily 联网搜索接入，联网来源独立持久化
- 原因：增强问答能力，支持联网实时信息检索
- 影响：新增 `ChatWebSearchSourceEntity`，前台聊天支持联网开关

## 2026-04-19

- 变更：文档清洗架构增强，引入信号检测层和策略化清洗
- 原因：不同文档类型需要不同的清洗策略，硬编码策略不灵活
- 影响：新增 `DocumentSignals`、`DocumentSignalAnalyzer`、`CleanerPolicyResolver`，清洗步骤按信号动态启用

## 2026-04-20

- 变更：文档分块重构为策略模式
- 原因：所有文档类型使用同一段分块逻辑，无法按类型优化
- 影响：新增 `DocumentChunkStrategy` 接口 + `RecursiveFallbackStrategy`，策略选择器按优先级匹配

## 2026-04-20

- 变更：补充 OcrNoiseCleaner 和 LineMergeCleaner
- 原因：清洗层有策略定义但无 CleanerStep 实现
- 影响：清洗层五个步骤全部实现完毕

## 2026-04-21

- 变更：实现四种专项分块策略（Markdown/Html/PdfOcr/PdfText）
- 原因：通用策略不感知文档结构，分块质量不够
- 影响：ChunkContext 增加 parseMode，新增 4 个策略 + 30 个测试，RAG 文档处理三块（加载/清洗/分块）全部完成

## 2026-04-21

- 变更：完成 memory 沉淀，清理已完成 plans
- 原因：6 个 plans 已完成，长期结论需回写到 architectures
- 影响：memory 五个文件补齐核心内容，6 个 plans 删除，架构文档更新

## 2026-04-21

- 变更：子代理路由规则增加并行执行判断策略
- 原因：用户要求主 Agent 自主判断任务是否适合并行开发，无需用户额外指示
- 影响：`subagent-routing.md` 增加并行判断章节，`learned-rules.md` 记录 R-007

## 2026-04-22

- 变更：实现 PG/Milvus/ES 三端数据同步，修复 Milvus 向量孤立问题
- 原因：文档删除和重解析时 Milvus 向量未被清理；ES 已配置 Docker 但无 Java 集成
- 影响：新建 ElasticsearchProperties + ElasticsearchClient + ChunkSearchSyncService；MilvusVectorStoreClient 新增 delete；DocumentParseProcessor 新增 SYNC_SEARCH_ENGINE 步骤；DocumentService/KnowledgeBaseService delete 接入三端清理；SystemHealthService 增加 ES 健康检查

## 2026-04-22

- 变更：自定义 agent 角色从 `.claude/subagents`（概念文档）迁移到 `.claude/agents`（可被 Agent 工具调用）
- 原因：planner/executor/verifier 是软件工程固定角色，应注册为持久化自定义 agent
- 影响：新建 `.claude/agents/planner.md`、`executor.md`、`verifier.md`，删除旧 `.claude/subagents/` 目录，CLAUDE.md 更新引用

## 2026-04-22

- 变更：RAG 检索全链路补全（ES 关键词检索 + RRF 融合 + 混合检索 + 查询改写 + Reranking + 语义分块 + 父子分块）
- 原因：检索层仅有向量单路召回，缺少关键词检索、融合排序、查询增强和精排能力；分块层缺少语义感知
- 影响：检索模式新增 SEMANTIC_ONLY/KEYWORD_ONLY/HYBRID 三种；新增 KeywordRetrievalStrategy、RrfFusionService、QueryRewritingService（Multi-Query + HyDE）、RerankingService、SemanticChunkStrategy、ParentChunkExpansionService；4 次 git commit，320 个测试全部通过
- 关键提交：102e5c5（关键词+RRF+混合）、9efae8a（查询改写）、8f2438e（Reranking）、3e1816f（语义分块+父子块）

## 2026-04-25

- 变更：SessionStart hook 从加载完整 `project-progress.md` 调整为加载轻量 `session-brief.md`
- 原因：新会话需要知道宏观进度，但不应在简单任务中强制注入完整进度明细占用上下文
- 影响：`project-progress.md` 保持完整进度事实源，中等及以上任务和重大功能收口时按需读取；重大功能完成后需同步更新 `project-progress.md` 与 `session-brief.md`
- 后续观察点：观察短摘要是否足以降低重复规划和上下文噪声
