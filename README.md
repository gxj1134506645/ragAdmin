# ragAdmin

`ragAdmin` 是一个面向内部使用的 RAG 知识库管理平台。

当前仓库已经完成首期单后端双前端形态的大部分主链路，重点覆盖：

- 认证与用户管理
- 模型提供方、模型定义、模型探活
- 知识库与文档管理
- 文档解析任务、任务监控、任务重试
- 文本/Office/PDF 文档解析
- 图片 OCR 与扫描 PDF OCR 兜底解析
- 向量化与 Milvus 引用写入
- 单知识库 RAG 问答
- 独立问答前台与多知识库临时联查
- 审计日志、统计接口、系统健康检查

当前整体状态已经进入收口尾声：后端主链路基本完成，管理后台与独立问答前台均已落地，剩余重点是控制文档数量并完成真实环境联调验收。

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
  rag-admin-web/
  rag-chat-web/
  AGENTS.md
  pom.xml
```

当前仓库采用单后端双前端结构：

- `rag-admin-server`：统一后端，承载 `/api/admin`、`/api/app`、`/api/internal`
- `rag-admin-web`：后台管理端，默认开发端口 `5173`
- `rag-chat-web`：独立问答前台，默认开发端口 `5174`

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
- OCR / Tesseract 健康检查

### 3.2 模型与知识库

- 模型提供方列表与新增
- 模型提供方健康检查
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
- 任务摘要统计
- 内部任务回调
- 文档 chunk 查询

### 3.4 RAG 问答

- 会话创建与列表
- 会话消息查询
- 基于 Milvus 的检索问答
- 回答引用落库
- 问答反馈
- `/api/app` 前台登录、知识库列表、模型列表
- 首页通用聊天与知识库内聊天
- 多知识库临时联查
- 前台运行时模型切换与联网开关
- SSE 流式输出

说明：

- 前台联网能力当前已接入 `WebSearchProvider` 抽象
- 如果未配置真实联网搜索 Provider，开启联网开关后会自动降级为空结果，不会中断主问答链路

## 4. 本地启动

先编译后端：

```bash
mvn -q -pl rag-admin-server -am -DskipTests compile
```

再启动后端：

```bash
mvn -q -pl rag-admin-server spring-boot:run
```

默认启动端口：

- `9212`

前端本地启动：

```bash
npm --prefix rag-admin-web install
npm --prefix rag-admin-web run dev
```

```bash
npm --prefix rag-chat-web install
npm --prefix rag-chat-web run dev
```

默认前端端口：

- `rag-admin-web`：`5173`
- `rag-chat-web`：`5174`

发布前检查口径统一以 `9212` 为准。若联调时为避免端口冲突临时改到其他端口，只作为一次性排障手段，不作为仓库默认运行口径。

默认 profile 为 `local`，对应本机 `127.0.0.1` + Docker 容器环境。

如果你需要继续使用内网共享开发环境，可显式切换：

```bash
mvn -q -pl rag-admin-server spring-boot:run -Dspring-boot.run.profiles=dev
```

配置语义：

- `application-local.yml`：本机 Docker 容器环境
- `application-dev.yml`：内网共享开发环境示例

### 4.1 本地 Ollama 默认模型说明

当前仓库的本地开发示例默认把模型提供方切到 `Ollama`，并约定以下默认模型：

- 聊天模型：`qwen2.5:1.5b`
- 向量模型：`quentinz/bge-small-zh-v1.5`

这些默认值主要分布在：

- `docker/compose/docker-compose.yml`
  - `ollama-init` 会自动预拉取上述两个模型
- `rag-admin-server/src/main/resources/application-local-secret.yml`
  - 本地私有配置里会把它们声明为默认聊天模型和默认向量模型
- `rag-admin-server/src/main/java/com/ragadmin/server/infra/ai/embedding/OllamaProperties.java`
  - 代码层面保留同一组默认值，避免未覆盖配置时出现旧口径

重要说明：

- 这组模型只是仓库当前的本地开发示例，不是强制标准
- 如果你准备替换为其他本地模型，请至少同时调整：
  - `application-local-secret.yml` 中的 `default-chat-model`
  - `application-local-secret.yml` 中的 `default-embedding-model`
  - `docker/compose/docker-compose.yml` 中 `ollama-init` 的预拉取模型列表
- 如果只改了配置文件而没有在本地 Ollama 中实际拉取对应模型，应用通常仍可启动，但以下能力会失败或降级：
  - 模型探活
  - 聊天生成
  - 文档向量化
  - RAG 检索问答
- 如果本机根本没有安装或启动 Ollama，本地默认 `Ollama` 链路同样不会可用；此时可以：
  - 安装并启动 Ollama，再拉取对应模型
  - 或改用其他已启用的模型提供方

推荐做法：

- 想直接按仓库默认联调，就执行 `docker compose`，让 `ollama-init` 自动预拉取默认模型
- 想自定义模型，就把“本地私有配置”和“Compose 初始化拉取列表”一起改掉，保持单一事实来源

### 4.2 OCR 配置说明

当前 OCR 默认集成在知识库文档解析流水线内部，用于图片文件和扫描版 `PDF` 的兜底文本抽取。

- 这里的“系统内建 OCR 能力”指的是项目已经内建了 OCR 调用链路，不代表 OCR 引擎二进制程序已经随 Java 应用打包进来
- 当前一期实现仍依赖外部 `Tesseract` 软件本体和语言包；裸机部署时需要预装，容器部署时需要打进镜像
- `application-local.yml` 中的 `C:/Program Files/Tesseract-OCR/tesseract.exe` 只是 Windows 本地开发示例
- 生产部署到 Linux 时，不再使用 Windows `exe`，应改为 `tesseract` 或 Linux 绝对路径，例如 `/usr/bin/tesseract`
- `data-path` 表示 `tessdata` 语言包目录，不是 OCR 程序本体路径；如果 Linux 环境已按系统默认目录安装语言包，可不显式配置
- 如使用 Docker 部署，应在镜像内安装 `tesseract` 和所需语言包，而不是依赖宿主机临时手工安装

术语定位：

- `Apache Tika`：通用文档文本提取器，负责从文本型 `PDF`、`DOCX`、`XLSX`、`PPTX` 等文件中抽取文本和元数据，不是 OCR 引擎
- `Tesseract`：OCR 引擎，负责从图片或扫描件中识别文字
- `Tess4J`：Java 对 `Tesseract` 的封装，不是独立 OCR 引擎；当前项目未采用这条路径，而是直接调用命令行
- `MinerU`：高阶文档解析方案，不只是 OCR；既能处理文本型 `PDF`，也能处理扫描版 `PDF` / 图片，并补充版面、表格、公式、阅读顺序等能力

当前推荐流程是“上传原始文档，由系统异步完成解析、OCR、切片、Embedding 和索引构建”，而不是要求运营人员先手工借助 `MinerU` 之类工具做前置 OCR，再把产物上传到知识库。

对于排版复杂、表格密集、公式较多或普通 OCR 效果不稳定的文档，后续可以在解析流水线内增加 `MinerU`、`PaddleOCR` 或其他更强解析器，作为增强型解析通道；但这类能力仍应由系统内部统一编排，而不是默认变成用户手工前置步骤。

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
- 自动拉取默认 Ollama 聊天模型 `qwen2.5:1.5b` 与 Embedding 模型 `quentinz/bge-small-zh-v1.5`

更完整的依赖准备、接口验收顺序和常见排查方式，直接看联调文档。

## 5. 关键文档

- 架构设计：[docs/architectures/rag-admin-architecture.md](docs/architectures/rag-admin-architecture.md)
- AI 编排设计：[docs/architectures/rag-admin-ai-orchestration-architecture.md](docs/architectures/rag-admin-ai-orchestration-architecture.md)
- 接口设计：[docs/architectures/rag-admin-api-design.md](docs/architectures/rag-admin-api-design.md)
- 数据库草案：[docs/architectures/rag-admin-schema-v1.sql](docs/architectures/rag-admin-schema-v1.sql)
- 实施计划：[docs/plans/rag-admin-implementation-plan.md](docs/plans/rag-admin-implementation-plan.md)
- 后端联调说明：[docs/tasks/rag-admin-backend-debug-guide.md](docs/tasks/rag-admin-backend-debug-guide.md)
- API 验收脚本说明：[docs/tasks/rag-admin-api-acceptance.md](docs/tasks/rag-admin-api-acceptance.md)
- 访问边界计划：[docs/plans/access-boundary-plan.md](docs/plans/access-boundary-plan.md)
- 前台聊天演进计划：[docs/plans/app-chat-evolution-plan.md](docs/plans/app-chat-evolution-plan.md)
- 项目协作规则：[AGENTS.md](AGENTS.md)
