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
