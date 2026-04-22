# ragAdmin Reranking 架构设计

## 1. 目标

在检索结果返回前，使用 LLM 对候选文档片段进行精排，提升最终返回结果的相关性质量。

## 2. 设计思路

采用 LLM-based Reranking 方案：将候选 passages 编号展示给 LLM，由 LLM 对每个 passage 的相关性打分（1-10），按分数排序取 top-N。

### 为什么选择 LLM-based 而非 Cross-Encoder

- 无需部署额外的专用模型
- 复用现有 LLM 基础设施
- 灵活性高，可通过 prompt 调整评分标准
- 未来可平滑迁移到 Cross-Encoder

## 3. 触发条件

同时满足以下条件时触发 reranking：

1. `kb_knowledge_base.rerank_enabled = true`（知识库级别开关）
2. `rag.retrieval.reranking.enabled = true`（全局开关）
3. 候选结果数 > 1

## 4. 处理流程

```
检索结果 (RetrievedChunk 列表)
    │
    ▼
检查触发条件 (rerankEnabled + 全局enabled + size > 1)
    │
    ▼ 不满足 → 返回原始结果
    │
    ▼ 满足
截取 maxCandidates 个候选
    │
    ▼
构建 Reranking Prompt (编号 passages)
    │
    ▼
LLM 返回 JSON 评分 [{"index": 1, "score": 9.5}, ...]
    │
    ▼
解析评分，按分数排序取 topN
    │
    ▼
返回精排后的 RetrievedChunk 列表
```

## 5. 配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `rag.retrieval.reranking.enabled` | false | 全局开关 |
| `rag.retrieval.reranking.top-n` | 3 | 精排后保留的结果数量 |
| `rag.retrieval.reranking.max-candidates` | 20 | 参与精排的最大候选数 |

## 6. 容错设计

- 无可用聊天模型：跳过 reranking，返回原始排序
- LLM 调用失败：捕获异常，返回原始排序
- JSON 解析失败：跳过 reranking，返回原始排序
- 未被 LLM 评分的候选：追加到结果末尾

## 7. 核心组件

| 组件 | 职责 |
|------|------|
| `RerankingService` | Reranking 入口，调度 LLM 评分和结果排序 |
| `RerankingProperties` | 全局配置 |
| `RetrievalService` | 在 retrieveSingleQuery 中集成 reranking |

## 8. Prompt 模板

| 模板 | 路径 | 用途 |
|------|------|------|
| System | `prompts/ai/retrieval/reranking-system.st` | 评分标准和输出格式 |
| User | `prompts/ai/retrieval/reranking-user.st` | 查询 + 编号 passages |

## 9. 未来扩展

- Cross-Encoder 模型集成（如 bge-reranker）
- 混合 Reranking：先 LLM 粗排，再 Cross-Encoder 精排
- 缓存机制：相同查询 + 相似候选集时复用评分结果
