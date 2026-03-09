# ragAdmin 错误码说明

## 目录

- [1. 说明](#1-说明)
- [2. 通用错误码](#2-通用错误码)
- [3. 认证与用户](#3-认证与用户)
- [4. 模型与知识库](#4-模型与知识库)
- [5. 文档与任务](#5-文档与任务)
- [6. RAG 与向量化](#6-rag-与向量化)
- [7. 内部接口](#7-内部接口)

## 1. 说明

本文档仅整理当前代码中已经实际使用到的错误码。

统一响应结构：

```json
{
  "code": "ERROR_CODE",
  "message": "错误说明",
  "data": null
}
```

如果后续新增错误码，必须同步更新本文件。

## 2. 通用错误码

| 错误码 | 含义 | 典型场景 |
|---|---|---|
| `VALIDATION_ERROR` | 参数校验失败 | 请求体缺字段、字段为空、格式不合法 |
| `INTERNAL_SERVER_ERROR` | 系统内部错误 | 未捕获异常、框架层错误 |

## 3. 认证与用户

| 错误码 | 含义 | 典型场景 |
|---|---|---|
| `UNAUTHORIZED` | 未授权或登录态失效 | 未传 Bearer Token、Token 过期、密码错误 |
| `USERNAME_EXISTS` | 用户名已存在 | 新增用户时用户名重复 |
| `MOBILE_EXISTS` | 手机号已存在 | 新增或更新用户时手机号重复 |
| `USER_NOT_FOUND` | 用户不存在 | 更新用户、配置角色时目标用户不存在 |
| `ROLE_NOT_FOUND` | 角色不存在 | 配置用户角色时传入非法角色编码 |

## 4. 模型与知识库

| 错误码 | 含义 | 典型场景 |
|---|---|---|
| `PROVIDER_CODE_EXISTS` | 模型提供方编码已存在 | 新增模型提供方时重复 |
| `PROVIDER_NOT_FOUND` | 模型提供方不存在 | 模型引用了不存在的提供方 |
| `MODEL_CODE_EXISTS` | 模型编码已存在 | 同一提供方下模型编码重复 |
| `MODEL_NOT_FOUND` | 模型不存在 | 根据模型 ID 查询失败 |
| `MODEL_CAPABILITY_INVALID` | 模型能力不匹配 | 知识库绑定了错误类型的模型 |
| `MODEL_CAPABILITY_EMPTY` | 模型未配置能力类型 | 模型探活时发现能力映射为空 |
| `KB_CODE_EXISTS` | 知识库编码已存在 | 新增知识库时重复 |
| `KB_NOT_FOUND` | 知识库不存在 | 查询详情、挂载文档、做统计时找不到知识库 |
| `KB_STATUS_INVALID` | 知识库状态不合法 | 状态切换时传入非 `ENABLED` / `DISABLED` |

## 5. 文档与任务

| 错误码 | 含义 | 典型场景 |
|---|---|---|
| `MINIO_NOT_CONFIGURED` | MinIO 未完成配置 | 获取上传签名时基础配置缺失 |
| `UPLOAD_URL_GENERATE_FAILED` | 上传地址生成失败 | MinIO 预签名异常 |
| `DOCUMENT_NOT_FOUND` | 文档不存在 | 查询文档、切换状态、触发解析时文档不存在 |
| `DOCUMENT_VERSION_NOT_FOUND` | 文档版本不存在 | 解析任务、版本切换、内部回调找不到目标版本 |
| `TASK_NOT_FOUND` | 任务不存在 | 查询任务详情、内部回调时找不到任务 |
| `TASK_TYPE_UNSUPPORTED` | 任务类型暂不支持 | 任务列表筛选传入不支持的类型 |
| `TASK_RETRY_NOT_ALLOWED` | 当前任务不允许重试 | 任务仍在运行时重复重试 |
| `TASK_STATUS_INVALID` | 内部回调任务状态不合法 | 内部回调传入非 `SUCCESS` / `FAILED` |
| `PARSE_STATUS_INVALID` | 内部回调解析状态不合法 | 内部回调传入非法解析状态 |

## 6. RAG 与向量化

| 错误码 | 含义 | 典型场景 |
|---|---|---|
| `CHAT_SESSION_NOT_FOUND` | 会话不存在 | 查询消息、问答、反馈时会话不属于当前用户或不存在 |
| `CHAT_MESSAGE_NOT_FOUND` | 消息不存在 | 提交反馈时找不到目标消息 |
| `CHAT_KB_MISMATCH` | 会话与知识库不匹配 | 会话所属知识库与请求参数不一致 |
| `CHAT_PROVIDER_UNSUPPORTED` | 聊天提供方未实现 | 当前 provider 没有聊天客户端实现 |
| `CHAT_FAILED` | 聊天调用失败 | Ollama 聊天返回为空或异常 |
| `EMBEDDING_PROVIDER_UNSUPPORTED` | 向量提供方未实现 | 当前 provider 没有 embedding 客户端实现 |
| `EMBEDDING_FAILED` | 向量化失败 | Ollama Embedding 返回为空 |
| `EMBEDDING_SIZE_MISMATCH` | 向量数量与 chunk 数量不一致 | 批量向量化返回结果异常 |
| `MILVUS_INSERT_FAILED` | 向量写入失败 | Milvus upsert 失败 |
| `MILVUS_SEARCH_FAILED` | 向量检索失败 | Milvus search 失败 |

## 7. 内部接口

| 错误码 | 含义 | 典型场景 |
|---|---|---|
| `INTERNAL_UNAUTHORIZED` | 内部回调鉴权失败 | `X-Internal-Token` 缺失或错误 |
