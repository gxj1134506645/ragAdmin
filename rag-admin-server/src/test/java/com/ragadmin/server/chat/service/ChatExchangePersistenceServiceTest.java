package com.ragadmin.server.chat.service;

import com.ragadmin.server.chat.dto.ChatAnswerMetadataResponse;
import com.ragadmin.server.chat.dto.ChatReferenceResponse;
import com.ragadmin.server.chat.dto.ChatResponse;
import com.ragadmin.server.chat.entity.ChatAnswerReferenceEntity;
import com.ragadmin.server.chat.entity.ChatMessageEntity;
import com.ragadmin.server.chat.entity.ChatSessionEntity;
import com.ragadmin.server.chat.mapper.ChatAnswerReferenceMapper;
import com.ragadmin.server.chat.mapper.ChatMessageMapper;
import com.ragadmin.server.document.mapper.DocumentMapper;
import com.ragadmin.server.infra.ai.chat.ChatAnswerMetadata;
import com.ragadmin.server.retrieval.service.RetrievalService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatExchangePersistenceServiceTest {

    @Mock
    private ChatMessageMapper chatMessageMapper;

    @Mock
    private ChatAnswerReferenceMapper chatAnswerReferenceMapper;

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private RetrievalService retrievalService;

    @InjectMocks
    private ChatExchangePersistenceService chatExchangePersistenceService;

    @Test
    void shouldPersistAnswerMetadataIntoChatMessageAndResponse() {
        ChatSessionEntity session = new ChatSessionEntity();
        session.setId(31L);

        ChatAnswerMetadata answerMetadata = new ChatAnswerMetadata("HIGH", true, false);
        RetrievalService.RetrievalResult retrievalResult = new RetrievalService.RetrievalResult(List.of(), "");

        when(chatMessageMapper.insert(any(ChatMessageEntity.class))).thenAnswer(invocation -> {
            ChatMessageEntity entity = invocation.getArgument(0);
            entity.setId(801L);
            return 1;
        });
        when(retrievalService.toReferenceResponses(eq(List.of()), any())).thenReturn(List.of());

        ChatResponse response = chatExchangePersistenceService.persistExchange(
                session,
                100L,
                "请总结制度要点",
                "制度要求按时提交周报。",
                901L,
                120,
                30,
                88,
                answerMetadata,
                retrievalResult
        );

        ArgumentCaptor<ChatMessageEntity> messageCaptor = ArgumentCaptor.forClass(ChatMessageEntity.class);
        verify(chatMessageMapper).insert(messageCaptor.capture());
        ChatMessageEntity inserted = messageCaptor.getValue();
        assertEquals("HIGH", inserted.getAnswerConfidence());
        assertEquals(true, inserted.getHasKnowledgeBaseEvidence());
        assertEquals(false, inserted.getNeedFollowUp());
        verify(chatAnswerReferenceMapper, never()).insert(any(ChatAnswerReferenceEntity.class));

        assertEquals(801L, response.messageId());
        assertEquals("text/markdown", response.answerContentType());
        assertNotNull(response.metadata());
        ChatAnswerMetadataResponse metadata = response.metadata();
        assertEquals("HIGH", metadata.confidence());
        assertEquals(true, metadata.hasKnowledgeBaseEvidence());
        assertEquals(false, metadata.needFollowUp());
    }

    @Test
    void shouldKeepResponseMetadataNullWhenAnswerMetadataMissing() {
        ChatSessionEntity session = new ChatSessionEntity();
        session.setId(32L);

        when(chatMessageMapper.insert(any(ChatMessageEntity.class))).thenAnswer(invocation -> {
            ChatMessageEntity entity = invocation.getArgument(0);
            entity.setId(802L);
            return 1;
        });
        when(retrievalService.toReferenceResponses(eq(List.of()), any())).thenReturn(List.of(
                new ChatReferenceResponse(1L, 2L, "制度.md", 3L, 1, 0.9D, "片段")
        ));

        ChatResponse response = chatExchangePersistenceService.persistExchange(
                session,
                100L,
                "没有元数据怎么办",
                "正常返回即可。",
                902L,
                50,
                10,
                30,
                null,
                new RetrievalService.RetrievalResult(List.of(), "")
        );

        assertEquals(802L, response.messageId());
        assertEquals("text/markdown", response.answerContentType());
        assertEquals(null, response.metadata());
    }
}
