# ragAdmin 会话记忆架构设计

## 1. 文档定位

本文档用于收敛 `ragAdmin` 当前确认的会话记忆架构设计，作为后续实现、评审、数据库演进与接口联调的长期事实来源。

本文档重点回答以下问题：

- 会话记忆为什么不能只依赖单一存储
- `spring_ai_chat_memory`、`chat_message`、Redis 各自承担什么职责
- 长期记忆与短期记忆如何划分
- 短期记忆窗口、空闲过期、摘要触发规则如何配置化
- 首页通用会话、知识库内会话、后台管理会话如何隔离

当前阶段如果历史计划文档与本文档冲突，以本文档为准。

## 2. 背景与问题

当前系统已经具备以下基础能力：

- `chat_session`：会话主数据、终端类型、场景类型、模型偏好、知识库绑定
- `chat_message`：问答业务事实、模型用量、耗时、问答内容
- `chat_answer_reference`：回答引用的切片来源
- `chat_feedback`：用户反馈
- `spring_ai_chat_memory`：Spring AI 官方 JDBC Chat Memory 持久化表

但如果只依赖固定窗口 memory，会存在以下问题：

- 服务重启后需要恢复模型上下文，但不能只依赖进程内 memory
- 只用 `spring_ai_chat_memory` 无法完整承载业务审计、引用、反馈、统计等字段
- 只用 `chat_message` 直接全量回灌模型，历史会越来越长，上下文成本不可控
- 只用 Redis 保存会话记忆，会把热数据误当事实源，存在一致性和丢失风险

因此当前阶段需要明确采用“长期记忆 + 短期记忆”分层方案。

## 3. 设计目标

本次会话记忆架构必须满足以下目标：

- 模型上下文记忆可持久化到 PostgreSQL
- 业务事实数据长期保留并可审计
- 热会话追问具备较快响应能力
- 首页通用会话、知识库内会话、后台管理会话严格隔离
- 短期窗口轮数与空闲过期时间必须可配置，不允许硬编码
- 后续可以平滑扩展摘要压缩、会话归档、会话迁移和多知识库问答

## 4. 最终方案

当前采用方案：

- PostgreSQL 负责长期记忆与业务事实
- Redis 负责短期热记忆
- Spring AI `ChatClient` 继续保留
- Spring AI 官方 `spring_ai_chat_memory` 继续作为模型记忆持久化基础设施
- 新增独立摘要表承载长期摘要和压缩游标

这是一个分层方案，而不是“某一张表包打一切”的方案。

## 5. 核心原则

### 5.1 存储职责分离

不同存储必须各司其职：

- `chat_message`：业务事实源
- `spring_ai_chat_memory`：模型记忆持久化源
- `chat_session_memory_summary`：长期摘要与压缩元数据源
- Redis：短期热记忆缓存

禁止把其中任意一个存储误当成所有职责的唯一来源。

### 5.2 PostgreSQL 是长期事实源

长期保留和审计相关的数据只能落 PostgreSQL，不能只放在 Redis。

### 5.3 Redis 只承载热上下文

Redis 只负责活跃会话的热数据，不负责长期保留，不作为唯一事实源。

### 5.4 会话隔离必须稳定可重复计算

所有记忆存取、恢复、清理都必须以稳定的 `conversationId` 为边界，避免串会话、串终端、串知识库。

### 5.5 配置化优先

短期窗口轮数、空闲过期时间、摘要触发阈值必须放入配置项，不允许写死在代码常量中。

## 6. 存储职责定义

### 6.1 `chat_message`

职责：

- 保存完整问答业务事实
- 支撑前端历史消息展示
- 支撑消息检索、分页、统计、反馈、引用、审计

特点：

- 面向业务查询
- 长期保留
- 不直接等同于模型实际注入上下文

### 6.2 `spring_ai_chat_memory`

职责：

- 承载 Spring AI `ChatClient + MessageChatMemoryAdvisor` 的持久化消息
- 在 PostgreSQL 中保存模型使用的会话消息
- 支撑服务重启后的 memory 恢复

特点：

- 属于模型记忆基础设施层
- 不替代 `chat_message`
- 不适合直接承载摘要版本、压缩游标、审计语义

结论：

必须继续使用 `spring_ai_chat_memory` 将模型记忆持久化到 PostgreSQL，但不能把它当作唯一的业务事实表。

### 6.3 `chat_session_memory_summary`

职责：

- 保存长期摘要文本
- 保存摘要版本
- 保存已压缩到哪一条消息的游标
- 保存摘要来源消息边界

特点：

- 属于业务可控的记忆编排元数据
- 不依赖 Spring AI 表结构演进
- 便于后续做摘要重算、摘要升级、审计与问题排查

### 6.4 Redis 短期记忆

职责：

- 保存当前活跃会话最近 `N` 轮问答
- 保存摘要副本，减少数据库重复读取
- 保存热上下文游标与估算 token
- 提高连续追问场景下的响应效率

特点：

- 只保存热数据
- 采用空闲过期
- 过期后可从 PostgreSQL 恢复

## 7. 长期记忆与短期记忆划分

### 7.1 长期记忆

长期记忆默认落 PostgreSQL，包含：

- `chat_message` 中的完整问答事实
- `spring_ai_chat_memory` 中的模型记忆消息
- `chat_session_memory_summary` 中的长期摘要与压缩游标

长期记忆特点：

- 默认长期保留
- 可用于审计、问题排查、历史恢复
- 允许服务重启后重新恢复会话上下文

### 7.2 短期记忆

短期记忆默认落 Redis，包含：

- 最近 `N` 轮 USER / ASSISTANT 消息
- 最新长期摘要副本
- `compressedUntilMessageId`
- `estimatedTokens`
- `lastActiveAt`

短期记忆特点：

- 面向活跃会话
- 采用空闲过期
- 过期后不丢长期事实，只丢热缓存

### 7.3 当前默认策略

当前已确认的默认业务策略：

- 短期窗口先按最近 `10` 轮
- Redis 空闲过期先按 `2` 小时
- 以上值必须通过配置文件控制

## 8. conversationId 规则

### 8.1 统一规则

当前会话记忆统一采用以下格式：

`chat-terminal-{terminalType}-scene-{sceneType}-user-{userId}-session-{sessionId}`

例如：

- 前台首页通用会话：`chat-terminal-app-scene-general-user-1001-session-21`
- 前台知识库会话：`chat-terminal-app-scene-knowledge_base-user-1001-session-22`
- 后台知识库会话：`chat-terminal-admin-scene-knowledge_base-user-1-session-9`

### 8.2 设计目的

该规则必须满足：

- 区分后台与前台
- 区分首页通用会话与知识库会话
- 区分同一用户的不同会话
- 对 Redis key、memory 清理、摘要恢复都稳定可复用

### 8.3 边界要求

禁止继续使用只包含 `userId` 的 `conversationId`。

否则会导致：

- 首页与知识库内会话串记忆
- 前后台同一用户串记忆
- 多个独立会话共享同一上下文

## 9. 建议新增数据结构

### 9.1 长期摘要表

建议新增表：

`chat_session_memory_summary`

建议字段：

- `id BIGSERIAL PRIMARY KEY`
- `session_id BIGINT NOT NULL`
- `conversation_id VARCHAR(128) NOT NULL`
- `summary_text TEXT NOT NULL`
- `summary_version INTEGER NOT NULL DEFAULT 1`
- `compressed_message_count INTEGER NOT NULL DEFAULT 0`
- `compressed_until_message_id BIGINT`
- `last_source_message_id BIGINT`
- `created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`
- `updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`

建议约束：

- `UNIQUE (session_id)`
- `INDEX (conversation_id)`

### 9.2 Redis 短期记忆模型

建议每个 `conversationId` 对应一份 Redis 热记忆对象。

主 key：

`rag:chat:memory:short:{conversationId}`

建议内容字段：

- `summaryText`
- `compressedUntilMessageId`
- `recentMessages`
- `recentRoundCount`
- `estimatedTokens`
- `lastActiveAt`

辅助 key：

- `rag:chat:memory:lock:{conversationId}`
  - 用于摘要更新互斥锁
- `rag:chat:memory:warm:{conversationId}`
  - 可选，用于标记该会话已完成热加载

## 10. 上下文组装规则

模型调用前，统一组装以下三部分：

1. 长期摘要
2. 最近 `N` 轮短期消息
3. 当前请求的 prompt 消息

禁止直接将全量 `chat_message` 历史全部回灌给模型。

### 10.1 命中 Redis 的路径

如果 Redis 中存在短期热记忆：

- 直接读取短期记忆
- 组装为 `长期摘要副本 + 最近 N 轮`
- 进入模型调用

### 10.2 Redis 未命中的恢复路径

如果 Redis 不存在短期热记忆：

1. 读取 `chat_session_memory_summary`
2. 读取 `spring_ai_chat_memory` 或基于 `chat_message` 恢复最近窗口消息
3. 构造 Redis 热记忆对象
4. 写回 Redis 并设置空闲过期时间
5. 再进入模型调用

### 10.3 业务层边界

`ChatService` 与 `AppChatService` 不应自己拼装“长期摘要 + 最近窗口”的细节。

该能力应下沉到 `infra.ai.chat` 的记忆编排层，由统一组件负责：

- 热记忆读取
- Redis 回填
- 摘要刷新
- 上下文输出

### 10.4 刷新执行方式

业务消息落库后的会话记忆刷新应采用后台异步执行：

- 主问答链路只负责提交刷新任务，不同步等待摘要生成完成
- 刷新任务默认走阻塞型 IO 执行器，例如虚拟线程执行器
- 即使使用虚拟线程，也必须由业务显式控制最大并发，避免摘要模型调用无限放量
- 同一 `conversationId` 在单实例内应尽量做本地去重，减少重复刷新提交

## 11. 摘要触发策略

### 11.1 触发时机

当满足以下任一条件时，触发摘要压缩：

- 最近短期消息轮数超过配置阈值
- 估算 token 超过配置阈值

### 11.2 压缩行为

触发摘要时建议执行：

1. 取出“已摘要游标之后”的较早一段消息
2. 生成新的长期摘要文本
3. 更新 `chat_session_memory_summary`
4. 更新 Redis 中的 `summaryText`
5. 仅保留最近 `N` 轮消息继续作为短期记忆

### 11.3 目标结果

压缩后的上下文应变为：

- 一份更短但连续的长期摘要
- 一份较小的近期对话窗口

这样可以控制模型输入体积，避免会话越聊越重。

## 12. 配置设计

所有短期窗口与摘要阈值必须配置化，统一挂在：

`rag.chat.memory`

建议配置项：

- `max-messages`
  - Spring AI 底层 memory 的兜底窗口值
- `short-term-rounds`
  - 短期记忆保留轮数
- `short-term-idle-ttl-minutes`
  - 短期记忆空闲过期分钟数
- `summary-trigger-rounds`
  - 触发摘要的轮数阈值
- `summary-trigger-token-threshold`
  - 触发摘要的估算 token 阈值
- `summary-max-length`
  - 长期摘要最大长度
- `summary-lock-seconds`
  - 摘要更新锁超时时间
- `refresh-max-concurrency`
  - 会话记忆后台刷新最大并发数
- `redis-key-prefix`
  - Redis key 前缀

当前默认建议值：

- `max-messages: 20`
- `short-term-rounds: 10`
- `short-term-idle-ttl-minutes: 120`

但这些值只作为初始默认值，最终以部署环境配置为准。

## 13. 删除与清理规则

删除会话时，必须同时清理以下四类数据：

- `chat_message`
- `chat_answer_reference`
- `chat_feedback`
- `spring_ai_chat_memory`
- `chat_session_memory_summary`
- Redis 短期热记忆

注意：

- Redis 清理只是热数据删除
- PostgreSQL 清理才是长期事实删除
- 两者必须一起执行，不能只删其一

## 14. 并发、一致性与事务说明

### 14.1 事务边界

数据库写入仍需遵守现有 `@Transactional` 事务边界，尤其要注意 Spring AOP 自调用失效问题。

### 14.2 外部模型调用

外部模型调用不受数据库事务控制，因此必须坚持以下原则：

- 模型成功后再持久化业务事实
- 摘要更新失败不能污染主问答结果
- Redis 写失败时，允许回退到 PostgreSQL 恢复路径

### 14.3 摘要更新互斥

同一 `conversationId` 的摘要更新应通过 Redis 锁或同等级互斥机制控制，避免并发请求重复压缩、游标错乱。

### 14.4 后台刷新限流

会话记忆后台刷新默认属于阻塞型 IO 任务，虽然可以运行在虚拟线程中，但仍需满足以下约束：

- 必须配置独立的最大并发数，不能无限提交
- 达到并发上限时，允许跳过当前次刷新，由后续消息再次触发重建
- 刷新失败只记录日志并释放占位，不能影响主问答结果返回

## 15. 与现有实现的关系

当前仓库中已经具备以下基础：

- `spring_ai_chat_memory` 官方 JDBC 表
- `ChatClient + MessageChatMemoryAdvisor`
- `chat_session / chat_message` 会话与消息业务表

后续实现时应调整为：

- 保留 `spring_ai_chat_memory` 持久化能力
- 不再将固定窗口 memory 视为完整方案
- 引入“长期摘要 + 短期热记忆”的统一编排层
- 将窗口轮数和空闲时间迁移为配置项

## 16. 当前结论

本项目会话记忆架构最终结论如下：

- 模型记忆必须通过 `spring_ai_chat_memory` 持久化到 PostgreSQL
- 业务事实必须继续由 `chat_message` 承担
- 长期摘要必须由独立摘要表承载
- 短期记忆必须放在 Redis 中，并采用空闲过期
- 短期窗口轮数和空闲过期时间必须配置化
- 记忆恢复与上下文组装必须通过统一记忆编排层完成

这套设计既保留了 Spring AI 官方记忆链路，也保留了业务侧对会话、摘要、审计和隔离规则的显式控制权。
