import { http, unwrapResponse } from '@/api/http'
import type { CurrentUser, LoginRequest, LoginResponse, RefreshTokenResponse } from '@/types/auth'

export async function login(payload: LoginRequest): Promise<LoginResponse> {
  const response = await http.post('/app/auth/login', payload)
  return unwrapResponse(response.data)
}

export async function refresh(refreshToken: string): Promise<RefreshTokenResponse> {
  const response = await http.post('/app/auth/refresh', { refreshToken })
  return unwrapResponse(response.data)
}

export async function logout(): Promise<void> {
  const response = await http.post('/app/auth/logout')
  unwrapResponse(response.data)
}

export async function getCurrentUser(): Promise<CurrentUser> {
  const response = await http.get('/app/auth/me')
  return unwrapResponse(response.data)
}
