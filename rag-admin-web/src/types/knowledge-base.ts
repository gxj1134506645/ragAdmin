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

export interface UploadUrlRequest {
  fileName: string
  contentType: string
  bizType: 'KB_DOCUMENT'
}

export interface UploadUrlResponse {
  bucket: string
  objectKey: string
  uploadUrl: string
}

export interface CreateKnowledgeBaseDocumentRequest {
  docName: string
  docType: string
  storageBucket: string
  storageObjectKey: string
}

export interface DocumentDetail {
  documentId: number
  kbId: number | null
  kbName?: string | null
  docName: string
  docType: string
  parseStatus: string
  enabled: boolean
  storageBucket?: string | null
  storageObjectKey?: string | null
  createdAt: string
  updatedAt?: string | null
}

export interface DocumentVersion {
  versionId: number
  storageObjectKey: string
  contentHash?: string | null
  active?: boolean
  createdAt: string
}

export interface CreateDocumentVersionRequest {
  storageBucket: string
  storageObjectKey: string
  contentHash?: string | null
  fileSize?: number | null
}

export interface DocumentChunk {
  chunkId: number
  chunkNo?: number | null
  content?: string | null
  contentSnippet?: string | null
  score?: number | null
  tokenCount?: number | null
  charCount?: number | null
}
