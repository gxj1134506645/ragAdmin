import type { PageResponse } from './api'

export interface AuditLogRecord {
  id: number
  operatorUserId: number | null
  operatorUsername: string | null
  actionType: string
  bizType: string
  bizId: number | null
  requestMethod: string
  requestPath: string
  requestIp: string | null
  responseCode: string | null
  success: boolean
  createdAt: string
}

export interface AuditLogListQuery {
  operator?: string
  bizType?: string
  startTime?: string
  endTime?: string
  pageNo?: number
  pageSize?: number
}

export type AuditLogPage = PageResponse<AuditLogRecord>
