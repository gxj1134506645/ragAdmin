export interface ModelSummary {
  id: number
  providerId: number
  providerCode: string
  providerName: string
  modelCode: string
  modelName: string
  capabilityTypes: string[]
  modelType: string
  maxTokens: number | null
  temperatureDefault: number | null
  status: string
  isDefaultChatModel: boolean
}
