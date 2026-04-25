package com.ragadmin.server.system.dto;

public record HealthCheckResponse(
        String status,
        DependencyHealthResponse postgres,
        DependencyHealthResponse redis,
        DependencyHealthResponse minio,
        DependencyHealthResponse bailian,
        DependencyHealthResponse ollama,
        DependencyHealthResponse milvus,
        DependencyHealthResponse tavily,
        DependencyHealthResponse mineru,
        DependencyHealthResponse elasticsearch
) {
}
