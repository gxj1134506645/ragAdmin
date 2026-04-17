# Async Embedding 扩展点预留实施计划

**Goal:** 在不接入百炼 async 向量模型真实调用链路的前提下，为后续接入预留稳定扩展点，并保持当前 `text-embedding-v3/v4` 主链路可用。

**Architecture:** 继续沿用当前“模型层解析 + 文档/检索编排层调用”的结构，在模型描述符中补充向量调用模式，并将当前知识库主链路明确收口为同步文本向量化。异步批量模型先允许登记，但在知识库绑定、向量化和检索阶段返回明确占位错误。

**Tech Stack:** Java 21, Spring Boot 3, MyBatis Plus, Spring AI Alibaba, JUnit 5, Mockito

---

### Task 1: 模型调用模式元数据

**Files:**
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/infra/ai/embedding/EmbeddingExecutionMode.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/document/support/EmbeddingModelDescriptor.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/infra/ai/SpringAiModelSupport.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/model/service/ModelService.java`

- [ ] 定义 `EmbeddingExecutionMode`，至少包含 `SYNC_TEXT`、`ASYNC_BATCH`
- [ ] 扩展 `EmbeddingModelDescriptor`，携带向量调用模式
- [ ] 在百炼模型支持工具中区分“可登记模型”和“当前同步主链路可执行模型”
- [ ] 调整模型保存与解析逻辑：允许登记 async 文本向量模型，但保留多模态模型拦截

### Task 2: 当前知识库主链路收口

**Files:**
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/knowledge/service/KnowledgeBaseService.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/document/support/ChunkVectorizationService.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/retrieval/service/RetrievalService.java`

- [ ] 增加“当前知识库主链路仅支持同步文本 Embedding”的统一校验入口
- [ ] 知识库创建/更新时阻止绑定 async 向量模型
- [ ] 文档向量化与检索阶段改用同步主链路描述符，避免未来误配后在深层 provider 调用处报错

### Task 3: 探活与测试回归

**Files:**
- Modify: `rag-admin-server/src/test/java/com/ragadmin/server/model/service/ModelServiceTest.java`
- Modify: `rag-admin-server/src/test/java/com/ragadmin/server/document/support/ChunkVectorizationServiceTest.java`
- Modify: `rag-admin-server/src/test/java/com/ragadmin/server/retrieval/service/RetrievalServiceTest.java`
- Modify: `rag-admin-server/src/test/java/com/ragadmin/server/infra/ai/SpringAiModelSupportTest.java`

- [ ] 增加 async 文本向量模型可登记的单测
- [ ] 增加知识库主链路拒绝 async 模型的单测
- [ ] 保持现有 `text-embedding-v3/v4` 回归通过
- [ ] 运行与本次改动直接相关的 Maven 测试
