# 独立问答前台与 `/api/app` 架构设计

## 1. 背景

当前仓库已经具备两类能力：

- `rag-admin-server` 已完成管理端主链路、文档解析、Milvus 向量写入、单知识库问答、流式输出、会话持久化
- `rag-admin-web` 已具备后台管理台和一期问答工作台

但现状仍有两个明显问题：

1. 管理台问答入口与后台治理页面混在同一个前端工程内，终端定位不清晰，不适合组织内普通使用者直接使用
2. 当前问答模型仍以“知识库详情页内单知识库问答”为主，不满足首页简洁聊天、`@知识库`、多知识库选择、模型切换、联网开关等前台产品形态

用户已经明确新的产品定位：

- 不做公网开放注册
- 用户由后台统一维护
- 同仓库保留后台管理端 `rag-admin-web`
- 新增独立问答前台 `rag-chat-web`
- 两个前端共用同一个后端 `rag-admin-server`

## 2. 目标与非目标

### 2.1 本轮目标

- 在同一仓库内形成“双前端单后端”结构
- 新增前台接口域 `/api/app/*`
- 前台支持简洁聊天页、模型切换、联网开关、知识库选择
- 支持两种前台问答入口：
  - 首页通用对话
  - 进入某个知识库内部后的知识库内对话
- 保持会话隔离，避免首页会话、知识库内会话、后台管理台会话互相污染
- 继续沿用 Spring AI Alibaba + Spring AI Chat Memory + PostgreSQL 持久化能力

### 2.2 本轮非目标

- 不引入公网注册、短信验证码、租户化等公网产品能力
- 不在本轮同时重构整个后台管理端权限模型
- 不在本轮引入新的外部搜索供应商实现，只先预留联网搜索抽象
- 不把知识库运行时检索链路拆到独立微服务，仍保持单体后端

## 3. 方案对比

### 3.1 方案 A：继续在 `rag-admin-web` 内扩展一个“用户聊天区”

优点：

- 代码改动最少
- 可以直接复用当前登录态、路由、聊天组件

缺点：

- 管理端与使用端产品定位继续混杂
- 菜单、权限、导航、样式基调都更偏后台，不适合终端用户
- 后续首页聊天、知识库工作台、模型选择、`@知识库` 等能力会把后台工程继续做重

结论：

- 不采用

### 3.2 方案 B：同仓库双前端单后端

优点：

- 产品边界清晰，管理台与使用端分别演进
- 后端仍可复用现有鉴权、会话、检索、模型适配、审计能力
- 适合当前“组织内受控开通”的开源项目定位
- 后续公司二次开发时，可单独替换前台或后台而不必重做整套后端

缺点：

- 需要新增 `rag-chat-web`
- 后端需要补齐 `/api/app` 接口域和终端隔离逻辑

结论：

- 本次采用此方案

### 3.3 方案 C：双前端双后端

优点：

- 前后台边界最彻底
- 前台服务可以独立部署、独立扩容

缺点：

- 当前仓库仍是单体后端，直接拆成双后端会显著放大改动面
- 会引入重复鉴权、重复问答编排、重复模型适配问题
- 与当前阶段“尽快交付完整可运行闭环”的目标不匹配

结论：

- 当前阶段不采用

## 4. 最终设计

### 4.1 仓库结构

新增后的仓库结构建议如下：

```text
ragAdmin/
  docs/
  docker/
  rag-admin-server/
  rag-admin-web/
  rag-chat-web/
  AGENTS.md
  pom.xml
```

职责边界：

- `rag-admin-web`：后台治理与配置
- `rag-chat-web`：组织内用户问答前台
- `rag-admin-server`：统一鉴权、知识库管理、检索编排、模型调用、会话持久化

### 4.2 接口分域

后端接口分为三类：

- `/api/admin/*`：后台管理端接口
- `/api/app/*`：前台问答端接口
- `/api/internal/*`：内部回调与内部调用

这样做的目的不是复制一套后端，而是明确终端边界：

- 后台接口关注治理、录入、配置、监控
- 前台接口关注登录、可见知识库、可用模型、会话列表、流式问答

### 4.3 用户来源与权限边界

用户体系仍共用当前 `sys_user / sys_role / JWT / Redis` 这套基础能力，不新增独立前台账号体系。

前后台权限边界采用以下原则：

- 同一用户源
- 不同接口域
- 不同前端菜单和页面
- 不同会话终端标识

首期权限建议：

- 后台继续保留 `ADMIN`、`KB_ADMIN`、`AUDITOR`
- 前台新增 `APP_USER` 角色或等价的前台访问权限码

首期数据可见性基线：

- 前台只展示 `status=ENABLED` 的知识库与聊天模型
- 更细粒度的“按用户授权知识库”能力先不在本轮强行落地，后续再按组织需要扩展

### 4.4 知识库与模型的运行时解耦

当前系统中，知识库记录里存在 `chat_model_id` 与 `embedding_model_id`。其中：

- `embedding_model_id` 仍然属于知识库索引构建阶段的固定属性
- `chat_model_id` 不应继续被视为唯一运行时问答模型，而应收敛为“默认聊天模型”

前台问答的运行时规则调整为：

1. 检索阶段仍按选中知识库各自的向量索引执行
2. 生成阶段优先使用当前会话或当前提问显式指定的 `chatModelId`
3. 如果当前请求没有显式指定模型，再回退到知识库默认模型或系统默认模型

这样可以实现“模型独立、知识库可组合”的前台体验，更接近腾讯 ima 这类产品。

### 4.5 会话模型与终端隔离

当前 `chat_session` 仅按 `scene_type` 区分 `GENERAL` 与 `KNOWLEDGE_BASE`，这对新增前台还不够，因为：

- 后台管理台首页通用会话
- 前台首页通用会话
- 前台知识库内部会话

三者都不能共享同一个会话桶或 memory 桶。

因此建议将会话隔离维度扩为：

- `terminal_type`：终端类型，例如 `ADMIN`、`APP`
- `scene_type`：场景类型，例如 `GENERAL`、`KNOWLEDGE_BASE`

同时新增关系表：

- `chat_session_kb_rel`

用途：

- 记录当前会话选中的知识库集合
- 支持首页通过 `@知识库` 或选择器绑定多个知识库
- 进入具体知识库内部时，也统一通过该表保留当前绑定关系

这样可以避免把知识库 ID 硬编码进会话主表结构或会话唯一约束里。

### 4.6 `conversationId` 规则

当前后端知识库内会话的 `conversationId` 会显式拼接 `kbId`。该规则在“单知识库会话”下成立，但在“首页可动态选择多个知识库”的前台场景下会失去伸缩性。

新的规则建议改为：

- `conversationId` 只表达终端、场景、用户、会话
- 知识库集合通过 `chat_session_kb_rel` 表维护

推荐格式：

- 前台首页：`chat-terminal-app-scene-general-user-{userId}-session-{sessionId}`
- 前台知识库内：`chat-terminal-app-scene-kb-user-{userId}-session-{sessionId}`
- 后台管理台：`chat-terminal-admin-scene-{sceneType}-user-{userId}-session-{sessionId}`

这样做的原因：

- 首页与知识库内会话天然隔离
- 前后台同一用户不会串会话
- 多知识库选择不需要改变 memory 主键格式
- 后续如果会话绑定的知识库集合调整，不需要迁移 memory 数据

### 4.7 前台聊天交互设计

前台应提供两种入口：

### 4.7.1 首页通用聊天

能力要求：

- 默认纯模型对话
- 可手动选择聊天模型
- 可开启或关闭联网搜索
- 可通过 `@知识库` 或显式选择器绑定多个知识库
- 未绑定知识库时，走纯模型回答

### 4.7.2 知识库内部聊天

能力要求：

- 进入某个知识库详情后，默认带入该知识库
- 该场景与首页通用聊天使用不同会话列表
- 允许继续追加其他知识库，但默认应锁定当前知识库为首选知识库
- 该场景的会话历史不能与首页聊天互相污染

交互原则：

- 切换会话时恢复该会话已绑定的模型、联网开关、知识库集合
- 用户如果需要干净上下文，应通过“新建会话”获得新的 memory 桶

### 4.8 联网能力抽象

联网搜索不应直接写死到某个厂商 SDK 中，而应在基础设施层抽象：

- `WebSearchProvider`

抽象职责：

- 接收用户查询
- 返回有限条数的搜索摘要、标题、URL、抓取时间
- 对上层问答编排暴露统一结构

首期策略：

- 先定义接口与空实现
- 当 `webSearchEnabled=true` 且配置了实际 Provider 时，再把搜索结果拼到 prompt
- 未配置 Provider 时，直接忽略联网开关并给出明确日志

### 4.9 流式输出与服务端通知

前台问答主链路继续采用 `WebFlux + Flux + SSE`：

- `/api/app/chat/sessions/{sessionId}/messages/stream`

原因：

- 当前仓库已经有流式聊天基础
- 与 Spring AI 的流式输出模型兼容
- 前端实现简单，适合聊天打字机效果

注意：

- 前台聊天流式输出与后台文档解析 SSE 推送是两条不同链路
- 不应把“文档解析进度 SSE”与“聊天输出 SSE”混成一个统一频道

### 4.10 检索编排规则

前台问答时，编排层应按以下顺序执行：

1. 解析当前请求的 `chatModelId`、`selectedKbIds`、`webSearchEnabled`
2. 若存在知识库集合，则执行多知识库检索聚合
3. 若开启联网且 Provider 可用，则补充联网摘要上下文
4. 构造系统提示词与用户提示词
5. 调用运行时选中的聊天模型生成回答
6. 落库消息、引用、反馈数据

其中：

- 多知识库检索的结果聚合仍由后端编排层统一完成
- Controller 只负责接收参数，不承担任何检索拼装逻辑

## 5. 数据模型调整建议

本轮建议最小化调整如下：

### 5.1 `chat_session`

新增字段：

- `terminal_type VARCHAR(32) NOT NULL DEFAULT 'ADMIN'`
- 可选增加 `model_id BIGINT`，用于记录会话默认聊天模型
- 可选增加 `web_search_enabled BOOLEAN NOT NULL DEFAULT FALSE`

索引调整：

- 新增 `(user_id, terminal_type, scene_type)` 复合索引
- 现有首页单会话唯一索引调整为 `(user_id, terminal_type, scene_type)` 条件唯一

### 5.2 `chat_session_kb_rel`

建议新增表：

```sql
chat_session_kb_rel (
  id BIGSERIAL PRIMARY KEY,
  session_id BIGINT NOT NULL,
  kb_id BIGINT NOT NULL,
  sort_no INTEGER NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (session_id, kb_id)
)
```

用途：

- 保存当前会话绑定的知识库集合
- 支持一个会话绑定多个知识库
- 支持知识库内部聊天的默认绑定

### 5.3 `ai_model`

首期不强制增加“前台可见”字段，先按 `status=ENABLED + CHAT` 作为前台可选模型来源。

如果后续出现“后台可用但前台不可选”的强需求，再补：

- `app_visible`

## 6. API 设计建议

首期新增前台接口建议如下：

- `POST /api/app/auth/login`
- `POST /api/app/auth/refresh`
- `POST /api/app/auth/logout`
- `GET /api/app/auth/me`
- `GET /api/app/knowledge-bases`
- `GET /api/app/models`
- `POST /api/app/chat/sessions`
- `GET /api/app/chat/sessions`
- `GET /api/app/chat/sessions/{sessionId}/messages`
- `PUT /api/app/chat/sessions/{sessionId}/knowledge-bases`
- `POST /api/app/chat/sessions/{sessionId}/messages`
- `POST /api/app/chat/sessions/{sessionId}/messages/stream`
- `POST /api/app/chat/messages/{messageId}/feedback`

其中：

- `PUT /api/app/chat/sessions/{sessionId}/knowledge-bases` 用于显式更新会话的知识库集合
- 如果前端采用“发送消息时同时提交 selectedKbIds”，后端也可以在消息接口内隐式同步关系表，但仍建议保留显式接口，便于会话恢复与独立保存

## 7. 实施顺序

建议按以下顺序推进：

1. 先补架构文档、接口文档、SQL 草案
2. 后端扩展会话模型与 `/api/app` 最小接口域
3. 新建 `rag-chat-web` 工程骨架
4. 先完成首页通用对话最小闭环
5. 再补 `@知识库`、知识库内部聊天、模型切换、联网开关
6. 最后补视觉细节、历史恢复、体验优化

## 8. 主要风险

### 8.1 会话隔离不彻底

如果只加 `/api/app` 路径而不补 `terminal_type`，后台和前台同用户的通用会话会直接串历史。

### 8.2 运行时模型切换与知识库默认模型冲突

如果后端仍把 `kb.chat_model_id` 当作唯一回答模型，前台模型切换将失效。

### 8.3 多知识库选择后的上下文污染

如果用户频繁变更知识库集合但仍沿用同一会话，历史回答仍然会保留在 memory 中。这是可接受的产品行为，但前端必须提供“新建会话”入口，让用户显式获得干净上下文。

### 8.4 联网搜索供应商耦合

如果联网实现直接写进聊天编排逻辑，后续切换搜索来源会牵连核心链路，因此必须先做 `WebSearchProvider` 抽象。

## 9. 结论

本次方向收敛为：

- 保留后台管理端 `rag-admin-web`
- 新增独立问答前台 `rag-chat-web`
- 后端新增 `/api/app` 接口域
- 共用用户源，但通过 `terminal_type + scene_type + sessionId` 做会话隔离
- 通过 `chat_session_kb_rel` 支持首页多知识库问答与知识库内部问答
- 聊天模型在运行时可切换，知识库默认模型只作为兜底配置

这条路线兼顾了当前项目的可交付性、组织内使用场景和后续二次开发可扩展性。
