package com.ragadmin.server.infra.storage.support;

import com.ragadmin.server.infra.storage.MinioProperties;
import io.minio.MinioClient;
import org.springframework.stereotype.Component;

@Component
public class MinioClientFactory {

    private final MinioProperties minioProperties;

    public MinioClientFactory(MinioProperties minioProperties) {
        this.minioProperties = minioProperties;
    }

    public MinioClient createClient() {
        return MinioClient.builder()
                .endpoint(minioProperties.getBaseUrl())
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                .build();
    }
}
