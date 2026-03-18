package com.ragadmin.server.infra.ai.bailian;

import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.infra.ai.SpringAiModelFactory;
import com.ragadmin.server.infra.ai.chat.BailianChatClient;
import com.ragadmin.server.infra.ai.chat.ChatModelClient;
import com.ragadmin.server.infra.ai.embedding.BailianEmbeddingClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BailianApiClientInitializationTest {

    @Test
    void shouldNotRequireApiKeyAtBeanInitializationTimeForChatClient() {
        BailianProperties properties = new BailianProperties();
        SpringAiModelFactory springAiModelFactory = new SpringAiModelFactory(List.of(new BailianModelFactory(properties)));

        BailianChatClient client = new BailianChatClient(springAiModelFactory);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> client.chat("qwen-max", List.of(new ChatModelClient.ChatMessage("user", "ping")))
        );

        assertEquals("BAILIAN_API_KEY_MISSING", exception.getCode());
    }

    @Test
    void shouldNotRequireApiKeyAtBeanInitializationTimeForEmbeddingClient() {
        BailianProperties properties = new BailianProperties();
        SpringAiModelFactory springAiModelFactory = new SpringAiModelFactory(List.of(new BailianModelFactory(properties)));

        BailianEmbeddingClient client = new BailianEmbeddingClient(springAiModelFactory);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> client.embed("text-embedding-v3", List.of("ping"))
        );

        assertEquals("BAILIAN_API_KEY_MISSING", exception.getCode());
    }

    @Test
    void shouldRejectMultimodalEmbeddingModelBeforeCallingDashScope() {
        BailianProperties properties = new BailianProperties();
        SpringAiModelFactory springAiModelFactory = new SpringAiModelFactory(List.of(new BailianModelFactory(properties)));

        BailianEmbeddingClient client = new BailianEmbeddingClient(springAiModelFactory);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> client.embed("qwen2.5-vl-embedding", List.of("ping"))
        );

        assertEquals("EMBEDDING_MODEL_UNSUPPORTED", exception.getCode());
    }
}
