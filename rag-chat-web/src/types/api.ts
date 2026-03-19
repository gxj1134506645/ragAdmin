export interface ApiResponse<T> {
  code: string
  message: string
  data: T
}

export interface PageResponse<T> {
  list: T[]
  pageNo: number
  pageSize: number
  total: number
}
