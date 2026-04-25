export interface DependencyHealth {
  status: string
  message: string
}

export interface HealthCheckResponse {
  status: string
  postgres: DependencyHealth
  redis: DependencyHealth
  minio: DependencyHealth
  bailian: DependencyHealth
  ollama: DependencyHealth
  milvus: DependencyHealth
  tavily: DependencyHealth
  mineru: DependencyHealth
  elasticsearch: DependencyHealth
}
