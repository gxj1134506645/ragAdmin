import type { ApiResponse, PageResponse } from '@/types/api'
import type { AuditLogListQuery, AuditLogRecord } from '@/types/audit'
import { http, unwrapResponse } from './http'

export async function listAuditLogs(
  query: AuditLogListQuery,
): Promise<PageResponse<AuditLogRecord>> {
  const response = await http.get<ApiResponse<PageResponse<AuditLogRecord>>>('/admin/audit-logs', {
    params: {
      operator: query.operator || undefined,
      bizType: query.bizType || undefined,
      startTime: query.startTime || undefined,
      endTime: query.endTime || undefined,
      pageNo: query.pageNo ?? 1,
      pageSize: query.pageSize ?? 20,
    },
  })
  return unwrapResponse(response.data)
}
