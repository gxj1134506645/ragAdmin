export interface ModelProvider {
  id: number
  providerCode: string
  providerName: string
  baseUrl: string | null
  apiKeySecretRef?: string | null
  status: string
}

export interface ModelProviderCreateRequest {
  providerCode: string
  providerName: string
  baseUrl: string | null
  apiKeySecretRef?: string | null
  status: string
}

export interface ModelDefinition {
  id: number
  providerId: number
  providerCode: string | null
  providerName: string | null
  modelCode: string
  modelName: string
  capabilityTypes: string[]
  modelType: string
  maxTokens?: number | null
  temperatureDefault?: number | null
  status: string
  isDefaultChatModel: boolean
}

export interface ModelCreateRequest {
  providerId: number | null
  modelCode: string
  modelName: string
  capabilityTypes: string[]
  modelType: string
  maxTokens?: number | null
  temperatureDefault?: number | null
  status: string
}

export interface UpdateModelRequest {
  providerId: number | null
  modelCode: string
  modelName: string
  capabilityTypes: string[]
  modelType: string
  maxTokens?: number | null
  temperatureDefault?: number | null
  status: string
}

export interface BatchDeleteModelsRequest {
  modelIds: number[]
}

export interface ModelBatchDeleteFailure {
  modelId: number
  modelName: string
  message: string
}

export interface ModelBatchDeleteResult {
  requestedCount: number
  successCount: number
  failedCount: number
  deletedIds: number[]
  failedItems: ModelBatchDeleteFailure[]
}

export interface ModelCapabilityHealthCheck {
  capabilityType: string
  status: string
  message: string
}

export interface ModelHealthCheck {
  modelId: number
  modelCode: string
  providerCode: string
  status: string
  message: string
  capabilityChecks: ModelCapabilityHealthCheck[]
}

export interface ModelProviderCapabilityHealthCheck {
  capabilityType: string
  modelId?: number | null
  modelCode?: string | null
  status: string
  message: string
}

export interface ModelProviderHealthCheck {
  providerId: number
  providerCode: string
  providerName: string
  status: string
  message: string
  capabilityChecks: ModelProviderCapabilityHealthCheck[]
}
