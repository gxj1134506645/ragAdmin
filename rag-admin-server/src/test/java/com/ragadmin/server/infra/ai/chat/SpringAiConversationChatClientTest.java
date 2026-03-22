package com.ragadmin.server.infra.ai.chat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class SpringAiConversationChatClientTest {

    private SpringAiConversationChatClient chatClient;

    private ChatClientAdvisorProperties chatClientAdvisorProperties;

    @BeforeEach
    void setUp() {
        chatClient = new SpringAiConversationChatClient();
        chatClientAdvisorProperties = new ChatClientAdvisorProperties();
        ReflectionTestUtils.setField(chatClient, "chatMemory", mock(ChatMemory.class));
        ReflectionTestUtils.setField(chatClient, "chatClientAdvisorProperties", chatClientAdvisorProperties);
    }

    @Test
    void shouldBuildMemoryAdvisorOnlyWhenSimpleLoggerAdvisorDisabled() {
        chatClientAdvisorProperties.setSimpleLoggerAdvisorEnabled(false);

        List<Advisor> advisors = chatClient.buildDefaultAdvisors();

        assertEquals(1, advisors.size());
        assertInstanceOf(MessageChatMemoryAdvisor.class, advisors.getFirst());
    }

    @Test
    void shouldNotBuildMemoryAdvisorForStatelessStructuredOutputClient() {
        chatClientAdvisorProperties.setSimpleLoggerAdvisorEnabled(true);

        List<Advisor> advisors = chatClient.buildStatelessAdvisors();

        assertEquals(1, advisors.size());
        assertInstanceOf(SimpleLoggerAdvisor.class, advisors.getFirst());
        assertFalse(advisors.stream().anyMatch(MessageChatMemoryAdvisor.class::isInstance));
    }

    @Test
    void shouldBuildSimpleLoggerAdvisorAfterMemoryAdvisorWhenEnabled() {
        chatClientAdvisorProperties.setSimpleLoggerAdvisorEnabled(true);

        List<Advisor> advisors = chatClient.buildDefaultAdvisors();

        assertEquals(2, advisors.size());
        assertInstanceOf(MessageChatMemoryAdvisor.class, advisors.get(0));
        assertInstanceOf(SimpleLoggerAdvisor.class, advisors.get(1));
        assertTrue(advisors.get(1).getOrder() > advisors.get(0).getOrder());
    }

    @Test
    void shouldFormatRequestWithTruncatedMessagesAndContextKeysOnly() {
        chatClientAdvisorProperties.setSimpleLoggerMaxTextLength(10);
        chatClientAdvisorProperties.setSimpleLoggerRequestBodyEnabled(true);

        ChatClientRequest request = new ChatClientRequest(
                new Prompt(List.of(
                        new SystemMessage("系统提示需要被裁剪，因为内容非常长非常长"),
                        new UserMessage("用户问题也需要被裁剪，并且要归一化\n换行")
                )),
                Map.of(
                        "conversationId", "secret-conversation-id",
                        "tenantCode", "internal-tenant"
                )
        );

        String formatted = chatClient.formatChatClientRequest(request);

        assertTrue(formatted.contains("messageCount=2"));
        assertTrue(formatted.contains("contextKeys=[conversationId, tenantCode]"));
        assertTrue(formatted.contains("truncated,total="));
        assertFalse(formatted.contains("secret-conversation-id"));
        assertFalse(formatted.contains("internal-tenant"));
    }

    @Test
    void shouldFormatResponseWithTruncatedAssistantTextAndUsageSummary() {
        chatClientAdvisorProperties.setSimpleLoggerMaxTextLength(18);
        chatClientAdvisorProperties.setSimpleLoggerResponseBodyEnabled(true);

        ChatResponse response = ChatResponse.builder()
                .metadata(ChatResponseMetadata.builder()
                        .model("qwen-plus")
                        .usage(new Usage() {
                            @Override
                            public Integer getPromptTokens() {
                                return 12;
                            }

                            @Override
                            public Integer getCompletionTokens() {
                                return 34;
                            }

                            @Override
                            public Object getNativeUsage() {
                                return null;
                            }
                        })
                        .build())
                .generations(List.of(new Generation(new AssistantMessage("这是一个非常长非常长的回答内容，用于验证日志裁剪是否生效"))))
                .build();

        String formatted = chatClient.formatChatResponse(response);

        assertTrue(formatted.contains("resultCount=1"));
        assertTrue(formatted.contains("model=qwen-plus"));
        assertTrue(formatted.contains("promptTokens=12"));
        assertTrue(formatted.contains("completionTokens=34"));
        assertTrue(formatted.contains("totalTokens=46"));
        assertTrue(formatted.contains("truncated,total="));
        assertTrue(formatted.contains("sha256="));
    }

    @Test
    void shouldHideRequestBodyWhenBodyLoggingDisabled() {
        chatClientAdvisorProperties.setSimpleLoggerRequestBodyEnabled(false);

        ChatClientRequest request = new ChatClientRequest(
                new Prompt(List.of(new UserMessage("这里是一段不应该直接出现在日志里的请求正文"))),
                Map.of("conversationId", "secret-conversation-id")
        );

        String formatted = chatClient.formatChatClientRequest(request);

        assertTrue(formatted.contains("(hidden,len="));
        assertTrue(formatted.contains("sha256="));
        assertFalse(formatted.contains("这里是一段不应该直接出现在日志里的请求正文"));
        assertFalse(formatted.contains("secret-conversation-id"));
    }

    @Test
    void shouldHideResponseBodyWhenBodyLoggingDisabled() {
        chatClientAdvisorProperties.setSimpleLoggerResponseBodyEnabled(false);

        ChatResponse response = ChatResponse.builder()
                .metadata(ChatResponseMetadata.builder().model("qwen-plus").build())
                .generations(List.of(new Generation(new AssistantMessage("这里是一段不应该直接出现在日志里的回答正文"))))
                .build();

        String formatted = chatClient.formatChatResponse(response);

        assertTrue(formatted.contains("(hidden,len="));
        assertTrue(formatted.contains("sha256="));
        assertFalse(formatted.contains("这里是一段不应该直接出现在日志里的回答正文"));
    }
}
