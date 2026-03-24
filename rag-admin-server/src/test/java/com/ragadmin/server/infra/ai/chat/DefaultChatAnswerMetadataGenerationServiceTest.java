package com.ragadmin.server.infra.ai.chat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultChatAnswerMetadataGenerationServiceTest {

    @Mock
    private ConversationChatClient conversationChatClient;

    private ChatAnswerMetadataProperties properties;

    private DefaultChatAnswerMetadataGenerationService service;

    private PromptTemplateService promptTemplateService;

    @BeforeEach
    void setUp() {
        properties = new ChatAnswerMetadataProperties();
        properties.setLogMetadata(false);
        promptTemplateService = new PromptTemplateService();
        service = new DefaultChatAnswerMetadataGenerationService(conversationChatClient, properties, promptTemplateService);
        ReflectionTestUtils.setField(service, "metadataSystemPromptTemplate", new ClassPathResource("prompts/ai/chat/answer-metadata-system.st"));
        ReflectionTestUtils.setField(service, "metadataUserPromptTemplate", new ClassPathResource("prompts/ai/chat/answer-metadata-user.st"));
    }

    @Test
    void shouldUseStructuredMetadataWhenModelReturnsValidDecision() {
        ChatAnswerMetadataGenerationRequest request = new ChatAnswerMetadataGenerationRequest(
                "BAILIAN",
                "qwen-plus",
                "制度里是否要求提交周报？",
                "制度要求按时提交周报。",
                1
        );
        when(conversationChatClient.chatEntity(eq("BAILIAN"), eq("qwen-plus"), anyList(), eq(ChatAnswerMetadataStructuredOutput.class)))
                .thenReturn(new ChatAnswerMetadataStructuredOutput("HIGH", Boolean.TRUE, Boolean.FALSE, "回答与知识库证据一致"));

        ChatAnswerMetadata metadata = service.generate(request);

        assertEquals("HIGH", metadata.confidence());
        assertTrue(metadata.hasKnowledgeBaseEvidence());
        assertFalse(metadata.needFollowUp());
    }

    @Test
    void shouldFallbackToRuleBasedMetadataWhenModelGenerationFails() {
        ChatAnswerMetadataGenerationRequest request = new ChatAnswerMetadataGenerationRequest(
                "OLLAMA",
                "qwen2.5:7b",
                "制度里是否要求提交周报？",
                "制度要求按时提交周报。",
                1
        );
        when(conversationChatClient.chatEntity(eq("OLLAMA"), eq("qwen2.5:7b"), anyList(), eq(ChatAnswerMetadataStructuredOutput.class)))
                .thenThrow(new RuntimeException("metadata error"));

        ChatAnswerMetadata metadata = service.generate(request);

        assertEquals("MEDIUM", metadata.confidence());
        assertTrue(metadata.hasKnowledgeBaseEvidence());
        assertFalse(metadata.needFollowUp());
    }

    @Test
    void shouldFallbackToRuleBasedMetadataWhenGenerationIsDisabled() {
        properties.setEnabled(false);
        ChatAnswerMetadataGenerationRequest request = new ChatAnswerMetadataGenerationRequest(
                "BAILIAN",
                "qwen-max",
                "今天的安排是什么？",
                "建议先处理高优先级事项，再安排例行检查。",
                0
        );

        ChatAnswerMetadata metadata = service.generate(request);

        assertEquals("LOW", metadata.confidence());
        assertFalse(metadata.hasKnowledgeBaseEvidence());
        assertFalse(metadata.needFollowUp());
        verifyNoInteractions(conversationChatClient);
    }
}
