export interface KnowledgeBase {
  id: number
  kbCode: string
  kbName: string
  description: string | null
  embeddingModelId: number | null
  embeddingModelName: string | null
  chatModelId: number | null
  chatModelName: string | null
  retrieveTopK: number
  rerankEnabled: boolean
  status: string
}
