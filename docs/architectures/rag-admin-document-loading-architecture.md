# ragAdmin 文档加载架构设计

## 1. 文档定位

本文档用于收敛 `ragAdmin` 在知识库导入链路中的“文档加载”正式技术设计，统一回答以下问题：

- 文档加载阶段的统一输出标准是什么
- 不同文件类型应如何选择 `DocumentReader`
- 哪些文档必须先走 `OCR` 或更强的结构化解析
- 文档识别、读取与分流的职责边界如何划分

当前阶段如果局部实现与本文档冲突，应优先更新本文档，再调整实现。

## 2. 设计边界

本文档只覆盖知识库导入链路中的“文档识别与加载”阶段，不覆盖以下内容：

- 系统总体架构，见 `rag-admin-architecture.md`
- 文档导入总入口，见 `rag-admin-document-ingestion-architecture.md`
- 文档清洗，见 `rag-admin-document-cleaning-architecture.md`
- API 契约，见 `rag-admin-api-design.md`
- 数据库结构，见 `rag-admin-schema-v1.sql`

## 3. 总体结论

### 3.1 当前统一输出标准

`ragAdmin` 当前阶段将 `org.springframework.ai.document.Document` 作为文档加载后的统一结果，统一输出形态为 `List<Document>`。

原因如下：

- Spring AI / Spring AI Alibaba 已围绕 `Document` 提供后续切片、向量化与检索能力
- 当前知识库导入主链路的核心需求是稳定抽取文本并进入 RAG，`content + metadata` 已能满足一期落地
- 当前阶段不强制引入项目内中间模型，避免在读取层过早增加抽象成本

因此，加载阶段的默认输出为：

`原始文件 -> DocumentReader / MinerU API / 结构化解析 -> List<Document>`

### 3.2 读取职责边界

- 文档识别层：负责识别文件类型、解析模式、读取器类型
- 读取策略层：负责选择最合适的 `DocumentReader` 或等价解析器
- 加载阶段只负责产出 `List<Document>`，不负责最终分块设计

禁止混淆：

- `Tika` 不负责分块
- `MinerU API` / OCR 不负责向量化
- 读取层不应直接等价于最终 chunk 方案

### 3.3 PDF 的特殊性

`PDF` 不能被视为单一类型，至少要区分三类：

- 文本型 `PDF`
- 扫描型 `PDF`
- 复杂版式 `PDF`

三者的加载策略不能完全相同。尤其是扫描型与复杂版式 `PDF`，不能只依赖通用 `PDF DocumentReader` 或 `Tika` 纯文本抽取。

## 4. 分层设计

### 4.1 文档识别层

职责：

- 基于扩展名、MIME、文件头、抽样内容识别文档类型
- 区分文本型 `PDF` 与扫描型 `PDF`
- 判断是否需要 `MinerU API` 或结构化增强解析

输入示例：

- `fileName`
- `extension`
- `mimeType`
- `Resource`
- `storageBucket`
- `storageObjectKey`

输出示例：

- `docType`
- `parseMode`
- `readerType`

推荐的 `parseMode`：

- `TEXT`
- `OCR`
- `LAYOUT`

### 4.2 读取策略层

职责：

- 根据文档类型选择最合适的 `DocumentReader` 或解析器
- 对外统一提供 `read()` 能力
- 统一返回 `List<Document>`

推荐接口：

```java
public interface DocumentReaderStrategy {

    boolean supports(DocumentParseRequest request);

    List<Document> read(DocumentParseRequest request) throws Exception;
}
```

推荐再提供一个路由器：

```java
public interface DocumentReaderRouter {

    DocumentReaderStrategy route(DocumentParseRequest request);
}
```

## 5. 不同文档类型的读取策略

### 5.1 TXT

推荐实现：

- `TextReader`

适用特点：

- 纯文本
- 结构简单
- 无复杂版式

### 5.2 Markdown

推荐实现：

- `MarkdownDocumentReader`

适用特点：

- 标题层级天然明确
- 适合知识库场景

### 5.3 HTML

推荐实现：

- `JsoupDocumentReader`

适用特点：

- 网页、帮助中心、富文本页面

约束：

- 不建议无选择器全页面直读
- `HTML` 的关键不是“能不能读”，而是“读哪一块”

### 5.4 文本型 PDF

推荐实现：

- 优先 `ParagraphPdfDocumentReader`
- 兜底 `PagePdfDocumentReader`

使用原则：

- `ParagraphPdfDocumentReader` 优先用于质量较高、内部结构较完整的电子版 `PDF`
- `PagePdfDocumentReader` 用于段落抽取质量差时的回退，或需要稳定页码定位时
- 如果 `ParagraphPdfDocumentReader` 因缺少 TOC / outline 抛出异常，必须直接降级到 `PagePdfDocumentReader`
- 即使未抛异常，只要段落抽取结果明显偏弱，例如总文本过短、单段文本过短或结构明显不完整，也应降级到 `PagePdfDocumentReader`

结论：

- RAG 场景默认优先段落 reader
- 若段落抽取质量不稳定，则回退为按页读取后再统一进入清洗与切片阶段
- 简历、合同、导出报告等无目录 PDF 不应因为段落 reader 失败而终止整条解析任务
- 段落 reader 的“伪成功”同样需要识别；不能只在抛异常时才考虑按页降级

### 5.5 扫描型 PDF

推荐实现：

- `MinerU API`

约束：

- 扫描型 `PDF` 本质上属于图像文本识别链路
- 不应默认使用普通 `PDF DocumentReader`
- 不应假定 `Tika` 可恢复高质量正文
- 当前标准主链路不再依赖本地 `tesseract/tess4j`

推荐链路：

`扫描 PDF -> MinerU API -> List<Document>`

### 5.6 复杂版式 PDF

典型特征：

- 多栏排版
- 表格密集
- 公式较多
- 图文混排
- 图片中包含关键信息

推荐实现：

- 优先 `MinerU API`
- 或采用等价的版面恢复与结构化解析能力

结论：

- 对复杂版式 `PDF`，`Tika` 与简单 `PDF DocumentReader` 只能作为降级兜底，不能作为默认最佳实现

### 5.7 DOCX / PPTX / XLSX

推荐实现：

- `TikaDocumentReader`

适用特点：

- 一期优先打通主链路
- 支持常见 Office 文档快速接入

局限：

- 表格、幻灯片层级、单元格关系的保真度有限

### 5.8 PNG / JPG / JPEG / WEBP

推荐实现：

- `MinerU API` Reader

结论：

- 图片文件不走普通文本 reader
- 默认先通过 `MinerU API` 产出文本与结构化结果，再统一进入 `List<Document>` 链路

## 6. Reader 路由策略

当前阶段建议使用“文件类型 + 内容特征”双重路由，而不是只看后缀。

推荐路由如下：

- `TXT` -> `TextReaderStrategy`
- `MD` -> `MarkdownReaderStrategy`
- `HTML/HTM` -> `HtmlReaderStrategy`
- `PDF`
  - 文本型且结构较好 -> `PdfParagraphReaderStrategy`
  - 文本型但段落质量差 -> `PdfPageReaderStrategy`
  - 扫描型或复杂版式 -> `MineruDocumentReaderStrategy`
- `DOCX/PPTX/XLSX` -> `TikaReaderStrategy`
- `PNG/JPG/JPEG/WEBP` -> `MineruDocumentReaderStrategy`

## 7. 与清洗的关系

加载阶段统一输出 `List<Document>` 后，必须进入清洗阶段，再由分块层消费。

加载阶段默认只承担：

- 文件识别
- 解析能力路由
- 文本与基础 metadata 抽取

去噪、断行修复、符号保护、页眉页脚处理等规则，见 `rag-admin-document-cleaning-architecture.md`。

## 8. 当前阶段实施建议

### 8.1 一期推荐组合

- `TXT` -> `TextReader`
- `MD` -> `MarkdownDocumentReader`
- `HTML` -> `JsoupDocumentReader`
- `PDF` -> 优先 `ParagraphPdfDocumentReader`，失败回退 `PagePdfDocumentReader`
- `DOCX/PPTX/XLSX` -> `TikaDocumentReader`
- `PNG/JPG/JPEG/WEBP` -> `MinerU API`

### 8.2 二期增强方向

- 增加扫描 `PDF` 自动识别
- 细化 `MinerU API` 的任务提交、轮询、回调与结果下载策略
- 为 `XLSX` / `PPTX` 引入更强结构保真策略

## 9. 最终原则

一句话总结：

`ragAdmin` 的文档加载阶段只负责把不同格式的原始文件转换为统一的 `List<Document>`，并通过最合适的 reader、`MinerU API` 或结构化解析能力完成分流，而不是在读取阶段提前承担清洗与最终切片职责。
