package com.ragadmin.server.retrieval.service;

import com.ragadmin.server.infra.ai.chat.ChatCompletionResult;
import com.ragadmin.server.infra.ai.chat.ConversationChatClient;
import com.ragadmin.server.infra.ai.chat.PromptTemplateService;
import com.ragadmin.server.model.service.ModelService;
import com.ragadmin.server.model.service.ModelService.ChatModelDescriptor;
import com.ragadmin.server.retrieval.config.QueryRewritingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueryRewritingServiceTest {

    @Mock
    private ConversationChatClient conversationChatClient;

    @Mock
    private PromptTemplateService promptTemplateService;

    @Mock
    private ModelService modelService;

    private QueryRewritingService queryRewritingService;

    @BeforeEach
    void setUp() {
        queryRewritingService = new QueryRewritingService();
        ReflectionTestUtils.setField(queryRewritingService, "conversationChatClient", conversationChatClient);
        ReflectionTestUtils.setField(queryRewritingService, "promptTemplateService", promptTemplateService);
        ReflectionTestUtils.setField(queryRewritingService, "modelService", modelService);

        QueryRewritingProperties properties = new QueryRewritingProperties();
        properties.setMultiQueryCount(3);
        ReflectionTestUtils.setField(queryRewritingService, "properties", properties);

        ReflectionTestUtils.setField(queryRewritingService, "multiQueryPromptResource", new ByteArrayResource("multi-query prompt".getBytes()));
        ReflectionTestUtils.setField(queryRewritingService, "hydePromptResource", new ByteArrayResource("hyde prompt".getBytes()));
    }

    @Test
    void shouldReturnOriginalQueryWhenModeNone() {
        QueryRewritingService.RewrittenQueries result = queryRewritingService.rewrite("测试问题", "NONE");

        assertEquals(1, result.queries().size());
        assertEquals("测试问题", result.queries().getFirst());
        verifyNoInteractions(modelService);
    }

    @Test
    void shouldReturnOriginalQueryWhenModeIsNull() {
        QueryRewritingService.RewrittenQueries result = queryRewritingService.rewrite("测试问题", null);

        assertEquals(1, result.queries().size());
        assertEquals("测试问题", result.queries().getFirst());
    }

    @Test
    void shouldGenerateMultiQueries() {
        when(modelService.findDefaultChatModelDescriptor())
                .thenReturn(new ChatModelDescriptor(1L, "qwen-max", "BAILIAN", "百炼"));
        when(promptTemplateService.load(any())).thenReturn("system prompt");
        when(conversationChatClient.chat(eq("BAILIAN"), eq("qwen-max"), any()))
                .thenReturn(new ChatCompletionResult("1. 什么是RAG系统\n2. RAG系统怎么工作\n3. RAG的应用场景", null, null));

        QueryRewritingService.RewrittenQueries result = queryRewritingService.rewrite("RAG系统是什么", "MULTI_QUERY");

        assertTrue(result.queries().size() >= 2);
        assertEquals("RAG系统是什么", result.queries().getFirst());
        verify(conversationChatClient).chat(eq("BAILIAN"), eq("qwen-max"), any());
    }

    @Test
    void shouldGenerateHydeQuery() {
        when(modelService.findDefaultChatModelDescriptor())
                .thenReturn(new ChatModelDescriptor(1L, "qwen-max", "BAILIAN", "百炼"));
        when(promptTemplateService.load(any())).thenReturn("system prompt");
        when(conversationChatClient.chat(eq("BAILIAN"), eq("qwen-max"), any()))
                .thenReturn(new ChatCompletionResult("RAG系统是一种结合检索和生成的AI架构...", null, null));

        QueryRewritingService.RewrittenQueries result = queryRewritingService.rewrite("RAG系统是什么", "HYDE");

        assertEquals(2, result.queries().size());
        assertEquals("RAG系统是什么", result.queries().getFirst());
        assertNotNull(result.queries().get(1));
    }

    @Test
    void shouldCombineMultiQueryAndHyde() {
        when(modelService.findDefaultChatModelDescriptor())
                .thenReturn(new ChatModelDescriptor(1L, "qwen-max", "BAILIAN", "百炼"));
        when(promptTemplateService.load(any())).thenReturn("system prompt");

        ChatCompletionResult multiResult = new ChatCompletionResult("1. 查询改写方法\n2. 查询优化技巧", null, null);
        ChatCompletionResult hydeResult = new ChatCompletionResult("查询改写是指对用户原始查询进行转换...", null, null);

        when(conversationChatClient.chat(eq("BAILIAN"), eq("qwen-max"), any()))
                .thenReturn(multiResult)
                .thenReturn(hydeResult);

        QueryRewritingService.RewrittenQueries result = queryRewritingService.rewrite("什么是查询改写", "MULTI_QUERY_AND_HYDE");

        assertTrue(result.queries().size() >= 3);
    }

    @Test
    void shouldFallBackToOriginalWhenNoModel() {
        when(modelService.findDefaultChatModelDescriptor()).thenReturn(null);

        QueryRewritingService.RewrittenQueries result = queryRewritingService.rewrite("测试问题", "MULTI_QUERY");

        assertEquals(1, result.queries().size());
        assertEquals("测试问题", result.queries().getFirst());
    }

    @Test
    void shouldFallBackToOriginalWhenLlmFails() {
        when(modelService.findDefaultChatModelDescriptor())
                .thenReturn(new ChatModelDescriptor(1L, "qwen-max", "BAILIAN", "百炼"));
        when(promptTemplateService.load(any())).thenReturn("system prompt");
        when(conversationChatClient.chat(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("模型调用失败"));

        QueryRewritingService.RewrittenQueries result = queryRewritingService.rewrite("测试问题", "MULTI_QUERY");

        assertEquals(1, result.queries().size());
        assertEquals("测试问题", result.queries().getFirst());
    }
}
