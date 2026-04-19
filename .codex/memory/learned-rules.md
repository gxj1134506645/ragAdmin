# 已学规则

## [R-001] PDF 段落读取必须允许降级到按页读取

- 日期：2026-04-19
- 规则：`PdfDocumentReaderStrategy` 不能假定所有文本型 PDF 都支持 `ParagraphPdfDocumentReader`；当 PDF 缺少 TOC / outline 或段落 reader 抛出相关异常时，必须自动回退到 `PagePdfDocumentReader`
- 来源：真实联调中解析 `/Users/gfish/Downloads/简历 _ gfish.online.pdf` 时触发 `Document outline (e.g. TOC) is null`
- 适用范围：知识库 PDF 加载策略、文档解析任务、后续 PDF reader 扩展
- 备注：这条规则优先于“优先段落 reader”的一般建议

## [R-002] 文档清洗必须走规则驱动的有序选择

- 日期：2026-04-19
- 规则：不能把所有 cleaner 无脑全跑一遍；应先做安全清洗，再根据 `docType + parseMode + 内容特征` 生成策略，只执行一个有序的 cleaner 子集
- 来源：真实联调中确认 PDF 页眉页脚、空行、符号噪声等问题会互相影响，暴力穷举 cleaner 容易破坏结构
- 适用范围：所有文档清洗链路，尤其是 PDF、OCR、MinerU 结果清洗
- 备注：建议后续增加 `DocumentSignalAnalyzer` / `DocumentSignals` 层
