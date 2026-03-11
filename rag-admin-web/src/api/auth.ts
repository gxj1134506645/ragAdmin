import type { ApiResponse } from '@/types/api'
import type { CurrentUser, LoginRequest, LoginResponse } from '@/types/auth'
import { http, unwrapResponse } from './http'

export async function login(payload: LoginRequest): Promise<LoginResponse> {
  const response = await http.post<ApiResponse<LoginResponse>>('/admin/auth/login', payload)
  return unwrapResponse(response.data)
}

export async function logout(): Promise<void> {
  const response = await http.post<ApiResponse<null>>('/admin/auth/logout')
  unwrapResponse(response.data)
}

export async function getCurrentUser(): Promise<CurrentUser> {
  const response = await http.get<ApiResponse<CurrentUser>>('/admin/auth/me')
  return unwrapResponse(response.data)
}
