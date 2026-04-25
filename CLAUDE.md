# 项目协作规范

## 第一性原则

- 任何沟通、说明、结论、文档默认使用中文简体
- 技术名词、接口路径、类名、表名、命令、配置键名可保留英文原文
- 本文件是项目级宪法，保持精简、稳定、可长期复用

## 项目定位

- 这里是 Claude Code 项目的顶层入口文件
- 项目目标、事实来源、架构边界、开发原则写在这里
- 详细执行细则下沉到 `.claude/`

## Agent 工程运行约定

- `.claude/rules`：任务分类、记忆写入、子代理路由、验证清单等执行细则
- `.claude/hooks`：任务开始、任务结束、失败复盘、提交前检查等关键阶段触发约定
- `.claude/memory`：纠正记录、阶段观察、已学规则、反模式、演化日志等工程记忆
- `.claude/agents`：`planner`、`executor`、`verifier` 三个固定角色 agent，可被 Agent 工具直接调用

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
3. **读取 `.claude/memory/project-progress.md` 了解项目整体进度和当前优先级**
4. 按任务类型读取 `.claude/rules`
5. 按需读取 `.claude/memory` 下其他工程记忆
6. 判断是否启用子代理分工
7. 执行任务
8. 在关键 hook 阶段完成验证、复盘与沉淀
9. 重大功能完成后更新 `.claude/memory/project-progress.md`

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
