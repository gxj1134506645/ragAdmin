# 聊天受控 Markdown 渲染实施计划

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为问答前台补齐受控 Markdown 子集渲染能力，避免回答中的 `*`、`**` 等 Markdown 符号以纯文本形式裸露显示，同时保持后端结构化元数据与前端安全渲染边界清晰。

**Architecture:** 后端统一声明聊天正文采用 `text/markdown` 作为默认内容类型，并通过系统提示词约束模型只输出受控 Markdown 子集；前端仅对助手消息按白名单 Markdown 渲染，用户消息与结构化元数据继续保持显式字段控制，不把主回答全文改造成 JSON 结构化输出。

**Tech Stack:** Spring Boot 3、Vue 3、TypeScript、Vite、Vitest、`marked`、`dompurify`

---

## 文件边界

- 修改: `rag-admin-server/src/main/java/com/ragadmin/server/app/service/AppChatService.java`
  - 收敛前台通用问答/知识库问答系统提示词，明确受控 Markdown 子集约束。
- 修改: `rag-admin-server/src/main/java/com/ragadmin/server/chat/service/ChatService.java`
  - 同步后台问答链路的系统提示词与消息返回内容类型。
- 修改: `rag-admin-server/src/main/java/com/ragadmin/server/chat/service/ChatExchangePersistenceService.java`
  - 统一在消息响应中回传正文内容类型。
- 修改: `rag-admin-server/src/main/java/com/ragadmin/server/chat/dto/ChatMessageResponse.java`
  - 为历史消息接口增加正文内容类型字段。
- 修改: `rag-admin-server/src/main/java/com/ragadmin/server/chat/dto/ChatResponse.java`
  - 为非流式/流式 complete 返回增加正文内容类型字段。
- 修改: `rag-admin-server/src/main/java/com/ragadmin/server/chat/dto/ChatStreamEventResponse.java`
  - 将 complete 事件透传正文内容类型。
- 修改: `rag-admin-server/src/test/java/com/ragadmin/server/web/AppApiWebMvcTest.java`
  - 更新接口测试断言，验证消息返回中包含 Markdown 内容类型。
- 修改: `rag-admin-server/src/test/java/com/ragadmin/server/chat/service/ChatExchangePersistenceServiceTest.java`
  - 验证持久化返回默认正文内容类型。
- 修改: `rag-chat-web/package.json`
  - 增加 Markdown 渲染与净化依赖。
- 修改: `rag-chat-web/package-lock.json`
  - 锁定新增依赖版本。
- 修改: `rag-chat-web/src/types/chat.ts`
  - 为聊天消息、返回结果、流式事件补充正文内容类型类型定义。
- 修改: `rag-chat-web/src/api/chat.ts`
  - 映射服务端新增正文内容类型字段。
- 新增: `rag-chat-web/src/components/chat/ChatMarkdownContent.vue`
  - 负责将助手消息按受控 Markdown 子集渲染成安全 HTML。
- 新增: `rag-chat-web/src/utils/chat-markdown.ts`
  - 集中维护 Markdown 解析器、白名单净化和回退策略。
- 新增: `rag-chat-web/src/utils/chat-markdown.test.ts`
  - 验证危险标签被清理、常用 Markdown 子集可渲染。
- 修改: `rag-chat-web/src/components/chat/AppChatWorkspace.vue`
  - 仅在助手消息展示区接入 `ChatMarkdownContent`，保持现有编排逻辑不扩散。

## 任务拆分

### 任务 1：收敛后端 Markdown 内容契约

**Files:**
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/app/service/AppChatService.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/chat/service/ChatService.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/chat/service/ChatExchangePersistenceService.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/chat/dto/ChatMessageResponse.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/chat/dto/ChatResponse.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/chat/dto/ChatStreamEventResponse.java`

- [ ] 定义聊天正文内容类型常量，V1 固定返回 `text/markdown`
- [ ] 优化系统提示词，明确允许的 Markdown 子集与禁止项
- [ ] 将历史消息、普通回答、流式完成事件统一透传正文内容类型
- [ ] 保持现有结构化元数据字段不变，不把正文改造成 JSON schema

### 任务 2：补齐前端受控 Markdown 渲染

**Files:**
- Modify: `rag-chat-web/package.json`
- Modify: `rag-chat-web/package-lock.json`
- Modify: `rag-chat-web/src/types/chat.ts`
- Modify: `rag-chat-web/src/api/chat.ts`
- Create: `rag-chat-web/src/components/chat/ChatMarkdownContent.vue`
- Create: `rag-chat-web/src/utils/chat-markdown.ts`
- Modify: `rag-chat-web/src/components/chat/AppChatWorkspace.vue`

- [ ] 安装并接入最小 Markdown 解析与 HTML 净化依赖
- [ ] 在独立工具中配置受控 Markdown 子集与白名单净化
- [ ] 新建独立消息渲染组件，避免把 `v-html` 直接散落到工作区大组件
- [ ] 助手消息按 `contentType` 渲染，用户消息继续保持纯文本
- [ ] 为代码块、列表、引用、粗体补最小样式，避免默认样式失真

### 任务 3：验证与回归

**Files:**
- Modify: `rag-admin-server/src/test/java/com/ragadmin/server/web/AppApiWebMvcTest.java`
- Modify: `rag-admin-server/src/test/java/com/ragadmin/server/chat/service/ChatExchangePersistenceServiceTest.java`
- Create: `rag-chat-web/src/utils/chat-markdown.test.ts`

- [ ] 更新后端测试构造器与断言，验证 `text/markdown` 返回
- [ ] 新增前端 Markdown 工具测试，覆盖安全清洗和子集渲染
- [ ] 执行前端 `npm test` 与 `npm run build`
- [ ] 执行服务端相关测试，确认 DTO 变更未破坏既有接口

