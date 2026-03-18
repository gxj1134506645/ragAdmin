# 会话记忆持久化设计

## 1. 背景

当前问答链路已经具备：

- `chat_session`：会话列表与会话归属
- `chat_message`：问答内容、模型用量、耗时
- `chat_answer_reference`：回答引用切片
- `chat_feedback`：用户反馈

但模型上下文记忆仍然没有持久化能力，导致服务重启后无法继续基于历史对话维持上下文，也无法直接复用 Spring AI 官方提供的 `ChatMemory` 能力。

用户已经明确要求：

- 优先采用 Spring AI 官方方案
- 持久化落 PostgreSQL
- 结合 Spring AI Alibaba / Spring AI `ChatClient`
- 严格做用户隔离
- 当前阶段默认长期保留

## 2. 方案对比

### 2.1 方案 A：直接用官方 memory 表替代现有会话业务表

优点：

- 统一只维护一套消息存储
- 模型上下文与业务消息天然一致

缺点：

- 官方表只有 `conversation_id / content / type / timestamp`
- 无法承载当前系统已有的知识库、引用片段、反馈、耗时、token、模型 ID 等业务字段
- 需要同步重构会话列表、消息展示、反馈、审计、统计等链路，改动面过大

### 2.2 方案 B：官方 memory 表与现有业务表并存

优点：

- 改动面最小
- 现有 API 与前端结构基本不变
- `chat_message` 继续承担业务展示、审计、引用溯源
- 官方 `ChatMemory` 只负责模型上下文记忆，职责清晰

缺点：

- 会存在两套消息存储
- 需要处理历史会话向 memory 的首次补种

### 2.3 方案 C：完全自研 memory，不接 Spring AI 官方仓储

优点：

- 完全可控
- 能完全按现有表设计

缺点：

- 重复造轮子
- 后续无法直接复用 Spring AI 的 `MessageChatMemoryAdvisor` / `PromptChatMemoryAdvisor`
- 与用户已明确指定的方向不一致

## 3. 最终选择

本次采用方案 B。

核心决策：

- 保留 `chat_session`、`chat_message`、`chat_answer_reference`、`chat_feedback`
- 新增官方 JDBC Chat Memory 表 `spring_ai_chat_memory`
- 问答调用改为通过 Spring AI `ChatClient + MessageChatMemoryAdvisor`
- 现有业务消息继续写 `chat_message`
- 模型上下文记忆通过官方 `ChatMemory` 自动持久化

这样可以在不推倒现有问答闭环的前提下，把“模型记忆”与“业务记录”分离出来。

## 4. conversationId 设计

不能直接只用 `userId` 作为 `conversationId`，否则首页通用对话、知识库内对话以及同一用户的多会话都会串上下文。

当前知识库内会话采用以下格式：

`chat-scene-kb-user-{userId}-kb-{kbId}-session-{sessionId}`

这样做的原因：

- 包含 `scene`，显式区分“知识库内会话”和“首页通用会话”
- 包含 `userId`，满足用户隔离要求
- 包含 `kbId`，避免不同知识库之间共享同一段模型记忆
- 包含 `sessionId`，避免同一用户不同会话相互污染
- 可以稳定映射回现有 `chat_session`

首页通用模型对话当前还未落地，预留规则如下：

- 如果首页只保留“每用户一个默认会话”，可采用 `chat-scene-home-user-{userId}`
- 如果首页后续也支持多会话列表，则扩展为 `chat-scene-home-user-{userId}-session-{sessionId}`

这样可以保证首页会话与知识库会话天然分桶，后续扩展不会与当前知识库 memory 数据混用。

## 5. 数据流设计

### 5.1 首次进入已有会话

如果某个 `conversationId` 在 `spring_ai_chat_memory` 中尚无记录，则：

1. 读取该 `sessionId` 既有的 `chat_message`
2. 依次转换成 `USER / ASSISTANT` 消息
3. 补种到官方 `ChatMemory`

这样可以保证历史会话在切到新链路后，不会因为 memory 表为空而丢失上下文。

### 5.2 新增一轮问答

1. 业务层先完成知识库检索，拼出系统提示词与当前用户问题
2. 基础设施层通过 `ChatClient` 调用聊天模型
3. `MessageChatMemoryAdvisor` 自动读取历史 memory，并在成功后把本轮问答追加到 memory 表
4. 业务层继续把本轮问答写入 `chat_message`
5. 业务层写入 `chat_answer_reference`

## 6. 事务与一致性

本次保持 `ChatService.chat()` 上的声明式事务。

注意点：

- Spring AI JDBC Chat Memory 与业务表共用同一个 PostgreSQL 数据源
- `ChatService.chat()` 的事务边界继续由 Spring AOP 代理管理
- memory 的数据库写入和业务表写入都落在同一个数据库中
- 为避免与 Flyway 职责冲突，关闭 `spring.ai.chat.memory.repository.jdbc.initialize-schema`

风险说明：

- 外部模型调用本身不受数据库事务控制
- 但 memory 表和业务表都在模型调用成功之后落库，能减少“模型失败但库内已写入脏记录”的风险

## 7. 配置约定

新增以下配置基线：

- 引入 `spring-ai-starter-model-chat-memory-repository-jdbc`
- `spring.ai.chat.memory.repository.jdbc.initialize-schema=never`
- `rag.chat.memory.max-messages`

其中 `max-messages` 控制单会话进入模型上下文窗口的历史消息数量，默认先走保守值，后续再根据硬件资源和模型上下文成本调优。

## 8. 后续演进

本次不做的内容：

- 不改现有前端会话 API
- 不做 memory 清理策略
- 不做多知识库联合问答的会话路由
- 不做会话级模型切换
- 不做流式回答改造

后续可以继续演进：

- 会话级模型选择
- 会话归档与清理策略
- 基于 `@知识库` 的多知识库问答入口
- SSE / WebSocket 实时流式输出
