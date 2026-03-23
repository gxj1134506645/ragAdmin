# RAG 知识库管理系统接口设计 V1

## 目录

- [1. 设计目标](#1-设计目标)
- [2. 接口约定](#2-接口约定)
- [3. 认证与用户接口](#3-认证与用户接口)
- [4. 模型管理接口](#4-模型管理接口)
- [5. 知识库管理接口](#5-知识库管理接口)
- [6. 文档与任务接口](#6-文档与任务接口)
- [7. RAG 问答接口](#7-rag-问答接口)
- [8. 审计与运维接口](#8-审计与运维接口)
- [9. 首期实现优先级](#9-首期实现优先级)

## 1. 设计目标

本接口设计面向首期内部 RAG 管理平台，目标是明确前后端交互边界、后端模块职责和后续实现顺序。

首期 API 只覆盖以下核心闭环：

- 用户登录和权限校验
- 模型配置查询
- 知识库和文档管理
- 文档上传与解析任务跟踪
- 单知识库 RAG 问答
- 问答引用溯源

## 2. 接口约定

### 2.1 基础路径

- 管理台 API 前缀：`/api/admin`
- 问答前台 API 前缀：`/api/app`
- 内部任务回调前缀：`/api/internal`

### 2.2 统一响应

成功响应：

```json
{
  "code": "OK",
  "message": "success",
  "data": {}
}
```

失败响应：

```json
{
  "code": "KB_NOT_FOUND",
  "message": "知识库不存在",
  "data": null
}
```

### 2.3 分页约定

请求参数：

- `pageNo`
- `pageSize`

分页响应：

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "list": [],
    "pageNo": 1,
    "pageSize": 20,
    "total": 100
  }
}
```

### 2.4 鉴权约定

- 登录成功后返回 `accessToken` 与 `refreshToken`
- 管理台与问答前台请求头统一使用 `Authorization: Bearer <token>`
- 后端认证与授权统一基于 `sa-token`
- 登录态、在线会话热数据与强制下线标记统一存储在 Redis
- 前后台登录态按 `loginType=admin/app` 隔离
- 个人问答会话历史与消息事实数据必须持久化到 PostgreSQL，不与登录态热数据混存

### 2.5 状态字段建议

- 通用启停状态：`ENABLED` / `DISABLED`
- 文档解析状态：`PENDING` / `PROCESSING` / `SUCCESS` / `FAILED`
- 任务状态：`WAITING` / `RUNNING` / `SUCCESS` / `FAILED` / `CANCELED`

## 3. 认证与用户接口

### 3.1 用户登录

- `POST /api/admin/auth/login`
- `POST /api/app/auth/login`

请求体：

```json
{
  "loginId": "admin 或 13800000000",
  "password": "******"
}
```

响应体：

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "accessToken": "token-value",
    "refreshToken": "refresh-token-value",
    "expiresIn": 7200,
    "refreshExpiresIn": 604800,
    "user": {
      "id": 1,
      "username": "admin",
      "displayName": "系统管理员",
      "roles": ["ADMIN"],
      "permissions": ["DASHBOARD_VIEW", "USER_MANAGE"]
    }
  }
}
```

### 3.2 获取当前用户信息

- `GET /api/admin/auth/me`
- `GET /api/app/auth/me`

响应体补充约定：

- 后台 `GET /api/admin/auth/me` 必须返回 `roles + permissions`
- 前台 `GET /api/app/auth/me` 至少返回用户基本信息与角色信息
- 后台前端的菜单、路由和关键高风险按钮统一基于 `permissions` 渲染

### 3.3 刷新 Token

- `POST /api/admin/auth/refresh`
- `POST /api/app/auth/refresh`

请求体：

```json
{
  "refreshToken": "refresh-token-value"
}
```

### 3.4 用户退出

- `POST /api/admin/auth/logout`
- `POST /api/app/auth/logout`

### 3.4 用户列表

- `GET /api/admin/users`

查询参数：

- `keyword`
- `status`
- `pageNo`
- `pageSize`

### 3.5 新增用户

- `POST /api/admin/users`

### 3.6 更新用户

- `PUT /api/admin/users/{userId}`

### 3.7 配置用户角色

- `PUT /api/admin/users/{userId}/roles`

请求体：

```json
{
  "roleCodes": ["KB_ADMIN"]
}
```

### 3.8 在线用户会话列表

- `GET /api/admin/user-sessions`

查询参数：

- `keyword`
- `roleCode`
- `onlineScope`
- `pageNo`
- `pageSize`

说明：

- 返回用户维度的在线会话概览
- 首期不返回设备、浏览器或单 Token 维度明细

### 3.9 在线用户会话详情

- `GET /api/admin/user-sessions/{userId}`

说明：

- 返回指定用户在 `admin`、`app` 两个登录域的在线状态
- 同时返回最近登录时间与最近活跃时间

### 3.10 强制用户下线

- `POST /api/admin/user-sessions/{userId}/kickout`

请求体：

```json
{
  "scope": "all",
  "reason": "管理员手动下线"
}
```

说明：

- `scope` 支持：`admin`、`app`、`all`
- 首期只支持按 `userId` 维度强制下线，不做设备粒度治理
- 强制下线操作必须写入审计日志

## 4. 模型管理接口

模型管理接口由 `model` 领域负责，前端只感知“模型提供方 + 模型定义 + 能力类型”。

### 4.1 模型提供方列表

- `GET /api/admin/model-providers`

### 4.2 新增模型提供方

- `POST /api/admin/model-providers`

请求体：

```json
{
  "providerCode": "BAILIAN",
  "providerName": "阿里百炼",
  "baseUrl": "https://dashscope.aliyuncs.com",
  "apiKeySecretRef": "vault://rag/bailian/api-key",
  "status": "ENABLED"
}
```

### 4.3 模型定义列表

- `GET /api/admin/models`

查询参数：

- `providerCode`
- `capabilityType`
- `status`
- `pageNo`
- `pageSize`

### 4.3.1 前台可用聊天模型列表

- `GET /api/app/models`

说明：

- 仅返回 `status=ENABLED` 且 `modelType=CHAT` 的模型
- 作为问答前台运行时可选聊天模型数据源

### 4.4 新增模型定义

- `POST /api/admin/models`

请求体：

```json
{
  "providerId": 1,
  "modelCode": "qwen-max",
  "modelName": "通义千问 Max",
  "capabilityTypes": ["TEXT_GENERATION"],
  "modelType": "CHAT",
  "maxTokens": 8000,
  "temperatureDefault": 0.7,
  "status": "ENABLED"
}
```

### 4.5 更新模型定义

- `PUT /api/admin/models/{modelId}`

### 4.5.1 设置默认聊天模型

- `POST /api/admin/models/{modelId}/default-chat-model`

说明：

- 只允许将启用中的聊天模型设为默认聊天模型
- 全局默认聊天模型只能且必须有一个
- 运行时只读取后台模型管理中手动设置的默认聊天模型，不再回退 `application.yml` 的聊天默认配置

### 4.6 模型能力探活

- `POST /api/admin/models/{modelId}/health-check`

用途：

- 校验配置是否可用
- 验证 API Key 是否有效
- 验证该模型是否具备声明的能力类型

## 5. 知识库管理接口

### 5.1 知识库列表

- `GET /api/admin/knowledge-bases`

查询参数：

- `keyword`
- `status`
- `pageNo`
- `pageSize`

### 5.1.1 前台可见知识库列表

- `GET /api/app/knowledge-bases`

查询参数：

- `keyword`
- `pageNo`
- `pageSize`

说明：

- 首期仅返回 `status=ENABLED` 的知识库
- 作为问答前台的知识库选择数据源

### 5.2 创建知识库

- `POST /api/admin/knowledge-bases`

说明：

- `chatModelId` 与 `embeddingModelId` 必须来自后台模型管理中已入库的模型数据
- 管理台模型下拉数据源应来自 `GET /api/admin/models` 或等价后台接口，不直接读取配置文件
- 若知识库未显式绑定聊天模型，后端只回退到后台模型管理中设置的默认聊天模型，不再读取配置文件聊天默认值

请求体：

```json
{
  "kbCode": "company-policy",
  "kbName": "公司制度库",
  "description": "制度、流程、规范文档",
  "embeddingModelId": 2,
  "chatModelId": 1,
  "retrieveTopK": 5,
  "rerankEnabled": true,
  "status": "ENABLED"
}
```

### 5.3 更新知识库

- `PUT /api/admin/knowledge-bases/{kbId}`

### 5.4 获取知识库详情

- `GET /api/admin/knowledge-bases/{kbId}`

### 5.5 启停知识库

- `PUT /api/admin/knowledge-bases/{kbId}/status`

### 5.6 知识库文档列表

- `GET /api/admin/knowledge-bases/{kbId}/documents`

查询参数：

- `keyword`
- `parseStatus`
- `enabled`
- `pageNo`
- `pageSize`

## 6. 文档与任务接口

### 6.1 获取上传凭证

- `POST /api/admin/files/upload-url`

用途：

- 管理台直传 MinIO
- 服务端生成对象 Key 和临时上传信息

请求体：

```json
{
  "fileName": "员工手册.pdf",
  "contentType": "application/pdf",
  "bizType": "KB_DOCUMENT"
}
```

响应体：

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "bucket": "ragadmin",
    "objectKey": "kb/20260309/uuid/employee-manual.pdf",
    "uploadUrl": "presigned-url"
  }
}
```

### 6.2 新增文档记录

- `POST /api/admin/knowledge-bases/{kbId}/documents`

请求体：

```json
{
  "docName": "员工手册.pdf",
  "docType": "PDF",
  "storageBucket": "ragadmin",
  "storageObjectKey": "kb/20260309/uuid/employee-manual.pdf"
}
```

### 6.3 获取文档详情

- `GET /api/admin/documents/{documentId}`

### 6.3.1 新增文档版本

- `POST /api/admin/documents/{documentId}/versions`

请求体：

```json
{
  "storageBucket": "ragadmin",
  "storageObjectKey": "kb/20260309/uuid/employee-manual-v2.pdf",
  "contentHash": "sha256-value"
}
```

### 6.3.2 激活文档版本

- `PUT /api/admin/documents/{documentId}/versions/{versionId}/activate`

### 6.4 更新文档启停状态

- `PUT /api/admin/documents/{documentId}/status`

### 6.5 触发文档解析

- `POST /api/admin/documents/{documentId}/parse`

说明：

- 仅投递任务，不同步执行
- 若文档已有进行中的解析任务，应返回幂等提示

### 6.5.1 文档版本列表

- `GET /api/admin/documents/{documentId}/versions`

查询参数：

- `pageNo`
- `pageSize`

### 6.6 文档切片列表

- `GET /api/admin/documents/{documentId}/chunks`

查询参数：

- `pageNo`
- `pageSize`

### 6.7 任务列表

- `GET /api/admin/tasks`

查询参数：

- `taskType`
- `taskStatus`
- `bizId`
- `pageNo`
- `pageSize`

### 6.8 任务详情

- `GET /api/admin/tasks/{taskId}`

### 6.9 重试任务

- `POST /api/admin/tasks/{taskId}/retry`

说明：

- 仅 `FAILED` 或 `CANCELED` 状态允许重试
- 重试必须沿用原任务对应的 `documentVersionId`

### 6.10 内部任务回调

- `POST /api/internal/tasks/{taskId}/complete`

用途：

- Worker 完成处理后回写任务状态
- 回写文档解析状态和统计信息
- 请求头必须携带：`X-Internal-Token`

请求体：

```json
{
  "taskStatus": "SUCCESS",
  "errorMessage": null,
  "parseStatus": "SUCCESS"
}
```

## 7. RAG 问答接口

说明：

- 当前问答主场景统一收口到问答前台 `rag-chat-web`
- 后台管理端不再承载独立问答入口，也不再暴露 `/api/admin/chat/**`
- 会话隔离至少依赖 `terminalType + sceneType + sessionId`
- 前台知识库集合通过会话关系表维护，运行时允许切换聊天模型与联网开关

### 7.1 前台创建会话

- `POST /api/app/chat/sessions`

请求体：

```json
{
  "sceneType": "GENERAL",
  "kbId": null,
  "sessionName": "今天的工作梳理",
  "chatModelId": 1,
  "webSearchEnabled": false,
  "selectedKbIds": [1, 3]
}
```

说明：

- 前台首页通用会话允许 `selectedKbIds` 为空
- 前台知识库内会话创建时，`kbId` 作为当前主知识库锚点，同时仍会把知识库集合写入关系表

### 7.2 前台会话列表

- `GET /api/app/chat/sessions`

查询参数：

- `sceneType`
- `kbId`
- `pageNo`
- `pageSize`

### 7.3 前台获取会话消息

- `GET /api/app/chat/sessions/{sessionId}/messages`

说明：

- 每条消息除 `question`、`answer`、`references`、`feedbackType`、`feedbackComment` 外，还会返回 `metadata`
- 当前 `metadata` 最小字段为：`confidence`、`hasKnowledgeBaseEvidence`、`needFollowUp`
- 对于历史老消息或尚未补写元数据的消息，`metadata` 允许为空

### 7.4 前台更新会话元数据

- `PUT /api/app/chat/sessions/{sessionId}`

请求体：

```json
{
  "sessionName": "新的会话名称",
  "chatModelId": 1,
  "webSearchEnabled": false
}
```

说明：

- 前台会话名称、显式聊天模型和联网开关统一在该接口内更新
- `chatModelId=null` 表示清空显式模型，回退到知识库显式聊天模型或后台默认聊天模型
- 前台工作台切换模型或联网开关后，会使用该接口即时持久化当前会话偏好

### 7.5 前台更新会话知识库集合

- `PUT /api/app/chat/sessions/{sessionId}/knowledge-bases`

请求体：

```json
{
  "selectedKbIds": [1, 3, 5]
}
```

### 7.6 前台发起流式问答

- `POST /api/app/chat/sessions/{sessionId}/messages/stream`
- `Content-Type: application/json`
- `Accept: text/event-stream`

请求体：

```json
{
  "question": "请结合制度库和研发规范库总结上线前检查项",
  "chatModelId": 1,
  "selectedKbIds": [1, 3],
  "webSearchEnabled": true
}
```

说明：

- `selectedKbIds` 为空时，走纯模型问答
- `chatModelId` 为运行时模型选择，优先级高于知识库默认聊天模型；最终兜底为后台模型管理中设置的默认聊天模型
- `webSearchEnabled=true` 时，由后端按 `WebSearchProvider` 配置决定是否补充联网搜索上下文
- 控制器直接返回 `Flux<ChatStreamEventResponse>`，仍通过 `text/event-stream` 传输；前端按响应体中的 `eventType` 判断事件语义，不再依赖额外的 `event:` / `id:` SSE 包装字段
- 流式 `DELTA` 事件只承载正文增量；`COMPLETE` 事件除 `messageId`、`answer`、`references`、`usage` 外，还会补充 `metadata`
- 当前 `metadata` 最小字段为：`confidence`、`hasKnowledgeBaseEvidence`、`needFollowUp`

### 7.7 前台提交反馈

- `POST /api/app/chat/messages/{messageId}/feedback`

## 8. 审计与运维接口

### 8.1 审计日志列表

- `GET /api/admin/audit-logs`

查询参数：

- `operator`
- `bizType`
- `startTime`
- `endTime`
- `pageNo`
- `pageSize`

说明：

- `bizType` 典型值包括：`AUTH`、`KNOWLEDGE_BASE`、`DOCUMENT`、`TASK`、`CHAT`、`CHAT_FEEDBACK`、`MODEL`、`AUDIT`、`SYSTEM`
- 用户强制下线与在线会话治理建议归类为 `AUTH`
- 问答反馈提交会单独归类为 `CHAT_FEEDBACK`，便于后台治理侧筛选

### 8.2 模型调用统计

- `GET /api/admin/statistics/model-calls`

### 8.3 知识库问答统计

- `GET /api/admin/statistics/knowledge-bases/{kbId}/chat`

### 8.4 系统健康检查

- `GET /api/admin/system/health`

返回项建议：

- PostgreSQL 连通性
- MinIO 连通性
- 向量库连通性
- 百炼模型连通性

## 9. 首期实现优先级

### P0

- `POST /api/admin/auth/login`
- `POST /api/app/auth/login`
- `GET /api/admin/auth/me`
- `GET /api/app/auth/me`
- `GET /api/admin/models`
- `GET /api/app/models`
- `GET /api/admin/knowledge-bases`
- `GET /api/app/knowledge-bases`
- `POST /api/admin/knowledge-bases`
- `POST /api/admin/files/upload-url`
- `POST /api/admin/knowledge-bases/{kbId}/documents`
- `POST /api/admin/documents/{documentId}/parse`
- `GET /api/admin/tasks`
- `POST /api/app/chat/sessions`
- `POST /api/app/chat/sessions/{sessionId}/messages/stream`

### P1

- `GET /api/admin/documents/{documentId}/chunks`
- `POST /api/admin/tasks/{taskId}/retry`
- `PUT /api/app/chat/sessions/{sessionId}`
- `PUT /api/app/chat/sessions/{sessionId}/knowledge-bases`
- `POST /api/app/chat/messages/{messageId}/feedback`
- `GET /api/admin/audit-logs`
- `GET /api/admin/system/health`

### P2

- `POST /api/admin/models/{modelId}/health-check`
- `GET /api/admin/statistics/model-calls`
- `GET /api/admin/statistics/knowledge-bases/{kbId}/chat`

---

这份接口设计用于首期开发对齐，默认后续会继续补充 OpenAPI 文档和更完整的错误码枚举。
