# ragAdmin 文档分块架构设计

## 1. 文档定位

本文档用于收敛 `ragAdmin` 在知识库导入链路中的"文档分块（chunking）"正式技术设计，统一回答以下问题：

- 分块策略应该如何按文档类型和信号选择
- 是否应该实现 Spring AI 的 `TextSplitter` 接口
- 自定义策略接口如何设计
- 各类文档的分块规则具体是什么
- 重叠（overlap）机制应如何实现
- 分块与清洗的职责边界如何划分
- 各框架（Spring AI / LangChain4j / LlamaIndex）分块能力对比与选型决策

当前阶段如果局部实现与本文档冲突，应优先更新本文档，再调整实现。

## 2. 设计边界

本文档只覆盖知识库导入链路中的"文档分块"阶段，不覆盖以下内容：

- 系统总体架构，见 `rag-admin-architecture.md`
- 文档导入总入口，见 `rag-admin-document-ingestion-architecture.md`
- 文档加载，见 `rag-admin-document-loading-architecture.md`
- 文档清洗，见 `rag-admin-document-cleaning-architecture.md`
- API 契约，见 `rag-admin-api-design.md`
- 数据库结构，见 `rag-admin-schema-v1.sql`

## 3. 为什么不实现 Spring AI 的 TextSplitter

### 3.1 Spring AI TextSplitter 的契约

```java
// Spring AI 的 TextSplitter 是抽象类
public abstract class TextSplitter implements DocumentTransformer {
    // 核心方法：只接收纯文本，返回字符串列表
    protected abstract List<String> splitText(String text);
    
    // 外层方法：接收 List<Document>，内部调用 splitText
    public List<Document> apply(List<Document> documents) { ... }
}
```

### 3.2 契约的局限性

| 维度 | Spring AI TextSplitter | 项目实际需要 |
|------|----------------------|-------------|
| 输入 | 纯文本 `String` | 文本 + 文档类型 + 信号 + 解析模式 |
| 策略选择 | 无法感知文档类型 | 必须按 PDF/MD/HTML/OCR 选择不同策略 |
| 重叠控制 | 不支持 | 需要边界感知的智能重叠 |
| 段落感知 | 不支持 | 需要识别标题层级、列表、代码块等结构 |
| 上下文传递 | 仅文本 | 需要 `DocumentCleanContext`、`DocumentSignals` |

### 3.3 结论

Spring AI 的 `TextSplitter` 把分块简化为"字符串切割"，丢失了分块决策所需的全部上下文。项目需要自定义接口，不继承 `TextSplitter`。

但 `DocumentTransformer` 接口（`List<Document> apply(List<Document>)`）可以作为**可选适配层**，在需要与 Spring AI 生态对接时桥接到自定义策略。

## 4. 分块策略架构

### 4.1 核心接口

```java
/**
 * 文档分块策略接口。
 * 接收清洗后的文档列表和上下文，返回切分结果。
 */
public interface DocumentChunkStrategy {

    /**
     * 判断是否支持当前文档上下文。
     * 由策略自身决定匹配条件（文档类型、信号、解析模式等）。
     */
    boolean supports(ChunkContext context);

    /**
     * 执行分块。
     * 返回的每个 ChunkDraft 已包含文本和 metadata。
     */
    List<ChunkDraft> chunk(List<Document> documents, ChunkContext context);
}

/**
 * 分块上下文，携带分块决策所需的全部信息。
 */
public record ChunkContext(
    DocumentEntity document,
    DocumentSignals signals,
    ChunkStrategyProperties strategyProperties
) {}

/**
 * 分块策略配置，替代当前 DocumentVectorizationProperties.StrategyProperties。
 */
public record ChunkStrategyProperties(
    int maxChunkChars,       // 单块最大字符数
    int overlapChars,        // 重叠目标字符数
    int minChunkChars        // 单块最小字符数（低于此值合并到前一块）
) {}
```

### 4.2 策略选择器

```java
@Component
public class DocumentChunkStrategyResolver {

    private final List<DocumentChunkStrategy> strategies;

    public DocumentChunkStrategy resolve(ChunkContext context) {
        return strategies.stream()
            .filter(s -> s.supports(context))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("无匹配的分块策略"));
    }
}
```

### 4.3 策略注册顺序

通过 `@Order` 控制优先级，第一个 `supports` 返回 `true` 的策略生效：

| 顺序 | 策略 | 匹配条件 |
|------|------|---------|
| 10 | `MarkdownChunkStrategy` | docType=MD/MARKDOWN |
| 20 | `HtmlChunkStrategy` | docType=HTML/HTM |
| 30 | `PdfOcrChunkStrategy` | docType=PDF 且 parseMode=OCR |
| 40 | `PdfTextChunkStrategy` | docType=PDF 且 parseMode=TEXT |
| 50 | `RecursiveFallbackStrategy` | 兜底，始终匹配 |

## 5. 各策略分块规则

### 5.1 MarkdownChunkStrategy

**目标**：按标题层级切分，保持代码块和表格完整。

**规则**：
1. 按 `#` `##` `###` 标题行分段，同一 heading 层级下的内容归入同一块
2. 代码块（` ``` ` 围栏）不拆分，整块保留
3. 表格（`| ... |` 连续行）不拆分，整块保留
4. 单块超过 `maxChunkChars` 时，在标题子层级切分
5. 不产生跨标题层级的重叠，重叠区取同级最后 1-2 个段落

### 5.2 HtmlChunkStrategy

**目标**：按 DOM 语义结构切分。

**规则**：
1. 按 `<section>`、`<article>`、`<h1>`-`<h6>` 分段
2. 列表（`<ul>`/`<ol>`）和表格（`<table>`）不拆分
3. 输入来自 `JsoupDocumentReader`，已是按元素分割的 `Document` 列表
4. 聚合同一 section 下的多个 `Document`，直到达到 `maxChunkChars`

### 5.3 PdfOcrChunkStrategy

**目标**：处理 OCR 文本的弱结构，先合并再切分。

**规则**：
1. 若 `signals.weakParagraphStructure()=true`，先将单换行符连接的短行合并为段落
2. 合并后按双换行符分段
3. 按 `maxChunkChars` 聚合段落为块
4. 重叠取段落边界，不取字符边界（使用已实现的边界感知 `overlapTail`）

### 5.4 PdfTextChunkStrategy

**目标**：处理正常 PDF 解析的段落文本。

**规则**：
1. 按双换行符分段
2. 识别表格行（连续的 `\t` 分隔或 `|` 分隔行），保持表格完整
3. 按 `maxChunkChars` 聚合段落
4. 若 `signals.repeatedHeaderDetected()=true`，首段可能包含残留页眉，跳过空段

### 5.5 RecursiveFallbackStrategy（兜底）

**目标**：通用递归降级分块，类似 LangChain4j 的 `DocumentSplitters.recursive()`。

**规则**：
1. 第一层：按 `\n\n`（段落）分
2. 第二层：单段超限则按 `\n`（行）分
3. 第三层：单行超限则按句子边界（`。` `.` `！` `?` `；` `;`）分
4. 第四层：单句超限则按空白字符分
5. 每层都使用边界感知重叠

## 6. 重叠（Overlap）机制

### 6.1 设计原则

重叠的目标是让相邻切片共享少量上下文，避免检索时丢失跨块语义。重叠区应：
- **不在单词/句子中间截断**（已实现边界感知 `overlapTail`）
- **大小可配置**，默认 120 字符
- **不超过 maxChunkChars 的 50%**

### 6.2 边界感知优先级

当前已实现在 `DocumentParseProcessor.overlapTail` 中：
1. `\n\n`（段落边界）— 最优
2. `\n`（行边界）
3. 空白字符（词边界）
4. 原始位置（兜底）

### 6.3 策略可覆盖

每个策略可以覆盖默认的重叠行为：
- Markdown 策略：重叠区取同级最后 1-2 个完整段落
- 代码密集内容：可能关闭重叠避免代码片段重复
- OCR 内容：使用较长的重叠弥补结构弱的问题

## 7. 与清洗阶段的衔接

### 7.1 数据流

```
DocumentContentExtractor.extract()
    → List<Document>（原始文档）
    → DocumentCleaner.clean()
    → List<Document>（清洗后文档）
    → DocumentChunkStrategyResolver.resolve(context)
    → DocumentChunkStrategy.chunk(documents, context)
    → List<ChunkDraft>（切片结果）
    → persistChunks() + vectorize()
```

### 7.2 信号传递

清洗阶段产出的 `DocumentSignals` 直接传递给分块阶段的 `ChunkContext`，用于：
- `weakParagraphStructure` → PdfOcrChunkStrategy 触发行合并
- `repeatedHeaderDetected` → PdfTextChunkStrategy 跳过残留页眉
- `tocOutlineMissing` → 影响是否启用标题感知分块

### 7.3 职责划分

| 阶段 | 职责 | 不负责 |
|------|------|-------|
| 清洗 | 去噪、去页眉页脚、合并弱段落、保护符号 | 切分文本 |
| 分块 | 按结构和语义切分、控制重叠、生成 metadata | 修改文本内容 |

清洗不应做分块的事（不拆分文本），分块不应做清洗的事（不修改文本内容）。

## 8. 配置模型

```yaml
rag:
  document:
    chunk:
      defaults:
        max-chunk-chars: 800
        overlap-chars: 120
        min-chunk-chars: 50
      strategy-overrides:
        markdown:
          max-chunk-chars: 1200    # Markdown 结构清晰，可放宽
          overlap-chars: 80
        pdf-ocr:
          max-chunk-chars: 600     # OCR 内容噪音多，缩短块
          overlap-chars: 160       # 加大重叠弥补结构弱
        pdf-text:
          max-chunk-chars: 800
          overlap-chars: 120
```

策略配置由 `ChunkStrategyProperties` 承载，`DocumentChunkStrategyResolver` 根据文档类型选择对应的配置覆盖。

## 9. 当前状态与演进路径

### 9.1 当前实现

- `DocumentParseProcessor.splitText()` 是唯一的分块逻辑，段落聚合 + 字符级重叠
- 所有文档类型使用相同策略，不区分 Markdown/PDF/OCR
- 重叠已改为边界感知

### 9.2 演进步骤

1. **抽取接口**：将 `splitText` 重构为 `RecursiveFallbackStrategy`，作为兜底策略
2. **引入 ChunkContext**：封装 `DocumentEntity` + `DocumentSignals` + `ChunkStrategyProperties`
3. **逐个实现策略**：按优先级从高到低，Markdown → HTML → PdfOcr → PdfText
4. **接入配置**：支持按策略覆盖分块参数
5. **验证**：每种策略补充单元测试，用真实文档验证切片质量

## 10. 框架分块能力对比与选型决策

### 10.1 能力矩阵

| 能力 | Spring AI | Spring AI Alibaba | LangChain4j | LlamaIndex (Python) |
|------|-----------|-------------------|-------------|---------------------|
| Token 分块 | `TokenTextSplitter` (jtokkit) | 无 | 所有分块器支持 Token 计量 | 有 |
| 字符分块 | 无 | 无 | 有 | 有 |
| 递归层级分块 | 无 | 无 | 段落→行→句子→词→字符 | 段落→句子→词 |
| 段落/行/句子分块 | 无 | 无 | 各有独立实现 | 各有独立实现 |
| 中文句子检测 | 无 | 无 | 无（OpenNLP 仅英文） | 需额外配置 |
| 重叠支持 | 无 | 仅 Regex | 所有层级都支持 | 支持 |
| Markdown 按标题切分 | Reader 层有，分块层无 | 无 | 无 | `MarkdownNodeParser` |
| HTML 按结构切分 | Reader 层有，分块层无 | 无 | 无（Jsoup 仅做文本提取） | `HTMLNodeParser` |
| 语义分块（embedding 感知） | 无 | 无 | 无 | `SemanticSplitter` |
| 多层级父子索引 | 无 | 无 | 无 | `HierarchicalNodeParser` |
| 可用分块器数量 | 1 | 1 | 7 | 10+ |

### 10.2 选型决策

**决策：不自建依赖任何框架的分块器，自建策略层。**

#### 排除 Spring AI / Spring AI Alibaba

1. **契约限制**：`TextSplitter.splitText(String)` 只接收纯文本，丢失文档类型、信号、解析模式等分块决策上下文
2. **能力缺失**：两者合计只有 2 个分块器（`TokenTextSplitter` + `RegexTextSplitter`），不支持段落/标题/语义感知
3. **设计哲学不匹配**：Spring AI 将结构感知放在 Reader 层，分块层只做 token 切割。但 RAG Admin 的需求是"同一文档类型可能需要不同分块策略"，这要求分块层具备完整的策略选择能力

#### 排除引入 LangChain4j 分块依赖

1. **结构感知缺失**：7 个分块器全部工作在纯文本层，不感知 Markdown 标题、HTML DOM、PDF 表格等结构
2. **中文支持空白**：`DocumentBySentenceSplitter` 使用 Apache OpenNLP，仅捆绑英文模型，中文句子检测完全不可用
3. **依赖成本**：引入 LangChain4j 分块模块会带入 OpenNLP 等依赖，但实际可复用的只有递归降级模式——这个逻辑不复杂，自建更灵活
4. **可借鉴的设计**：递归降级（段落→行→句子→词→字符）的 fallback 链条思路，在本项目的 `RecursiveFallbackStrategy` 中采纳

#### LlamaIndex 为什么不能直接用

1. **语言生态**：LlamaIndex 是 Python 框架，本项目是 Spring Boot / Java 技术栈，无法直接集成
2. **可借鉴的设计**：
   - `MarkdownNodeParser`：按 heading 层级切分，代码块/表格保持完整 → 本项目 `MarkdownChunkStrategy` 的设计原型
   - `SemanticSplitter`：用 embedding 相似度在段落间找语义断点 → 本项目远期目标
   - `HierarchicalNodeParser`：小 Chunk 用于检索，大 Chunk 用于回答 → 多层级索引的演进方向

#### 面试视角的选型论证

**问：为什么不用 LangChain4j 的分块器？**

LangChain4j 的分块器设计目标是"通用文本切割"，它的递归降级模式对纯文本场景够用。但在 RAG Admin 系统中，分块质量直接决定检索精度，需要按文档类型（Markdown/PDF/HTML）和解析模式（TEXT/OCR）选择不同策略。LangChain4j 的分块器不感知文档结构，也没有中文句子检测能力，引入后仍需大量自定义，不如自建策略层更干净。

**问：为什么自建而不实现 Spring AI 的 TextSplitter 接口？**

Spring AI 的 `TextSplitter` 签名是 `String → List<String>`，把分块简化为纯字符串切割。但分块决策需要知道文档类型（PDF 还是 Markdown）、解析模式（TEXT 还是 OCR）、清洗信号（是否有弱段落结构）等上下文。实现 `TextSplitter` 意味着在方法内部重新解析这些信息，破坏了已有的信号检测链路。自定义接口 `DocumentChunkStrategy` 可以直接接收 `ChunkContext`（包含 document + signals + properties），让每个策略基于完整上下文做决策。

**问：LlamaIndex 的分块能力更完整，有没有考虑过？**

LlamaIndex 确实是目前分块生态最完整的框架，特别是 `SemanticSplitter` 和 `HierarchicalNodeParser`。但它是 Python 框架，与 Java/Spring Boot 技术栈不兼容。我们借鉴了 LlamaIndex 的设计思路（Markdown heading 层级切分、语义分块概念），用 Java 在自建策略层中重新实现。远期目标是在 `SemanticChunkStrategy` 中用 embedding 相似度做语义断点检测，这目前没有 Java 框架提供。

## 11. 远期演进：语义分块

### 11.1 概念

语义分块（Semantic Chunking）不按固定字符数或段落边界切分，而是用 embedding 相似度在段落间寻找语义断点。当两个相邻段落的语义相似度低于阈值时，在此处切分。

### 11.2 实现思路

```java
public class SemanticChunkStrategy implements DocumentChunkStrategy {
    
    private final EmbeddingModelClient embeddingClient;
    private final double similarityThreshold; // 默认 0.5
    
    @Override
    public List<ChunkDraft> chunk(List<Document> documents, ChunkContext context) {
        // 1. 将文档按段落拆分
        // 2. 对每个段落生成 embedding
        // 3. 计算相邻段落的余弦相似度
        // 4. 在相似度低于阈值的段落间切分
        // 5. 聚合同一语义段内的段落
    }
}
```

### 11.3 代价

- 每个段落需要一次 embedding 调用，成本较高
- 适合高价值文档（知识库核心内容），不适合批量导入场景
- 可以作为可选策略，与固定分块策略并存

### 11.4 多层级索引（更远期）

借鉴 LlamaIndex 的 `HierarchicalNodeParser`：
- 小 Chunk（200-300 字符）用于精确检索
- 大 Chunk（800-1200 字符）用于上下文回答
- 两者通过父子关系关联，检索时先命中小 Chunk，回答时取对应的大 Chunk
