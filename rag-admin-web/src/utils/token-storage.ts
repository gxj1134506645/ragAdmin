const ACCESS_TOKEN_KEY = 'rag_admin_access_token'
const REFRESH_TOKEN_KEY = 'rag_admin_refresh_token'
const CURRENT_USER_KEY = 'rag_admin_current_user'

function read(key: string): string {
  return window.localStorage.getItem(key) ?? ''
}

function write(key: string, value: string): void {
  window.localStorage.setItem(key, value)
}

export function getAccessToken(): string {
  return read(ACCESS_TOKEN_KEY)
}

export function setAccessToken(token: string): void {
  write(ACCESS_TOKEN_KEY, token)
}

export function getRefreshToken(): string {
  return read(REFRESH_TOKEN_KEY)
}

export function setRefreshToken(token: string): void {
  write(REFRESH_TOKEN_KEY, token)
}

export function getCurrentUserRaw(): string {
  return read(CURRENT_USER_KEY)
}

export function setCurrentUserRaw(value: string): void {
  write(CURRENT_USER_KEY, value)
}

export function clearSessionStorage(): void {
  window.localStorage.removeItem(ACCESS_TOKEN_KEY)
  window.localStorage.removeItem(REFRESH_TOKEN_KEY)
  window.localStorage.removeItem(CURRENT_USER_KEY)
}
