export type ChatSceneType = 'GENERAL' | 'KNOWLEDGE_BASE'
export type ChatFeedbackType = 'LIKE' | 'DISLIKE'

export interface ChatReference {
  documentId: number | null
  documentName: string | null
  chunkId: number
  chunkNo?: number | null
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
}

export interface ChatSession {
  id: number
  kbId: number | null
  sceneType: ChatSceneType
  sessionName: string
  status: string
}

export interface CreateChatSessionRequest {
  kbId?: number | null
  sceneType?: ChatSceneType
  sessionName: string
}

export interface ChatRequest {
  question: string
  kbId?: number | null
  stream?: boolean
}

export interface ChatResponsePayload {
  messageId: number
  answer: string
  references: ChatReference[]
  usage: ChatUsage
}

export interface ChatStreamEvent {
  eventType: 'DELTA' | 'COMPLETE' | 'ERROR'
  delta?: string | null
  messageId?: number | null
  answer?: string | null
  references?: ChatReference[] | null
  usage?: ChatUsage | null
  errorMessage?: string | null
}

export interface ChatFeedbackRequest {
  feedbackType: ChatFeedbackType
  comment?: string
}
