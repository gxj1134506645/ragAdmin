export type ChatSceneType = 'GENERAL' | 'KNOWLEDGE_BASE'

export type ChatFeedbackType = 'LIKE' | 'DISLIKE'

export interface ChatReference {
  kbId: number | null
  documentId: number | null
  documentName: string | null
  chunkId: number
  chunkNo: number | null
  score: number
  contentSnippet: string
}

export interface ChatUsage {
  promptTokens: number | null
  completionTokens: number | null
}

export interface ChatExchange {
  id: number
  questionText: string
  answerText: string
  references: ChatReference[]
  feedbackType: ChatFeedbackType | null
  feedbackComment: string | null
  usage?: ChatUsage | null
}

export interface ChatSession {
  id: number
  kbId: number | null
  sceneType: ChatSceneType
  sessionName: string
  chatModelId: number | null
  webSearchEnabled: boolean
  selectedKbIds: number[]
  status: string
}

export interface CreateChatSessionRequest {
  kbId?: number
  sceneType?: ChatSceneType
  sessionName: string
  chatModelId?: number
  webSearchEnabled?: boolean
  selectedKbIds?: number[]
}

export interface UpdateSessionKnowledgeBasesRequest {
  selectedKbIds: number[]
}

export interface UpdateChatSessionRequest {
  sessionName: string
  chatModelId: number | null
  webSearchEnabled: boolean
}

export interface ChatRequest {
  question: string
  chatModelId?: number
  selectedKbIds?: number[]
  webSearchEnabled?: boolean
}

export interface ChatResponse {
  messageId: number
  answer: string
  references: ChatReference[]
  usage: ChatUsage | null
}

export interface ChatStreamEvent {
  eventType: 'DELTA' | 'COMPLETE' | 'ERROR'
  delta: string | null
  messageId: number | null
  answer: string | null
  references: ChatReference[] | null
  usage: ChatUsage | null
  errorMessage: string | null
}

export interface ChatFeedbackRequest {
  feedbackType: ChatFeedbackType
  comment?: string
}
