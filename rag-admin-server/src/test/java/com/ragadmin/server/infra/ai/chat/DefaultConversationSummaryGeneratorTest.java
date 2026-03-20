package com.ragadmin.server.infra.ai.chat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultConversationSummaryGeneratorTest {

    @Mock
    private ChatClientRegistry chatClientRegistry;

    @Mock
    private ChatModelClient chatModelClient;

    private DefaultConversationSummaryGenerator generator;

    @BeforeEach
    void setUp() {
        ChatMemoryProperties chatMemoryProperties = new ChatMemoryProperties();
        chatMemoryProperties.setSummaryMaxLength(50);
        generator = new DefaultConversationSummaryGenerator(chatClientRegistry, chatMemoryProperties);
    }

    @Test
    void shouldUseModelSummaryWhenModelAvailable() {
        ConversationSummaryRequest request = new ConversationSummaryRequest(
                "conv-1",
                "BAILIAN",
                "qwen-plus",
                List.of(
                        new ChatModelClient.ChatMessage("user", "请总结今天讨论的重点"),
                        new ChatModelClient.ChatMessage("assistant", "重点是会话隔离和流式输出")
                ),
                null
        );
        when(chatClientRegistry.getClient("BAILIAN")).thenReturn(chatModelClient);
        when(chatModelClient.chat(eq("qwen-plus"), anyList()))
                .thenReturn(new ChatModelClient.ChatCompletionResult("模型摘要结果", 10, 20));

        ConversationSummaryResult result = generator.generate(request);

        assertEquals(ConversationSummarySource.MODEL, result.source());
        assertEquals("模型摘要结果", result.summaryText());
        assertEquals(2, result.inputMessageSize());
        verify(chatModelClient).chat(eq("qwen-plus"), anyList());
    }

    @Test
    void shouldFallbackToRuleSummaryWhenModelFails() {
        ConversationSummaryRequest request = new ConversationSummaryRequest(
                "conv-2",
                "BAILIAN",
                "qwen-plus",
                List.of(
                        new ChatModelClient.ChatMessage("user", "第一条"),
                        new ChatModelClient.ChatMessage("assistant", "第二条")
                ),
                null
        );
        when(chatClientRegistry.getClient("BAILIAN")).thenReturn(chatModelClient);
        when(chatModelClient.chat(eq("qwen-plus"), anyList())).thenThrow(new RuntimeException("model error"));

        ConversationSummaryResult result = generator.generate(request);

        assertEquals(ConversationSummarySource.RULE_BASED, result.source());
        assertTrue(result.summaryText().contains("用户：第一条"));
        assertTrue(result.summaryText().contains("助手：第二条"));
    }

    @Test
    void shouldFallbackToRuleSummaryWhenModelConfigMissing() {
        ConversationSummaryRequest request = new ConversationSummaryRequest(
                "conv-3",
                null,
                null,
                List.of(new ChatModelClient.ChatMessage("user", "只做规则摘要")),
                null
        );

        ConversationSummaryResult result = generator.generate(request);

        assertEquals(ConversationSummarySource.RULE_BASED, result.source());
        assertTrue(result.summaryText().contains("用户：只做规则摘要"));
        verify(chatClientRegistry, never()).getClient(eq("BAILIAN"));
    }

    @Test
    void shouldApplySummaryMaxLengthWhenFallbackSummaryTooLong() {
        ConversationSummaryRequest request = new ConversationSummaryRequest(
                "conv-4",
                null,
                null,
                List.of(new ChatModelClient.ChatMessage("user", "0123456789012345678901234567890123456789")),
                10
        );

        ConversationSummaryResult result = generator.generate(request);

        assertEquals(ConversationSummarySource.RULE_BASED, result.source());
        assertEquals(10, result.summaryText().length());
    }

    @Test
    void shouldBuildTwoPromptMessagesWhenUsingModel() {
        ConversationSummaryRequest request = new ConversationSummaryRequest(
                "conv-5",
                "BAILIAN",
                "qwen-plus",
                List.of(new ChatModelClient.ChatMessage("user", "你好")),
                null
        );
        when(chatClientRegistry.getClient("BAILIAN")).thenReturn(chatModelClient);
        when(chatModelClient.chat(eq("qwen-plus"), anyList()))
                .thenReturn(new ChatModelClient.ChatCompletionResult("ok", 1, 1));

        generator.generate(request);

        ArgumentCaptor<List<ChatModelClient.ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(chatModelClient).chat(eq("qwen-plus"), captor.capture());
        List<ChatModelClient.ChatMessage> modelMessages = captor.getValue();
        assertEquals(2, modelMessages.size());
        assertEquals("system", modelMessages.get(0).role());
        assertEquals("user", modelMessages.get(1).role());
    }
}

