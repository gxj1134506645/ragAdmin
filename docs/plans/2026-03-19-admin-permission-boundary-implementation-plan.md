# 后台权限边界与在线会话治理 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将后台认证授权统一切换到 `sa-token + Redis`，落地后台权限码矩阵、按 `userId` 强制下线能力，并让后台前端按 `permissions` 完成菜单、路由和高风险按钮收口。

**Architecture:** 后端继续复用统一用户表、角色表和 PostgreSQL 事实数据，使用 `sa-token` 接管登录态、注解鉴权和在线会话治理，Redis 承担登录热数据。后台权限码首期采用静态矩阵，由统一权限服务解析后返回给前端；前端统一通过 `/api/admin/auth/me` 获取 `roles + permissions`，据此控制路由、菜单和高风险按钮。

**Tech Stack:** Spring Boot 3、Sa-Token、Redis、MyBatis Plus、Vue 3、Pinia、Vue Router、Element Plus

---

## Chunk 1: 后端认证基线切换

### Task 1: 接入 `sa-token` 基础设施并保留统一用户源

**Files:**
- Modify: `rag-admin-server/pom.xml`
- Modify: `rag-admin-server/src/main/resources/application.yml`
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/auth/config/SaTokenConfiguration.java`
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/auth/config/SaTokenAdminStpInterface.java`
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/auth/config/SaTokenAppStpInterface.java`
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/auth/config/SaTokenExceptionHandler.java`
- Test: `rag-admin-server/src/test/java/com/ragadmin/server/web/AdminApiWebMvcTest.java`

- [ ] **Step 1: 添加 `sa-token-spring-boot3-starter` 与 Redis 适配依赖**

```xml
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-spring-boot3-starter</artifactId>
</dependency>
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-redis-jackson</artifactId>
</dependency>
```

- [ ] **Step 2: 在 `application.yml` 增加 `sa-token` 基础配置**

```yaml
sa-token:
  token-name: Authorization
  token-prefix: Bearer
  timeout: 7200
  active-timeout: -1
  is-concurrent: true
  is-share: false
```

- [ ] **Step 3: 创建后台与前台双 `loginType` 的权限装载实现**

```java
@Component
public class SaTokenAdminStpInterface implements StpInterface {
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        if (!"admin".equals(loginType)) {
            return List.of();
        }
        return adminPermissionService.listPermissions((Long) loginId);
    }
}
```

- [ ] **Step 4: 增加统一异常处理，把 `sa-token` 的未登录、无权限错误转成项目标准 `ApiResponse`**

Run: `mvn -q -pl rag-admin-server -Dtest=AdminApiWebMvcTest test`
Expected: 现有公开登录接口仍可访问，未登录访问受保护接口时返回统一 401 结构

- [ ] **Step 5: 提交**

```bash
git add rag-admin-server/pom.xml rag-admin-server/src/main/resources/application.yml rag-admin-server/src/main/java/com/ragadmin/server/auth/config
git commit -m "feat: 接入 sa-token 认证基础设施"
```

### Task 2: 用 `sa-token` 重写前后台认证入口与当前用户接口

**Files:**
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/auth/service/AuthService.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/auth/controller/AuthController.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/app/controller/AppAuthController.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/auth/dto/CurrentUserResponse.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/auth/dto/LoginResponse.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/auth/mapper/AuthUserStructMapper.java`
- Modify: `rag-admin-server/src/test/java/com/ragadmin/server/auth/service/AuthServiceTest.java`
- Modify: `rag-admin-server/src/test/java/com/ragadmin/server/web/AdminApiWebMvcTest.java`

- [ ] **Step 1: 让 `CurrentUserResponse` 增加 `permissions` 字段**

```java
private List<String> roles;
private List<String> permissions;
```

- [ ] **Step 2: 在 `AuthService` 中把登录、刷新、登出、获取当前用户全部切到 `sa-token`**

```java
StpLogic stpLogic = stpLogicResolver.resolve(loginType);
stpLogic.login(user.getId());
SaSession session = stpLogic.getSessionByLoginId(user.getId(), false);
session.set("username", user.getUsername());
```

- [ ] **Step 3: 登录时继续保留入口角色边界**

```java
loginForAdminPortal -> ADMIN / KB_ADMIN / AUDITOR
loginForAppPortal -> APP_USER / ADMIN / KB_ADMIN / AUDITOR
```

- [ ] **Step 4: `/api/admin/auth/me` 返回 `roles + permissions`，`/api/app/auth/me` 至少返回角色**

Run: `mvn -q -pl rag-admin-server -Dtest=AuthServiceTest,AdminApiWebMvcTest test`
Expected: 登录、刷新、登出、当前用户查询全部通过，后台 `me` 响应包含 `permissions`

- [ ] **Step 5: 提交**

```bash
git add rag-admin-server/src/main/java/com/ragadmin/server/auth rag-admin-server/src/main/java/com/ragadmin/server/app/controller/AppAuthController.java rag-admin-server/src/test/java/com/ragadmin/server/auth/service/AuthServiceTest.java rag-admin-server/src/test/java/com/ragadmin/server/web/AdminApiWebMvcTest.java
git commit -m "feat: 切换前后台认证到 sa-token"
```

## Chunk 2: 后端权限矩阵与旧链路退场

### Task 3: 落地后台静态权限矩阵与 `sa-token` 注解鉴权

**Files:**
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/auth/model/AdminPermissionCode.java`
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/auth/service/AdminPermissionService.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/auth/controller/UserController.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/chat/controller/ChatController.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/knowledge/controller/KnowledgeBaseController.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/document/controller/DocumentController.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/document/controller/FileController.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/model/controller/ModelController.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/model/controller/ModelProviderController.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/task/controller/TaskController.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/task/controller/TaskEventController.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/audit/controller/AuditLogController.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/statistics/controller/StatisticsController.java`
- Test: `rag-admin-server/src/test/java/com/ragadmin/server/web/AdminApiWebMvcTest.java`

- [ ] **Step 1: 定义后台权限码枚举和角色映射**

```java
public enum AdminPermissionCode {
    DASHBOARD_VIEW,
    CHAT_CONSOLE_ACCESS,
    KB_MANAGE,
    MODEL_MANAGE,
    TASK_VIEW,
    TASK_OPERATE,
    AUDIT_VIEW,
    STATISTICS_VIEW,
    USER_MANAGE
}
```

- [ ] **Step 2: 在 `AdminPermissionService` 中实现静态权限矩阵**

```java
ADMIN -> all
KB_ADMIN -> DASHBOARD_VIEW, CHAT_CONSOLE_ACCESS, KB_MANAGE, MODEL_MANAGE, TASK_VIEW, TASK_OPERATE, STATISTICS_VIEW
AUDITOR -> DASHBOARD_VIEW, TASK_VIEW, AUDIT_VIEW, STATISTICS_VIEW
```

- [ ] **Step 3: 将后台 Controller 改为 `@SaCheckLogin(type = "admin")` 与 `@SaCheckPermission(...)`**

```java
@SaCheckLogin(type = "admin")
@SaCheckPermission("KB_MANAGE")
@PostMapping("/knowledge-bases/{kbId}/documents")
```

- [ ] **Step 4: 用 `AdminApiWebMvcTest` 补充“有权限通过、无权限拒绝”的接口测试**

Run: `mvn -q -pl rag-admin-server -Dtest=AdminApiWebMvcTest test`
Expected: 后台受保护接口按权限码返回 200/403，测试不再依赖 `assertAnyRole(...)`

- [ ] **Step 5: 提交**

```bash
git add rag-admin-server/src/main/java/com/ragadmin/server/auth/model/AdminPermissionCode.java rag-admin-server/src/main/java/com/ragadmin/server/auth/service/AdminPermissionService.java rag-admin-server/src/main/java/com/ragadmin/server/auth/controller/UserController.java rag-admin-server/src/main/java/com/ragadmin/server/chat/controller/ChatController.java rag-admin-server/src/main/java/com/ragadmin/server/knowledge/controller/KnowledgeBaseController.java rag-admin-server/src/main/java/com/ragadmin/server/document/controller/DocumentController.java rag-admin-server/src/main/java/com/ragadmin/server/document/controller/FileController.java rag-admin-server/src/main/java/com/ragadmin/server/model/controller/ModelController.java rag-admin-server/src/main/java/com/ragadmin/server/model/controller/ModelProviderController.java rag-admin-server/src/main/java/com/ragadmin/server/task/controller/TaskController.java rag-admin-server/src/main/java/com/ragadmin/server/task/controller/TaskEventController.java rag-admin-server/src/main/java/com/ragadmin/server/audit/controller/AuditLogController.java rag-admin-server/src/main/java/com/ragadmin/server/statistics/controller/StatisticsController.java rag-admin-server/src/test/java/com/ragadmin/server/web/AdminApiWebMvcTest.java
git commit -m "feat: 落地后台权限码注解鉴权"
```

### Task 4: 退场旧的自定义 Token 与拦截器链路

**Files:**
- Modify: `rag-admin-server/pom.xml`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/auth/config/AuthWebMvcConfiguration.java`
- Delete: `rag-admin-server/src/main/java/com/ragadmin/server/auth/service/AuthInterceptor.java`
- Delete: `rag-admin-server/src/main/java/com/ragadmin/server/auth/service/TokenService.java`
- Delete: `rag-admin-server/src/main/java/com/ragadmin/server/auth/model/AuthClaims.java`
- Delete: `rag-admin-server/src/main/java/com/ragadmin/server/auth/model/AuthTokenType.java`
- Modify: `rag-admin-server/src/test/java/com/ragadmin/server/web/AdminApiWebMvcTest.java`

- [ ] **Step 1: 删除 `jjwt-*` 依赖与旧拦截器配置**
- [ ] **Step 2: 清理依赖 `request.setAttribute(...)` 的旧鉴权读取方式**
- [ ] **Step 3: 让控制器统一从 `sa-token` 获取当前登录人**

```java
Long userId = StpUtil.getLoginIdAsLong();
```

- [ ] **Step 4: 运行回归测试，确认旧类删除后编译和鉴权测试通过**

Run: `mvn -q -pl rag-admin-server test`
Expected: 后端测试通过，不再引用 `AuthInterceptor`、`TokenService`、`AuthClaims`

- [ ] **Step 5: 提交**

```bash
git add rag-admin-server/pom.xml rag-admin-server/src/main/java/com/ragadmin/server/auth/config/AuthWebMvcConfiguration.java rag-admin-server/src/test/java/com/ragadmin/server/web/AdminApiWebMvcTest.java
git rm rag-admin-server/src/main/java/com/ragadmin/server/auth/service/AuthInterceptor.java rag-admin-server/src/main/java/com/ragadmin/server/auth/service/TokenService.java rag-admin-server/src/main/java/com/ragadmin/server/auth/model/AuthClaims.java rag-admin-server/src/main/java/com/ragadmin/server/auth/model/AuthTokenType.java
git commit -m "refactor: 退场旧鉴权链路"
```

## Chunk 3: 在线会话治理与审计

### Task 5: 增加按 `userId` 的在线会话查询与强制下线

**Files:**
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/auth/controller/UserSessionController.java`
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/auth/service/UserSessionAdminService.java`
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/auth/dto/UserSessionListItemResponse.java`
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/auth/dto/UserSessionDetailResponse.java`
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/auth/dto/KickoutUserSessionRequest.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/audit/service/AuditLogService.java`
- Modify: `rag-admin-server/src/test/java/com/ragadmin/server/web/AdminApiWebMvcTest.java`

- [ ] **Step 1: 定义在线会话返回结构，保持用户维度，不暴露设备细节**

```java
private Long userId;
private String username;
private List<String> roles;
private Boolean adminOnline;
private Boolean appOnline;
private LocalDateTime lastLoginAt;
private LocalDateTime lastActiveAt;
```

- [ ] **Step 2: 在 `UserSessionAdminService` 中封装基于 `sa-token` 的在线查询与踢下线逻辑**

```java
kickout(userId, "admin");
kickout(userId, "app");
kickout(userId, "all");
```

- [ ] **Step 3: 新增后台接口并挂 `@SaCheckPermission("USER_MANAGE")`**

```java
GET /api/admin/user-sessions
GET /api/admin/user-sessions/{userId}
POST /api/admin/user-sessions/{userId}/kickout
```

- [ ] **Step 4: 强制下线后补充审计日志写入与 WebMvc 测试**

Run: `mvn -q -pl rag-admin-server -Dtest=AdminApiWebMvcTest test`
Expected: 用户会话接口受 `USER_MANAGE` 保护，强制下线接口可按 `scope=admin|app|all` 正常执行

- [ ] **Step 5: 提交**

```bash
git add rag-admin-server/src/main/java/com/ragadmin/server/auth/controller/UserSessionController.java rag-admin-server/src/main/java/com/ragadmin/server/auth/service/UserSessionAdminService.java rag-admin-server/src/main/java/com/ragadmin/server/auth/dto/UserSessionListItemResponse.java rag-admin-server/src/main/java/com/ragadmin/server/auth/dto/UserSessionDetailResponse.java rag-admin-server/src/main/java/com/ragadmin/server/auth/dto/KickoutUserSessionRequest.java rag-admin-server/src/main/java/com/ragadmin/server/audit/service/AuditLogService.java rag-admin-server/src/test/java/com/ragadmin/server/web/AdminApiWebMvcTest.java
git commit -m "feat: 支持在线会话治理与踢人下线"
```

## Chunk 4: 后台前端权限适配

### Task 6: 让后台前端按 `permissions` 收口菜单、路由和高风险按钮

**Files:**
- Modify: `rag-admin-web/src/types/auth.ts`
- Modify: `rag-admin-web/src/api/auth.ts`
- Modify: `rag-admin-web/src/stores/auth.ts`
- Modify: `rag-admin-web/src/router/index.ts`
- Modify: `rag-admin-web/src/layouts/AdminLayout.vue`
- Modify: `rag-admin-web/src/views/user/UserManagementView.vue`
- Modify: `rag-admin-web/src/api/http.ts`
- Create: `rag-admin-web/src/utils/permission.ts`
- Test: `rag-admin-web` build

- [ ] **Step 1: 在前端 `CurrentUser` 类型中增加 `permissions`**

```ts
export interface CurrentUser {
  id: number
  username: string
  displayName: string
  roles: string[]
  permissions: string[]
}
```

- [ ] **Step 2: 抽出统一权限判断工具**

```ts
export function hasPermission(user: CurrentUser | null, code: string): boolean {
  return user?.permissions?.includes(code) ?? false
}
```

- [ ] **Step 3: 路由改成 `meta.requiredPermissions`，菜单改成按 `permissions` 渲染**

```ts
meta: { requiredPermissions: ['USER_MANAGE'] }
```

- [ ] **Step 4: 在高风险页面按钮上补权限控制，并在 401/踢下线时统一清理登录态**

Run: `npm --prefix rag-admin-web run build`
Expected: 管理台成功构建；无权限页面不显示菜单且无法通过路由直接进入；401 时自动清理登录态并返回登录页

- [ ] **Step 5: 提交**

```bash
git add rag-admin-web/src/types/auth.ts rag-admin-web/src/api/auth.ts rag-admin-web/src/stores/auth.ts rag-admin-web/src/router/index.ts rag-admin-web/src/layouts/AdminLayout.vue rag-admin-web/src/views/user/UserManagementView.vue rag-admin-web/src/api/http.ts rag-admin-web/src/utils/permission.ts
git commit -m "feat: 后台前端按权限码收口访问边界"
```

## Chunk 5: 联调回归与交付

### Task 7: 完成联调回归、文档校验与最终收口

**Files:**
- Verify: `docs/plans/2026-03-19-admin-permission-boundary-design.md`
- Verify: `docs/rag-admin-architecture.md`
- Verify: `docs/rag-admin-api-design.md`
- Verify: `AGENTS.md`
- Test: `rag-admin-server`
- Test: `rag-admin-web`

- [ ] **Step 1: 运行后端全量测试**

Run: `mvn -q -pl rag-admin-server test`
Expected: PASS

- [ ] **Step 2: 运行后台前端构建**

Run: `npm --prefix rag-admin-web run build`
Expected: PASS

- [ ] **Step 3: 人工校对文档与实现是否一致**

检查项：

- 后端认证授权描述均为 `sa-token + Redis`
- 在线会话治理均为按 `userId`
- 聊天历史持久化仍明确为 PostgreSQL
- 后台 `/api/admin/auth/me` 已明确返回 `permissions`

- [ ] **Step 4: 汇总变更并完成最终提交**

```bash
git add AGENTS.md docs/rag-admin-architecture.md docs/rag-admin-api-design.md
git commit -m "docs: 同步权限实现与设计文档"
```

- [ ] **Step 5: 推送远端**

```bash
git push github master
```
