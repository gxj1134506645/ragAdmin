# ragAdmin 查询改写架构设计

## 1. 目标

通过查询改写提升检索召回质量，支持将原始查询从多角度重新表述或生成假设文档，以弥补用户查询与文档表达之间的语义鸿沟。

## 2. 改写策略

### 2.1 Multi-Query Decomposition

- 将用户的原始问题从不同角度重新表述，生成 N 个语义相同但表达不同的替代查询
- 每个替代查询独立执行检索，结果合并后去重（按 chunkId，保留最高分）
- 适用场景：用户查询表述模糊、用词不精确、需要多角度覆盖

### 2.2 HyDE（Hypothetical Document Embedding）

- 使用 LLM 生成一段假设性的回答文档，将此文档作为检索查询
- 假设文档的语义空间更接近真实文档，能提升检索的语义匹配质量
- 适用场景：用户查询过于简短、口语化，与文档正式表述差异大

### 2.3 组合模式

- `MULTI_QUERY_AND_HYDE`：同时启用两种策略，生成多查询 + 假设文档
- 所有查询结果统一合并去重

## 3. 配置

### 知识库级别

`kb_knowledge_base.retrieval_query_rewriting_mode` 字段控制每个知识库的改写模式：

| 模式 | 说明 |
|------|------|
| `NONE` | 不改写（默认） |
| `MULTI_QUERY` | 仅 Multi-Query 分解 |
| `HYDE` | 仅 HyDE 假设文档 |
| `MULTI_QUERY_AND_HYDE` | 两者都启用 |

### 全局配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `rag.retrieval.query-rewriting.multi-query-enabled` | false | Multi-Query 全局开关 |
| `rag.retrieval.query-rewriting.multi-query-count` | 3 | 生成的替代查询数量 |
| `rag.retrieval.query-rewriting.hyde-enabled` | false | HyDE 全局开关 |

## 4. 处理流程

```
用户原始查询
    │
    ▼
QueryRewritingService.rewrite(query, mode)
    │
    ├── NONE → 返回原始查询
    │
    ├── MULTI_QUERY → LLM 生成 N 个替代查询
    │
    ├── HYDE → LLM 生成假设文档
    │
    └── MULTI_QUERY_AND_HYDE → 两者都执行
    │
    ▼
对每个查询独立执行 RetrievalService.retrieveSingleQuery()
    │
    ▼
合并所有结果：按 chunkId 去重，保留最高分
    │
    ▼
返回合并后的 RetrievalResult
```

## 5. 容错设计

- 无可用聊天模型时：跳过改写，使用原始查询
- LLM 调用失败时：捕获异常，回退到原始查询
- Multi-Query 解析失败时：使用已成功解析的查询（可能为空，退化为原始查询）
- HyDE 生成失败时：跳过假设文档查询

## 6. 核心组件

| 组件 | 职责 |
|------|------|
| `QueryRewritingService` | 查询改写入口，调度 Multi-Query 和 HyDE |
| `QueryRewritingMode` | 改写模式枚举 |
| `QueryRewritingProperties` | 全局配置 |
| `RetrievalService` | 在 retrieve() 中集成改写，多查询结果合并 |

## 7. Prompt 模板

| 模板 | 路径 | 用途 |
|------|------|------|
| Multi-Query | `prompts/ai/retrieval/multi-query-decomposition.st` | 指导 LLM 从不同角度重述查询 |
| HyDE | `prompts/ai/retrieval/hyde-generation.st` | 指导 LLM 生成假设性回答文档 |
