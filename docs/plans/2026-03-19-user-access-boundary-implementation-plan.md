# 统一用户源与前后台登录边界实施计划

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 基于统一用户源落地前后台按角色放行的登录边界，并补齐后台用户管理页面。

**Architecture:** 后端继续复用 `sys_user / sys_role / JWT / Redis`，在认证域集中实现“按登录入口校验角色”的逻辑。后台 Web 新增用户管理页，直接复用既有用户管理接口，不新增独立账号体系。

**Tech Stack:** Spring Boot 3、MyBatis Plus、Flyway、Vue 3、TypeScript、Element Plus

---

## 涉及文件

- 新增：`rag-admin-server/src/main/resources/db/migration/V7__seed_app_user_role.sql`
- 修改：`rag-admin-server/src/main/java/com/ragadmin/server/auth/service/AuthService.java`
- 修改：`rag-admin-server/src/main/java/com/ragadmin/server/app/service/AppPortalService.java`
- 修改：`rag-admin-server/src/main/java/com/ragadmin/server/auth/controller/AuthController.java`
- 修改：`rag-admin-server/src/test/java/com/ragadmin/server/web/AdminApiWebMvcTest.java`
- 修改：`rag-admin-server/src/test/java/com/ragadmin/server/web/AppApiWebMvcTest.java`
- 新增：`rag-admin-web/src/api/user.ts`
- 新增：`rag-admin-web/src/types/user.ts`
- 新增：`rag-admin-web/src/views/user/UserManagementView.vue`
- 修改：`rag-admin-web/src/router/index.ts`
- 修改：`rag-admin-web/src/layouts/AdminLayout.vue`

## 任务 1：补齐后端角色基础数据

- [ ] 新增 Flyway 脚本，补齐 `APP_USER` 角色
- [ ] 保证脚本可重复执行，不影响已有数据
- [ ] 检查脚本版本号连续性

## 任务 2：收口前后台登录边界

- [ ] 在 `AuthService` 中抽出统一的账号密码校验逻辑
- [ ] 在 `AuthService` 中新增“前台登录”与“后台登录”角色校验入口
- [ ] 后台登录允许 `ADMIN`、`KB_ADMIN`、`AUDITOR`
- [ ] 前台登录允许 `APP_USER`、`ADMIN`、`KB_ADMIN`、`AUDITOR`
- [ ] 未命中允许角色时返回明确错误信息
- [ ] `AppPortalService.login` 改为调用前台登录入口
- [ ] `AuthController.login` 改为调用后台登录入口

## 任务 3：补测试覆盖

- [ ] 为后台登录增加“允许管理角色登录”的测试
- [ ] 为后台登录增加“拒绝仅前台角色登录”的测试
- [ ] 为前台登录增加“允许 `APP_USER` 登录”的测试
- [ ] 为前台登录增加“允许 `ADMIN` 登录”的测试
- [ ] 为前台登录增加“拒绝无前台权限角色登录”的测试
- [ ] 运行 `mvn -q -pl rag-admin-server test`

## 任务 4：补后台用户管理 API 封装

- [ ] 新增 `rag-admin-web/src/types/user.ts`，定义用户列表项、创建请求、更新请求、角色分配请求
- [ ] 新增 `rag-admin-web/src/api/user.ts`，封装列表、新增、更新、分配角色接口
- [ ] 保持字段命名与后端 DTO 一致

## 任务 5：补后台用户管理页面

- [ ] 新建 `UserManagementView.vue`
- [ ] 支持关键词与状态筛选
- [ ] 支持分页列表
- [ ] 支持新增用户
- [ ] 支持编辑用户基础资料与密码
- [ ] 支持角色分配
- [ ] 页面内显式展示角色标签，便于判断前后台访问边界

## 任务 6：接入管理台导航与路由

- [ ] 在路由中新增 `/users`
- [ ] 在左侧导航中新增“用户管理”
- [ ] 保持现有后台视觉风格，不额外引入新的布局体系

## 任务 7：前端验证

- [ ] 运行 `npm --prefix rag-admin-web run build`
- [ ] 检查页面是否存在类型错误与路由错误
- [ ] 确认用户管理页可正常进入

## 任务 8：收口提交

- [ ] 自检工作区只包含本轮相关改动
- [ ] 提交后端角色边界与用户管理改动
- [ ] 推送到 `github/master`
