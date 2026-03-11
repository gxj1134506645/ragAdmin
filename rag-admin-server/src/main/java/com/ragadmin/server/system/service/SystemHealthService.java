package com.ragadmin.server.system.service;

import com.ragadmin.server.infra.ai.bailian.BailianApiSupport;
import com.ragadmin.server.infra.ai.bailian.BailianProperties;
import com.ragadmin.server.infra.ai.embedding.OllamaProperties;
import com.ragadmin.server.infra.storage.MinioProperties;
import com.ragadmin.server.infra.vector.MilvusProperties;
import com.ragadmin.server.system.dto.DependencyHealthResponse;
import com.ragadmin.server.system.dto.HealthCheckResponse;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class SystemHealthService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private MinioProperties minioProperties;

    @Autowired
    private BailianProperties bailianProperties;

    @Autowired
    private OllamaProperties ollamaProperties;

    @Autowired
    private MilvusProperties milvusProperties;

    public HealthCheckResponse check() {
        DependencyHealthResponse postgres = checkPostgres();
        DependencyHealthResponse redis = checkRedis();
        DependencyHealthResponse minio = checkMinio();
        DependencyHealthResponse bailian = checkBailian();
        DependencyHealthResponse ollama = checkOllama();
        DependencyHealthResponse milvus = checkMilvus();
        String status = isHealthy(postgres, redis, minio, bailian, ollama, milvus) ? "UP" : "DEGRADED";
        return new HealthCheckResponse(status, postgres, redis, minio, bailian, ollama, milvus);
    }

    private DependencyHealthResponse checkPostgres() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (result != null && result == 1) {
                return new DependencyHealthResponse("UP", "PostgreSQL 连通正常");
            }
            return new DependencyHealthResponse("DOWN", "PostgreSQL 响应异常");
        } catch (DataAccessException ex) {
            return new DependencyHealthResponse("DOWN", buildMessage("PostgreSQL 检查失败", ex));
        }
    }

    private DependencyHealthResponse checkRedis() {
        if (stringRedisTemplate.getConnectionFactory() == null) {
            return new DependencyHealthResponse("DOWN", "Redis 连接工厂未初始化");
        }
        try (RedisConnection connection = stringRedisTemplate.getConnectionFactory().getConnection()) {
            String pong = connection.ping();
            if ("PONG".equalsIgnoreCase(pong)) {
                return new DependencyHealthResponse("UP", "Redis 连通正常");
            }
            return new DependencyHealthResponse("DOWN", "Redis 响应异常");
        } catch (Exception ex) {
            return new DependencyHealthResponse("DOWN", buildMessage("Redis 检查失败", ex));
        }
    }

    private DependencyHealthResponse checkMinio() {
        if (!minioProperties.isConfigured()) {
            return new DependencyHealthResponse("UNKNOWN", "MinIO 未完成本地配置");
        }
        try {
            MinioClient client = MinioClient.builder()
                    .endpoint(minioProperties.getBaseUrl())
                    .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                    .build();
            boolean exists = client.bucketExists(BucketExistsArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .build());
            if (exists) {
                return new DependencyHealthResponse("UP", "MinIO 连通正常");
            }
            return new DependencyHealthResponse("DOWN", "MinIO Bucket 不存在");
        } catch (Exception ex) {
            return new DependencyHealthResponse("DOWN", buildMessage("MinIO 检查失败", ex));
        }
    }

    private boolean isUp(DependencyHealthResponse response) {
        return "UP".equals(response.status());
    }

    private boolean isHealthy(DependencyHealthResponse... responses) {
        for (DependencyHealthResponse response : responses) {
            if (!isUp(response) && !"UNKNOWN".equals(response.status())) {
                return false;
            }
        }
        return true;
    }

    private DependencyHealthResponse checkOllama() {
        if (!StringUtils.hasText(ollamaProperties.getBaseUrl())) {
            return new DependencyHealthResponse("UNKNOWN", "Ollama 未配置地址");
        }
        try {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(Duration.ofSeconds(Math.max(5, ollamaProperties.getTimeoutSeconds())));
            requestFactory.setReadTimeout(Duration.ofSeconds(Math.max(5, ollamaProperties.getTimeoutSeconds())));
            RestClient client = RestClient.builder()
                    .baseUrl(ollamaProperties.getBaseUrl())
                    .requestFactory(requestFactory)
                    .build();
            OllamaTagsResponse response = client.get()
                    .uri("/api/tags")
                    .retrieve()
                    .body(OllamaTagsResponse.class);
            int modelCount = response == null || response.models() == null ? 0 : response.models().size();
            return new DependencyHealthResponse("UP", "Ollama 连通正常，模型数=" + modelCount);
        } catch (Exception ex) {
            return new DependencyHealthResponse("DOWN", buildMessage("Ollama 检查失败", ex));
        }
    }

    private DependencyHealthResponse checkBailian() {
        if (!bailianProperties.isEnabled()) {
            return new DependencyHealthResponse("UNKNOWN", "百炼已禁用");
        }
        if (!StringUtils.hasText(bailianProperties.getApiKey())) {
            return new DependencyHealthResponse("UNKNOWN", "百炼未配置 API Key");
        }
        try {
            RestClient client = BailianApiSupport.buildRestClient(bailianProperties);
            BailianModelsResponse response = client.get()
                    .uri("/models")
                    .retrieve()
                    .body(BailianModelsResponse.class);
            int modelCount = response == null || response.data() == null ? 0 : response.data().size();
            return new DependencyHealthResponse("UP", "百炼连通正常，模型数=" + modelCount);
        } catch (Exception ex) {
            return new DependencyHealthResponse("DOWN", buildMessage("百炼检查失败", ex));
        }
    }

    private DependencyHealthResponse checkMilvus() {
        if (!milvusProperties.isEnabled()) {
            return new DependencyHealthResponse("UNKNOWN", "Milvus 已禁用");
        }
        if (!StringUtils.hasText(milvusProperties.getBaseUrl())) {
            return new DependencyHealthResponse("UNKNOWN", "Milvus 未配置地址");
        }
        try {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(Duration.ofSeconds(10));
            requestFactory.setReadTimeout(Duration.ofSeconds(15));
            RestClient client = RestClient.builder()
                    .baseUrl(milvusProperties.getBaseUrl())
                    .defaultHeader("Authorization", "Bearer " + milvusProperties.getToken())
                    .requestFactory(requestFactory)
                    .build();
            MilvusCollectionsResponse response = client.post()
                    .uri("/v2/vectordb/collections/list")
                    .body(Map.of("dbName", "_default"))
                    .retrieve()
                    .body(MilvusCollectionsResponse.class);
            int collectionCount = response == null || response.data() == null ? 0 : response.data().size();
            return new DependencyHealthResponse("UP", "Milvus 连通正常，集合数=" + collectionCount);
        } catch (Exception ex) {
            return new DependencyHealthResponse("DOWN", buildMessage("Milvus 检查失败", ex));
        }
    }

    private String buildMessage(String prefix, Exception ex) {
        String message = ex.getMessage();
        if (!StringUtils.hasText(message)) {
            return prefix;
        }
        return prefix + ": " + message;
    }

    private record OllamaTagsResponse(List<Map<String, Object>> models) {
    }

    private record BailianModelsResponse(List<Map<String, Object>> data) {
    }

    private record MilvusCollectionsResponse(List<Object> data) {
    }
}
