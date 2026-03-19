import { http, unwrapResponse } from '@/api/http'
import type { PageResponse } from '@/types/api'
import type { KnowledgeBaseSummary } from '@/types/knowledge-base'

interface KnowledgeBaseQuery {
  keyword?: string
  pageNo?: number
  pageSize?: number
}

export async function listKnowledgeBases(
  params: KnowledgeBaseQuery = {},
): Promise<PageResponse<KnowledgeBaseSummary>> {
  const response = await http.get('/app/knowledge-bases', {
    params: {
      keyword: params.keyword,
      pageNo: params.pageNo ?? 1,
      pageSize: params.pageSize ?? 100,
    },
  })
  return unwrapResponse(response.data)
}
