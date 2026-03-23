# ragAdmin 联网增强与 Tavily 接入评估

## 1. 结论

### 1.1 是否建议企业内部 RAG 系统具备联网能力

建议具备，但不应作为核心依赖，只应作为“可控的增强能力”。

原因如下：

- 企业内部 RAG 的核心事实源必须始终是内部知识库，而不是互联网。
- 联网能力主要解决“知识库之外、且强时效”的问题，例如天气、新闻、政策动态、行业公开信息。
- 对制度、流程、SOP、组织口径、权限规则、客户资料等内部事实类问题，联网不应替代内部知识库。
- 如果把联网结果与内部事实混在一起且没有来源隔离，容易带来口径漂移、错误引用和审计风险。

因此更合理的产品定位是：

- 内部知识库问答：以知识库为主，联网只做补充
- 通用问答：允许按权限开启联网增强
- 默认关闭联网，由用户或管理员显式开启

### 1.2 是否推荐优先接入 Tavily

推荐。

在当前 `ragAdmin` 阶段，Tavily 比较适合作为第一家真实联网搜索 Provider，主要原因如下：

- 面向 AI Agent / RAG 场景设计，返回结果更适合直接拼接到 prompt
- 接入成本低，先落最小可用版本更快
- 官方有清晰的 credit 计费模型，便于控制预算
- 先把 `WebSearchProvider` 跑通，比一开始就做多 Provider 更符合当前仓库的 KISS 原则

不建议当前阶段先做复杂的多搜索源统一适配层扩展，只要保持现有 `WebSearchProvider` 抽象即可。

## 2. Tavily 需要钱吗

需要分情况回答。

### 2.1 不是“必须先付费”才能接入

根据 Tavily 官方文档，当前提供免费额度：

- 免费计划 `Researcher`：每月 `1,000` API credits
- 不需要信用卡

所以：

- 做本地开发
- 做联调验证
- 做低频内部试用

通常可以先用免费额度，不一定一开始就付费。

### 2.2 真正上线后，大概率是要花钱的

如果你们准备把联网增强开放给真实用户长期使用，那么大概率需要付费，因为免费额度很容易被消耗完。

官方当前公开价格中，核心信息如下：

| 计划 | 月额度 | 月费 |
| --- | --- | --- |
| Researcher | 1,000 credits | 免费 |
| Project | 4,000 credits | 30 美元 |
| Bootstrap | 15,000 credits | 100 美元 |
| Startup | 38,000 credits | 220 美元 |
| Growth | 100,000 credits | 500 美元 |
| Pay as you go | 按量 | 0.008 美元 / credit |
| Enterprise | 自定义 | 自定义 |

### 2.3 按当前官方 credit 规则推算，联网搜索成本不算高，但必须做预算控制

根据 Tavily 官方 credits 说明：

- `basic search`：每次请求消耗 `1` credit
- `advanced search`：每次请求消耗 `2` credits

按官方 `Pay as you go` 价格 `0.008 美元 / credit` 推算：

- 1 次 `basic search` 约等于 `0.008` 美元
- 1 次 `advanced search` 约等于 `0.016` 美元
- 免费计划大约支持 `1,000` 次 basic search 或 `500` 次 advanced search

这里是基于官方 credits 与单价做的推算，不是 Tavily 明文写出的“每次搜索固定美元价”。

## 3. 对 ragAdmin 的建议

### 3.1 首期产品建议

建议按下面的优先级控制：

1. 先把内部知识库问答作为主链路
2. 联网能力默认关闭
3. 只在前台通用问答优先开放联网增强
4. 知识库问答里允许联网，但只作为补充，不得覆盖内部知识结论

### 3.2 权限与审计建议

联网增强一旦上线，建议至少具备下面这些治理约束：

- 通过后台配置统一启停 Tavily Provider
- 前台只在 `webSearchAvailable=true` 时展示可用联网开关
- 回答中显式区分“知识库引用”和“联网结果”
- 审计日志记录是否开启联网、搜索词、调用耗时、结果条数
- 对高频用户或高风险场景做限流与预算保护

### 3.3 技术实现建议

当前仓库已经有 `WebSearchProvider` 抽象，这是正确方向。后续建议：

- 保持 `infra.search` 作为联网搜索适配层
- 新增 `TavilyWebSearchProvider`
- 使用配置项管理 `apiKey`、`baseUrl`、`searchDepth`、超时、默认结果条数
- 失败时继续保留当前降级策略，不要中断主问答链路
- 在后端显式控制“是否可联网”，不要只靠前端开关

## 4. 我对当前阶段的推荐决策

如果只问“现在要不要做”，我的建议是：

- 要做，但定义成“可选增强能力”
- 第一家真实 Provider 优先接 Tavily
- 先接最小可用版本，只支持搜索摘要，不做复杂网页抓取编排
- 默认在开发环境和试用环境验证
- 等确认确实能提升回答质量后，再决定是否升级到付费计划

更直接一点说：

- 现在可以先接 Tavily 免费额度做验证
- 真正上线给多人长期使用时，应预期需要付费

## 5. 官方信息来源

以下信息基于 2026-03-23 检索到的 Tavily 官方页面整理：

- Tavily Docs: [Credits & Pricing](https://docs.tavily.com/documentation/api-credits)
- Tavily 官网: [Pricing](https://www.tavily.com/pricing)
- Tavily Help Center: [Pricing](https://help.tavily.com/articles/8816424538-pricing)
