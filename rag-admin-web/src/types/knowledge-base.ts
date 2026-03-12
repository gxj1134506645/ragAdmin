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

export interface KnowledgeBaseUpsertRequest {
  kbCode: string
  kbName: string
  description: string | null
  embeddingModelId: number | null
  chatModelId: number | null
  retrieveTopK: number
  rerankEnabled: boolean
  status: string
}

export interface ModelDefinition {
  id: number
  modelCode: string
  modelName: string
  modelType: string
  capabilityTypes?: string[]
  capabilityType?: string
  status: string
}

export interface KnowledgeBaseDocument {
  documentId: number
  docName: string
  docType: string
  parseStatus: string
  enabled: boolean
  createdAt: string
}
