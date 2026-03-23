# 默认聊天模型唯一来源 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将默认聊天模型收敛为后台模型管理中的唯一手动配置项，移除 `application.yml` 的运行时聊天兜底。

**Architecture:** 后端由 `ModelService` 统一解析和校验默认聊天模型，运行时只读取数据库默认标记；前端模型管理页和知识库表单同步收敛提示文案，并在关键操作前做轻量阻断。配置文件仅保留接入参数和默认向量模型，不再承载默认聊天模型事实。

**Tech Stack:** JDK 21、Spring Boot 3、MyBatis Plus、Vue 3、TypeScript、Element Plus、JUnit 5

---

## Chunk 1: 文档与约束说明

### Task 1: 补充设计与计划文档

**Files:**
- Create: `docs/plans/2026-03-23-default-chat-model-design.md`
- Create: `docs/plans/2026-03-23-default-chat-model-implementation-plan.md`
- Modify: `docs/rag-admin-api-design.md`
- Modify: `docs/rag-admin-schema-v1.sql`

- [ ] **Step 1: 写入已确认设计**
- [ ] **Step 2: 更新接口与数据说明中的默认聊天模型规则**
- [ ] **Step 3: 自检术语是否统一为“模型管理手动设置的默认聊天模型”**

### Task 2: 明确配置边界

**Files:**
- Modify: `rag-admin-server/src/main/resources/application.yml`
- Modify: `rag-admin-server/src/main/resources/application-local.yml`
- Modify: `rag-admin-server/src/main/resources/application-dev.yml`
- Modify: `rag-admin-server/src/main/resources/application-local-secret.example.yml`

- [ ] **Step 1: 删除 `default-chat-model` 配置项**
- [ ] **Step 2: 保留提供方接入参数和默认向量模型配置**
- [ ] **Step 3: 检查示例配置说明是否仍引用聊天兜底**

## Chunk 2: 后端规则收敛

### Task 3: 调整默认聊天模型解析逻辑

**Files:**
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/model/service/ModelService.java`

- [ ] **Step 1: 写/改失败测试，覆盖“无后台默认时不再回退配置文件”**
- [ ] **Step 2: 最小化修改 `resolveChatModelDescriptor(null)`，只读取数据库默认模型**
- [ ] **Step 3: 增加明确错误码与中文提示**
- [ ] **Step 4: 运行相关单测确认通过**

### Task 4: 防止默认聊天模型被破坏

**Files:**
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/model/service/ModelService.java`
- Test: `rag-admin-server/src/test/java/com/ragadmin/server/model/service/ModelServiceTest.java`

- [ ] **Step 1: 写失败测试，覆盖删除默认模型和把默认模型改成无效状态**
- [ ] **Step 2: 在更新和删除路径增加阻断校验**
- [ ] **Step 3: 保持“设为默认”接口仍只允许唯一启用聊天模型**
- [ ] **Step 4: 运行单测确认通过**

### Task 5: 清理启动初始化对聊天默认配置的依赖

**Files:**
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/model/service/ModelBootstrapInitializer.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/infra/ai/bailian/BailianProperties.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/infra/ai/embedding/OllamaProperties.java`
- Test: `rag-admin-server/src/test/java/com/ragadmin/server/model/service/ModelBootstrapInitializerTest.java`

- [ ] **Step 1: 删除聊天默认模型配置字段和初始化分支**
- [ ] **Step 2: 保留默认向量模型初始化逻辑**
- [ ] **Step 3: 更新相关测试**

## Chunk 3: 前端提示与验证

### Task 6: 收敛模型管理页的默认模型交互

**Files:**
- Modify: `rag-admin-web/src/views/model/ModelManagementView.vue`

- [ ] **Step 1: 增加默认聊天模型唯一来源提示**
- [ ] **Step 2: 在前端阻断删除当前默认聊天模型**
- [ ] **Step 3: 在编辑默认聊天模型时阻断明显无效状态修改**

### Task 7: 更新知识库配置页提示

**Files:**
- Modify: `rag-admin-web/src/components/knowledge-base/KnowledgeBaseForm.vue`

- [ ] **Step 1: 将聊天模型兜底说明改成“模型管理中设置的默认聊天模型”**
- [ ] **Step 2: 保持向量模型提示不变或仅做必要澄清**

## Chunk 4: 验证与提交

### Task 8: 执行回归验证

**Files:**
- Test: `rag-admin-server/src/test/java/com/ragadmin/server/model/service/ModelServiceTest.java`
- Test: `rag-admin-server/src/test/java/com/ragadmin/server/model/service/ModelBootstrapInitializerTest.java`

- [ ] **Step 1: 运行后端模型相关单测**
- [ ] **Step 2: 如前端可快速验证，则执行构建或类型检查**
- [ ] **Step 3: 记录未覆盖的残余风险**

### Task 9: Git 收口

**Files:**
- Modify: 本次变更涉及文件

- [ ] **Step 1: 自检工作区仅包含本任务相关改动**
- [ ] **Step 2: 使用中文 Conventional Commit 提交**
- [ ] **Step 3: 如环境允许，推送到远端**
