# ragAdmin 项目进度快照

> 最后更新：2026-04-26
>
> 本文件是项目完整进度的单一事实源。每次完成重大功能或阶段后更新；新会话默认先读取 `session-brief.md`，中等及以上任务再读取本文件。

## 总体状态

- **当前阶段**：Phase 1 基本完成，Phase 2 大部分完成，Phase 3 未启动
- **后端**：17 个 Flyway migration，核心管线已跑通
- **管理前端**（rag-admin-web）：12 个视图模块已实现
- **聊天前端**（rag-chat-web）：通用聊天 + 知识库聊天已实现

---

## 已完成功能

### 后端基础设施
- [x] 认证授权（sa-token + Redis JWT）
- [x] 用户管理（角色、权限）
- [x] 模型提供方 & 模型管理（百炼、Ollama、智谱多提供方）
- [x] 模型健康检查（提供方级 + 模型级）
- [x] 系统健康检查（PostgreSQL、Redis、MinIO、Milvus、Ollama、MinerU）
- [x] 审计日志
- [x] Prompt 模板 CRUD（V17）

### 文档处理管线
- [x] 文档上传（MinIO 预签名 URL）
- [x] 文档注册、启用/禁用
- [x] 文档加载架构（MinerU 统一 PDF/Office、XLSX 表格感知、Tika 兜底）
- [x] 文档清洗（页眉页脚、噪声去除、归一化、HTML 残留清理）
- [x] 文档分块（5 种策略：Markdown、HTML、PdfOcr、PdfText、RecursiveFallback）
- [x] 三端数据同步（PG + Milvus + ES）
- [x] 图片处理管线（CDN 图片下载转存 MinIO、MinerU ZIP 图片提取上传、URL 重写）
- [x] 内容感知清洗策略（DocumentSignalAnalyzer 12 种信号驱动清洗策略选择）

### 检索与 RAG
- [x] 混合检索：语义（Milvus）+ 关键词（ES BM25）+ 混合（RRF 融合）
- [x] 知识库 CRUD + 状态控制
- [x] 知识库级检索模式（SEMANTIC_ONLY / KEYWORD_ONLY / HYBRID），HYBRID 为默认值
- [x] 查询改写（Multi-Query 分解 + HyDE 假设文档），由知识库级模式控制
- [x] LLM 重排序
- [x] Web 搜索集成（Tavily），支持优雅降级
- [x] RAG 聊天会话 + SSE 流式响应
- [x] 答案引用与反馈

### 前端
- [x] 管理后台 Dashboard（系统健康、任务摘要、模型调用统计）
- [x] 用户管理、模型管理、知识库管理、文档管理
- [x] 任务监控、审计日志、反馈管理
- [x] Prompt 模板管理
- [x] 统计（向量索引概览）
- [x] Ember 设计系统迁移完成
- [x] rag-chat-web 通用聊天 + 知识库聊天
- [x] Genesis 设计系统迁移完成

### 任务管理
- [x] 任务列表、步骤详情、重试
- [x] 统计（模型调用、知识库聊天）

---

## 进行中 / 部分完成

| 功能 | 完成度 | 说明 |
|------|--------|------|
| 聊天记忆 — Redis 短期记忆层 | 95% | 三层存储全部实现并端到端验证通过。摘要触发阈值生效、刷新日志提升为 info、HikariPool keepalive 已配置 |
| 查询改写接入检索管线 | 95% | 核心能力已激活（Multi-Query + HyDE），全局 flag 门控已移除，混合检索默认 HYBRID。待扩展：查询预处理管道（语义丰富化 + 脏话过滤 + NL→检索语言转化） |
| 语义分块 + 父子分块 | 20% | DB 列就绪（V16），SemanticChunkStrategy 和 ParentChunkExpansionService 未实现 |
| Cross-Encoder 重排序 | 10% | LLM 重排序已完成，Cross-Encoder 策略 + Ollama 部署方案未开始 |
| 统计 Dashboard 扩展 | 30% | 目前只有向量索引概览，缺更全面的指标面板 |
| 分块策略 YAML 参数覆写 | 60% | 5 种策略代码完成，每策略独立配置覆写机制待实现 |

---

## 已设计未启动

| 功能 | 设计文档 | 说明 |
|------|----------|------|
| 内部 MCP 工具平台 | `rag-admin-internal-mcp-platform-design.md` | 企业工具集成，Streamable-HTTP MCP，无代码 |
| 多模型路由 / 智能切换 | 架构文档 Phase 2 | 目前只能手动切模型 |
| 多模态能力 | 架构文档 Phase 3 | OCR/视觉摄入、图片生成等 |
| 组织/SSO 集成 | 架构文档 Phase 3 | 细粒度数据权限、知识库共享审批发布 |

---

## 下一优先级建议

1. 聊天记忆 Redis 短期记忆层（基础设施已就绪，实现成本低，对话连贯性提升大）
2. 查询改写接入检索管线（Prompt + DB 已有，接入即可用）
3. 语义分块 + 父子分块（DB 列就绪，检索质量关键提升）
4. Cross-Encoder 重排序（LLM 重排序已验证，Cross-Encoder 是性能+质量双重提升）
