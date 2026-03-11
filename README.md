# ragAdmin

`ragAdmin` 是一个面向内部使用的 RAG 知识库管理平台。

当前仓库已经完成首期后端单体的大部分主链路，重点覆盖：

- 认证与用户管理
- 模型提供方、模型定义、模型探活
- 知识库与文档管理
- 文档解析任务、任务监控、任务重试
- 文本/Office/PDF 文档解析
- 向量化与 Milvus 引用写入
- 单知识库 RAG 问答
- 审计日志、统计接口、系统健康检查

## 目录

- [1. 仓库结构](#1-仓库结构)
- [2. 技术栈](#2-技术栈)
- [3. 当前能力概览](#3-当前能力概览)
- [4. 本地启动](#4-本地启动)
- [5. 关键文档](#5-关键文档)

## 1. 仓库结构

```text
ragAdmin/
  docs/
  docker/
    compose/
  rag-admin-server/
  AGENTS.md
  pom.xml
```

当前以 `rag-admin-server` 为主，前端项目尚未正式落库。

## 2. 技术栈

- JDK 21
- Spring Boot 3
- MyBatis Plus
- PostgreSQL 16
- Redis
- MinIO
- Ollama
- Milvus
- Flyway
- Lombok
- MapStruct

## 3. 当前能力概览

### 3.1 认证与治理

- 登录、刷新、登出、当前用户
- 用户列表、新增、更新、角色配置
- 审计日志自动记录与查询
- 模型调用统计、知识库问答统计
- 系统健康检查

### 3.2 模型与知识库

- 模型提供方列表与新增
- 模型定义列表与新增
- 模型健康检查
- 知识库创建、详情、状态切换
- 知识库文档列表

### 3.3 文档处理

- MinIO 上传签名
- 文档登记
- 文档版本列表
- 文档新版本上传
- 文档版本切换
- 文档解析任务投递
- 内部任务回调
- 文档 chunk 查询

### 3.4 RAG 问答

- 会话创建与列表
- 会话消息查询
- 基于 Milvus 的检索问答
- 回答引用落库
- 问答反馈

## 4. 本地启动

先编译：

```bash
mvn -q -pl rag-admin-server -am -DskipTests compile
```

再启动：

```bash
mvn -q -pl rag-admin-server spring-boot:run
```

默认启动端口：

- `9212`

默认 profile 为 `local`，对应本机 `127.0.0.1` + Docker 容器环境。

如果你需要继续使用内网共享开发环境，可显式切换：

```bash
mvn -q -pl rag-admin-server spring-boot:run -Dspring-boot.run.profiles=dev
```

配置语义：

- `application-local.yml`：本机 Docker 容器环境
- `application-dev.yml`：内网共享开发环境示例

如需本地拉起 PostgreSQL、Milvus、Ollama 以及 Milvus 依赖，可先准备：

```bash
cp docker/compose/.env.example docker/compose/.env
docker compose --env-file docker/compose/.env -f docker/compose/docker-compose.yml up -d
```

当前 `compose` 已包含项目首期会用到的完整中间件：

- PostgreSQL
- Redis
- MinIO
- Etcd
- Milvus
- Ollama

同时包含两个初始化动作：

- 自动创建 MinIO bucket
- 自动拉取默认 Ollama 聊天模型与 Embedding 模型

更完整的依赖准备、接口验收顺序和常见排查方式，直接看联调文档。

## 5. 关键文档

- 架构设计：[docs/rag-admin-architecture.md](docs/rag-admin-architecture.md)
- 接口设计：[docs/rag-admin-api-design.md](docs/rag-admin-api-design.md)
- 数据库草案：[docs/rag-admin-schema-v1.sql](docs/rag-admin-schema-v1.sql)
- 实施计划：[docs/rag-admin-implementation-plan.md](docs/rag-admin-implementation-plan.md)
- 后端完成度清单：[docs/rag-admin-backend-completion-checklist.md](docs/rag-admin-backend-completion-checklist.md)
- 后端联调说明：[docs/rag-admin-backend-debug-guide.md](docs/rag-admin-backend-debug-guide.md)
- API 验收脚本说明：[docs/rag-admin-api-acceptance.md](docs/rag-admin-api-acceptance.md)
- 项目协作规则：[AGENTS.md](AGENTS.md)
