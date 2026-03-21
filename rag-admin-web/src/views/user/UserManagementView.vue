<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  assignUserRoles,
  createUser,
  getUserSessionDetail,
  kickoutUserSession,
  listUserSessions,
  listUsers,
  updateUser,
} from '@/api/user'
import { resolveErrorMessage } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import type {
  CreateUserRequest,
  KickoutUserSessionRequest,
  UpdateUserRequest,
  UserListItem,
  UserSessionDetail,
  UserSessionScope,
} from '@/types/user'

interface KickoutTarget {
  id: number
  username: string
  displayName: string
  roles: string[]
}

const router = useRouter()
const authStore = useAuthStore()

const ROLE_OPTIONS = [
  { value: 'APP_USER', label: '问答前台用户', description: '可登录聊天前台，适合普通组织成员。' },
  { value: 'ADMIN', label: '系统管理员', description: '可登录后台，并拥有用户管理能力。' },
  { value: 'KB_ADMIN', label: '知识库管理员', description: '可登录后台，治理知识库与文档。' },
  { value: 'AUDITOR', label: '审计用户', description: '可登录后台，查看审计与运行轨迹。' },
]

const STATUS_OPTIONS = [
  { label: '全部状态', value: '' },
  { label: '启用', value: 'ENABLED' },
  { label: '禁用', value: 'DISABLED' },
]

const ENABLED_STATUS_OPTIONS = STATUS_OPTIONS.filter((item) => item.value)

const loading = ref(false)
const loadError = ref('')
const rows = ref<UserListItem[]>([])
const submitting = ref(false)
const roleSubmitting = ref(false)
const userDialogVisible = ref(false)
const roleDialogVisible = ref(false)
const userDialogMode = ref<'create' | 'edit'>('create')
const editingUserId = ref<number | null>(null)
const currentRoleTarget = ref<UserListItem | null>(null)

const pagination = reactive({
  pageNo: 1,
  pageSize: 10,
  total: 0,
})

const query = reactive({
  keyword: '',
  status: '',
})

const userForm = reactive({
  username: '',
  password: '',
  displayName: '',
  email: '',
  mobile: '',
  status: 'ENABLED',
  roleCodes: [] as string[],
})

const roleForm = reactive({
  roleCodes: [] as string[],
})

const hasData = computed(() => rows.value.length > 0)
const dialogTitle = computed(() => (userDialogMode.value === 'create' ? '新增用户' : '编辑用户'))
const dialogConfirmText = computed(() => (userDialogMode.value === 'create' ? '确认创建' : '确认保存'))
const sessionSummaryLoading = ref(false)
const sessionDetailLoading = ref(false)
const sessionDetailError = ref('')
const sessionDrawerVisible = ref(false)
const sessionDetail = ref<UserSessionDetail | null>(null)
const kickoutDialogVisible = ref(false)
const kickoutSubmitting = ref(false)
const kickoutTarget = ref<KickoutTarget | null>(null)
const kickoutForm = reactive<KickoutUserSessionRequest>({
  scope: 'all',
  reason: '管理员手动下线',
})
const sessionSummary = reactive({
  allOnline: 0,
  adminOnline: 0,
  appOnline: 0,
})
const currentPageSummary = computed(() => {
  return rows.value.reduce(
    (result, item) => {
      result.total += 1
      if (item.status === 'ENABLED') {
        result.enabled += 1
      }
      return result
    },
    {
      total: 0,
      enabled: 0,
    },
  )
})
const tableResultSummary = computed(() => {
  if (pagination.total === 0 || rows.value.length === 0) {
    return '当前暂无匹配用户，支持按账号、状态快速筛选。'
  }
  const start = (pagination.pageNo - 1) * pagination.pageSize + 1
  const end = start + rows.value.length - 1
  return `当前展示第 ${start}-${end} 条，共 ${pagination.total} 条用户记录。`
})
const sessionDrawerTitle = computed(() => {
  if (!sessionDetail.value) {
    return '在线会话详情'
  }
  return `${sessionDetail.value.displayName || sessionDetail.value.username} 的在线会话`
})
const kickoutTargetName = computed(() => kickoutTarget.value?.displayName || kickoutTarget.value?.username || '')
const currentSessionKickoutDisabledReason = computed(() => {
  if (!sessionDetail.value) {
    return ''
  }
  return resolveKickoutDisabledReason(sessionDetail.value.userId, sessionDetail.value.roles)
})
const sessionOnlineDisabled = computed(() => {
  return !sessionDetail.value || (!sessionDetail.value.adminOnline && !sessionDetail.value.appOnline)
})

function statusTagType(status: string): 'success' | 'info' {
  return status === 'ENABLED' ? 'success' : 'info'
}

function sessionTagType(online: boolean): 'success' | 'info' {
  return online ? 'success' : 'info'
}

function roleTagType(roleCode: string): 'primary' | 'success' | 'warning' | 'info' {
  if (roleCode === 'ADMIN') {
    return 'warning'
  }
  if (roleCode === 'APP_USER') {
    return 'primary'
  }
  if (roleCode === 'KB_ADMIN') {
    return 'success'
  }
  return 'info'
}

function roleLabel(roleCode: string): string {
  return ROLE_OPTIONS.find((item) => item.value === roleCode)?.label ?? roleCode
}

function accessScopes(roles: string[]): string[] {
  const scopes: string[] = []
  if (roles.includes('APP_USER') || roles.some((role) => ['ADMIN', 'KB_ADMIN', 'AUDITOR'].includes(role))) {
    scopes.push('聊天前台')
  }
  if (roles.some((role) => ['ADMIN', 'KB_ADMIN', 'AUDITOR'].includes(role))) {
    scopes.push('后台管理')
  }
  return scopes
}

function resolveKickoutDisabledReason(userId: number, roles: string[]): string {
  if (userId === authStore.currentUser?.id) {
    return '当前登录管理员不能强制下线自己'
  }
  if (roles.includes('ADMIN')) {
    return '系统管理员不允许被强制下线'
  }
  return ''
}

function normalizeOptionalValue(value: string): string | null {
  const normalized = value.trim()
  return normalized ? normalized : null
}

function formatDateTime(value?: string | null): string {
  if (!value) {
    return '暂无记录'
  }
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) {
    return value
  }
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(parsed)
}

function resetUserForm(): void {
  editingUserId.value = null
  userDialogMode.value = 'create'
  userForm.username = ''
  userForm.password = ''
  userForm.displayName = ''
  userForm.email = ''
  userForm.mobile = ''
  userForm.status = 'ENABLED'
  userForm.roleCodes = ['APP_USER']
}

function buildCreatePayload(): CreateUserRequest {
  return {
    username: userForm.username.trim(),
    password: userForm.password,
    displayName: userForm.displayName.trim(),
    email: normalizeOptionalValue(userForm.email),
    mobile: normalizeOptionalValue(userForm.mobile),
    status: userForm.status,
    roleCodes: [...userForm.roleCodes],
  }
}

function buildUpdatePayload(): UpdateUserRequest {
  return {
    displayName: userForm.displayName.trim(),
    email: normalizeOptionalValue(userForm.email),
    mobile: normalizeOptionalValue(userForm.mobile),
    status: userForm.status,
    password: userForm.password.trim() ? userForm.password : null,
  }
}

function openCreateDialog(): void {
  resetUserForm()
  userDialogVisible.value = true
}

function openEditDialog(user: UserListItem): void {
  userDialogMode.value = 'edit'
  editingUserId.value = user.id
  userForm.username = user.username
  userForm.password = ''
  userForm.displayName = user.displayName
  userForm.email = user.email ?? ''
  userForm.mobile = user.mobile ?? ''
  userForm.status = user.status
  userForm.roleCodes = [...user.roles]
  userDialogVisible.value = true
}

function openRoleDialog(user: UserListItem): void {
  currentRoleTarget.value = user
  roleForm.roleCodes = [...user.roles]
  roleDialogVisible.value = true
}

async function loadData(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const response = await listUsers({
      pageNo: pagination.pageNo,
      pageSize: pagination.pageSize,
      keyword: query.keyword.trim() || undefined,
      status: query.status || undefined,
    })
    rows.value = response.list
    pagination.total = response.total
  } catch (error) {
    rows.value = []
    pagination.total = 0
    loadError.value = resolveErrorMessage(error)
  } finally {
    loading.value = false
  }
}

async function loadSessionSummary(): Promise<void> {
  sessionSummaryLoading.value = true
  try {
    const [all, admin, app] = await Promise.all([
      listUserSessions({ pageNo: 1, pageSize: 1, onlineScope: 'all' }),
      listUserSessions({ pageNo: 1, pageSize: 1, onlineScope: 'admin' }),
      listUserSessions({ pageNo: 1, pageSize: 1, onlineScope: 'app' }),
    ])
    sessionSummary.allOnline = all.total
    sessionSummary.adminOnline = admin.total
    sessionSummary.appOnline = app.total
  } catch {
    sessionSummary.allOnline = 0
    sessionSummary.adminOnline = 0
    sessionSummary.appOnline = 0
  } finally {
    sessionSummaryLoading.value = false
  }
}

async function loadSessionDetail(userId: number): Promise<void> {
  sessionDetailLoading.value = true
  sessionDetailError.value = ''
  try {
    sessionDetail.value = await getUserSessionDetail(userId)
  } catch (error) {
    sessionDetail.value = null
    sessionDetailError.value = resolveErrorMessage(error)
  } finally {
    sessionDetailLoading.value = false
  }
}

async function handleSearch(): Promise<void> {
  pagination.pageNo = 1
  await loadData()
}

async function handleReset(): Promise<void> {
  query.keyword = ''
  query.status = ''
  pagination.pageNo = 1
  await loadData()
}

async function handleRefresh(): Promise<void> {
  await Promise.all([loadData(), loadSessionSummary()])
}

async function handleCurrentChange(pageNo: number): Promise<void> {
  pagination.pageNo = pageNo
  await loadData()
}

async function handleSizeChange(pageSize: number): Promise<void> {
  pagination.pageSize = pageSize
  pagination.pageNo = 1
  await loadData()
}

async function handleSaveUser(): Promise<void> {
  if (!userForm.displayName.trim()) {
    ElMessage.warning('请输入用户名称')
    return
  }
  if (userDialogMode.value === 'create' && (!userForm.username.trim() || !userForm.password.trim())) {
    ElMessage.warning('新增用户时必须填写账号和密码')
    return
  }

  submitting.value = true
  try {
    if (userDialogMode.value === 'create') {
      await createUser(buildCreatePayload())
      ElMessage.success('用户创建成功')
    } else if (editingUserId.value) {
      await updateUser(editingUserId.value, buildUpdatePayload())
      ElMessage.success('用户资料已更新')
    }
    userDialogVisible.value = false
    resetUserForm()
    await loadData()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error))
  } finally {
    submitting.value = false
  }
}

async function handleSaveRoles(): Promise<void> {
  if (!currentRoleTarget.value) {
    return
  }
  if (roleForm.roleCodes.length === 0) {
    ElMessage.warning('请至少选择一个角色')
    return
  }

  roleSubmitting.value = true
  try {
    await assignUserRoles(currentRoleTarget.value.id, {
      roleCodes: [...roleForm.roleCodes],
    })
    roleDialogVisible.value = false
    currentRoleTarget.value = null
    ElMessage.success('用户角色已更新')
    await loadData()
    if (sessionDrawerVisible.value && sessionDetail.value) {
      await loadSessionDetail(sessionDetail.value.userId)
    }
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error))
  } finally {
    roleSubmitting.value = false
  }
}

async function openSessionDrawer(user: UserListItem): Promise<void> {
  sessionDrawerVisible.value = true
  await loadSessionDetail(user.id)
}

function resetSessionDrawer(): void {
  sessionDetail.value = null
  sessionDetailError.value = ''
  sessionDetailLoading.value = false
}

function openKickoutDialog(target: KickoutTarget, scope: UserSessionScope = 'all'): void {
  const disabledReason = resolveKickoutDisabledReason(target.id, target.roles)
  if (disabledReason) {
    ElMessage.warning(disabledReason)
    return
  }
  kickoutTarget.value = target
  kickoutForm.scope = scope
  kickoutForm.reason = '管理员手动下线'
  kickoutDialogVisible.value = true
}

function openKickoutDialogForCurrentSession(scope: UserSessionScope = 'all'): void {
  if (!sessionDetail.value) {
    return
  }
  openKickoutDialog(
    {
      id: sessionDetail.value.userId,
      username: sessionDetail.value.username,
      displayName: sessionDetail.value.displayName,
      roles: [...sessionDetail.value.roles],
    },
    scope,
  )
}

function resetKickoutDialog(): void {
  kickoutTarget.value = null
  kickoutForm.scope = 'all'
  kickoutForm.reason = '管理员手动下线'
}

async function handleKickout(): Promise<void> {
  if (!kickoutTarget.value) {
    return
  }
  const disabledReason = resolveKickoutDisabledReason(kickoutTarget.value.id, kickoutTarget.value.roles)
  if (disabledReason) {
    ElMessage.warning(disabledReason)
    return
  }
  const reason = kickoutForm.reason.trim()
  if (!reason) {
    ElMessage.warning('请填写强制下线原因')
    return
  }

  kickoutSubmitting.value = true
  try {
    await kickoutUserSession(kickoutTarget.value.id, {
      scope: kickoutForm.scope,
      reason,
    })
    kickoutDialogVisible.value = false
    ElMessage.success('强制下线已执行')

    const isSelfAdminKickout = kickoutTarget.value.id === authStore.currentUser?.id
      && ['admin', 'all'].includes(kickoutForm.scope)

    await loadSessionSummary()
    if (sessionDrawerVisible.value && sessionDetail.value?.userId === kickoutTarget.value.id) {
      await loadSessionDetail(kickoutTarget.value.id)
    }

    if (isSelfAdminKickout) {
      authStore.clearSession()
      await router.replace('/login')
      return
    }
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error))
  } finally {
    kickoutSubmitting.value = false
  }
}

onMounted(async () => {
  resetUserForm()
  await Promise.all([loadData(), loadSessionSummary()])
})
</script>

<template>
  <section class="user-page">
    <header class="user-head soft-panel">
      <div>
        <p class="user-eyebrow">治理</p>
        <h1 class="page-title">用户管理</h1>
        <p class="page-subtitle">
          后台统一维护组织用户。`APP_USER` 用于聊天前台白名单，`ADMIN`、`KB_ADMIN`、`AUDITOR` 用于后台治理入口。
        </p>
      </div>
      <div class="head-actions">
        <el-button @click="handleRefresh">刷新列表</el-button>
        <el-button type="primary" @click="openCreateDialog">新增用户</el-button>
      </div>
    </header>

    <section class="summary-grid">
      <article class="summary-card summary-card-overview soft-panel">
        <span>分页概览</span>
        <div class="summary-overview-grid">
          <div class="summary-overview-item">
            <strong>{{ pagination.total }}</strong>
            <small>检索结果</small>
          </div>
          <div class="summary-overview-item">
            <strong>{{ currentPageSummary.total }}</strong>
            <small>当前页用户</small>
          </div>
          <div class="summary-overview-item">
            <strong>{{ currentPageSummary.enabled }}</strong>
            <small>当前页启用</small>
          </div>
        </div>
      </article>
      <article class="summary-card soft-panel" v-loading="sessionSummaryLoading">
        <span>前台在线</span>
        <strong>{{ sessionSummary.appOnline }}</strong>
        <p>问答前台按 userId 去重后的在线人数</p>
      </article>
      <article class="summary-card soft-panel is-warm" v-loading="sessionSummaryLoading">
        <span>后台在线</span>
        <strong>{{ sessionSummary.adminOnline }}</strong>
        <p>后台治理域按 userId 去重后的在线人数</p>
      </article>
    </section>

    <section class="table-panel soft-panel">
      <header class="table-panel-head">
        <div class="table-panel-copy">
          <p class="user-eyebrow">列表重点</p>
          <h2 class="table-title">用户分页列表</h2>
          <p class="table-subtitle">{{ tableResultSummary }}</p>
        </div>
        <div class="table-role-hints">
          <div v-for="role in ROLE_OPTIONS" :key="role.value" class="role-hint-chip">
            <el-tag :type="roleTagType(role.value)" effect="plain">{{ role.value }}</el-tag>
            <small>{{ role.label }}</small>
          </div>
        </div>
      </header>

      <div class="filter-grid">
        <el-input
          v-model="query.keyword"
          placeholder="搜索账号、用户名、手机号"
          clearable
          @keyup.enter="handleSearch"
        />
        <el-select v-model="query.status" placeholder="账号状态">
          <el-option
            v-for="item in STATUS_OPTIONS"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </div>
      <div class="filter-actions">
        <el-button @click="handleReset">重置</el-button>
        <el-button @click="handleRefresh">刷新</el-button>
        <el-button type="primary" @click="handleSearch">查询用户</el-button>
      </div>

      <section v-if="loadError" class="table-error">
        <p class="error-text">{{ loadError }}</p>
      </section>

      <el-table v-else :data="rows" v-loading="loading" empty-text="当前暂无用户数据" stripe>
        <el-table-column prop="username" label="登录账号" min-width="160" />
        <el-table-column prop="displayName" label="用户名称" min-width="160" />
        <el-table-column label="联系方式" min-width="220">
          <template #default="{ row }">
            <div class="contact-cell">
              <span>{{ row.mobile || '未配置手机号' }}</span>
              <small>{{ row.email || '未配置邮箱' }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="角色" min-width="260">
          <template #default="{ row }">
            <div class="tag-list">
              <el-tag
                v-for="roleCode in row.roles"
                :key="`${row.id}-${roleCode}`"
                :type="roleTagType(roleCode)"
                effect="plain"
              >
                {{ roleLabel(roleCode) }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="可进入系统" min-width="180">
          <template #default="{ row }">
            <div class="tag-list">
              <el-tag
                v-for="scope in accessScopes(row.roles)"
                :key="`${row.id}-${scope}`"
                effect="plain"
              >
                {{ scope }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <div class="action-links">
              <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
              <el-button link type="primary" @click="openRoleDialog(row)">配置角色</el-button>
              <el-button link type="primary" @click="openSessionDrawer(row)">会话详情</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="hasData || pagination.total > 0" class="table-footer">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :current-page="pagination.pageNo"
          :page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="pagination.total"
          @current-change="handleCurrentChange"
          @size-change="handleSizeChange"
        />
      </div>
    </section>

    <el-dialog v-model="userDialogVisible" :title="dialogTitle" width="620px" @closed="resetUserForm">
      <el-form label-position="top">
        <div v-if="userDialogMode === 'create'" class="form-grid">
          <el-form-item label="登录账号">
            <el-input v-model="userForm.username" placeholder="请输入唯一账号" />
          </el-form-item>
          <el-form-item label="初始密码">
            <el-input v-model="userForm.password" type="password" show-password placeholder="请输入初始密码" />
          </el-form-item>
        </div>

        <div v-else class="reset-tip">
          当前编辑账号：<strong>{{ userForm.username }}</strong>
          <span>如需重置密码，直接在下方填写新密码即可。</span>
        </div>

        <div class="form-grid">
          <el-form-item label="用户名称">
            <el-input v-model="userForm.displayName" placeholder="请输入用户名称" />
          </el-form-item>
          <el-form-item label="账号状态">
            <el-select v-model="userForm.status">
              <el-option
                v-for="item in ENABLED_STATUS_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </div>

        <div class="form-grid">
          <el-form-item label="手机号">
            <el-input v-model="userForm.mobile" placeholder="可为空" />
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input v-model="userForm.email" placeholder="可为空" />
          </el-form-item>
        </div>

        <el-form-item v-if="userDialogMode === 'edit'" label="新密码">
          <el-input
            v-model="userForm.password"
            type="password"
            show-password
            placeholder="留空表示不修改密码"
          />
        </el-form-item>

        <el-form-item v-if="userDialogMode === 'create'" label="初始角色">
          <el-checkbox-group v-model="userForm.roleCodes">
            <el-checkbox v-for="role in ROLE_OPTIONS" :key="role.value" :value="role.value">
              {{ role.label }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="userDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSaveUser">
            {{ dialogConfirmText }}
          </el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="roleDialogVisible" title="配置用户角色" width="560px">
      <div class="role-dialog-head">
        <strong>{{ currentRoleTarget?.displayName || currentRoleTarget?.username }}</strong>
        <span>角色变化会直接影响前后台登录入口。</span>
      </div>
      <el-checkbox-group v-model="roleForm.roleCodes" class="role-checkbox-list">
        <label v-for="role in ROLE_OPTIONS" :key="role.value" class="role-checkbox-item">
          <el-checkbox :value="role.value">
            {{ role.label }}
          </el-checkbox>
          <p>{{ role.description }}</p>
        </label>
      </el-checkbox-group>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="roleDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="roleSubmitting" @click="handleSaveRoles">
            保存角色
          </el-button>
        </div>
      </template>
    </el-dialog>

    <el-drawer
      v-model="sessionDrawerVisible"
      :title="sessionDrawerTitle"
      size="560px"
      @closed="resetSessionDrawer"
    >
      <div class="session-drawer">
        <section class="session-summary-card soft-panel" v-loading="sessionDetailLoading">
          <template v-if="sessionDetail">
            <div class="session-summary-head">
              <div>
                <p class="user-eyebrow">会话治理</p>
                <strong class="session-user-name">
                  {{ sessionDetail.displayName || sessionDetail.username }}
                </strong>
              </div>
              <el-tag :type="statusTagType(sessionDetail.status)" effect="plain">
                {{ sessionDetail.status }}
              </el-tag>
            </div>
            <div class="tag-list">
              <el-tag
                v-for="roleCode in sessionDetail.roles"
                :key="`detail-${sessionDetail.userId}-${roleCode}`"
                :type="roleTagType(roleCode)"
                effect="plain"
              >
                {{ roleLabel(roleCode) }}
              </el-tag>
            </div>
            <p class="session-summary-text">
              当前平台在线用户：<strong>{{ sessionSummary.allOnline }}</strong>
              <span>当前查看对象支持前后台分域下线。</span>
            </p>
            <p v-if="currentSessionKickoutDisabledReason" class="session-guard-text">
              {{ currentSessionKickoutDisabledReason }}
            </p>
          </template>
          <p v-else-if="sessionDetailError" class="error-text">{{ sessionDetailError }}</p>
        </section>

        <section v-if="sessionDetail" class="session-domain-grid">
          <article class="session-domain-card soft-panel">
            <div class="session-domain-head">
              <div>
                <span class="session-domain-label">后台管理域</span>
                <strong>admin</strong>
              </div>
              <el-tag :type="sessionTagType(sessionDetail.adminOnline)">
                {{ sessionDetail.adminOnline ? '在线' : '离线' }}
              </el-tag>
            </div>
            <dl class="session-metadata">
              <div>
                <dt>最近登录</dt>
                <dd>{{ formatDateTime(sessionDetail.adminLastLoginAt) }}</dd>
              </div>
              <div>
                <dt>最近活跃</dt>
                <dd>{{ formatDateTime(sessionDetail.adminLastActiveAt) }}</dd>
              </div>
            </dl>
            <div class="session-domain-actions">
              <el-button
                type="warning"
                plain
                :disabled="!sessionDetail.adminOnline || Boolean(currentSessionKickoutDisabledReason)"
                @click="openKickoutDialogForCurrentSession('admin')"
              >
                强制下线
              </el-button>
            </div>
          </article>

          <article class="session-domain-card soft-panel">
            <div class="session-domain-head">
              <div>
                <span class="session-domain-label">问答前台域</span>
                <strong>app</strong>
              </div>
              <el-tag :type="sessionTagType(sessionDetail.appOnline)">
                {{ sessionDetail.appOnline ? '在线' : '离线' }}
              </el-tag>
            </div>
            <dl class="session-metadata">
              <div>
                <dt>最近登录</dt>
                <dd>{{ formatDateTime(sessionDetail.appLastLoginAt) }}</dd>
              </div>
              <div>
                <dt>最近活跃</dt>
                <dd>{{ formatDateTime(sessionDetail.appLastActiveAt) }}</dd>
              </div>
            </dl>
            <div class="session-domain-actions">
              <el-button
                type="warning"
                plain
                :disabled="!sessionDetail.appOnline || Boolean(currentSessionKickoutDisabledReason)"
                @click="openKickoutDialogForCurrentSession('app')"
              >
                强制下线
              </el-button>
            </div>
          </article>
        </section>

        <div v-if="sessionDetail" class="session-drawer-footer">
          <el-button
            type="danger"
            :disabled="sessionOnlineDisabled || Boolean(currentSessionKickoutDisabledReason)"
            @click="openKickoutDialogForCurrentSession('all')"
          >
            全部下线
          </el-button>
        </div>
      </div>
    </el-drawer>

    <el-dialog
      v-model="kickoutDialogVisible"
      title="强制用户下线"
      width="560px"
      @closed="resetKickoutDialog"
    >
      <div class="kickout-dialog-head">
        <strong>{{ kickoutTargetName }}</strong>
        <span>按 userId 执行分域下线，`admin` 与 `app` 会分别影响后台和聊天前台。</span>
      </div>

      <el-form label-position="top">
        <el-form-item label="下线范围">
          <el-radio-group v-model="kickoutForm.scope">
            <el-radio value="admin">仅后台</el-radio>
            <el-radio value="app">仅前台</el-radio>
            <el-radio value="all">全部域</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="下线原因">
          <el-input
            v-model="kickoutForm.reason"
            type="textarea"
            :rows="4"
            maxlength="200"
            show-word-limit
            placeholder="请输入管理员执行下线的原因"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="kickoutDialogVisible = false">取消</el-button>
          <el-button type="danger" :loading="kickoutSubmitting" @click="handleKickout">
            确认下线
          </el-button>
        </div>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.user-page {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.user-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 28px 32px;
  background:
    radial-gradient(circle at right top, rgba(198, 107, 34, 0.12), transparent 32%),
    linear-gradient(180deg, rgba(255, 251, 246, 0.96), rgba(255, 248, 241, 0.9));
}

.user-eyebrow,
.summary-card span {
  margin: 0 0 10px;
  color: #9d7a58;
  font-size: 12px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.head-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.summary-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.summary-card {
  padding: 18px 20px;
}

.summary-card strong {
  display: block;
  margin-top: 10px;
  color: #2f241d;
  font-family: "Noto Serif SC", serif;
  font-size: 28px;
}

.summary-card p {
  margin: 10px 0 0;
  color: #6d5948;
  line-height: 1.65;
}

.summary-card-overview {
  background:
    linear-gradient(180deg, rgba(255, 251, 246, 0.98), rgba(255, 247, 239, 0.94)),
    linear-gradient(120deg, rgba(198, 107, 34, 0.06), transparent 60%);
}

.summary-overview-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-top: 10px;
}

.summary-overview-item {
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(255, 252, 247, 0.78);
  border: 1px solid rgba(110, 84, 54, 0.08);
}

.summary-overview-item strong {
  margin-top: 0;
  font-size: 24px;
}

.summary-overview-item small {
  display: block;
  margin-top: 6px;
  color: #8f7159;
}

.summary-card.is-warm {
  background: linear-gradient(180deg, rgba(255, 248, 238, 0.96), rgba(255, 252, 247, 0.9));
}

.table-panel {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.table-panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
}

.table-panel-copy {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.table-title {
  margin: 0;
  color: #2f241d;
  font-family: "Noto Serif SC", serif;
  font-size: 30px;
}

.table-subtitle {
  margin: 0;
  color: #6d5948;
  line-height: 1.7;
}

.table-role-hints {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
  max-width: 560px;
}

.role-hint-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(255, 250, 243, 0.82);
  border: 1px solid rgba(110, 84, 54, 0.08);
}

.role-hint-chip small {
  color: #6d5948;
}

.filter-grid,
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.filter-actions,
.dialog-footer,
.table-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.filter-actions,
.table-footer {
  margin-top: 18px;
}

.contact-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.contact-cell small,
.error-text,
.reset-tip span,
.role-dialog-head span,
.kickout-dialog-head span,
.session-summary-text span {
  color: #8f7159;
}

.tag-list,
.action-links {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.table-error {
  padding: 12px 0;
}

.error-text {
  margin: 0;
  line-height: 1.7;
}

.reset-tip {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
  padding: 14px 16px;
  border-radius: 16px;
  background: rgba(255, 249, 241, 0.82);
}

.role-dialog-head {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 16px;
}

.role-checkbox-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.role-checkbox-item {
  display: block;
  padding: 14px 16px;
  border-radius: 16px;
  background: rgba(255, 251, 245, 0.82);
  border: 1px solid rgba(110, 84, 54, 0.08);
}

.role-checkbox-item p {
  margin: 10px 0 0 24px;
  color: #6d5948;
  line-height: 1.7;
}

.session-drawer {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.session-summary-card,
.session-domain-card {
  padding: 20px;
}

.session-summary-head,
.session-domain-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.session-user-name {
  display: block;
  margin-top: 4px;
  color: #2f241d;
  font-family: "Noto Serif SC", serif;
  font-size: 24px;
}

.session-summary-text {
  margin: 14px 0 0;
  color: #6d5948;
  line-height: 1.7;
}

.session-guard-text {
  margin: 14px 0 0;
  color: #b04d18;
  line-height: 1.7;
}

.session-domain-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.session-domain-label {
  display: block;
  margin-bottom: 6px;
  color: #9d7a58;
  font-size: 12px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.session-domain-head strong {
  color: #2f241d;
  font-family: "Noto Serif SC", serif;
  font-size: 20px;
}

.session-metadata {
  display: grid;
  gap: 14px;
  margin: 18px 0 0;
}

.session-metadata dt {
  margin-bottom: 6px;
  color: #9d7a58;
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.session-metadata dd {
  margin: 0;
  color: #3d2f25;
  line-height: 1.7;
}

.session-domain-actions,
.session-drawer-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}

.kickout-dialog-head {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 18px;
}

@media (max-width: 1180px) {
  .summary-grid {
    grid-template-columns: 1fr 1fr;
  }

  .table-panel-head {
    flex-direction: column;
  }

  .table-role-hints {
    justify-content: flex-start;
    max-width: none;
  }
}

@media (max-width: 900px) {
  .user-head,
  .table-panel-head,
  .filter-actions {
    flex-direction: column;
    align-items: flex-start;
  }

  .filter-grid,
  .form-grid,
  .summary-grid,
  .summary-overview-grid,
  .session-domain-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .head-actions,
  .filter-actions,
  .dialog-footer {
    flex-direction: column;
    width: 100%;
  }
}
</style>
