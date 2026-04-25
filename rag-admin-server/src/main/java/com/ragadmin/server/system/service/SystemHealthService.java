package com.ragadmin.server.system.service;

import com.ragadmin.server.infra.ai.bailian.BailianApiSupport;
import com.ragadmin.server.infra.ai.bailian.BailianProperties;
import com.ragadmin.server.document.parser.MineruParseService;
import com.ragadmin.server.document.parser.OcrCapability;
import com.ragadmin.server.document.mapper.ChunkVectorRefMapper;
import com.ragadmin.server.infra.ai.embedding.OllamaProperties;
import com.ragadmin.server.infra.search.NoopWebSearchProvider;
import com.ragadmin.server.infra.search.TavilyProperties;
import com.ragadmin.server.infra.search.WebSearchProperties;
import com.ragadmin.server.infra.search.WebSearchProvider;
import com.ragadmin.server.infra.elasticsearch.ElasticsearchClient;
import com.ragadmin.server.infra.elasticsearch.ElasticsearchProperties;
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
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Collections;
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

    @Autowired
    private ElasticsearchProperties elasticsearchProperties;

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Autowired
    private WebSearchProperties webSearchProperties;

    @Autowired
    private TavilyProperties tavilyProperties;

    @Autowired
    private MineruParseService mineruParseService;

    @Autowired
    private ChunkVectorRefMapper chunkVectorRefMapper;

    @Autowired(required = false)
    private WebSearchProvider webSearchProvider = new NoopWebSearchProvider();

    public HealthCheckResponse check() {
        DependencyHealthResponse postgres = checkPostgres();
        DependencyHealthResponse redis = checkRedis();
        DependencyHealthResponse minio = checkMinio();
        DependencyHealthResponse bailian = checkBailian();
        DependencyHealthResponse ollama = checkOllama();
        DependencyHealthResponse milvus = checkMilvus();
        DependencyHealthResponse elasticsearch = checkElasticsearch();
        DependencyHealthResponse tavily = checkTavily();
        DependencyHealthResponse mineru = checkMineru();
        String status = isHealthy(postgres, redis, minio, bailian, ollama, milvus, tavily, mineru, elasticsearch) ? "UP" : "DEGRADED";
        return new HealthCheckResponse(status, postgres, redis, minio, bailian, ollama, milvus, tavily, mineru, elasticsearch);
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
        if (!ollamaProperties.isEnabled()) {
            return new DependencyHealthResponse("UNKNOWN", "Ollama 已禁用");
        }
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

            // 当前 Milvus REST 的 collections/list 在本地环境下会返回空集，不能可靠反映真实集合数。
            // 这里先用轻量搜索接口确认连通性，再以业务侧已落库的 collectionName 去重数作为观测值。
            client.post()
                    .uri("/v2/vectordb/collections/list")
                    .body(Collections.emptyMap())
                    .retrieve()
                    .toBodilessEntity();

            Integer collectionCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(DISTINCT collection_name) FROM kb_chunk_vector_ref WHERE status = 'ENABLED'",
                    Integer.class
            );
            int safeCount = collectionCount == null ? 0 : collectionCount;
            return new DependencyHealthResponse("UP", "Milvus 连通正常，业务集合数=" + safeCount);
        } catch (Exception ex) {
            return new DependencyHealthResponse("DOWN", buildMessage("Milvus 检查失败", ex));
        }
    }

    private DependencyHealthResponse checkElasticsearch() {
        if (!elasticsearchProperties.isEnabled()) {
            return new DependencyHealthResponse("UNKNOWN", "Elasticsearch 已禁用");
        }
        if (!StringUtils.hasText(elasticsearchProperties.getUris())) {
            return new DependencyHealthResponse("UNKNOWN", "Elasticsearch 未配置地址");
        }
        try {
            // 使用 ElasticsearchClient 的内部 RestClient 调用 _cluster/health API
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(Duration.ofSeconds(5));
            requestFactory.setReadTimeout(Duration.ofSeconds(10));
            RestClient client = RestClient.builder()
                    .baseUrl(StringUtils.hasText(elasticsearchProperties.getUris()) ? elasticsearchProperties.getUris() : "http://127.0.0.1:9200")
                    .requestFactory(requestFactory)
                    .defaultHeader("Content-Type", "application/json")
                    .build();

            // 调用 ES 集群健康检查 API
            @SuppressWarnings("unchecked")
            Map<String, Object> response = client.get()
                    .uri("/_cluster/health")
                    .retrieve()
                    .body(Map.class);

            if (response != null && response.containsKey("status")) {
                String esStatus = String.valueOf(response.get("status"));
                // ES 状态: green(健康), yellow(分片副本可用但未全部分配), red(部分数据不可用)
                String clusterName = response.containsKey("cluster_name") ? String.valueOf(response.get("cluster_name")) : "unknown";
                int activeShards = response.containsKey("active_shards") ? ((Number) response.get("active_shards")).intValue() : 0;
                return new DependencyHealthResponse("UP", String.format("Elasticsearch 连通正常，集群=%s，状态=%s，活跃分片=%d", clusterName, esStatus, activeShards));
            }
            return new DependencyHealthResponse("UP", "Elasticsearch 连通正常");
        } catch (Exception ex) {
            return new DependencyHealthResponse("DOWN", buildMessage("Elasticsearch 检查失败", ex));
        }
    }

    private DependencyHealthResponse checkTavily() {
        if (!webSearchProperties.isEnabled()) {
            return new DependencyHealthResponse("UNKNOWN", "联网搜索已全局禁用");
        }
        if (!tavilyProperties.isEnabled()) {
            return new DependencyHealthResponse("UNKNOWN", "Tavily 已禁用");
        }
        if (!StringUtils.hasText(tavilyProperties.getBaseUrl())) {
            return new DependencyHealthResponse("UNKNOWN", "Tavily 未配置地址");
        }
        if (!StringUtils.hasText(tavilyProperties.getApiKey())) {
            return new DependencyHealthResponse("UNKNOWN", "Tavily 未配置 API Key");
        }
        if (webSearchProvider != null && webSearchProvider.isAvailable()) {
            return new DependencyHealthResponse("UP", "Tavily 配置已就绪");
        }
        return new DependencyHealthResponse("DOWN", "Tavily Provider 未就绪");
    }

    private DependencyHealthResponse checkMineru() {
        OcrCapability capability = mineruParseService.describeCapability();
        if (!capability.enabled()) {
            return new DependencyHealthResponse("UNKNOWN", "MinerU 已禁用");
        }
        return capability.available()
                ? new DependencyHealthResponse("UP", capability.message())
                : new DependencyHealthResponse("DOWN", capability.message());
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
}
