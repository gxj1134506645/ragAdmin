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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultChatExecutionPlanningServiceTest {

    @Mock
    private ConversationChatClient conversationChatClient;

    private ChatExecutionPlanningProperties planningProperties;

    private DefaultChatExecutionPlanningService planningService;

    private PromptTemplateService promptTemplateService;

    @BeforeEach
    void setUp() {
        planningProperties = new ChatExecutionPlanningProperties();
        planningProperties.setLogPlan(false);
        promptTemplateService = new PromptTemplateService();
        planningService = new DefaultChatExecutionPlanningService(conversationChatClient, planningProperties, promptTemplateService);
        ReflectionTestUtils.setField(planningService, "planningSystemPromptTemplate", new ClassPathResource("prompts/ai/chat/execution-planning-system.st"));
        ReflectionTestUtils.setField(planningService, "planningUserPromptTemplate", new ClassPathResource("prompts/ai/chat/execution-planning-user.st"));
    }

    @Test
    void shouldUseStructuredPlanWhenModelReturnsValidDecision() {
        ChatExecutionPlanningRequest request = new ChatExecutionPlanningRequest(
                "BAILIAN",
                "qwen-plus",
                "最近智能体应用有什么值得关注的实践？",
                true,
                true,
                2,
                true
        );
        when(conversationChatClient.chatEntity(eq("BAILIAN"), eq("qwen-plus"), anyList(), eq(ChatExecutionPlanStructuredOutput.class)))
                .thenReturn(new ChatExecutionPlanStructuredOutput(
                        "KNOWLEDGE_BASE_AND_WEB_SEARCH",
                        Boolean.TRUE,
                        "智能体应用落地 最佳实践",
                        Boolean.TRUE,
                        "智能体 行业动态",
                        "优先参考知识库并补充联网动态"
                ));

        ChatExecutionPlan plan = planningService.plan(request);

        assertEquals("KNOWLEDGE_BASE_AND_WEB_SEARCH", plan.intent());
        assertTrue(plan.needRetrieval());
        assertEquals("智能体应用落地 最佳实践", plan.retrievalQuery());
        assertTrue(plan.needWebSearch());
        assertEquals("智能体 行业动态", plan.webSearchQuery());
        assertEquals("MODEL", plan.source());
    }

    @Test
    void shouldFallbackToRuleBasedPlanWhenModelPlanningFails() {
        ChatExecutionPlanningRequest request = new ChatExecutionPlanningRequest(
                "OLLAMA",
                "qwen2.5:7b",
                "请总结制度里的周报要求",
                true,
                false,
                1,
                false
        );
        when(conversationChatClient.chatEntity(eq("OLLAMA"), eq("qwen2.5:7b"), anyList(), eq(ChatExecutionPlanStructuredOutput.class)))
                .thenThrow(new RuntimeException("plan error"));

        ChatExecutionPlan plan = planningService.plan(request);

        assertEquals("KNOWLEDGE_BASE_QA", plan.intent());
        assertTrue(plan.needRetrieval());
        assertEquals("请总结制度里的周报要求", plan.retrievalQuery());
        assertFalse(plan.needWebSearch());
        assertEquals("", plan.webSearchQuery());
        assertEquals("RULE_BASED", plan.source());
    }

    @Test
    void shouldMaskModelDecisionByAvailableCapabilities() {
        ChatExecutionPlanningRequest request = new ChatExecutionPlanningRequest(
                "BAILIAN",
                "qwen-max",
                "给我解释下架构思路",
                false,
                false,
                0,
                false
        );

        ChatExecutionPlan plan = planningService.plan(request);

        assertEquals("GENERAL_QA", plan.intent());
        assertFalse(plan.needRetrieval());
        assertEquals("", plan.retrievalQuery());
        assertFalse(plan.needWebSearch());
        assertEquals("", plan.webSearchQuery());
    }

    @Test
    void shouldFallbackToRuleBasedPlanWhenPlanningIsDisabled() {
        planningProperties.setEnabled(false);
        ChatExecutionPlanningRequest request = new ChatExecutionPlanningRequest(
                "BAILIAN",
                "qwen-max",
                "请帮我总结知识库里的制度",
                true,
                true,
                2,
                true
        );

        ChatExecutionPlan plan = planningService.plan(request);

        assertEquals("KNOWLEDGE_BASE_AND_WEB_SEARCH", plan.intent());
        assertTrue(plan.needRetrieval());
        assertTrue(plan.needWebSearch());
        assertEquals("RULE_BASED", plan.source());
        verifyNoInteractions(conversationChatClient);
    }
}
