# ragAdmin 语义分块与父子分块架构设计

## 1. 目标

通过基于 embedding 相似度的语义断点检测，实现更智能的文档分块；通过父子分块关系，兼顾检索精度和上下文完整性。

## 2. 语义分块算法

### 2.1 核心思路

1. 将文档文本切分为小段落（child units，约 400 字符）
2. 对每个段落计算 embedding 向量
3. 计算相邻段落的余弦相似度
4. 在相似度低于阈值（默认 0.5）的位置设置断点
5. 将断点之间的段落组合为父块（parent chunk）

### 2.2 断点检测

```
段落1 ←→ 段落2: 相似度 0.85 → 同组
段落2 ←→ 段落3: 相似度 0.82 → 同组
段落3 ←→ 段落4: 相似度 0.25 → 断点！
段落4 ←→ 段落5: 相似度 0.78 → 同组（新组）
```

### 2.3 容错设计

- Embedding 模型不可用时：回退到 `RecursiveFallbackStrategy`
- 单段落文档：直接返回，不建立父子关系
- 只有一个分组时：返回普通 chunks，不建立父子关系

## 3. 父子分块关系

### 3.1 数据模型

`kb_chunk` 表新增字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `parent_chunk_id` | BIGINT | 父块 ID（子块才有值） |
| `chunk_strategy` | VARCHAR(32) | 分块策略标识 |

### 3.2 父子关系

- **父块**：包含多个子块的完整文本，提供上下文
- **子块**：较小的独立段落，用于精确检索
- `ChunkDraft.parentChunkId`：在持久化阶段解析为真实 ID

### 3.3 检索流程

1. 向量化和 ES 索引仅对子块执行（更精确的 embedding）
2. 检索命中子块后，通过 `ParentChunkExpansionService` 扩展为父块全文
3. 扩展后保留子块的检索分数

## 4. DB 迁移

- Flyway V12：添加 `parent_chunk_id` 和 `chunk_strategy` 列

## 5. 核心组件

| 组件 | 职责 |
|------|------|
| `SemanticChunkStrategy` | 语义分块策略，基于 embedding 相似度找断点 |
| `ParentChunkExpansionService` | 检索后子块 → 父块上下文扩展 |
| `ChunkDraft` | 新增 `parentChunkId` 字段支持父子关系 |
| `ChunkEntity` | 新增 `parentChunkId`、`chunkStrategy` 字段 |

## 6. 未来扩展

- 自适应相似度阈值（基于文档类型和统计分布自动调整）
- 语义分块配置项纳入 `ChunkStrategyProperties`
- 多粒度父子层级（祖-父-子三级结构）
