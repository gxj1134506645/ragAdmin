package com.ragadmin.server.infra.ai.chat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultConversationSummaryGeneratorTest {

    @Mock
    private ConversationChatClient conversationChatClient;

    private DefaultConversationSummaryGenerator generator;

    private PromptTemplateService promptTemplateService;

    @BeforeEach
    void setUp() {
        ChatMemoryProperties chatMemoryProperties = new ChatMemoryProperties();
        chatMemoryProperties.setSummaryMaxLength(50);
        promptTemplateService = new PromptTemplateService();
        generator = new DefaultConversationSummaryGenerator(conversationChatClient, chatMemoryProperties, promptTemplateService);
        ReflectionTestUtils.setField(generator, "summarySystemPromptTemplate", new ClassPathResource("prompts/ai/chat/conversation-summary-system.st"));
    }

    @Test
    void shouldUseModelSummaryWhenModelAvailable() {
        ConversationSummaryRequest request = new ConversationSummaryRequest(
                "conv-1",
                "BAILIAN",
                "qwen-plus",
                List.of(
                        new ChatPromptMessage("user", "请总结今天讨论的重点"),
                        new ChatPromptMessage("assistant", "重点是会话隔离和流式输出")
                ),
                null
        );
        when(conversationChatClient.chatEntity(eq("BAILIAN"), eq("qwen-plus"), anyList(), any()))
                .thenReturn(new com.ragadmin.server.infra.ai.chat.ConversationSummaryStructuredOutput("模型摘要结果"));

        ConversationSummaryResult result = generator.generate(request);

        assertEquals(ConversationSummarySource.MODEL, result.source());
        assertEquals("模型摘要结果", result.summaryText());
        assertEquals(2, result.inputMessageSize());
        verify(conversationChatClient).chatEntity(eq("BAILIAN"), eq("qwen-plus"), anyList(), any());
    }

    @Test
    void shouldFallbackToRuleSummaryWhenModelFails() {
        ConversationSummaryRequest request = new ConversationSummaryRequest(
                "conv-2",
                "BAILIAN",
                "qwen-plus",
                List.of(
                        new ChatPromptMessage("user", "第一条"),
                        new ChatPromptMessage("assistant", "第二条")
                ),
                null
        );
        when(conversationChatClient.chatEntity(eq("BAILIAN"), eq("qwen-plus"), anyList(), any()))
                .thenThrow(new RuntimeException("model error"));

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
                List.of(new ChatPromptMessage("user", "只做规则摘要")),
                null
        );

        ConversationSummaryResult result = generator.generate(request);

        assertEquals(ConversationSummarySource.RULE_BASED, result.source());
        assertTrue(result.summaryText().contains("用户：只做规则摘要"));
        verify(conversationChatClient, never()).chatEntity(eq("BAILIAN"), eq("qwen-plus"), anyList(), any());
    }

    @Test
    void shouldApplySummaryMaxLengthWhenFallbackSummaryTooLong() {
        ConversationSummaryRequest request = new ConversationSummaryRequest(
                "conv-4",
                null,
                null,
                List.of(new ChatPromptMessage("user", "0123456789012345678901234567890123456789")),
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
                List.of(new ChatPromptMessage("user", "你好")),
                null
        );
        when(conversationChatClient.chatEntity(eq("BAILIAN"), eq("qwen-plus"), anyList(), any()))
                .thenReturn(new com.ragadmin.server.infra.ai.chat.ConversationSummaryStructuredOutput("ok"));

        generator.generate(request);

        ArgumentCaptor<List<ChatPromptMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(conversationChatClient).chatEntity(eq("BAILIAN"), eq("qwen-plus"), captor.capture(), any());
        List<ChatPromptMessage> modelMessages = captor.getValue();
        assertEquals(2, modelMessages.size());
        assertEquals("system", modelMessages.get(0).role());
        assertEquals("user", modelMessages.get(1).role());
    }
}
