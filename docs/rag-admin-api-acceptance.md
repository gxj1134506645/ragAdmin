# ragAdmin API 验收脚本说明

## 目录

- [1. 目的](#1-目的)
- [2. 使用约定](#2-使用约定)
- [3. 验收前准备](#3-验收前准备)
- [4. 基础变量](#4-基础变量)
- [5. 验收步骤](#5-验收步骤)
- [6. 结果判定](#6-结果判定)

## 1. 目的

本文档用于提供一套可直接执行的 API 验收步骤，覆盖当前首期后端最核心的闭环：

- 健康检查
- 登录与登录态验证
- 模型提供方与模型健康检查
- 知识库创建与查询
- 上传签名与文档登记
- 文档解析与任务查询
- 会话创建与 RAG 问答
- `/api/app` 前台问答闭环

本文档以 Windows PowerShell 环境为默认示例。

## 2. 使用约定

- 以下命令默认在 PowerShell 中执行
- 如系统自带 `curl` 被 PowerShell 别名劫持，统一使用 `curl.exe`
- 需要将上一步响应中的关键值手工写回变量
- 如使用的模型、MinIO、Milvus、Ollama 未就绪，后续步骤可能失败

## 3. 验收前准备

至少保证以下条件成立：

- `rag-admin-server` 已启动
- PostgreSQL、Redis、MinIO、Milvus、Ollama 可访问
- 默认管理员已初始化
- 已存在可用的聊天模型和 Embedding 模型
- 本地准备了一个待上传文件，例如 `F:\codes\ragAdmin\docs\sample.md`

建议先执行：

```powershell
mvn -q -pl rag-admin-server -am test
```

## 4. 基础变量

先执行以下变量定义：

```powershell
$BaseUrl = "http://127.0.0.1:9212"
$LoginId = "admin"
$Password = "Admin@123456"
$SampleFile = "F:\codes\ragAdmin\docs\sample.md"

$AccessToken = ""
$RefreshToken = ""
$EmbeddingModelId = 0
$ChatModelId = 0
$KbId = 0
$DocumentId = 0
$TaskId = 0
$SessionId = 0
$MessageId = 0
$AppAccessToken = ""
$AppSessionId = 0
$UploadBucket = ""
$UploadObjectKey = ""
$UploadUrl = ""
```

如果 `sample.md` 不存在，可以自行新建一个简单 Markdown 文件用于测试。

## 5. 验收步骤

### 5.1 健康检查

```powershell
curl.exe "$BaseUrl/api/admin/system/health"
```

判定要点：

- `code` 为 `OK`
- `data` 中至少能看到 `postgres`、`redis`、`minio`、`milvus`、`bailian`
- 若启用了 OCR，还应能看到 `ocr`

### 5.2 登录

```powershell
$LoginBody = @'
{
  "loginId": "admin",
  "password": "Admin@123456"
}
'@

$LoginResponse = Invoke-RestMethod `
  -Method Post `
  -Uri "$BaseUrl/api/admin/auth/login" `
  -ContentType "application/json" `
  -Body $LoginBody

$AccessToken = $LoginResponse.data.accessToken
$RefreshToken = $LoginResponse.data.refreshToken
$AccessToken
$RefreshToken
```

判定要点：

- 成功返回 `accessToken` 与 `refreshToken`
- `data.user.username` 应为 `admin`

### 5.3 当前用户

```powershell
curl.exe `
  -H "Authorization: Bearer $AccessToken" `
  "$BaseUrl/api/admin/auth/me"
```

### 5.4 刷新 Token

```powershell
$RefreshBody = @"
{
  "refreshToken": "$RefreshToken"
}
"@

Invoke-RestMethod `
  -Method Post `
  -Uri "$BaseUrl/api/admin/auth/refresh" `
  -Headers @{ Authorization = "Bearer $AccessToken" } `
  -ContentType "application/json" `
  -Body $RefreshBody
```

### 5.5 查询模型并记录模型 ID

```powershell
curl.exe `
  -H "Authorization: Bearer $AccessToken" `
  "$BaseUrl/api/admin/models?pageNo=1&pageSize=20"
```

从返回结果中手工找到：

- 一个具备 Embedding 能力的模型 ID，写入 `$EmbeddingModelId`
- 一个聊天模型 ID，写入 `$ChatModelId`

示例：

```powershell
$EmbeddingModelId = 2
$ChatModelId = 1
```

### 5.5.1 查询模型提供方并做提供方健康检查

```powershell
$ProviderResp = Invoke-RestMethod `
  -Method Get `
  -Uri "$BaseUrl/api/admin/model-providers" `
  -Headers @{ Authorization = "Bearer $AccessToken" }

$ProviderId = $ProviderResp.data[0].id

Invoke-RestMethod `
  -Method Post `
  -Uri "$BaseUrl/api/admin/model-providers/$ProviderId/health-check" `
  -Headers @{ Authorization = "Bearer $AccessToken" }
```

判定要点：

- `status` 为 `UP` 或符合当前环境预期
- `capabilityChecks` 中能看到聊天或向量能力检查结果

### 5.6 创建知识库

```powershell
$KbBody = @"
{
  "kbCode": "demo-kb-001",
  "kbName": "演示知识库",
  "description": "用于 API 验收",
  "embeddingModelId": $EmbeddingModelId,
  "chatModelId": $ChatModelId,
  "retrieveTopK": 5,
  "rerankEnabled": false,
  "status": "ENABLED"
}
"@

$KbResponse = Invoke-RestMethod `
  -Method Post `
  -Uri "$BaseUrl/api/admin/knowledge-bases" `
  -Headers @{ Authorization = "Bearer $AccessToken" } `
  -ContentType "application/json" `
  -Body $KbBody

$KbId = $KbResponse.data.id
$KbId
```

### 5.7 获取上传地址

```powershell
$UploadReq = @'
{
  "fileName": "sample.md",
  "contentType": "text/markdown",
  "bizType": "KB_DOCUMENT"
}
'@

$UploadResp = Invoke-RestMethod `
  -Method Post `
  -Uri "$BaseUrl/api/admin/files/upload-url" `
  -Headers @{ Authorization = "Bearer $AccessToken" } `
  -ContentType "application/json" `
  -Body $UploadReq

$UploadBucket = $UploadResp.data.bucket
$UploadObjectKey = $UploadResp.data.objectKey
$UploadUrl = $UploadResp.data.uploadUrl

$UploadBucket
$UploadObjectKey
```

### 5.8 上传文件到 MinIO

```powershell
curl.exe `
  -X PUT `
  -H "Content-Type: text/markdown" `
  --data-binary "@$SampleFile" `
  "$UploadUrl"
```

判定要点：

- 返回 `200` 或 `204`

### 5.9 登记文档

```powershell
$DocumentBody = @"
{
  "docName": "sample.md",
  "docType": "MARKDOWN",
  "storageBucket": "$UploadBucket",
  "storageObjectKey": "$UploadObjectKey",
  "fileSize": 128,
  "contentHash": "demo-hash-001"
}
"@

$DocumentResp = Invoke-RestMethod `
  -Method Post `
  -Uri "$BaseUrl/api/admin/knowledge-bases/$KbId/documents" `
  -Headers @{ Authorization = "Bearer $AccessToken" } `
  -ContentType "application/json" `
  -Body $DocumentBody

$DocumentId = $DocumentResp.data.id
$DocumentId
```

### 5.10 触发解析任务

```powershell
$ParseResp = Invoke-RestMethod `
  -Method Post `
  -Uri "$BaseUrl/api/admin/documents/$DocumentId/parse" `
  -Headers @{ Authorization = "Bearer $AccessToken" }

$TaskId = $ParseResp.data.taskId
$TaskId
```

### 5.11 查询任务详情

```powershell
curl.exe `
  -H "Authorization: Bearer $AccessToken" `
  "$BaseUrl/api/admin/tasks/$TaskId"
```

判定要点：

- `taskStatus` 最终应变为 `SUCCESS`
- `steps` 中应能看到文本抽取、切片入库、生成向量等步骤

如需轮询，可重复执行：

```powershell
1..10 | ForEach-Object {
  Start-Sleep -Seconds 3
  curl.exe -H "Authorization: Bearer $AccessToken" "$BaseUrl/api/admin/tasks/$TaskId"
}
```

### 5.11.1 查询任务摘要

```powershell
curl.exe `
  -H "Authorization: Bearer $AccessToken" `
  "$BaseUrl/api/admin/tasks/summary"
```

判定要点：

- 返回 `total`、`waiting`、`running`、`success`、`failed`、`canceled`
- 触发解析后，至少有一个状态计数发生变化

### 5.12 查询文档切片

```powershell
curl.exe `
  -H "Authorization: Bearer $AccessToken" `
  "$BaseUrl/api/admin/documents/$DocumentId/chunks?pageNo=1&pageSize=20"
```

判定要点：

- `data.list` 不为空

### 5.13 创建会话

```powershell
$SessionBody = @"
{
  "kbId": $KbId,
  "sessionName": "验收会话"
}
"@

$SessionResp = Invoke-RestMethod `
  -Method Post `
  -Uri "$BaseUrl/api/admin/chat/sessions" `
  -Headers @{ Authorization = "Bearer $AccessToken" } `
  -ContentType "application/json" `
  -Body $SessionBody

$SessionId = $SessionResp.data.id
$SessionId
```

### 5.14 发起问答

```powershell
$ChatBody = @"
{
  "question": "请总结 sample.md 的主要内容",
  "kbId": $KbId,
  "stream": false
}
"@

$ChatResp = Invoke-RestMethod `
  -Method Post `
  -Uri "$BaseUrl/api/admin/chat/sessions/$SessionId/messages" `
  -Headers @{ Authorization = "Bearer $AccessToken" } `
  -ContentType "application/json" `
  -Body $ChatBody

$MessageId = $ChatResp.data.messageId
$MessageId
```

判定要点：

- 成功返回 `messageId`
- `answer` 非空
- `references` 至少有一条

### 5.15 查询消息列表

```powershell
curl.exe `
  -H "Authorization: Bearer $AccessToken" `
  "$BaseUrl/api/admin/chat/sessions/$SessionId/messages"
```

### 5.16 提交反馈

```powershell
$FeedbackBody = @'
{
  "feedbackType": "LIKE",
  "comment": "验收通过"
}
'@

Invoke-RestMethod `
  -Method Post `
  -Uri "$BaseUrl/api/admin/chat/messages/$MessageId/feedback" `
  -Headers @{ Authorization = "Bearer $AccessToken" } `
  -ContentType "application/json" `
  -Body $FeedbackBody
```

### 5.17 登出

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "$BaseUrl/api/admin/auth/logout" `
  -Headers @{ Authorization = "Bearer $AccessToken" }
```

### 5.18 前台登录

```powershell
$AppLoginBody = @'
{
  "loginId": "admin",
  "password": "Admin@123456"
}
'@

$AppLoginResp = Invoke-RestMethod `
  -Method Post `
  -Uri "$BaseUrl/api/app/auth/login" `
  -ContentType "application/json" `
  -Body $AppLoginBody

$AppAccessToken = $AppLoginResp.data.accessToken
$AppAccessToken
```

### 5.19 前台查询知识库与模型

```powershell
curl.exe `
  -H "Authorization: Bearer $AppAccessToken" `
  "$BaseUrl/api/app/knowledge-bases?pageNo=1&pageSize=20"
```

```powershell
curl.exe `
  -H "Authorization: Bearer $AppAccessToken" `
  "$BaseUrl/api/app/models?pageNo=1&pageSize=20"
```

### 5.20 创建前台通用会话

```powershell
$AppSessionBody = @'
{
  "sceneType": "GENERAL",
  "sessionName": "前台验收会话",
  "selectedKbIds": []
}
'@

$AppSessionResp = Invoke-RestMethod `
  -Method Post `
  -Uri "$BaseUrl/api/app/chat/sessions" `
  -Headers @{ Authorization = "Bearer $AppAccessToken" } `
  -ContentType "application/json" `
  -Body $AppSessionBody

$AppSessionId = $AppSessionResp.data.id
$AppSessionId
```

### 5.21 前台发起普通问答

```powershell
$AppChatBody = @'
{
  "question": "请简单介绍这个知识库平台能做什么",
  "selectedKbIds": [],
  "webSearchEnabled": false
}
'@

Invoke-RestMethod `
  -Method Post `
  -Uri "$BaseUrl/api/app/chat/sessions/$AppSessionId/messages" `
  -Headers @{ Authorization = "Bearer $AppAccessToken" } `
  -ContentType "application/json" `
  -Body $AppChatBody
```

### 5.22 前台验证流式输出

```powershell
curl.exe -N `
  -X POST `
  -H "Authorization: Bearer $AppAccessToken" `
  -H "Content-Type: application/json" `
  "$BaseUrl/api/app/chat/sessions/$AppSessionId/messages/stream" `
  -d "{\"question\":\"请再用两句话总结一次\",\"selectedKbIds\":[],\"webSearchEnabled\":false}"
```

判定要点：

- 返回头为 `text/event-stream`
- 能持续收到 `DELTA` / `COMPLETE` 事件
- 最终可在消息列表中看到落库消息

### 5.23 查询前台消息列表

```powershell
curl.exe `
  -H "Authorization: Bearer $AppAccessToken" `
  "$BaseUrl/api/app/chat/sessions/$AppSessionId/messages"
```

## 6. 结果判定

满足以下条件，可认为当前首期主链路验收通过：

- 健康检查成功，关键依赖状态正常
- 管理员登录、获取当前用户、刷新 Token、登出全部成功
- 知识库创建成功
- MinIO 上传签名成功，文件能上传
- 文档登记成功，解析任务最终进入 `SUCCESS`
- 文档切片可查询
- 会话创建成功，问答返回答案和引用
- `/api/app` 登录成功，前台会话可创建
- 前台普通问答与 SSE 流式问答均可用
- 反馈提交成功

如任一步骤失败，优先结合以下文档排查：

- `docs/rag-admin-backend-debug-guide.md`
- `docs/rag-admin-error-codes.md`
