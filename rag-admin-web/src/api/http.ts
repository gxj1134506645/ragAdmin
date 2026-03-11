import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import type { ApiResponse } from '@/types/api'
import type { RefreshTokenResponse } from '@/types/auth'
import {
  clearSessionStorage,
  getAccessToken,
  getRefreshToken,
  setAccessToken,
  setRefreshToken,
} from '@/utils/token-storage'

interface RetryRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean
}

export class ApiError extends Error {
  code?: string
  status?: number

  constructor(message: string, code?: string, status?: number) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
  }
}

export function unwrapResponse<T>(payload: ApiResponse<T>): T {
  if (payload.code !== 'OK') {
    throw new ApiError(payload.message || '请求失败', payload.code)
  }
  return payload.data
}

export function resolveErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message
  }
  if (error instanceof Error) {
    return error.message
  }
  return '请求失败，请稍后重试'
}

async function refreshAccessToken(): Promise<string> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    throw new ApiError('登录已失效，请重新登录', 'UNAUTHORIZED', 401)
  }
  const response = await axios.post<ApiResponse<RefreshTokenResponse>>(
    '/api/admin/auth/refresh',
    { refreshToken },
    { baseURL: '/' },
  )
  const session = unwrapResponse(response.data)
  setAccessToken(session.accessToken)
  setRefreshToken(session.refreshToken)
  return session.accessToken
}

let refreshTask: Promise<string> | null = null

export const http = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

http.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiResponse<unknown>>) => {
    const config = error.config as RetryRequestConfig | undefined
    const status = error.response?.status
    const requestUrl = config?.url ?? ''
    const isAuthEndpoint = requestUrl.includes('/auth/login') || requestUrl.includes('/auth/refresh')

    if (status === 401 && config && !config._retry && !isAuthEndpoint) {
      config._retry = true
      try {
        refreshTask ??= refreshAccessToken().finally(() => {
          refreshTask = null
        })
        const newToken = await refreshTask
        config.headers.Authorization = `Bearer ${newToken}`
        return http(config)
      } catch (refreshError) {
        clearSessionStorage()
        throw refreshError
      }
    }

    const payload = error.response?.data
    throw new ApiError(
      payload?.message || error.message || '请求失败',
      payload?.code,
      status,
    )
  },
)
