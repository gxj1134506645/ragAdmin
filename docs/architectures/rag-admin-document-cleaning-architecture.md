# ragAdmin 文档清洗架构设计

## 1. 文档定位

本文档用于收敛 `ragAdmin` 在知识库导入链路中的“文档清洗”正式技术设计，统一回答以下问题：

- 文档清洗应该如何分层
- 哪些清洗规则可以全局默认开启，哪些必须按策略启用
- 特殊符号、章节边界、列表边界应如何保护
- 清洗与分块的职责边界如何划分
- `metadata` 应如何补齐，支撑后续切片、检索与溯源

当前阶段如果局部实现与本文档冲突，应优先更新本文档，再调整实现。

## 2. 设计边界

本文档只覆盖知识库导入链路中的“文档清洗”阶段，不覆盖以下内容：

- 系统总体架构，见 `rag-admin-architecture.md`
- 文档导入总入口，见 `rag-admin-document-ingestion-architecture.md`
- 文档加载，见 `rag-admin-document-loading-architecture.md`
- API 契约，见 `rag-admin-api-design.md`
- 数据库结构，见 `rag-admin-schema-v1.sql`

## 3. 总体结论

### 3.1 清洗的定位

清洗层负责把加载阶段输出的 `List<Document>` 收口成稳定、可切片、可追溯的高质量文档结果。

导入主链路如下：

`原始文件 -> 文档加载 -> List<Document> -> 清洗 -> 切片 -> 向量化 -> 入库`

### 3.2 清洗不是单一通用方法

清洗不能试图用一套规则处理所有文档。

原因如下：

- 同一类型文件的内部结构和内容复杂度可能差异很大
- 某些符号在一个文档里是噪声，在另一个文档里可能是章节边界或列表边界
- `MinerU API` 返回结果、文本型 `PDF`、`Markdown`、`HTML` 的清洗风险完全不同

因此：

- 清洗必须分层
- 清洗必须按策略启用
- 可能改变语义的规则不能作为全局默认动作

### 3.3 清洗与分块的边界

- 清洗层负责去噪、归一化、补齐 metadata、保护结构边界
- 分块层负责利用这些结构边界生成最终 chunk

禁止混淆：

- 清洗层不应直接承担最终 chunk 设计
- 能在分块阶段利用的结构信息，不应在清洗阶段提前破坏

## 4. 分层设计

### 4.1 清洗标准化层

职责：

- 清理脏数据
- 统一换行与空白
- 按策略启用页眉页脚清理、断行修复、`MinerU`/OCR 噪声处理等增强动作
- 补齐统一 `metadata`
- 识别空文档、低质量文档、解析失败文档

当前阶段清洗层仍然以 `List<Document>` 为输入输出，不额外引入强制中间模型。

推荐接口：

```java
public interface DocumentCleaner {

    List<Document> clean(List<Document> documents, DocumentCleanContext context);
}
```

### 4.2 清洗策略层

职责：

- 根据文档类型、解析模式和内容特征决定启用哪些 cleaner
- 约束语义敏感清洗的启用条件
- 控制 cleaner 的执行顺序与启用子集，避免“把所有 cleaner 全跑一遍”的副作用叠加

推荐接口：

```java
public interface CleanerPolicyResolver {

    DocumentCleanPolicy resolve(DocumentParseRequest request);
}
```

其中 `DocumentCleanContext` 或 `DocumentCleanPolicy` 至少应包含：

- `docType`
- `parseMode`
- `readerType`
- `safeCleanEnabled`
- `semanticCleanEnabled`
- `preserveSymbols`
- `headerFooterCleanEnabled`
- `lineMergeEnabled`
- `ocrNoiseCleanEnabled`

### 4.3 特征分析层

清洗策略不能只看 `docType` 和 `parseMode`，还应结合内容特征做判断。

推荐增加特征分析层，用于把 reader 输出转换成一组可用于决策的信号：

```java
public interface DocumentSignalAnalyzer {

    DocumentSignals analyze(List<Document> documents, DocumentCleanContext context);
}
```

推荐的 `DocumentSignals` 至少覆盖以下维度：

- `repeatedHeaderDetected`
- `repeatedFooterDetected`
- `tooManyBlankLines`
- `weakParagraphStructure`
- `ocrNoiseDetected`
- `symbolDensityHigh`
- `tocOutlineMissing`

约束：

- 特征分析层只负责判断，不直接改写文本
- 清洗层消费的是“信号 + 策略”，不是直接穷举所有 cleaner

## 5. 清洗规则

### 5.1 清洗分层原则

清洗必须分层，不能将所有规则视为同一等级。

推荐分为两类：

#### 5.1.1 安全清洗

安全清洗默认可作为全局基础动作，原则上不改变文本语义，只做格式归一化。

示例：

- 统一 `\r\n` 为 `\n`
- 删除首尾空白
- 合并过多连续空行
- 清理明显不可见字符
- 统一空白字符形式

约束：

- 安全清洗不应删除结构性标点和特殊符号
- 安全清洗不应主动重排文本顺序

#### 5.1.2 语义敏感清洗

语义敏感清洗会影响文本边界、结构信息或特殊字符含义，只能按策略启用，不能全局默认开启。

示例：

- 删除页眉页脚
- 合并断行
- 删除重复水印文本
- 去掉编号前缀
- 去掉特殊符号
- 按特定分隔符预处理

约束：

- 这类规则必须依赖文档类型、解析模式和内容特征判断
- 任何可能破坏章节边界、条款边界、列表边界、代码边界、公式边界的规则，都必须默认关闭

### 5.2 通用清洗动作

所有 reader 的输出都应经过统一清洗：

- 统一 `\r\n` 为 `\n`
- 删除首尾空白
- 合并连续空白行
- 清理明显乱码字符
- 过滤无意义噪声文本
- 标记内容为空或内容过短的文档

说明：

- 以上动作属于安全清洗的默认范围
- 不包括删除特殊符号、激进断行合并、章节编号改写等语义敏感动作

### 5.3 按策略启用的增强清洗

以下规则只能按策略启用：

- `PdfHeaderFooterCleaner`
- `LineMergeCleaner`
- `MineruNoiseCleaner`
- `RepeatedWatermarkCleaner`
- `ListPrefixCleaner`
- `SymbolAwarePreprocessor`

建议策略输入至少包含三层信息：

#### 5.3.1 按文档类型

示例：

- `TXT`：默认只做轻量安全清洗
- `Markdown`：默认保留结构与特殊符号，不做激进文本改写
- `HTML`：优先做正文区域裁剪后的页面噪声移除
- 文本型 `PDF`：可按策略启用页眉页脚清理与断行修复
- 扫描型 `PDF` / 图片：优先做 `MinerU` 返回文本的噪声处理，但对标点与分隔符更保守

#### 5.3.2 按解析模式

示例：

- `TEXT`：可以启用更积极的断行修复
- `OCR` / `LAYOUT`：默认更保守，避免误删真实文本边界

#### 5.3.3 按内容特征

示例：

- 页首页尾重复行占比高，才启用页眉页脚清理
- 特殊符号占比异常高，才考虑进入特殊符号分析
- `MinerU` / OCR 返回结果噪声模式明显，才启用噪声清理
- 存在稳定章节分隔符，才把对应符号作为候选边界

### 5.4 清洗执行原则

清洗不应采用“有多少 cleaner 就都跑一遍”的方式。

原因如下：

- cleaner 之间会相互影响输入
- 页眉页脚清理、断行合并、符号预处理都可能改变后续 cleaner 的判断依据
- 暴力穷举会导致副作用叠加，最终破坏文本结构

因此当前推荐执行模型为：

1. 先执行低风险、安全清洗
2. 再结合 `docType + parseMode + DocumentSignals` 生成策略
3. 最后只执行一个有序的 cleaner 子集

一句话：

`规则驱动的有序选择` 优先于 `工具穷举试错`。

## 6. 特殊符号处理原则

### 6.1 特殊符号不能默认视为噪声

同一种符号在不同文档中可能承担不同语义。

典型示例：

- `•`
- `-`
- `1.`
- `（一）`
- `§`
- `【】`
- `---`

这些符号有时是噪声，有时是段落、列表、条款、章节或引用边界。

因此，处理原则如下：

- 能保留的优先保留，不做默认删除
- 能标记为结构提示的，不直接删除
- 能在切片阶段利用的，不在清洗阶段破坏
- 必须删除时，也应先完成边界识别，再执行最小化替换

### 6.2 特殊符号与切片的关系

如果某类特殊符号承担语义边界作用，更合理的做法不是直接删除，而是：

- 先将其识别为结构提示
- 或先用于预切分
- 再决定是否在最终 chunk 文本中保留原符号

推荐方式：

- 将列表符号标记为 `LIST_ITEM` 边界提示
- 将条款符号标记为 `SECTION_BOUNDARY` 提示
- 将装饰性分隔符标准化为统一边界标记，而不是直接抹掉

## 7. 文本质量判定

以下情况应判定为低质量输出，并进入失败、告警或回退逻辑：

- 文本为空
- 文本长度明显异常
- `MinerU` / OCR 结果高比例乱码
- 重复页眉页脚占比过高
- 页面数很多但抽取结果极少

### 7.1 页眉页脚处理

对 `PDF` 场景应支持统一的页眉页脚清理能力：

- 允许按重复模式识别
- 允许按固定配置删除
- 删除动作必须在 `metadata` 中保留清洗标记

约束：

- 页眉页脚清理属于语义敏感清洗，不应对所有 `PDF` 默认开启
- 只有在重复模式稳定、误删风险可控时才应启用

## 8. Metadata 约定

当前阶段 `Document.metadata` 应作为后续切片、溯源与审计的统一事实来源。

建议至少保留以下字段：

- `sourceFileName`
- `docType`
- `readerType`
- `parseMode`
- `pageNo`
- `sectionTitle`
- `storageBucket`
- `storageObjectKey`
- `cleaned`
- `cleanVersion`

按类型可补充字段：

- `sheetName`
- `slideNo`
- `htmlSelector`
- `ocrEngine`
- `layoutEngine`

后续入库到 `kb_chunk.metadata_json` 时，应优先继承这些字段，并追加 chunk 级信息，例如：

- `chunkSourceStart`
- `chunkSourceEnd`
- `chunkStrategy`

## 9. 与分块的关系

### 9.1 当前原则

文档读取结果统一为 `List<Document>` 后，不代表可以直接无清洗进入向量化。必须先经过清洗和必要的元数据补齐，再进入分块。

补充约束：

- 清洗默认应保守，而不是激进
- 分块默认应尽量利用结构，而不是依赖激进去噪
- 能在分块阶段利用的结构信息，不应在清洗阶段提前破坏

### 9.2 分块优先级

推荐优先保留以下边界：

- Markdown 标题边界
- PDF 段落边界
- 页码边界
- HTML 正文区块边界

长度控制应作为第二优先级，而不是第一优先级。

如果特殊符号本身承担分段作用，分块层应优先消费这些结构提示，而不是要求清洗层先删除它们。

## 10. 当前阶段实施建议

当前阶段建议先实现：

- `SafeNormalizationCleaner`
- `CleanerPolicyResolver`
- `PdfHeaderFooterCleaner`
- `OcrNoiseCleaner`
- `SymbolAwarePreprocessor`

实现顺序建议：

1. 先完成安全清洗
2. 再补策略控制骨架
3. 最后按文档类型逐个引入语义敏感清洗规则

## 11. 最终原则

一句话总结：

`ragAdmin` 的文档清洗必须分层并按策略启用；默认只做安全清洗，任何可能影响章节边界、列表边界或特殊符号含义的规则，都必须保守处理并交由策略与分块阶段协同消费。
