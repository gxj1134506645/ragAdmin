import type {
  KnowledgeBase,
  KnowledgeBaseUpsertRequest,
  ModelDefinition,
} from '@/types/knowledge-base'

export function createEmptyKnowledgeBaseForm(): KnowledgeBaseUpsertRequest {
  return {
    kbCode: '',
    kbName: '',
    description: null,
    embeddingModelId: null,
    chatModelId: null,
    retrieveTopK: 5,
    rerankEnabled: true,
    status: 'ENABLED',
  }
}

export function fillKnowledgeBaseForm(
  target: KnowledgeBaseUpsertRequest,
  source: KnowledgeBaseUpsertRequest,
): void {
  target.kbCode = source.kbCode
  target.kbName = source.kbName
  target.description = source.description
  target.embeddingModelId = source.embeddingModelId
  target.chatModelId = source.chatModelId
  target.retrieveTopK = source.retrieveTopK
  target.rerankEnabled = source.rerankEnabled
  target.status = source.status
}

export function mapKnowledgeBaseToForm(source: KnowledgeBase): KnowledgeBaseUpsertRequest {
  return {
    kbCode: source.kbCode,
    kbName: source.kbName,
    description: source.description,
    embeddingModelId: source.embeddingModelId,
    chatModelId: source.chatModelId,
    retrieveTopK: source.retrieveTopK,
    rerankEnabled: source.rerankEnabled,
    status: source.status,
  }
}

export function normalizeKnowledgeBaseForm(form: KnowledgeBaseUpsertRequest): KnowledgeBaseUpsertRequest {
  return {
    kbCode: form.kbCode.trim(),
    kbName: form.kbName.trim(),
    description: form.description?.trim() ? form.description.trim() : null,
    embeddingModelId: form.embeddingModelId ?? null,
    chatModelId: form.chatModelId ?? null,
    retrieveTopK: Number(form.retrieveTopK),
    rerankEnabled: form.rerankEnabled,
    status: form.status,
  }
}

function capabilitySet(model: ModelDefinition): Set<string> {
  return new Set(
    [...(model.capabilityTypes ?? []), model.capabilityType]
      .filter((item): item is string => Boolean(item))
      .map((item) => item.toUpperCase()),
  )
}

function isChatModel(model: ModelDefinition): boolean {
  const capabilities = capabilitySet(model)
  return model.modelType === 'CHAT' || capabilities.has('TEXT_GENERATION')
}

function isEmbeddingModel(model: ModelDefinition): boolean {
  const capabilities = capabilitySet(model)
  return model.modelType === 'EMBEDDING'
    || capabilities.has('TEXT_EMBEDDING')
    || capabilities.has('EMBEDDING')
}

function appendSelectedModel(
  options: ModelDefinition[],
  selectedId: number | null,
  selectedName: string | null,
  fallbackType: string,
): ModelDefinition[] {
  if (!selectedId || options.some((item) => item.id === selectedId)) {
    return options
  }
  return [
    {
      id: selectedId,
      modelCode: `selected-${selectedId}`,
      modelName: selectedName || `已绑定模型 #${selectedId}`,
      modelType: fallbackType,
      status: 'ENABLED',
    },
    ...options,
  ]
}

export function buildKnowledgeBaseModelOptions(
  models: ModelDefinition[],
  currentKnowledgeBase?: KnowledgeBase | null,
): {
  chatModelOptions: ModelDefinition[]
  embeddingModelOptions: ModelDefinition[]
} {
  const enabledModels = models.filter((item) => item.status === 'ENABLED' || !item.status)

  const chatModelOptions = appendSelectedModel(
    enabledModels.filter(isChatModel),
    currentKnowledgeBase?.chatModelId ?? null,
    currentKnowledgeBase?.chatModelName ?? null,
    'CHAT',
  )

  const embeddingModelOptions = appendSelectedModel(
    enabledModels.filter(isEmbeddingModel),
    currentKnowledgeBase?.embeddingModelId ?? null,
    currentKnowledgeBase?.embeddingModelName ?? null,
    'EMBEDDING',
  )

  return {
    chatModelOptions,
    embeddingModelOptions,
  }
}
