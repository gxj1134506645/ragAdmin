import type { ApiResponse, PageResponse } from '@/types/api'
import type {
  CreateDocumentVersionRequest,
  CreateKnowledgeBaseDocumentRequest,
  DocumentChunk,
  DocumentDetail,
  DocumentVersion,
  KnowledgeBase,
  KnowledgeBaseDocument,
  KnowledgeBaseUpsertRequest,
  UploadUrlRequest,
  UploadUrlResponse,
} from '@/types/knowledge-base'
import type { ModelDefinition } from '@/types/model'
import { http, unwrapResponse } from './http'
import { listModels as listModelDefinitions, type ModelListQuery } from './model'

export interface KnowledgeBaseListQuery {
  pageNo: number
  pageSize: number
  keyword?: string
  status?: string
}

export interface KnowledgeBaseDocumentListQuery {
  pageNo: number
  pageSize: number
  keyword?: string
  parseStatus?: string
  enabled?: boolean
}

export type { ModelListQuery }

export async function listKnowledgeBases(
  query: KnowledgeBaseListQuery,
): Promise<PageResponse<KnowledgeBase>> {
  const response = await http.get<ApiResponse<PageResponse<KnowledgeBase>>>('/admin/knowledge-bases', {
    params: query,
  })
  return unwrapResponse(response.data)
}

export async function createKnowledgeBase(payload: KnowledgeBaseUpsertRequest): Promise<number | null> {
  const response = await http.post<ApiResponse<KnowledgeBase>>('/admin/knowledge-bases', payload)
  return unwrapResponse(response.data).id
}

function mapDocumentResponse(item: {
  id: number
  kbId: number | null
  kbName?: string | null
  docName: string
  docType: string
  storageBucket?: string | null
  storageObjectKey?: string | null
  currentVersion?: number | null
  parseStatus: string
  enabled: boolean
  fileSize?: number | null
  contentHash?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}) {
  return {
    documentId: item.id,
    kbId: item.kbId,
    kbName: item.kbName ?? null,
    docName: item.docName,
    docType: item.docType,
    parseStatus: item.parseStatus,
    enabled: item.enabled,
    storageBucket: item.storageBucket ?? null,
    storageObjectKey: item.storageObjectKey ?? null,
    currentVersion: item.currentVersion ?? null,
    fileSize: item.fileSize ?? null,
    contentHash: item.contentHash ?? null,
    createdAt: item.createdAt ?? '',
    updatedAt: item.updatedAt ?? null,
  }
}

function mapDocumentVersionResponse(item: {
  id: number
  versionNo: number
  storageObjectKey: string
  contentHash?: string | null
  createdAt: string
  active?: boolean | null
}) {
  return {
    versionId: item.id,
    versionNo: item.versionNo,
    storageObjectKey: item.storageObjectKey,
    contentHash: item.contentHash ?? null,
    createdAt: item.createdAt,
    active: item.active ?? false,
  }
}

function mapChunkResponse(item: {
  id: number
  chunkNo: number
  chunkText: string
  tokenCount?: number | null
  charCount?: number | null
}) {
  return {
    chunkId: item.id,
    chunkNo: item.chunkNo,
    content: item.chunkText,
    contentSnippet: item.chunkText,
    tokenCount: item.tokenCount ?? null,
    charCount: item.charCount ?? null,
  }
}

export async function getKnowledgeBaseDetail(id: number): Promise<KnowledgeBase> {
  const response = await http.get<ApiResponse<KnowledgeBase>>(`/admin/knowledge-bases/${id}`)
  return unwrapResponse(response.data)
}

export async function updateKnowledgeBase(
  id: number,
  payload: KnowledgeBaseUpsertRequest,
): Promise<void> {
  const response = await http.put<ApiResponse<KnowledgeBase>>(`/admin/knowledge-bases/${id}`, payload)
  unwrapResponse(response.data)
}

export async function deleteKnowledgeBase(id: number): Promise<void> {
  const response = await http.delete<ApiResponse<null>>(`/admin/knowledge-bases/${id}`)
  unwrapResponse(response.data)
}

export async function listKnowledgeBaseDocuments(
  id: number,
  query: KnowledgeBaseDocumentListQuery,
): Promise<PageResponse<KnowledgeBaseDocument>> {
  const response = await http.get<ApiResponse<PageResponse<any>>>(
    `/admin/knowledge-bases/${id}/documents`,
    {
      params: query,
    },
  )
  const data = unwrapResponse(response.data)
  return {
    ...data,
    list: data.list.map((item) => ({
      ...mapDocumentResponse(item),
      createdAt: item.createdAt ?? '',
    })),
  }
}

export async function getKnowledgeBaseDocumentUploadUrl(
  payload: UploadUrlRequest,
): Promise<UploadUrlResponse> {
  const response = await http.post<ApiResponse<UploadUrlResponse>>('/admin/files/upload-url', payload)
  return unwrapResponse(response.data)
}

export async function createKnowledgeBaseDocument(
  id: number,
  payload: CreateKnowledgeBaseDocumentRequest,
): Promise<number | null> {
  const response = await http.post<ApiResponse<any>>(`/admin/knowledge-bases/${id}/documents`, payload)
  return mapDocumentResponse(unwrapResponse(response.data)).documentId
}

export async function triggerDocumentParse(documentId: number): Promise<void> {
  const response = await http.post<ApiResponse<null>>(`/admin/documents/${documentId}/parse`)
  unwrapResponse(response.data)
}

export async function getDocumentDetail(documentId: number): Promise<DocumentDetail> {
  const response = await http.get<ApiResponse<any>>(`/admin/documents/${documentId}`)
  return mapDocumentResponse(unwrapResponse(response.data))
}

export async function listDocumentVersions(
  documentId: number,
  query: { pageNo: number; pageSize: number },
): Promise<PageResponse<DocumentVersion>> {
  const response = await http.get<ApiResponse<PageResponse<any>>>(
    `/admin/documents/${documentId}/versions`,
    {
      params: query,
    },
  )
  const data = unwrapResponse(response.data)
  return {
    ...data,
    list: data.list.map(mapDocumentVersionResponse),
  }
}

export async function listDocumentChunks(
  documentId: number,
  query: { pageNo: number; pageSize: number },
): Promise<PageResponse<DocumentChunk>> {
  const response = await http.get<ApiResponse<PageResponse<any>>>(
    `/admin/documents/${documentId}/chunks`,
    {
      params: query,
    },
  )
  const data = unwrapResponse(response.data)
  return {
    ...data,
    list: data.list.map(mapChunkResponse),
  }
}

export async function activateDocumentVersion(documentId: number, versionId: number): Promise<void> {
  const response = await http.put<ApiResponse<null>>(
    `/admin/documents/${documentId}/versions/${versionId}/activate`,
  )
  unwrapResponse(response.data)
}

export async function createDocumentVersion(
  documentId: number,
  payload: CreateDocumentVersionRequest,
): Promise<number | null> {
  const response = await http.post<ApiResponse<any>>(
    `/admin/documents/${documentId}/versions`,
    payload,
  )
  return mapDocumentResponse(unwrapResponse(response.data)).documentId
}

export async function listModels(query: ModelListQuery): Promise<PageResponse<ModelDefinition>> {
  return listModelDefinitions(query)
}

