export interface CurrentUser {
  id: number
  username: string
  displayName: string
  mobile?: string
  roles: string[]
}

export interface LoginRequest {
  loginId: string
  password: string
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  refreshExpiresIn: number
  user: CurrentUser
}

export interface RefreshTokenResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  refreshExpiresIn: number
}
