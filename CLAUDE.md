# 项目协作规范

## 第一性原则

- 任何沟通、说明、结论、文档默认使用中文简体
- 技术名词、接口路径、类名、表名、命令、配置键名可保留英文原文
- 本文件是项目级宪法，保持精简、稳定、可长期复用

## 项目定位

`ragAdmin` 是一个面向内部使用的 RAG 知识库管理平台。

当前阶段以核心管线和管理能力持续演进为主，重点聚焦：
- 知识库与文档管理
- 文档异步解析、清洗、分块与索引构建
- 模型与提供方管理
- 单知识库 RAG 问答与会话记忆
- 后台治理：认证、权限、审计、任务监控

## 当前事实来源

当前阶段以下文档是主要事实来源：
- `docs/architectures/rag-admin-architecture.md`
- `docs/architectures/rag-admin-api-design.md`
- `docs/architectures/rag-admin-schema-v1.sql`
- `.claude/memory/project-progress.md`

实现与文档出现差异时，默认先对齐文档，再决定实现。

## Agent 工程运行约定

- `.claude/rules`：任务分类、记忆写入、子代理路由、验证清单等执行细则
- `.claude/memory`：会话启动摘要、项目进度快照、纠正记录、阶段观察、已学规则、反模式、演化日志等工程记忆
- `.claude/agents`：`planner`、`executor`、`verifier` 等子代理职责说明
- `.claude/settings.json`：项目级 Hook 配置（SessionStart 自动加载会话启动摘要，随仓库提交共享）

## 记忆协作关系

- 项目内部记忆存放在 `.claude/memory`
- 工程笔记沉淀到 `/Users/gfish/codes/mds/raw/notes`（跨项目共享的知识笔记）
- 外部记忆插件可用于恢复跨会话动态记忆
- 项目内部记忆负责项目规则、项目边界和项目级纠偏
- 工程笔记负责跨项目可复用的技术总结与选型论证
- 外部恢复记忆负责补充历史工作上下文
- 当前任务优先以本项目入口文件和项目内部记忆作为项目事实来源
- 外部恢复结果适合作为补充上下文，不替代项目内部规则与文档

## 记忆优先级顺序

1. 本 `CLAUDE.md`
2. `.claude/rules`
3. `.claude/memory`
4. 外部记忆插件恢复出的动态上下文

读取记忆时优先先核对项目内部规则和项目内部记忆，再参考外部恢复结果。

## 默认加载顺序

1. 识别任务作用域、影响路径与模块范围
2. 读取本 `CLAUDE.md`
3. 读取 `.claude/memory/session-brief.md` 了解宏观进度与记忆读取策略
4. 按任务类型读取 `.claude/rules`
5. 中等及以上任务、跨模块任务、阶段规划、重大功能收口时，读取 `.claude/memory/project-progress.md` 与相关工程记忆
6. 判断是否启用子代理分工
7. 执行任务
8. 在关键 hook 阶段完成验证、复盘与沉淀
9. 大块功能模块完成、阶段性提交、路线变化或重大验收通过后，主动更新 `.claude/memory/project-progress.md`，并同步更新 `.claude/memory/session-brief.md`

## 设计系统

本项目前端使用 DESIGN.md 管理设计规范。DESIGN.md 是一个 Markdown 文件，Claude Code 读取后按照其中的颜色、字体、间距、组件规范来生成和修改 UI。

- `rag-chat-web/DESIGN.md`：Genesis 设计系统（靛蓝主色 #6366F1、DM Sans / General Sans 字体、12px 圆角卡片）
- `rag-admin-web/DESIGN.md`：Ember Studio 设计系统（赤陶主色 #C2410C、Playfair Display / Source Sans 3 字体、暖色系管理面板风格）
- 涉及前端样式改动时，先读取对应前端目录下的 DESIGN.md 确认规范再动手
- 新增组件或页面时，颜色用 CSS 变量，不硬编码色值
- 可通过 designmd.ai MCP 工具搜索、下载、上传设计系统，也可用 `designmd` CLI 离线操作

## 文档规则

- `docs` 下只保留 `architectures`、`plans`、`tasks` 三类目录，不新增其他文档分类目录
- `docs/architectures` 存放当前正式生效的架构与技术设计，属于稳定事实源
- `docs/plans` 存放当前仍有效的阶段性方案、设计推演与实施计划
- `docs/tasks` 存放当前执行型文档，一个 plan 可以对应多个 task 文件
- 三类文档的默认流转关系为：`architectures -> plans -> tasks`
- `plans` 与 `tasks` 产生的长期有效结论，应及时回写到 `docs/architectures`
- 文件名优先使用稳定英文 slug，不使用按年月日追加的文件名前缀
- 路线变更后直接覆盖、重写或删除旧 plan，不为过期方案保留兼容性文书
- 任务完成且无复用价值后直接删除；长期有效结论及时回写到 `docs/architectures`

## 动态记忆上下文

- 跨会话动态记忆上下文存放在 `.claude/memory/recent-context.md`，不直接嵌入本文件
- 需要恢复历史工作上下文时，通过 `mem-search` 技能按需检索，或直接读取该文件
- 本文件只保留稳定的项目规则与边界约束，不承载易变的开发流水
