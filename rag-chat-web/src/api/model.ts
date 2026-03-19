import { http, unwrapResponse } from '@/api/http'
import type { PageResponse } from '@/types/api'
import type { ModelSummary } from '@/types/model'

interface ModelQuery {
  providerCode?: string
  pageNo?: number
  pageSize?: number
}

export async function listModels(params: ModelQuery = {}): Promise<PageResponse<ModelSummary>> {
  const response = await http.get('/app/models', {
    params: {
      providerCode: params.providerCode,
      pageNo: params.pageNo ?? 1,
      pageSize: params.pageSize ?? 100,
    },
  })
  return unwrapResponse(response.data)
}
