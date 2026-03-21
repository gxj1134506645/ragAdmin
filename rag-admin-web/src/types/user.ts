export interface UserListItem {
  id: number
  username: string
  displayName: string
  email?: string | null
  mobile?: string | null
  status: string
  roles: string[]
  adminOnline: boolean
  appOnline: boolean
}

export interface UserListQuery {
  pageNo: number
  pageSize: number
  keyword?: string
  status?: string
}

export interface CreateUserRequest {
  username: string
  password: string
  displayName: string
  email?: string | null
  mobile?: string | null
  status: string
  roleCodes: string[]
}

export interface UpdateUserRequest {
  displayName: string
  email?: string | null
  mobile?: string | null
  status: string
  password?: string | null
}

export interface AssignUserRolesRequest {
  roleCodes: string[]
}

export type UserSessionScope = 'admin' | 'app' | 'all'

export interface UserSessionListQuery {
  pageNo: number
  pageSize: number
  keyword?: string
  roleCode?: string
  onlineScope?: UserSessionScope
}

export interface UserSessionListItem {
  userId: number
  username: string
  displayName: string
  mobile?: string | null
  status: string
  roles: string[]
  adminOnline: boolean
  appOnline: boolean
  lastLoginAt?: string | null
  lastActiveAt?: string | null
}

export interface UserSessionDetail {
  userId: number
  username: string
  displayName: string
  mobile?: string | null
  status: string
  roles: string[]
  adminOnline: boolean
  appOnline: boolean
  adminLastLoginAt?: string | null
  adminLastActiveAt?: string | null
  appLastLoginAt?: string | null
  appLastActiveAt?: string | null
}

export interface KickoutUserSessionRequest {
  scope: UserSessionScope
  reason: string
}
