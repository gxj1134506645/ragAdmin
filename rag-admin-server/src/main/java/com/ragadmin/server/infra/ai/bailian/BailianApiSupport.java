package com.ragadmin.server.infra.ai.bailian;

import com.ragadmin.server.common.exception.BusinessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;

public final class BailianApiSupport {

    private BailianApiSupport() {
    }

    public static RestClient buildRestClient(BailianProperties properties) {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new BusinessException("BAILIAN_API_KEY_MISSING", "百炼 API Key 未配置", HttpStatus.SERVICE_UNAVAILABLE);
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()));
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()));
        return RestClient.builder()
                .baseUrl(normalizeBaseUrl(properties.getBaseUrl()))
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public static String normalizeBaseUrl(String baseUrl) {
        String resolved = StringUtils.hasText(baseUrl) ? baseUrl.trim() : "https://dashscope.aliyuncs.com";
        resolved = resolved.endsWith("/") ? resolved.substring(0, resolved.length() - 1) : resolved;
        if (resolved.endsWith("/compatible-mode/v1")) {
            return resolved;
        }
        if (resolved.endsWith("/api/v1")) {
            return resolved.substring(0, resolved.length() - "/api/v1".length()) + "/compatible-mode/v1";
        }
        return resolved + "/compatible-mode/v1";
    }
}
