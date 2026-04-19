# 纠正记录

## [C-001] PDF 无 TOC 时段落读取失败

- 日期：2026-04-19
- 场景：上传普通 PDF 文档进入知识库解析链路，优先走 `ParagraphPdfDocumentReader`
- 错误表现：任务在 `EXTRACT_TEXT` 步骤失败，异常为 `Document outline (e.g. TOC) is null`
- 根因：`ParagraphPdfDocumentReader` 依赖 PDF 自带目录/大纲信息；无 TOC 的 PDF 不满足该 reader 的输入前提
- 纠正动作：在 `PdfDocumentReaderStrategy` 中捕获段落 reader 的 `IllegalArgumentException` 与相关运行时异常，自动降级到 `PagePdfDocumentReader`
- 后续约束：不能把“文本型 PDF”简单等价为“可按段落读取的 PDF”；段落读取失败必须降级，不允许直接让整条解析任务失败
- 适用范围：所有 PDF 文档解析链路，尤其是简历、合同、导出报告等无目录 PDF
