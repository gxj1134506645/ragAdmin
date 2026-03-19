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
- 首期建议使用 JWT + Redis 会话控制

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
      "roles": ["ADMIN"]
    }
  }
}
```

### 3.2 获取当前用户信息

- `GET /api/admin/auth/me`
- `GET /api/app/auth/me`

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
- 若知识库未显式绑定模型，后端可按“后台系统默认模型 > 配置文件平台兜底默认模型”做兜底解析

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

- 后台管理端和问答前台共用问答核心链路，但必须通过不同接口域和不同会话终端类型隔离
- 会话隔离至少依赖 `terminalType + sceneType + sessionId`
- 对于问答前台，知识库集合通过会话关系表维护，运行时允许切换聊天模型与联网开关

### 7.1 创建会话

- `POST /api/admin/chat/sessions`

请求体：

```json
{
  "sceneType": "KNOWLEDGE_BASE",
  "kbId": 1,
  "sessionName": "制度问答"
}
```

说明：

- `sceneType=KNOWLEDGE_BASE` 时，`kbId` 必填
- `sceneType=GENERAL` 时，`kbId` 必须为空
- `sceneType` 不传时，默认按 `KNOWLEDGE_BASE` 处理，兼容历史接口

### 7.2 会话列表

- `GET /api/admin/chat/sessions`

查询参数：

- `kbId`
- `sceneType`
- `pageNo`
- `pageSize`

### 7.3 获取会话消息

- `GET /api/admin/chat/sessions/{sessionId}/messages`

响应体示例：

```json
{
  "code": "OK",
  "message": "success",
  "data": [
    {
      "messageId": 101,
      "question": "公司年假规则是什么？",
      "answer": "根据员工手册，年假按工龄分段计算。",
      "references": [
        {
          "documentId": 11,
          "documentName": "员工手册.pdf",
          "chunkId": 201,
          "chunkNo": 35,
          "score": 0.92,
          "contentSnippet": "员工累计工作满 1 年不满 10 年..."
        }
      ],
      "feedbackType": "LIKE",
      "feedbackComment": null
    }
  ]
}
```

### 7.4 发起 RAG 问答

- `POST /api/admin/chat/sessions/{sessionId}/messages`

请求体：

```json
{
  "question": "公司年假规则是什么？",
  "kbId": 1,
  "stream": false
}
```

说明：

- 知识库内会话必须携带匹配的 `kbId`
- 首页通用会话不得携带 `kbId`

首页通用会话示例：

```json
{
  "question": "帮我总结一下今天待办",
  "stream": false
}
```

响应体：

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "messageId": 101,
    "answer": "根据员工手册，年假按工龄分段计算。",
    "references": [
      {
        "documentId": 11,
        "documentName": "员工手册.pdf",
        "chunkId": 201,
        "chunkNo": 35,
        "score": 0.92,
        "contentSnippet": "员工累计工作满 1 年不满 10 年..."
      }
    ],
    "usage": {
      "promptTokens": 600,
      "completionTokens": 120
    }
  }
}
```

### 7.5 发起流式 RAG 问答

- `POST /api/admin/chat/sessions/{sessionId}/messages/stream`
- `Content-Type: application/json`
- `Accept: text/event-stream`

请求体：

```json
{
  "question": "公司年假规则是什么？",
  "kbId": 1
}
```

说明：

- 当前服务主体仍为 `Spring MVC`，流式接口通过 `Flux<ServerSentEvent<...>>` 输出 `SSE`
- 知识库内会话必须携带匹配的 `kbId`
- 首页通用会话不得携带 `kbId`
- `stream` 字段不再作为流式开关，是否流式由接口路径决定

事件类型：

- `DELTA`：增量文本片段
- `COMPLETE`：回答完成，同时返回落库后的消息主键、引用和用量信息
- `ERROR`：流式执行失败，当前轮回答不落业务消息

事件示例：

```text
event: delta
data: {"eventType":"DELTA","delta":"根据员工手册，"}

event: delta
data: {"eventType":"DELTA","delta":"年假按工龄分段计算。"}

event: complete
data: {"eventType":"COMPLETE","messageId":101,"answer":"根据员工手册，年假按工龄分段计算。","references":[{"documentId":11,"documentName":"员工手册.pdf","chunkId":201,"chunkNo":35,"score":0.92,"contentSnippet":"员工累计工作满 1 年不满 10 年..."}],"usage":{"promptTokens":600,"completionTokens":120}}
```

### 7.6 提交反馈

- `POST /api/admin/chat/messages/{messageId}/feedback`

请求体：

```json
{
  "feedbackType": "LIKE",
  "comment": "回答准确"
}
```

### 7.7 前台创建会话

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

### 7.8 前台会话列表

- `GET /api/app/chat/sessions`

查询参数：

- `sceneType`
- `kbId`
- `pageNo`
- `pageSize`

### 7.9 前台获取会话消息

- `GET /api/app/chat/sessions/{sessionId}/messages`

### 7.10 前台更新会话知识库集合

- `PUT /api/app/chat/sessions/{sessionId}/knowledge-bases`

请求体：

```json
{
  "selectedKbIds": [1, 3, 5]
}
```

### 7.11 前台发起流式问答

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
- `chatModelId` 为运行时模型选择，优先级高于知识库默认聊天模型
- `webSearchEnabled=true` 时，由后端按 `WebSearchProvider` 配置决定是否补充联网搜索上下文

### 7.12 前台提交反馈

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
- `POST /api/admin/chat/sessions`
- `POST /api/app/chat/sessions`
- `POST /api/admin/chat/sessions/{sessionId}/messages`
- `POST /api/admin/chat/sessions/{sessionId}/messages/stream`
- `POST /api/app/chat/sessions/{sessionId}/messages/stream`

### P1

- `GET /api/admin/documents/{documentId}/chunks`
- `POST /api/admin/tasks/{taskId}/retry`
- `POST /api/admin/chat/messages/{messageId}/feedback`
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
