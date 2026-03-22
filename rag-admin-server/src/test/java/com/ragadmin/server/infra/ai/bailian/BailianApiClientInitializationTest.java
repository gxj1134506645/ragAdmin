package com.ragadmin.server.infra.ai.bailian;

import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.infra.ai.SpringAiModelFactory;
import com.ragadmin.server.infra.ai.chat.ChatPromptMessage;
import com.ragadmin.server.infra.ai.chat.ChatClientAdvisorProperties;
import com.ragadmin.server.infra.ai.chat.SpringAiConversationChatClient;
import com.ragadmin.server.infra.ai.embedding.BailianEmbeddingClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BailianApiClientInitializationTest {

    @Test
    void shouldNotRequireApiKeyAtBeanInitializationTimeForChatClient() {
        BailianProperties properties = new BailianProperties();
        SpringAiModelFactory springAiModelFactory = new SpringAiModelFactory(List.of(new BailianModelFactory(properties)));
        SpringAiConversationChatClient client = new SpringAiConversationChatClient();
        ReflectionTestUtils.setField(client, "springAiModelFactory", springAiModelFactory);
        ReflectionTestUtils.setField(client, "chatClientAdvisorProperties", new ChatClientAdvisorProperties());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> client.chat("BAILIAN", "qwen-max", List.of(new ChatPromptMessage("user", "ping")))
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
