import type { ApiResponse, PageResponse } from '@/types/api'
import type { KnowledgeBase } from '@/types/knowledge-base'
import { http, unwrapResponse } from './http'

export interface KnowledgeBaseListQuery {
  pageNo: number
  pageSize: number
}

export async function listKnowledgeBases(
  query: KnowledgeBaseListQuery,
): Promise<PageResponse<KnowledgeBase>> {
  const response = await http.get<ApiResponse<PageResponse<KnowledgeBase>>>('/admin/knowledge-bases', {
    params: query,
  })
  return unwrapResponse(response.data)
}
