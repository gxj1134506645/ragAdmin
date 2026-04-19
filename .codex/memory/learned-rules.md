# 已学规则

## [R-001] PDF 段落读取必须允许降级到按页读取

- 日期：2026-04-19
- 规则：`PdfDocumentReaderStrategy` 不能假定所有文本型 PDF 都支持 `ParagraphPdfDocumentReader`；当 PDF 缺少 TOC / outline 或段落 reader 抛出相关异常时，必须自动回退到 `PagePdfDocumentReader`
- 来源：真实联调中解析 `/Users/gfish/Downloads/简历 _ gfish.online.pdf` 时触发 `Document outline (e.g. TOC) is null`
- 适用范围：知识库 PDF 加载策略、文档解析任务、后续 PDF reader 扩展
- 备注：这条规则优先于“优先段落 reader”的一般建议
