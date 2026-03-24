# ragAdmin 后端联调说明

## 目录

- [1. 目的](#1-目的)
- [2. 当前联调范围](#2-当前联调范围)
- [3. 依赖准备](#3-依赖准备)
- [4. 本地启动](#4-本地启动)
- [5. 建议联调顺序](#5-建议联调顺序)
- [6. 关键接口清单](#6-关键接口清单)
- [7. 常见排查点](#7-常见排查点)

## 1. 目的

本文档用于说明当前 `ragAdmin` 的最小联调方式，目标是让开发者能够在本地快速验证：

- 基础认证是否可用
- 模型管理、知识库管理、文档管理是否可用
- 文档解析、向量化、检索问答链路是否可用
- `rag-admin-web` 与 `rag-chat-web` 是否能正常接入
- 审计、统计、健康检查是否可用

## 2. 当前联调范围

当前已实现并适合联调的范围包括：

- 认证与用户管理
- 模型提供方、模型定义、模型探活
- 知识库创建、详情、状态切换、知识库文档列表
- 文件上传签名、文档登记、文档启停
- 解析任务投递、任务列表、任务详情、任务重试、任务步骤记录
- 文本类与 Tika 文档解析
- 图片 OCR 与扫描 PDF OCR 兜底
- 向量化、Milvus 引用写入
- RAG 会话、问答、引用落库、问答反馈
- `/api/app` 前台登录、模型列表、知识库列表、问答会话
- `rag-admin-web` 管理后台
- `rag-chat-web` 独立问答前台
- 前台运行时模型切换、知识库多选与联网开关降级编排
- 审计日志查询
- 模型调用统计、知识库问答统计
- 系统健康检查
- OCR 健康检查

当前仍属于首期后端版本，以下能力仍未完整实现或暂未展开：

- 多模型路由策略
- 复杂重排
- 对外开放注册
- 更复杂的前台会话运营能力

## 3. 依赖准备

### 3.1 已固定的本地配置

当前默认本地开发配置在 `application-local.yml`，指向本机 Docker 容器环境；如需继续使用内网共享环境，可切换到 `application-dev.yml`。

当前 `application-local.yml` 中使用以下依赖：

- PostgreSQL：`127.0.0.1:5432`
- Redis：`127.0.0.1:6379`
- MinIO：`127.0.0.1:9000`
- Ollama：`127.0.0.1:11434`
- Milvus：`127.0.0.1:19530`

OCR 配置补充说明：

- `application-local.yml` 中的 Windows `tesseract.exe` 路径仅用于本地开发示例
- Linux 服务器部署时，应改为 `tesseract` 或 Linux 绝对路径，不再沿用 Windows `exe`
- `rag.document.ocr.data-path` 指向的是 `tessdata` 语言包目录，不是 OCR 程序路径
- 默认流程是上传原始图片或扫描版 `PDF` 后由系统内部自动 OCR，不要求人工先借助 `MinerU` 导出文本再上传
- 如后续接入 `MinerU` 等增强型解析器，也应纳入系统解析流水线，而不是变成默认人工预处理环节

### 3.2 ChatClient 日志 advisor 说明

当前问答链路已经接入 Spring AI `SimpleLoggerAdvisor`，但不同环境的日志可见范围不同。

基础配置 `application.yml` 默认策略：

- `rag.ai.chat.client.simple-logger-advisor-enabled=false`
- `rag.ai.chat.client.simple-logger-max-text-length=800`
- `rag.ai.chat.client.simple-logger-request-body-enabled=true`
- `rag.ai.chat.client.simple-logger-response-body-enabled=false`

本地与开发环境额外覆盖策略：

- `application-local.yml` 默认开启 `simple-logger-advisor-enabled=true`
- `application-dev.yml` 默认开启 `simple-logger-advisor-enabled=true`
- `application-local.yml` 与 `application-dev.yml` 默认开启 `simple-logger-response-body-enabled=true`
- 两个 profile 默认把 `org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor` 日志级别提升到 `DEBUG`

当前日志输出规则：

- 请求日志记录消息条数、`contextKeys`、消息角色、元数据键集合
- `context` 只记录 key，不直接输出 value，避免把 `conversationId` 等上下文原值打进日志
- 当正文可记录且超长时，只保留前缀，并附带 `truncated`、原始长度和短 `sha256`
- 当正文日志关闭时，输出 `hidden`、原始长度和短 `sha256`
- 响应日志额外输出 `model`、`promptTokens`、`completionTokens`、`totalTokens`

联调建议：

- 排查 prompt 编排、记忆注入、联网摘要拼装问题时，优先使用 `local` 或 `dev` profile
- 如果只想确认模型调用是否发生，可以保持 `simple-logger-response-body-enabled=false`
- 不建议在生产环境长期开启响应正文日志

### 3.3 联调前至少确认

1. PostgreSQL 已创建数据库 `rag_admin`
2. Redis 可连通，密码正确
3. MinIO 可连通，Bucket `ragadmin` 已存在
4. Ollama 已启动，并至少拉取：
   - `qwen2.5:7b`
   - `nomic-embed-text`
5. Milvus 已启动，`base-url` 当前按 `http://127.0.0.1:19530`

### 3.4 首次启动会自动初始化

应用首次启动后会自动初始化：

- 默认管理员账号
- 默认角色
- 默认模型提供方
- 默认模型定义

默认管理员配置来自 `application.yml`：

- 用户名：`admin`
- 手机号：`13800000000`
- 密码：`Admin@123456`

如需修改，可在启动前覆盖以下配置：

- `BOOTSTRAP_ADMIN_USERNAME`
- `BOOTSTRAP_ADMIN_DISPLAY_NAME`
- `BOOTSTRAP_ADMIN_MOBILE`
- `BOOTSTRAP_ADMIN_PASSWORD`

## 4. 本地启动

### 4.0 先拉起本地中间件

仓库已提供基础编排文件：

- `docker/compose/docker-compose.yml`
- `docker/compose/.env.example`

建议先复制环境文件：

```bash
cp docker/compose/.env.example docker/compose/.env
```

然后启动首期必需的本地容器：

```bash
docker compose --env-file docker/compose/.env -f docker/compose/docker-compose.yml up -d
```

当前编排会启动：

- `postgres`
- `redis`
- `etcd`
- `minio`
- `milvus`
- `ollama`

并会自动执行：

- `minio-init`：创建默认 bucket
- `ollama-init`：拉取默认聊天模型和 Embedding 模型

说明：

- 当前 `application-local.yml` 默认用于本地容器环境
- 如果仍要复用内网 Redis、MinIO 或其他服务，建议切换到 `dev` profile，或自行覆盖环境变量
- 常用覆盖项包括：
  - `REDIS_HOST`
  - `REDIS_PORT`
  - `REDIS_PASSWORD`
  - `MINIO_ENDPOINT`
  - `MINIO_PORT`
  - `MINIO_ACCESS_KEY`
  - `MINIO_SECRET_KEY`
  - `MINIO_BUCKET_NAME`
  - `MINIO_PUBLIC_URL`
  - `OLLAMA_BASE_URL`
  - `MILVUS_BASE_URL`
  - `MILVUS_TOKEN`

### 4.1 编译

```bash
mvn -q -pl rag-admin-server -am -DskipTests compile
```

### 4.2 启动

```bash
mvn -q -pl rag-admin-server spring-boot:run
```

默认启动端口：

- `9212`

联调与发布前检查默认统一以 `9212` 作为标准端口。若本地已有历史 Java 进程占用 `9212`，应先清理残留进程；只有在临时排障时才允许改用其他端口，排障结束后仍应回归 `9212`。

### 4.3 启动两个前端

管理后台：

```bash
npm --prefix rag-admin-web install
npm --prefix rag-admin-web run dev
```

独立问答前台：

```bash
npm --prefix rag-chat-web install
npm --prefix rag-chat-web run dev
```

默认端口：

- `rag-admin-web`：`5173`
- `rag-chat-web`：`5174`

### 4.4 首先验证健康检查

```http
GET /api/admin/system/health
```

优先确认以下依赖状态：

- `postgres`
- `redis`
- `minio`
- `bailian`
- `ollama`
- `milvus`
- `ocr`

如果这里不通，后续联调没有意义。

## 5. 建议联调顺序

建议按下面顺序逐步验证，不要一开始直接打问答接口。

### 5.1 认证

1. 登录
2. 获取当前用户
3. 刷新 Token
4. 登出

### 5.2 模型与知识库

1. 查询模型提供方
2. 查询模型定义
3. 对 Embedding 模型和 Chat 模型做健康检查
4. 创建知识库
5. 查询知识库详情

### 5.3 文档链路

1. 获取上传签名
2. 前端或脚本直传 MinIO
3. 登记文档
4. 触发解析
5. 查询任务列表与详情
6. 查询文档详情和 chunk 列表

### 5.4 RAG 问答

1. 创建会话
2. 发起问答
3. 查看消息列表
4. 提交反馈

### 5.5 前台问答联调

1. 前台登录
2. 首页通用聊天
3. 首页勾选多个知识库后提问
4. 切入知识库内聊天
5. 切换聊天模型
6. 打开或关闭联网开关，并验证未配置真实 Provider 时不会报错
7. 验证 SSE 流式输出

### 5.6 治理与统计

1. 查询审计日志
2. 查询模型调用统计
3. 查询知识库问答统计

## 6. 关键接口清单

### 6.1 认证

- `POST /api/admin/auth/login`
- `POST /api/admin/auth/refresh`
- `POST /api/admin/auth/logout`
- `GET /api/admin/auth/me`

### 6.2 用户与权限

- `GET /api/admin/users`
- `POST /api/admin/users`
- `PUT /api/admin/users/{userId}`
- `PUT /api/admin/users/{userId}/roles`

### 6.3 模型

- `GET /api/admin/model-providers`
- `POST /api/admin/model-providers`
- `POST /api/admin/model-providers/{providerId}/health-check`
- `GET /api/admin/models`
- `POST /api/admin/models`
- `POST /api/admin/models/{modelId}/health-check`

### 6.4 知识库与文档

- `GET /api/admin/knowledge-bases`
- `POST /api/admin/knowledge-bases`
- `GET /api/admin/knowledge-bases/{kbId}`
- `PUT /api/admin/knowledge-bases/{kbId}/status`
- `GET /api/admin/knowledge-bases/{kbId}/documents`
- `POST /api/admin/files/upload-url`
- `POST /api/admin/knowledge-bases/{kbId}/documents`
- `GET /api/admin/documents/{documentId}`
- `PUT /api/admin/documents/{documentId}/status`
- `POST /api/admin/documents/{documentId}/parse`
- `GET /api/admin/documents/{documentId}/chunks`

### 6.5 任务

- `GET /api/admin/tasks`
- `GET /api/admin/tasks/summary`
- `GET /api/admin/tasks/{taskId}`
- `POST /api/admin/tasks/{taskId}/retry`

### 6.6 治理与统计

- `GET /api/admin/audit-logs`
- `GET /api/admin/statistics/model-calls`
- `GET /api/admin/statistics/knowledge-bases/{kbId}/chat`
- `GET /api/admin/system/health`

### 6.7 前台问答接口

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

## 7. 常见排查点

### 7.1 登录失败

优先检查：

- Flyway 是否成功执行
- 管理员账号是否已自动初始化
- Redis 是否可用

### 7.2 模型探活失败

优先检查：

- 百炼 `apiKey` 是否已通过本地私有配置注入
- 百炼模型编码是否与后台模型定义一致
- 若启用 Ollama，再检查 `rag.ai.ollama.base-url` 与模型是否可用

### 7.3 文档解析失败

优先检查：

- MinIO Bucket 是否存在
- 对象 Key 是否确实已上传
- 文档类型是否在当前支持范围内
- Tika 依赖是否正确加载
- 若是图片或扫描 PDF，检查 `rag.document.ocr.enabled` 是否启用
- 检查运行机是否已安装 `tesseract` 且语言包可用
- 若当前环境是 Linux，检查配置中是否仍误用了 Windows `tesseract.exe` 路径
- 若使用容器部署，检查镜像内是否已安装 `tesseract` 与所需语言包，而不是只在宿主机上安装

### 7.4 向量化失败

优先检查：

- 知识库绑定的 Embedding 模型是否具备 `EMBEDDING` 能力
- Ollama 的 `nomic-embed-text` 是否可用
- Milvus 地址与 Token 是否正确

### 7.5 RAG 问答没有命中

优先检查：

- 文档是否解析成功
- `kb_chunk` 是否已有数据
- `kb_chunk_vector_ref` 是否已回写
- Milvus collection 是否已写入向量

### 7.6 后台管理端问答已下线

- 后台管理端不再提供独立 `/api/admin/chat/**` 接口
- 如需验证问答主链路，请转到前台 `rag-chat-web` 或直接排查 `/api/app/chat/**`

### 7.7 前台流式问答没有返回

优先检查：

- 浏览器 Network 中 `text/event-stream` 请求是否已建立
- `/api/app/chat/sessions/{sessionId}/messages/stream` 是否返回了 401 或 5xx
- 当前前台会话是否已经绑定正确知识库
- 选中的 `chatModelId` 是否为可用聊天模型
- 如果 `webSearchEnabled=true` 但当前环境未接入真实搜索 Provider，预期应自动降级为空联网结果，而不是直接失败

### 7.7 审计与统计为空

优先检查：

- 是否真的有接口访问记录
- 访问是否走了 `/api/admin/**`
- 问答消息是否已成功落库

### 7.8 ChatClient 日志没有输出

优先检查：

- 当前是否真的使用了 `local` 或 `dev` profile
- `rag.ai.chat.client.simple-logger-advisor-enabled` 是否为 `true`
- `logging.level.org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor` 是否为 `DEBUG`
- 当前问答链路是否走到了 `SpringAiConversationChatClient`
- 如果只看到 `hidden` 但没有正文，检查：
  - `rag.ai.chat.client.simple-logger-request-body-enabled`
  - `rag.ai.chat.client.simple-logger-response-body-enabled`
- 如果只看到前缀没有完整长文本，这是预期行为；需要结合 `sha256` 和原始长度做问题比对
