# 混合会话记忆实现计划

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 Spring AI ChatClient 链路上落地“PostgreSQL 长期记忆 + Redis 短期热记忆 + 摘要表”的混合会话记忆基础设施。

**Architecture:** 保留 `spring_ai_chat_memory` 作为模型记忆持久化表，新增 `chat_session_memory_summary` 承载长期摘要元数据，并在 `infra.ai.chat` 下实现自定义 `ChatMemory` 与 Redis 短期记忆存储。聊天主链路继续由 `ChatService` / `AppChatService` 驱动，但记忆恢复、短期窗口和摘要刷新统一下沉到记忆编排层。

**Tech Stack:** JDK 21、Spring Boot 3、Spring AI、Redis、PostgreSQL、Flyway、MyBatis Plus、Jackson。

---

## 文件结构

本轮预期涉及以下文件：

- 新增：`docs/plans/chat-memory-hybrid-implementation-plan.md`
- 新增：`rag-admin-server/src/main/resources/db/migration/V8__add_chat_memory_summary.sql`
- 新增：`rag-admin-server/src/main/java/com/ragadmin/server/chat/entity/ChatSessionMemorySummaryEntity.java`
- 新增：`rag-admin-server/src/main/java/com/ragadmin/server/chat/mapper/ChatSessionMemorySummaryMapper.java`
- 新增：`rag-admin-server/src/main/java/com/ragadmin/server/infra/ai/chat/ConversationIdCodec.java`
- 新增：`rag-admin-server/src/main/java/com/ragadmin/server/infra/ai/chat/RedisShortTermChatMemoryStore.java`
- 新增：`rag-admin-server/src/main/java/com/ragadmin/server/infra/ai/chat/LayeredChatMemory.java`
- 修改：`rag-admin-server/src/main/java/com/ragadmin/server/infra/ai/chat/ChatMemoryProperties.java`
- 修改：`rag-admin-server/src/main/java/com/ragadmin/server/infra/ai/chat/ChatMemoryConfiguration.java`
- 修改：`rag-admin-server/src/main/java/com/ragadmin/server/infra/ai/chat/ConversationMemoryManager.java`
- 修改：`rag-admin-server/src/main/java/com/ragadmin/server/infra/ai/chat/SpringAiConversationMemoryManager.java`
- 修改：`rag-admin-server/src/main/java/com/ragadmin/server/chat/service/ChatService.java`
- 修改：`rag-admin-server/src/main/java/com/ragadmin/server/app/service/AppChatService.java`
- 修改：`rag-admin-server/src/main/java/com/ragadmin/server/chat/service/ChatExchangePersistenceService.java`
- 修改：`docs/architectures/rag-admin-schema-v1.sql`

## Task 1：扩展配置与数据库结构

**Files:**
- Create: `rag-admin-server/src/main/resources/db/migration/V8__add_chat_memory_summary.sql`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/infra/ai/chat/ChatMemoryProperties.java`
- Modify: `docs/architectures/rag-admin-schema-v1.sql`

- [ ] 增加短期窗口、空闲 TTL、摘要阈值、摘要最大长度、Redis key 前缀等配置项
- [ ] 新增 `chat_session_memory_summary` 表迁移脚本
- [ ] 同步更新 schema 草案

## Task 2：实现短期记忆存储与会话 ID 编解码

**Files:**
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/infra/ai/chat/ConversationIdCodec.java`
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/infra/ai/chat/RedisShortTermChatMemoryStore.java`

- [ ] 抽出统一 `conversationId` 构建与 `sessionId` 解析能力
- [ ] 实现 Redis 短期记忆对象读写
- [ ] 约束短期消息窗口只保留最近 `N` 轮消息

## Task 3：实现混合 ChatMemory

**Files:**
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/infra/ai/chat/LayeredChatMemory.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/infra/ai/chat/ChatMemoryConfiguration.java`

- [ ] 保留 `spring_ai_chat_memory` 作为长期模型记忆持久化存储
- [ ] `ChatMemory.get` 改为返回“长期摘要 + 短期窗口”
- [ ] `ChatMemory.add` 改为长期持久化并同步更新短期窗口
- [ ] `ChatMemory.clear` 同时清理 JDBC、Redis、摘要表

## Task 4：接入摘要表与刷新机制

**Files:**
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/chat/entity/ChatSessionMemorySummaryEntity.java`
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/chat/mapper/ChatSessionMemorySummaryMapper.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/infra/ai/chat/ConversationMemoryManager.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/infra/ai/chat/SpringAiConversationMemoryManager.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/chat/service/ChatExchangePersistenceService.java`

- [ ] 新增摘要表实体与 Mapper
- [ ] 在业务消息落库后重建短期热记忆
- [ ] 超过阈值时生成摘要文本并更新压缩游标

## Task 5：统一聊天链路接入

**Files:**
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/chat/service/ChatService.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/app/service/AppChatService.java`

- [ ] 后台问答改用统一 `conversationId` 规则
- [ ] 后台与前台在消息持久化后刷新混合记忆
- [ ] 删除会话时继续统一清理混合记忆

## Task 6：验证与收口

**Files:**
- Test: `rag-admin-server/src/test/java/**`

- [ ] 运行 `mvn -q -pl rag-admin-server test`
- [ ] 修复编译或测试回归
- [ ] 提交并推送本轮改动
