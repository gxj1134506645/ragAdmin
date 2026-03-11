package com.ragadmin.server.infra.ai.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.infra.ai.bailian.BailianApiSupport;
import com.ragadmin.server.infra.ai.bailian.BailianProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class BailianChatClient implements ChatModelClient {

    private final RestClient restClient;

    public BailianChatClient(BailianProperties bailianProperties) {
        this.restClient = BailianApiSupport.buildRestClient(bailianProperties);
    }

    @Override
    public boolean supports(String providerCode) {
        return "BAILIAN".equalsIgnoreCase(providerCode);
    }

    @Override
    public ChatCompletionResult chat(String modelCode, List<ChatMessage> messages) {
        BailianChatCompletionResponse response = restClient.post()
                .uri("/chat/completions")
                .body(new BailianChatCompletionRequest(modelCode, messages))
                .retrieve()
                .body(BailianChatCompletionResponse.class);
        if (response == null
                || response.choices() == null
                || response.choices().isEmpty()
                || response.choices().getFirst().message() == null
                || response.choices().getFirst().message().content() == null) {
            throw new BusinessException("CHAT_FAILED", "百炼聊天返回为空", HttpStatus.BAD_GATEWAY);
        }
        BailianChatUsage usage = response.usage();
        return new ChatCompletionResult(
                response.choices().getFirst().message().content(),
                usage == null ? null : usage.promptTokens(),
                usage == null ? null : usage.completionTokens()
        );
    }

    private record BailianChatCompletionRequest(String model, List<ChatMessage> messages) {
    }

    private record BailianChatCompletionResponse(List<BailianChatChoice> choices, BailianChatUsage usage) {
    }

    private record BailianChatChoice(BailianChatResponseMessage message) {
    }

    private record BailianChatResponseMessage(String role, String content) {
    }

    private record BailianChatUsage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("completion_tokens") Integer completionTokens
    ) {
    }
}
