import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as authApi from '@/api/auth'
import type { CurrentUser, LoginRequest } from '@/types/auth'
import {
  clearSessionStorage,
  getAccessToken,
  getCurrentUserRaw,
  getRefreshToken,
  setAccessToken,
  setCurrentUserRaw,
  setRefreshToken,
} from '@/utils/token-storage'

function parseStoredUser(): CurrentUser | null {
  const raw = getCurrentUserRaw()
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw) as CurrentUser
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('app-auth', () => {
  const accessToken = ref(getAccessToken())
  const refreshToken = ref(getRefreshToken())
  const currentUser = ref<CurrentUser | null>(parseStoredUser())
  const bootstrapFinished = ref(false)

  const isAuthenticated = computed(() => Boolean(accessToken.value))
  const displayName = computed(() => currentUser.value?.displayName || currentUser.value?.username || '未登录用户')

  function applySession(payload: {
    accessToken: string
    refreshToken: string
    user?: CurrentUser
  }): void {
    accessToken.value = payload.accessToken
    refreshToken.value = payload.refreshToken
    setAccessToken(payload.accessToken)
    setRefreshToken(payload.refreshToken)
    if (payload.user) {
      currentUser.value = payload.user
      setCurrentUserRaw(JSON.stringify(payload.user))
    }
  }

  function clearSession(): void {
    accessToken.value = ''
    refreshToken.value = ''
    currentUser.value = null
    clearSessionStorage()
  }

  async function login(payload: LoginRequest): Promise<void> {
    const response = await authApi.login(payload)
    applySession(response)
  }

  async function hydrateCurrentUser(): Promise<void> {
    if (!accessToken.value) {
      bootstrapFinished.value = true
      return
    }
    if (currentUser.value) {
      bootstrapFinished.value = true
      return
    }
    try {
      currentUser.value = await authApi.getCurrentUser()
      setCurrentUserRaw(JSON.stringify(currentUser.value))
    } catch {
      clearSession()
    } finally {
      bootstrapFinished.value = true
    }
  }

  async function logout(): Promise<void> {
    try {
      if (accessToken.value) {
        await authApi.logout()
      }
    } finally {
      clearSession()
    }
  }

  return {
    accessToken,
    refreshToken,
    currentUser,
    bootstrapFinished,
    isAuthenticated,
    displayName,
    login,
    logout,
    clearSession,
    hydrateCurrentUser,
    applySession,
  }
})
