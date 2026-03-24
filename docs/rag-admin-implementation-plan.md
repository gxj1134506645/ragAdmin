# ragAdmin 首批单体版实施计划

## 目录

- [1. 目标与交付边界](#1-目标与交付边界)
- [2. 固定技术基线](#2-固定技术基线)
- [3. 本地开发与中间件方案](#3-本地开发与中间件方案)
- [4. 代码结构与模块划分](#4-代码结构与模块划分)
- [5. 数据库与迁移方案](#5-数据库与迁移方案)
- [6. 认证与登录态方案](#6-认证与登录态方案)
- [7. 模型提供方与 AI 接入](#7-模型提供方与-ai-接入)
- [8. 文件上传解析与任务链路](#8-文件上传解析与任务链路)
- [9. 向量检索与 Milvus 方案](#9-向量检索与-milvus-方案)
- [10. 日志审计与可观测性](#10-日志审计与可观测性)
- [11. API 与对象调整](#11-api-与对象调整)
- [12. 分阶段实施顺序](#12-分阶段实施顺序)
- [13. 验收与测试场景](#13-验收与测试场景)
- [14. 已锁定假设](#14-已锁定假设)
- [15. 实施前需要同步修订的文档](#15-实施前需要同步修订的文档)

## 1. 目标与交付边界

本计划用于落地 `ragAdmin` 的首批可运行后端版本，交付范围是：

- 后端单体应用
- 本地 `docker-compose` + 内网依赖混合环境
- API 可验收主链路

首批不包含正式前端页面交付。

### 1.1 首批必须交付

- 本地环境与内网依赖接入说明
- Spring Boot 单体项目骨架
- Flyway 初始迁移脚本
- 登录、刷新、登出、当前用户
- 模型提供方与模型定义基础能力
- 知识库创建与查询
- MinIO 上传签名
- 文档登记
- 应用内异步解析任务
- 文档切片、Embedding、Milvus 入库
- 单知识库 RAG 问答
- 任务查询与重试
- 健康检查
- 基础审计日志与结构化日志

### 1.2 首批明确不做

- 正式前端管理台页面
- 多实例部署方案
- Quartz 或 XXL-JOB
- 多知识库联合检索
- 高级重排模型
- 表格结构化 OCR
- 复杂版面恢复
- 权限管理页面
- 统计大屏

## 2. 固定技术基线

以下基线已经锁定，实施时不得随意改动：

- 沟通与 Markdown 输出必须使用中文简体
- 后端：`JDK 21 + Spring Boot 3`
- 单体单进程形态
- 应用运行在宿主机
- PostgreSQL、Milvus、Ollama 优先通过 `docker-compose` 容器化运行
- Redis、MinIO 当前阶段允许连接内网现有服务
- 应用连接地址统一通过环境变量覆盖，禁止把敏感配置写死进仓库
- 数据库：`PostgreSQL 16`
- 缓存与会话：`Redis`
- 对象存储：`MinIO`
- 向量库：`Milvus Standalone`
- 本地第二模型提供方：`Ollama`
- 数据库迁移：`Flyway`
- 定时任务：`Spring Boot @Scheduled`
- 日志：`SLF4J + Logback`
- 首批即具备基础可观测性
- 认证：`JWT + Redis + Bearer Token`
- 登录支持手机号+密码、账号+密码
- 登录请求使用单一 `loginId`
- 密码密文存储使用 `BCrypt`
- 手机号全局唯一
- 文件上传：后端签名，前端直传 MinIO
- 文档解析主引擎：`Apache Tika`
- OCR：`Tesseract`
- 模型 Provider 首批支持：
  - 阿里百炼
  - Ollama

## 3. 本地开发与中间件方案

### 3.1 开发模式

- 后端应用在本机运行
- PostgreSQL、Milvus、Ollama 优先通过容器运行
- Redis、MinIO 当前阶段走内网服务器
- 所有连接通过环境变量配置，不强制写死 `127.0.0.1`
- OCR 当前允许本地开发环境直接调用已安装的 `Tesseract` 命令；Windows 可使用本机绝对路径，Linux 部署必须切换为 Linux 命令或 Linux 路径
- 生产环境不沿用 Windows `exe` 配置，若后续 OCR 服务化，则改为平台内部解析适配层或内部 `HTTP/RPC` 服务

### 3.1.1 当前已确认的内网依赖

- Redis：`192.168.0.11:6379`
- MinIO API：`http://192.168.0.11:9000`
- MinIO Bucket：`ragadmin`
- Redis 和 MinIO 账号密钥只允许保存在本地环境变量或未纳管的本地配置中

### 3.2 固定端口

- 后端：`9212`
- 前端预留：`5173`
- PostgreSQL：`5432`
- Redis：`6379`
- MinIO API：`9000`
- MinIO Console：`9001`
- Milvus gRPC：`19530`
- Milvus HTTP/health：`9091`
- Ollama：`11434`

### 3.3 `docker-compose` 必含服务

- `postgres`
- `milvus`
- `etcd`
- Milvus 所需显式依赖服务
- `ollama`

说明：

- 如果后续内网 Redis、MinIO 不可复用，再补本地容器编排
- 当前阶段 `docker-compose` 不强制拉起 Redis、MinIO

### 3.4 本地连接约定

- `jdbc:postgresql://127.0.0.1:5432/rag_admin`
- `redis://$REDIS_HOST:$REDIS_PORT`
- `http://$MINIO_ENDPOINT:$MINIO_PORT`
- `127.0.0.1:19530`
- `http://127.0.0.1:11434`

## 4. 代码结构与模块划分

### 4.1 仓库目标结构

```text
rag-admin/
  docs/
  docker/
    compose/
      docker-compose.yml
      .env.example
  rag-admin-server/
    src/main/java/
    src/main/resources/
      application.yml
      application-local.yml
      logback-spring.xml
      db/migration/
```

### 4.2 单体模块划分

`rag-admin-server` 内部按模块化单体组织：

- `auth`
- `model`
- `knowledge`
- `document`
- `task`
- `retrieval`
- `chat`
- `audit`
- `infra`
- `common`

### 4.3 模块职责

- `auth`：登录、刷新、登出、当前用户
- `model`：Provider、模型定义、能力映射、路由
- `knowledge`：知识库管理
- `document`：文档登记、上传签名、解析入口
- `task`：任务记录、状态流转、重试
- `retrieval`：Milvus 检索、上下文拼装
- `chat`：会话、消息、反馈、引用
- `audit`：操作审计与日志落库
- `infra`：MinIO、Redis、Milvus、Ollama、百炼、Tika、Tesseract 适配

## 5. 数据库与迁移方案

### 5.1 迁移工具

只允许使用 `Flyway`。

### 5.2 初始迁移建议拆分

1. `V1__init_auth_and_audit.sql`
2. `V2__init_model_tables.sql`
3. `V3__init_knowledge_and_document.sql`
4. `V4__init_chat_and_task.sql`
5. `V5__seed_admin_and_base_data.sql`

### 5.3 首批必须落地的表

- `sys_user`
- `sys_role`
- `sys_user_role`
- `sys_audit_log`
- `ai_provider`
- `ai_model`
- `ai_model_capability`
- `ai_model_route`
- `kb_knowledge_base`
- `kb_document`
- `kb_document_version`
- `kb_document_parse_task`
- `kb_chunk`
- `kb_chunk_vector_ref`
- `chat_session`
- `chat_message`
- `chat_answer_reference`
- `chat_feedback`
- `job_task_record`
- `job_task_step_record`
- `job_retry_record`

### 5.4 用户表约束

`sys_user` 必须满足：

- `username` 唯一
- `mobile` 全局唯一
- `password_hash` 存储 `BCrypt` 哈希
- 严禁明文密码落库

### 5.5 向量模型调整

当前 SQL 草案仍保留 `pgvector` 思路，实施前必须改为 `Milvus 引用模型`：

- PostgreSQL 不再保存向量本体
- `kb_chunk_vector_ref` 只保存：
  - `collection_name`
  - `partition_name`
  - `vector_id`
  - `embedding_model_id`
  - `embedding_dim`
  - `status`

### 5.6 首批种子数据

通过 Flyway 初始化：

- 管理员账号
- 管理员角色
- 默认模型提供方：
  - `BAILIAN`
  - `OLLAMA`
- 至少一条默认聊天模型
- 至少一条可用 Embedding 模型

## 6. 认证与登录态方案

### 6.1 最终方案

采用 `JWT + Redis + Bearer Token`。

### 6.2 登录方式

支持两种登录方式：

- 手机号 + 密码
- 账号 + 密码

### 6.3 登录请求格式

登录接口请求体统一为：

```json
{
  "loginId": "admin 或 13800000000",
  "password": "******"
}
```

说明：

- 后端自动识别 `loginId` 是手机号还是账号
- 不使用 `username/mobile` 双字段模式
- 不使用 `loginType` 字段

### 6.4 密码存储与校验

- 存储算法固定为 `BCrypt`
- 实现固定使用 `BCryptPasswordEncoder`
- 管理员种子密码必须提前生成 `BCrypt` 哈希

### 6.5 登录响应

登录成功后返回：

- `accessToken`
- `refreshToken`
- `expiresIn`
- `refreshExpiresIn`
- 当前用户信息

### 6.6 必做接口

- `POST /api/admin/auth/login`
- `POST /api/admin/auth/refresh`
- `POST /api/admin/auth/logout`
- `GET /api/admin/auth/me`

### 6.7 Redis 在认证中的职责

- Refresh Token 存储
- Access Token 失效标记或黑名单
- 登录会话状态
- 登出失效控制
- 会话续期

## 7. 模型提供方与 AI 接入

### 7.1 首批 Provider

首批必须实现两个 Provider：

- `BailianProvider`
- `OllamaProvider`

### 7.2 必须定义的抽象

- `ModelProviderClient`
- `ChatModelClient`
- `EmbeddingModelClient`
- `ModelProviderRegistry`

### 7.3 首批能力边界

- 百炼：
  - 聊天模型
  - Embedding 模型
- Ollama：
  - 聊天模型必须可用
  - 如 Embedding 首批不稳定，可先只用于聊天，但抽象层必须预留 Embedding 能力

### 7.4 模型来源与默认值约定

- 配置文件只负责平台接入级配置：例如百炼 `api-key`、接入地址、超时时间，以及平台级兜底默认模型
- 配置文件中的默认模型只用于系统初始化、健康检查、无显式业务绑定时的兜底，不作为后台业务主数据
- 后台管理的模型列表必须落库到 `ai_model`，管理台下拉项统一从数据库读取
- 知识库的 `embeddingModelId` 必须引用数据库中的模型记录，且作为知识库必填配置；知识库不再维护 `chatModelId`
- 运行时按“知识库绑定向量模型负责检索，聊天生成模型走请求/会话显式模型或后台默认聊天模型”解析
- 首期允许后台手工维护百炼模型列表；后续再补“从百炼模型广场同步模型定义入库”的能力

## 8. 文件上传解析与任务链路

### 8.1 上传策略

采用“后端签名，前端直传 MinIO”：

1. 获取上传签名
2. 前端直传 MinIO
3. 后端登记文档
4. 投递解析任务

### 8.2 文档解析执行方式

- 单应用内异步执行
- API 只负责投递任务
- 后台线程池或调度器消费任务
- 预留未来拆分 Worker 的接口边界

### 8.3 解析引擎

- 文本/Office/PDF：`Apache Tika`
- OCR：`Tesseract`

### 8.4 首批支持文件类型

- `PDF`
- `Markdown`
- `TXT`
- `DOCX`
- `XLSX`
- `PPTX`
- `PNG`
- `JPG`
- `JPEG`
- `WEBP`
- 扫描 PDF OCR

### 8.5 文件类型处理策略

- `Markdown` / `TXT`：直接文本抽取
- `DOCX` / `XLSX` / `PPTX`：优先使用 Tika 统一抽取
- 普通 `PDF`：优先文本抽取
- 图片：走 Tesseract OCR
- 扫描 PDF：无可用文本时走 OCR 路径

补充说明：

- 默认上传原始文件，由系统异步执行解析、OCR、清洗、切片、Embedding 和索引构建
- 一期不把“人工先用 `MinerU` 等工具提取语料再上传”作为标准操作流程
- `Tesseract` 负责首期图片与扫描 PDF OCR 兜底
- 如果后续遇到复杂版式、表格、公式或多栏 PDF，可在解析流水线内增加 `MinerU` 等增强型解析器，但仍由系统统一编排，不外溢为人工前置步骤

### 8.6 切片策略

按文件类型分别定制：

- `Markdown`：
  - 标题优先分段
  - 超长段落再窗口切片
- `TXT`：
  - 固定大小 + 重叠窗口
- `DOCX` / `PPTX`：
  - 逻辑段优先，再窗口切片
- `XLSX`：
  - 按 sheet / 行区块转文本后切片
- `PDF`：
  - 按页/段优先，再窗口切片
- OCR 结果：
  - 按页或图片区块组装后再切片

### 8.7 任务状态

- `WAITING`
- `RUNNING`
- `SUCCESS`
- `FAILED`
- `CANCELED`

必须记录：

- 主任务记录
- 步骤记录
- 错误信息
- 重试次数
- 开始时间
- 结束时间

## 9. 向量检索与 Milvus 方案

### 9.1 PostgreSQL 与 Milvus 职责划分

`PostgreSQL` 负责：

- 知识库
- 文档
- chunk
- 任务
- 聊天消息
- Milvus 引用元数据

`Milvus` 负责：

- 向量本体存储
- 相似度检索

### 9.2 检索流程

1. 用户提问
2. 生成查询向量
3. 调用 Milvus 检索
4. 根据 `vectorId` 回查 PostgreSQL 中的 chunk 元数据
5. 组装上下文
6. 调用模型生成回答
7. 保存消息与引用关系

### 9.3 Milvus 设计原则

- 每个 Embedding 模型必须有明确 collection 策略
- collection 命名必须可预测
- 删除文档或版本时，必须同步清理 Milvus 中的对应向量
- 重建索引时，必须支持按知识库或文档版本重写引用

## 10. 日志审计与可观测性

### 10.1 日志规范

必须统一采用：

- `SLF4J`
- `Logback`
- `logback-spring.xml`

### 10.2 首批日志要求

必须具备：

- 结构化日志
- 请求入口日志
- 关键业务成功/失败日志
- 外部依赖调用日志
- 任务链路日志
- 错误堆栈日志
- 关联 ID：
  - `traceId`
  - `requestId`
  - `taskId`

### 10.3 敏感信息规则

日志中严禁出现：

- 密码
- Token 原文
- API Key
- MinIO 密钥
- 原始敏感配置

### 10.4 健康检查

必须提供：

- `GET /api/admin/system/health`

至少检查：

- PostgreSQL
- Redis
- MinIO
- Milvus
- 百炼连接状态
- Ollama 连接状态

### 10.5 审计范围

至少覆盖：

- 登录 / 登出
- 知识库创建 / 更新
- 文档登记
- 解析任务触发
- 重试任务
- 问答调用

## 11. API 与对象调整

### 11.1 必须调整的接口

- `POST /api/admin/auth/login`
  - 请求体改为 `loginId + password`
  - 响应体改为双 Token
- 新增 `POST /api/admin/auth/refresh`
- `GET /api/admin/system/health`
  - 补 Redis / Ollama / Milvus 状态

### 11.2 必须定义的 VO/DTO

- `LoginRequest`
- `LoginResponse`
- `RefreshTokenRequest`
- `RefreshTokenResponse`
- `CurrentUserResponse`
- `UploadUrlRequest`
- `UploadUrlResponse`
- `CreateKnowledgeBaseRequest`
- `CreateDocumentRequest`
- `ParseDocumentResponse`
- `TaskListItemResponse`
- `TaskDetailResponse`
- `ChatRequest`
- `ChatResponse`
- `HealthCheckResponse`

## 12. 分阶段实施顺序

### 阶段 1：环境与骨架

- 创建 `rag-admin-server`
- 创建 `docker-compose`
- 配置本地环境
- 接入 Flyway
- 配置 Logback
- 拉起 PostgreSQL、Milvus、Ollama，并接入内网 Redis、MinIO

### 阶段 2：认证与基础治理

- 建用户、角色、审计表
- 补 `mobile` 唯一约束
- Flyway 种子管理员
- 接入 `BCryptPasswordEncoder`
- 实现登录、刷新、登出、当前用户
- 接入 Redis 会话
- 建立统一异常处理、响应封装、请求日志

### 阶段 3：模型与基础设施适配

- 建模型相关表
- 实现百炼与 Ollama 双 Provider
- 打通聊天与 Embedding 基础调用
- 实现健康检查中的模型探测

### 阶段 4：知识库与文档登记

- 建知识库、文档、版本、任务表
- 实现知识库增查改
- 实现上传签名
- 实现文档登记
- 实现解析任务投递

### 阶段 5：异步解析链路

- 任务扫描调度
- Tika 集成
- Tesseract 集成
- 分类型抽取与切片
- Embedding 生成
- Milvus 写入
- 回写 chunk 与引用关系

### 阶段 6：RAG 问答闭环

- 会话与消息表
- 实现单知识库问答
- Milvus 检索 + PostgreSQL 回查
- 回答引用落库
- 聊天记录与反馈落库

### 阶段 7：验收与补齐

- 任务查询与重试
- 健康检查
- 审计日志查询
- 本地启动文档
- API 验收说明

## 13. 验收与测试场景

### 13.1 环境验收

- `docker-compose up` 后 PostgreSQL、Milvus、Ollama 可用
- 应用启动后健康检查通过
- 本机应用可通过环境变量连通 PostgreSQL、Redis、MinIO、Milvus、Ollama

### 13.2 认证验收

- 管理员可通过账号 + 密码登录
- 管理员可通过手机号 + 密码登录
- 错误密码登录失败
- 登出后旧 Token 失效
- Refresh Token 能成功换新
- 数据库中密码不是明文，而是 `BCrypt` 哈希

### 13.3 文档链路验收

- 可获取上传签名
- 文件可直传 MinIO
- 文档登记成功
- 解析任务进入正确状态
- PDF、Markdown、TXT、DOCX、XLSX、PPTX、图片、扫描 PDF 均至少有成功样例
- chunk 成功落库
- Milvus 成功写入向量引用

### 13.4 问答验收

- 可创建会话
- 可发起单知识库问答
- 引用片段可回显
- 消息与引用关系落库
- 百炼与 Ollama 至少各完成一次成功调用验证

### 13.5 可观测性验收

- 结构化日志可用
- 任务失败日志可定位
- 健康检查可反映依赖状态
- 审计日志可记录关键操作

## 14. 已锁定假设

以下默认值已经锁定：

- 后端单体单进程
- 应用宿主机运行，中间件容器运行
- 端口固定
- Redis 首批必接，当前阶段走 `192.168.0.11`
- Milvus 首批必接
- Ollama 首批必接
- 定时任务使用 `@Scheduled`
- 密码哈希固定 `BCrypt`
- 登录请求固定为 `loginId`
- 手机号全局唯一
- 登录返回双 Token
- PostgreSQL 不存向量本体
- 首批只交付后端 + 中间件 + API 验收

## 15. 实施前需要同步修订的文档

实施前先同步修订以下文件：

- `docs/rag-admin-schema-v1.sql`
  - 改为 Milvus 引用模型
  - 增加 `mobile` 唯一约束
- `docs/rag-admin-api-design.md`
  - 登录请求改为 `loginId + password`
  - 登录响应改为双 Token
  - 新增 `/api/admin/auth/refresh`
- `docs/rag-admin-architecture.md`
  - 保持与本计划一致，不再保留旧的 `pgvector` 主方案残留
