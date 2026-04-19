# ragAdmin 项目协作规范

## 第一性原则

- 任何沟通、说明、结论、文档默认使用中文简体
- 技术名词、接口路径、类名、表名、命令、配置键名可保留英文原文
- 本文件是项目级宪法，保持精简、稳定、可长期复用

## 项目定位

`ragAdmin` 是一个面向内部使用的 RAG 知识库管理平台。

当前阶段以架构设计和项目启动为主，首期聚焦：
- 知识库与文档管理
- 文档异步解析与索引构建
- 模型与提供方管理
- 单知识库 RAG 问答
- 后台治理：认证、权限、审计、任务监控

## 当前事实来源

当前阶段以下文档是主要事实来源：
- `docs/architectures/rag-admin-architecture.md`
- `docs/architectures/rag-admin-api-design.md`
- `docs/architectures/rag-admin-schema-v1.sql`

实现与文档出现差异时，默认先对齐文档，再决定实现。

## 默认技术选型

- 后端：JDK 21 + Spring Boot 3
- 前端：TypeScript + Vue 3 + Element Plus
- 数据库：PostgreSQL 16
- 向量检索：首期优先 `pgvector`
- 对象存储：MinIO
- AI 接入：Spring AI Alibaba，外层保持自定义模型适配层
- 数据库迁移：Flyway
- ORM：MyBatis Plus
- 对象映射：MapStruct
- 认证与授权：`sa-token`
- 登录态与在线会话：`sa-token + Redis`
- 日志与可观测：`SLF4J + Logback`，首批即具备健康检查与关键链路追踪

## 核心架构约束

建议逻辑分层：
1. 接入层
2. 领域层
3. 编排层
4. 基础设施层

核心平台能力：
- Model Gateway
- Knowledge Pipeline
- Retrieval Orchestrator
- Admin Governance

- 业务层通过抽象能力使用模型，Spring AI Alibaba 位于适配层或基础设施层
- RAG 检索链路集中在编排层，文档处理采用异步链路，Embedding 模型与向量存储保持可替换
- 配置文件保存平台接入配置和兜底默认模型，知识库实际使用模型优先来自后台入库配置
- 认证登录态、在线状态、强制下线标记由 `sa-token + Redis` 承载，登录态按 `loginType` 隔离
- 用户问答侧会话记忆默认持久化到 PostgreSQL，`chatId` 优先使用稳定内部标识，会话操作按用户归属校验
- 首页通用问答与知识库内问答采用独立会话上下文
- `rag-admin-server` 顶层优先按业务域拆分，外部依赖适配优先下沉到 `infra`
- 后端包结构细则见 `docs/architectures/rag-admin-architecture.md`

## 开发原则

- 优先交付端到端可运行价值
- 优先做小而完整的垂直切片
- 优先选择简单、稳定、易维护的方案
- 明显重复逻辑及时收敛
- 核心模块保持清晰边界，简单 CRUD 保持务实

## API 与数据规范

- 管理端前缀：`/api/admin`
- 内部回调前缀：`/api/internal`
- 统一响应结构：`code`、`message`、`data`
- 分页字段：`pageNo`、`pageSize`、`total`、`list`
- 认证头：`Authorization: Bearer <token>`

当前状态枚举：
- 通用状态：`ENABLED` / `DISABLED`
- 文档解析状态：`PENDING` / `PROCESSING` / `SUCCESS` / `FAILED`
- 任务状态：`WAITING` / `RUNNING` / `SUCCESS` / `FAILED` / `CANCELED`

- API 契约保持清晰、显式、可校验；Controller 入参与出参使用 DTO / VO，Entity 作为持久化对象使用
- 公共 API 优先使用确定结构，流式接口统一使用 WebFlux `Flux` 或 `Flux<ServerSentEvent<...>>`
- PostgreSQL 16 + `pgvector` 作为当前数据库基线，初始向量维度 `1024`
- 表结构演进通过 Flyway 管理，主键默认使用 `BIGINT`，核心表保持统一审计字段和必要索引
- 实现调整向量维度或存储结构时，同步更新 SQL 草案与相关设计文档

## 实现约定

- 新增代码按最小必要依赖推进
- 命名优先与文档术语保持一致
- MyBatis Plus 单表查询优先使用 Lambda Wrapper 风格
- Entity 与 DTO / VO 映射优先使用 MapStruct，Mapper 显式配置为 Spring Bean
- 主要数据对象优先使用 Lombok 简化样板代码
- Bean 注入默认统一使用 `@Autowired`
- 前端异步流程优先使用 `await/async`
- 安全、认证、数据边界逻辑保持显式可见，复杂分支与关键边界补充高价值中文注释
- 结构化日志覆盖关键操作和失败路径，日志内容避开敏感数据
- 健康检查覆盖数据库、Redis、MinIO、模型调用等关键依赖
- 显式区分调度线程池、普通业务线程池、阻塞型 IO 执行器
- `@Scheduled` 使用独立调度线程池，`@Async` 默认使用普通业务线程池，阻塞型 IO 任务使用显式命名的虚拟线程执行器并配合业务并发闸门

## 文档规则

- `*.md` 文档默认使用中文简体
- 标题、说明、备注、步骤优先使用中文简体
- 技术名词、接口路径、类名、表名可保留英文，文件名优先使用稳定英文 slug
- `docs` 下只保留 `architectures`、`plans`、`tasks` 三类目录，不新增其他文档分类目录
- `docs/architectures` 存放当前正式生效的架构与技术设计，属于稳定事实源；一个稳定专题一篇文档，失效内容直接改写或删除，不保留并列旧版
- `docs/plans` 存放当前仍有效的阶段性方案、设计推演与实施计划；一个大功能或一个阶段一篇主 plan，路线变更后直接覆盖、重写或删除旧 plan，不追加兼容性历史
- `docs/tasks` 存放当前执行型文档；一个 plan 可以对应多个 task 文件，任务完成且无复用价值后直接删除
- 三类文档的默认流转关系为：`architectures -> plans -> tasks`
- `plans` 与 `tasks` 产生的长期有效结论，应及时回写到 `docs/architectures`
- 同一专题在同一阶段只维护一份主文档，避免多个事实源并存；需求、设计、任务边界分别落在对应目录，不交叉堆叠
- 文档内容保持高信号密度，只写会持续约束实现或指导执行的内容，不为仪式感生成冗长文书
- 计划结论一旦沉淀为长期有效约束，应及时回写到 `docs/architectures`，避免长期停留在 `plans`
- 跨模块、高风险、长周期主题允许按专题拆文档，但仍必须归属到 `architectures`、`plans`、`tasks` 三类目录之一
- `docs/architectures` 下的架构设计文档或技术方案类 `md` 发生新增、拆分、重写、合并或重要更新时，默认同步一份高信号摘要到 `/Users/gfish/codes/mds/raw/notes`
- 对应 `notes` 摘要应尽量与 `docs/architectures` 的专题拆分保持一致；如果原有摘要已不适配当前专题边界，应主动重写、拆分或删除旧摘要，避免形成过期并行事实源
- 如果用户只说“同步到 notes”或等价表达，默认指同步到 `/Users/gfish/codes/mds/raw/notes`，无需用户重复粘贴完整路径

## Agent 工程运行约定

让 Agent 在任务执行前先识别作用域，再按需加载规则和工程记忆，并在关键阶段完成复盘沉淀。

- `.codex/rules`：任务分类、记忆写入、子代理路由、验证清单等执行细则
- `.codex/rules/document-lifecycle.md`：文档流转、收口、删除与回写规则
- `.codex/hooks`：任务开始、任务结束、失败复盘、提交前检查等关键阶段触发约定
- `.codex/memory`：纠正记录、阶段观察、已学规则、反模式、演化日志等工程记忆
- `.codex/subagents`：`planner`、`executor`、`verifier` 等子代理职责说明

## 记忆协作关系

- 项目内部记忆存放在 `.codex/memory`
- 外部记忆插件可用于恢复跨会话动态记忆
- 项目内部记忆负责项目规则、项目边界和项目级纠偏
- 外部恢复记忆负责补充历史工作上下文
- 当前任务优先以本项目入口文件和项目内部记忆作为项目事实来源
- 外部恢复结果适合作为补充上下文，不替代项目内部规则与文档

## 记忆使用原则

- 外部记忆插件恢复出的内容适合作为历史工作背景
- `.codex/memory` 中的内容适合作为项目内部高价值记忆提炼
- 当外部恢复内容与项目内部规则、项目内部记忆或项目文档不一致时，以项目内部规则、项目内部记忆和项目文档为准
- 读取项目记忆时，优先先核对 `AGENTS.md`、`.codex/rules`、`.codex/memory`，再结合外部恢复内容理解上下文

1. 识别任务作用域、影响路径与模块范围
2. 读取本 `AGENTS.md` 中的项目目标、边界和实现约定
3. 按任务类型读取 `.codex/rules` 下相关规则
4. 按需读取 `.codex/memory` 下相关工程记忆
5. 判断是否启用子代理分工
6. 执行任务
7. 在关键 hook 阶段完成验证、复盘与沉淀

- `.codex/memory` 记录高价值、可复用、可降低重复错误概率的内容，同类问题优先更新原条目
- `planner` 负责拆解任务与定义边界，`executor` 负责在边界内实施改动，`verifier` 负责验证结果与剩余风险
- 简单任务可由主 Agent 直接完成，同时补齐规划视角和验证视角自检
- 规则、hook、memory、子代理说明默认优先使用正向表达，先说明目标，再说明动作、顺序、判断条件和预期输出

## 交付与提交

默认执行流程：
1. 明确目标与约束
2. 做最小且有效的改动
3. 在当前环境允许范围内完成验证
4. 总结关键决策和剩余风险

Git 规范：
- 使用 Conventional Commits
- 提交信息尽量使用中文简体
- 每次提交聚焦一个清晰改动
- 大任务完成后先自检，再提交并按需推送

## 备注

本文件是当前仓库的项目级工作规则文件，用于统一约束项目目标、工程边界和 Agent 协作方式。


<claude-mem-context>
# Memory Context

# [ragAdmin] recent context, 2026-04-19 9:09pm GMT+8

Legend: 🎯session 🔴bugfix 🟣feature 🔄refactor ✅change 🔵discovery ⚖️decision
Format: ID TIME TYPE TITLE
Fetch details: get_observations([IDs]) | Search: mem-search skill

Stats: 21 obs (7,941t read) | 0t work

### Apr 19, 2026
101 7:46p 🔵 Spring Boot application startup successful with service integrations
102 8:06p 🔵 MinerU document parsing service failure investigation
104 8:07p 🔵 MinerU parsing exception source code identified
105 " 🔵 Document parsing workflow and error handling flow identified
107 8:25p 🔵 PDF parsing fails on documents without table of contents
108 8:26p 🔴 PDF parsing now gracefully falls back to page reader on TOC errors
111 " 🔴 PDF reader fallback to page mode validated by unit tests
113 8:29p ✅ Documented PDF reader fallback pattern in project knowledge base
115 8:37p 🟣 PDF paragraph reader now validates extraction quality before accepting results
118 8:40p 🔴 PDF reader quality-based fallback validated by test suite
119 " 🔴 DocumentMetadataFactory now filters null keys and values during metadata enrichment
120 8:41p 🔴 Test suite enhanced to validate metadata null filtering
121 " 🔴 Test failure revealed Spring AI Document constructor rejects null metadata values
122 8:42p 🔴 Test fixed to work around Spring AI Document constructor null validation
124 " 🔴 Test suite validates DocumentMetadataFactory null filtering with all tests passing
126 8:56p 🔵 PDF metadata null cause traced to missing TOC in ParagraphPdfDocumentReader
127 " ⚖️ PDF parsing fallback strategy mandates automatic reader degradation
128 " 🔴 PDF paragraph extraction quality-based fallback implemented
130 9:08p 🔵 Document cleaning architecture requires layered policy-based approach
131 " 🔵 PDF paragraph reading requires automatic fallback to page-level reading
132 " ⚖️ Document cleaning must use rule-driven selective cleaner execution
</claude-mem-context>