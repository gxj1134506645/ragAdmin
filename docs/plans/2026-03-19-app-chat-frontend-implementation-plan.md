# 独立问答前台与 `/api/app` 实施计划

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有单后端基础上新增独立问答前台 `rag-chat-web`，补齐 `/api/app` 接口域、终端隔离会话模型、多知识库选择、模型切换和联网开关。

**Architecture:** 保持 `rag-admin-server` 作为统一后端，在聊天域内补 `terminal_type + chat_session_kb_rel`，把前台问答入口收敛到 `/api/app/*`。新增 `rag-chat-web` 作为独立 Vue 3 前端，复用现有 JWT、SSE、Chat Memory 和检索编排能力，不单独建设前台账号体系。

**Tech Stack:** JDK 21、Spring Boot 3、MyBatis Plus、Flyway、PostgreSQL、Spring AI Alibaba、Spring AI Chat Memory、WebFlux Flux/SSE、Vue 3、TypeScript、Vite、Pinia、Element Plus。

---

## 文件边界总览

### 后端文档与数据库

- Modify: `docs/rag-admin-architecture.md`
- Modify: `docs/rag-admin-api-design.md`
- Modify: `docs/rag-admin-schema-v1.sql`
- Create: `rag-admin-server/src/main/resources/db/migration/V5__support_app_chat_portal.sql`

### 后端鉴权与前台接口

- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/auth/config/AuthWebMvcConfiguration.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/auth/service/AuthService.java`
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/app/controller/AppAuthController.java`
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/app/controller/AppKnowledgeBaseController.java`
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/app/controller/AppModelController.java`
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/app/controller/AppChatController.java`
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/app/dto/AppChatRequest.java`
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/app/dto/AppUpdateSessionKnowledgeBasesRequest.java`
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/app/dto/AppSessionPreferenceResponse.java`

### 后端聊天域

- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/chat/entity/ChatSessionEntity.java`
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/chat/entity/ChatSessionKnowledgeBaseRelEntity.java`
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/chat/mapper/ChatSessionKnowledgeBaseRelMapper.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/chat/service/ChatService.java`
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/app/service/AppChatService.java`
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/app/service/AppPortalService.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/infra/ai/chat/SpringAiConversationChatClient.java`
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/infra/search/WebSearchProvider.java`
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/infra/search/NoopWebSearchProvider.java`
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/infra/search/WebSearchSnippet.java`

### 后端测试

- Create: `rag-admin-server/src/test/java/com/ragadmin/server/app/service/AppChatServiceTest.java`
- Create: `rag-admin-server/src/test/java/com/ragadmin/server/web/AppApiWebMvcTest.java`
- Modify: `rag-admin-server/src/test/java/com/ragadmin/server/chat/service/ChatServiceTest.java`

### 前台工程

- Create: `rag-chat-web/package.json`
- Create: `rag-chat-web/tsconfig.json`
- Create: `rag-chat-web/tsconfig.app.json`
- Create: `rag-chat-web/tsconfig.node.json`
- Create: `rag-chat-web/vite.config.ts`
- Create: `rag-chat-web/index.html`
- Create: `rag-chat-web/src/main.ts`
- Create: `rag-chat-web/src/App.vue`
- Create: `rag-chat-web/src/style.css`
- Create: `rag-chat-web/src/router/index.ts`
- Create: `rag-chat-web/src/stores/auth.ts`
- Create: `rag-chat-web/src/api/http.ts`
- Create: `rag-chat-web/src/api/auth.ts`
- Create: `rag-chat-web/src/api/chat.ts`
- Create: `rag-chat-web/src/api/knowledge-base.ts`
- Create: `rag-chat-web/src/api/model.ts`
- Create: `rag-chat-web/src/types/api.ts`
- Create: `rag-chat-web/src/types/auth.ts`
- Create: `rag-chat-web/src/types/chat.ts`
- Create: `rag-chat-web/src/types/knowledge-base.ts`
- Create: `rag-chat-web/src/types/model.ts`
- Create: `rag-chat-web/src/layouts/AppChatLayout.vue`
- Create: `rag-chat-web/src/views/login/AppLoginView.vue`
- Create: `rag-chat-web/src/views/chat/AppChatHomeView.vue`
- Create: `rag-chat-web/src/views/chat/AppKnowledgeBaseChatView.vue`
- Create: `rag-chat-web/src/components/chat/AppChatWorkspace.vue`
- Create: `rag-chat-web/src/components/chat/KnowledgeBaseSelector.vue`
- Create: `rag-chat-web/src/components/chat/ModelSelector.vue`

## Chunk 1: 后端基线与契约

### Task 1: 更新主文档和数据库草案

**Files:**

- Modify: `docs/rag-admin-architecture.md`
- Modify: `docs/rag-admin-api-design.md`
- Modify: `docs/rag-admin-schema-v1.sql`

- [ ] **Step 1: 更新架构文档中的前端与接口分域描述**

补充以下内容：

- 新增 `rag-chat-web`
- `/api/app` 接口域
- 双前端单后端定位
- 运行时模型选择与知识库检索解耦

- [ ] **Step 2: 更新接口文档**

补充前台接口最小清单：

```text
POST /api/app/auth/login
GET  /api/app/knowledge-bases
GET  /api/app/models
POST /api/app/chat/sessions
GET  /api/app/chat/sessions
GET  /api/app/chat/sessions/{sessionId}/messages
PUT  /api/app/chat/sessions/{sessionId}/knowledge-bases
POST /api/app/chat/sessions/{sessionId}/messages/stream
```

- [ ] **Step 3: 更新 SQL 草案**

至少补齐：

```sql
ALTER TABLE chat_session ADD COLUMN terminal_type VARCHAR(32) NOT NULL DEFAULT 'ADMIN';
ALTER TABLE chat_session ADD COLUMN model_id BIGINT;
ALTER TABLE chat_session ADD COLUMN web_search_enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE chat_session_kb_rel (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    kb_id BIGINT NOT NULL,
    sort_no INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (session_id, kb_id)
);
```

- [ ] **Step 4: 自查术语一致性**

检查以下术语在三份主文档中保持一致：

- `rag-chat-web`
- `/api/app`
- `terminal_type`
- `chat_session_kb_rel`
- `WebSearchProvider`

- [ ] **Step 5: 提交**

```bash
git add docs/rag-admin-architecture.md docs/rag-admin-api-design.md docs/rag-admin-schema-v1.sql
git commit -m "docs(chat): 补充独立问答前台主文档设计"
```

### Task 2: 增加数据库迁移与会话实体字段

**Files:**

- Create: `rag-admin-server/src/main/resources/db/migration/V5__support_app_chat_portal.sql`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/chat/entity/ChatSessionEntity.java`
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/chat/entity/ChatSessionKnowledgeBaseRelEntity.java`
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/chat/mapper/ChatSessionKnowledgeBaseRelMapper.java`

- [ ] **Step 1: 先写 Flyway migration**

迁移内容包含：

- `chat_session` 增加 `terminal_type`
- `chat_session` 增加 `model_id`
- `chat_session` 增加 `web_search_enabled`
- 新建 `chat_session_kb_rel`
- 调整 `uk_chat_session_general_user` 唯一索引

- [ ] **Step 2: 运行 migration 验证 SQL 可执行**

Run:

```bash
mvn -q -pl rag-admin-server spring-boot:run -Dspring-boot.run.profiles=local
```

Expected:

- 应用启动时 Flyway 成功执行
- 无索引重名、旧数据默认值冲突或启动期 SQL 异常

- [ ] **Step 3: 修改实体和 Mapper**

要求：

- `ChatSessionEntity` 加上新增字段
- 新增 `ChatSessionKnowledgeBaseRelEntity`
- 新增对应 MyBatis Plus Mapper

- [ ] **Step 4: 编译验证**

Run:

```bash
mvn -q -pl rag-admin-server -DskipTests compile
```

Expected:

- 编译通过

- [ ] **Step 5: 提交**

```bash
git add rag-admin-server/src/main/resources/db/migration/V5__support_app_chat_portal.sql rag-admin-server/src/main/java/com/ragadmin/server/chat/entity/ChatSessionEntity.java rag-admin-server/src/main/java/com/ragadmin/server/chat/entity/ChatSessionKnowledgeBaseRelEntity.java rag-admin-server/src/main/java/com/ragadmin/server/chat/mapper/ChatSessionKnowledgeBaseRelMapper.java
git commit -m "feat(chat): 扩展前台问答会话模型"
```

### Task 3: 新增 `/api/app` 鉴权与只读查询接口

**Files:**

- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/auth/config/AuthWebMvcConfiguration.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/auth/service/AuthService.java`
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/app/controller/AppAuthController.java`
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/app/controller/AppKnowledgeBaseController.java`
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/app/controller/AppModelController.java`
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/app/service/AppPortalService.java`

- [ ] **Step 1: 写控制器测试或最小接口测试**

至少覆盖：

- `/api/app/auth/login`
- `/api/app/auth/me`
- `/api/app/knowledge-bases`
- `/api/app/models`

Run:

```bash
mvn -q -pl rag-admin-server -Dtest=AppApiWebMvcTest test
```

Expected:

- 新接口未实现前失败

- [ ] **Step 2: 扩展鉴权白名单与登录入口**

要求：

- 放通 `/api/app/auth/login`
- 复用现有 JWT/Redis
- 不单独复制整套 `AuthService`

- [ ] **Step 3: 实现前台可见知识库和模型查询**

要求：

- 只返回 `status=ENABLED`
- 模型仅返回 `model_type=CHAT`
- 返回结构保持 `code/message/data`

- [ ] **Step 4: 跑测试与编译**

Run:

```bash
mvn -q -pl rag-admin-server -Dtest=AppApiWebMvcTest test
mvn -q -pl rag-admin-server -DskipTests compile
```

Expected:

- 测试通过
- 编译通过

- [ ] **Step 5: 提交**

```bash
git add rag-admin-server/src/main/java/com/ragadmin/server/auth/config/AuthWebMvcConfiguration.java rag-admin-server/src/main/java/com/ragadmin/server/auth/service/AuthService.java rag-admin-server/src/main/java/com/ragadmin/server/app/controller/AppAuthController.java rag-admin-server/src/main/java/com/ragadmin/server/app/controller/AppKnowledgeBaseController.java rag-admin-server/src/main/java/com/ragadmin/server/app/controller/AppModelController.java rag-admin-server/src/main/java/com/ragadmin/server/app/service/AppPortalService.java rag-admin-server/src/test/java/com/ragadmin/server/web/AppApiWebMvcTest.java
git commit -m "feat(app): 新增前台鉴权与基础查询接口"
```

## Chunk 2: 问答编排与流式链路

### Task 4: 实现前台会话与知识库集合管理

**Files:**

- Create: `rag-admin-server/src/main/java/com/ragadmin/server/app/service/AppChatService.java`
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/app/dto/AppUpdateSessionKnowledgeBasesRequest.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/chat/service/ChatService.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/infra/ai/chat/SpringAiConversationChatClient.java`

- [ ] **Step 1: 为 `AppChatService` 写失败测试**

至少覆盖：

- 首页会话与知识库内会话的 `terminal_type=APP`
- 更新会话知识库集合时的用户隔离
- 恢复会话时能回读知识库集合

- [ ] **Step 2: 实现会话创建和会话知识库关系维护**

要求：

- 首页场景支持空知识库集合
- 知识库内场景默认写入当前知识库关系
- 更新集合时采用“删除旧关系 + 批量插入新关系”的简单策略

- [ ] **Step 3: 重构 `conversationId` 生成规则**

调整为：

```text
chat-terminal-{terminalType}-scene-{sceneType}-user-{userId}-session-{sessionId}
```

禁止继续把多知识库集合硬编码进 `conversationId`

- [ ] **Step 4: 跑测试**

Run:

```bash
mvn -q -pl rag-admin-server -Dtest=AppChatServiceTest,ChatServiceTest test
```

Expected:

- 会话隔离与知识库集合恢复测试通过

- [ ] **Step 5: 提交**

```bash
git add rag-admin-server/src/main/java/com/ragadmin/server/app/service/AppChatService.java rag-admin-server/src/main/java/com/ragadmin/server/app/dto/AppUpdateSessionKnowledgeBasesRequest.java rag-admin-server/src/main/java/com/ragadmin/server/chat/service/ChatService.java rag-admin-server/src/main/java/com/ragadmin/server/infra/ai/chat/SpringAiConversationChatClient.java rag-admin-server/src/test/java/com/ragadmin/server/app/service/AppChatServiceTest.java rag-admin-server/src/test/java/com/ragadmin/server/chat/service/ChatServiceTest.java
git commit -m "feat(chat): 支持前台会话隔离与多知识库绑定"
```

### Task 5: 实现运行时模型切换与联网开关

**Files:**

- Create: `rag-admin-server/src/main/java/com/ragadmin/server/infra/search/WebSearchProvider.java`
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/infra/search/NoopWebSearchProvider.java`
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/infra/search/WebSearchSnippet.java`
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/app/dto/AppChatRequest.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/chat/service/ChatService.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/retrieval/service/RetrievalService.java`

- [x] **Step 1: 为模型切换和联网开关写测试**

至少覆盖：

- 请求显式传入 `chatModelId` 时优先使用该模型
- 未传模型时回退默认模型
- `webSearchEnabled=true` 且 Provider 不可用时不抛错

- [x] **Step 2: 实现 `WebSearchProvider` 抽象**

首版最小接口：

```java
List<WebSearchSnippet> search(String query, int topK);
```

并提供 `NoopWebSearchProvider`

- [x] **Step 3: 在聊天编排中接入运行时参数**

要求：

- `selectedKbIds` 控制检索范围
- `chatModelId` 控制生成模型
- `webSearchEnabled` 决定是否拼接联网上下文

- [x] **Step 4: 跑测试与编译**

Run:

```bash
mvn -q -pl rag-admin-server -Dtest=AppChatServiceTest,ChatServiceTest test
mvn -q -pl rag-admin-server -DskipTests compile
```

Expected:

- 后端测试与编译通过

执行说明：

- 当前已在 `AppChatService` 中接入 `chatModelId` 与 `webSearchEnabled` 的运行时编排
- 已新增 `WebSearchProvider`、`WebSearchSnippet` 与 `NoopWebSearchProvider`
- 当联网 Provider 不可用或未配置时，前台问答链路会记录日志并自动降级为空结果
- 后台管理端 `ChatService` 暂未复用联网搜索抽象，后续如需要后台问答统一能力，再单独补齐

- [ ] **Step 5: 提交**

```bash
git add rag-admin-server/src/main/java/com/ragadmin/server/infra/search/WebSearchProvider.java rag-admin-server/src/main/java/com/ragadmin/server/infra/search/NoopWebSearchProvider.java rag-admin-server/src/main/java/com/ragadmin/server/infra/search/WebSearchSnippet.java rag-admin-server/src/main/java/com/ragadmin/server/app/dto/AppChatRequest.java rag-admin-server/src/main/java/com/ragadmin/server/chat/service/ChatService.java rag-admin-server/src/main/java/com/ragadmin/server/retrieval/service/RetrievalService.java
git commit -m "feat(chat): 支持前台模型切换与联网开关"
```

### Task 6: 新增 `/api/app/chat` 流式问答接口

**Files:**

- Create: `rag-admin-server/src/main/java/com/ragadmin/server/app/controller/AppChatController.java`
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/app/dto/AppSessionPreferenceResponse.java`
- Modify: `rag-admin-server/src/test/java/com/ragadmin/server/web/AppApiWebMvcTest.java`

- [ ] **Step 1: 先写接口测试**

至少覆盖：

- 创建会话
- 查询会话列表
- 查询消息列表
- 更新知识库集合
- 流式输出接口返回 `text/event-stream`

- [ ] **Step 2: 实现控制器和 DTO**

要求：

- 入参只使用前台 DTO
- 返回统一 `ApiResponse`
- 流式接口返回 `Flux<...>`

- [ ] **Step 3: 端到端验证 SSE**

Run:

```bash
mvn -q -pl rag-admin-server -Dtest=AppApiWebMvcTest test
```

Expected:

- 前台聊天接口测试通过

- [ ] **Step 4: 手工验流**

Run:

```bash
curl -N -X POST http://127.0.0.1:9212/api/app/chat/sessions/1/messages/stream ^
  -H "Authorization: Bearer <token>" ^
  -H "Content-Type: application/json" ^
  -d "{\"question\":\"你好\",\"selectedKbIds\":[],\"webSearchEnabled\":false}"
```

Expected:

- 持续收到 `delta` 事件
- 最终收到 `complete` 事件

- [ ] **Step 5: 提交**

```bash
git add rag-admin-server/src/main/java/com/ragadmin/server/app/controller/AppChatController.java rag-admin-server/src/main/java/com/ragadmin/server/app/dto/AppSessionPreferenceResponse.java rag-admin-server/src/test/java/com/ragadmin/server/web/AppApiWebMvcTest.java
git commit -m "feat(app): 新增前台流式问答接口"
```

## Chunk 3: 独立前台工程

### Task 7: 初始化 `rag-chat-web` 工程骨架

**Files:**

- Create: `rag-chat-web/package.json`
- Create: `rag-chat-web/tsconfig.json`
- Create: `rag-chat-web/tsconfig.app.json`
- Create: `rag-chat-web/tsconfig.node.json`
- Create: `rag-chat-web/vite.config.ts`
- Create: `rag-chat-web/index.html`
- Create: `rag-chat-web/src/main.ts`
- Create: `rag-chat-web/src/App.vue`
- Create: `rag-chat-web/src/style.css`
- Create: `rag-chat-web/src/router/index.ts`

- [ ] **Step 1: 初始化 Vite Vue 3 工程**

要求：

- 不复制 `rag-admin-web` 的后台布局
- 使用独立端口，例如 `5174`
- `/api` 继续代理到 `9212`

- [ ] **Step 2: 建立基础目录和别名**

至少创建：

- `src/api`
- `src/router`
- `src/stores`
- `src/views`
- `src/components`
- `src/types`

- [ ] **Step 3: 构建验证**

Run:

```bash
npm --prefix rag-chat-web install
npm --prefix rag-chat-web run build
```

Expected:

- 工程首次构建通过

- [ ] **Step 4: 提交**

```bash
git add rag-chat-web
git commit -m "feat(app-web): 初始化独立问答前台工程"
```

### Task 8: 完成登录态与页面骨架

**Files:**

- Create: `rag-chat-web/src/stores/auth.ts`
- Create: `rag-chat-web/src/api/http.ts`
- Create: `rag-chat-web/src/api/auth.ts`
- Create: `rag-chat-web/src/types/api.ts`
- Create: `rag-chat-web/src/types/auth.ts`
- Create: `rag-chat-web/src/layouts/AppChatLayout.vue`
- Create: `rag-chat-web/src/views/login/AppLoginView.vue`

- [ ] **Step 1: 接入 `/api/app/auth/*`**

要求：

- 登录、刷新、登出、当前用户
- 本地存储 `accessToken`
- 未登录时路由守卫跳登录页

- [ ] **Step 2: 搭建前台布局**

布局建议：

- 左侧窄栏显示会话入口或知识库入口
- 中间为主聊天区
- 顶部保留模型切换和联网开关位置

- [ ] **Step 3: 构建验证**

Run:

```bash
npm --prefix rag-chat-web run build
```

Expected:

- 登录页与布局代码编译通过

- [ ] **Step 4: 提交**

```bash
git add rag-chat-web/src/stores/auth.ts rag-chat-web/src/api/http.ts rag-chat-web/src/api/auth.ts rag-chat-web/src/types/api.ts rag-chat-web/src/types/auth.ts rag-chat-web/src/layouts/AppChatLayout.vue rag-chat-web/src/views/login/AppLoginView.vue
git commit -m "feat(app-web): 完成前台登录与布局骨架"
```

### Task 9: 完成首页聊天工作台

**Files:**

- Create: `rag-chat-web/src/api/chat.ts`
- Create: `rag-chat-web/src/api/model.ts`
- Create: `rag-chat-web/src/api/knowledge-base.ts`
- Create: `rag-chat-web/src/types/chat.ts`
- Create: `rag-chat-web/src/types/knowledge-base.ts`
- Create: `rag-chat-web/src/types/model.ts`
- Create: `rag-chat-web/src/views/chat/AppChatHomeView.vue`
- Create: `rag-chat-web/src/components/chat/AppChatWorkspace.vue`
- Create: `rag-chat-web/src/components/chat/KnowledgeBaseSelector.vue`
- Create: `rag-chat-web/src/components/chat/ModelSelector.vue`

- [ ] **Step 1: 接前台聊天与列表接口**

包括：

- 会话列表
- 消息列表
- 流式发送
- 模型列表
- 知识库列表

- [ ] **Step 2: 实现首页聊天体验**

要求：

- 支持新建会话
- 支持模型切换
- 支持联网开关
- 支持知识库多选
- 支持流式增量渲染

- [ ] **Step 3: 构建验证**

Run:

```bash
npm --prefix rag-chat-web run build
```

Expected:

- 首页聊天页编译通过

- [ ] **Step 4: 手工联调**

检查项：

- 不选知识库时可纯模型回答
- 选多个知识库时可正常提问
- 模型切换后新问题生效
- 联网开关在无 Provider 时不报前端错误

- [ ] **Step 5: 提交**

```bash
git add rag-chat-web/src/api/chat.ts rag-chat-web/src/api/model.ts rag-chat-web/src/api/knowledge-base.ts rag-chat-web/src/types/chat.ts rag-chat-web/src/types/knowledge-base.ts rag-chat-web/src/types/model.ts rag-chat-web/src/views/chat/AppChatHomeView.vue rag-chat-web/src/components/chat/AppChatWorkspace.vue rag-chat-web/src/components/chat/KnowledgeBaseSelector.vue rag-chat-web/src/components/chat/ModelSelector.vue
git commit -m "feat(app-web): 完成首页聊天工作台"
```

### Task 10: 完成知识库内部聊天场景

**Files:**

- Create: `rag-chat-web/src/views/chat/AppKnowledgeBaseChatView.vue`
- Modify: `rag-chat-web/src/router/index.ts`
- Modify: `rag-chat-web/src/components/chat/AppChatWorkspace.vue`

- [ ] **Step 1: 新增知识库内部聊天路由**

建议路由：

```text
/chat
/knowledge-bases/:kbId/chat
```

- [ ] **Step 2: 让工作台支持两种场景**

要求：

- 首页场景允许空知识库
- 知识库内部场景默认绑定当前 `kbId`
- 两种场景的会话列表独立恢复

- [ ] **Step 3: 构建与手工验证**

Run:

```bash
npm --prefix rag-chat-web run build
```

检查项：

- 从首页进入知识库内部聊天时，会话不串
- 从知识库内部返回首页时，会话不串

- [ ] **Step 4: 提交**

```bash
git add rag-chat-web/src/views/chat/AppKnowledgeBaseChatView.vue rag-chat-web/src/router/index.ts rag-chat-web/src/components/chat/AppChatWorkspace.vue
git commit -m "feat(app-web): 支持知识库内部聊天场景"
```

## Chunk 4: 联调与收口

### Task 11: 更新 README 与联调说明

**Files:**

- Modify: `README.md`
- Modify: `docs/rag-admin-backend-debug-guide.md`
- Modify: `docs/rag-admin-api-acceptance.md`

- [x] **Step 1: 更新仓库结构说明**

补充：

- `rag-chat-web`
- 前台启动命令
- 前后台端口说明

- [x] **Step 2: 补充前台联调验收步骤**

至少包含：

- 前台登录
- 首页通用聊天
- 知识库内部聊天
- 模型切换
- 多知识库选择
- SSE 流式输出验收

- [x] **Step 3: 最终构建验收**

Run:

```bash
mvn -q -pl rag-admin-server -DskipTests compile
mvn -q -pl rag-admin-server test
npm --prefix rag-admin-web run build
npm --prefix rag-chat-web run build
```

Expected:

- 后端测试通过
- 两个前端都能构建

执行说明：

- 已执行 `mvn -q -pl rag-admin-server test`，测试通过
- 已执行 `npm --prefix rag-admin-web run build` 与 `npm --prefix rag-chat-web run build`
- 受限沙箱内构建会因 `esbuild spawn EPERM` 出现假失败；在正常 Shell 环境中两端构建均已验证通过

- [ ] **Step 4: 最终提交**

```bash
git add README.md docs/rag-admin-backend-debug-guide.md docs/rag-admin-api-acceptance.md
git commit -m "docs(app): 补充独立问答前台联调说明"
```

## 交付检查清单

- [ ] `/api/app` 与 `/api/admin` 接口域边界清晰
- [ ] `terminal_type` 已接入会话隔离
- [ ] `chat_session_kb_rel` 已支持多知识库绑定
- [ ] 前台支持首页通用聊天与知识库内部聊天
- [ ] 前台支持模型切换、联网开关、知识库多选
- [ ] 会话记忆不会在前后台之间串历史
- [ ] 后端测试、前端构建、SSE 联调全部通过

Plan complete and saved to `docs/plans/2026-03-19-app-chat-frontend-implementation-plan.md`. Ready to execute?
