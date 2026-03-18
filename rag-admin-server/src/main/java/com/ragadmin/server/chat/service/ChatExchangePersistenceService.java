package com.ragadmin.server.chat.service;

import com.ragadmin.server.chat.dto.ChatReferenceResponse;
import com.ragadmin.server.chat.dto.ChatResponse;
import com.ragadmin.server.chat.dto.ChatUsageResponse;
import com.ragadmin.server.chat.entity.ChatAnswerReferenceEntity;
import com.ragadmin.server.chat.entity.ChatMessageEntity;
import com.ragadmin.server.chat.entity.ChatSessionEntity;
import com.ragadmin.server.chat.mapper.ChatAnswerReferenceMapper;
import com.ragadmin.server.chat.mapper.ChatMessageMapper;
import com.ragadmin.server.document.entity.DocumentEntity;
import com.ragadmin.server.document.mapper.DocumentMapper;
import com.ragadmin.server.retrieval.service.RetrievalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ChatExchangePersistenceService {

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private ChatAnswerReferenceMapper chatAnswerReferenceMapper;

    @Autowired
    private DocumentMapper documentMapper;

    @Autowired
    private RetrievalService retrievalService;

    @Transactional
    public ChatResponse persistExchange(
            ChatSessionEntity session,
            Long userId,
            String question,
            String answer,
            Long modelId,
            Integer promptTokens,
            Integer completionTokens,
            int latencyMs,
            RetrievalService.RetrievalResult retrievalResult
    ) {
        ChatMessageEntity message = new ChatMessageEntity();
        message.setSessionId(session.getId());
        message.setUserId(userId);
        message.setMessageType("RAG");
        message.setQuestionText(question);
        message.setAnswerText(answer);
        message.setModelId(modelId);
        message.setPromptTokens(promptTokens);
        message.setCompletionTokens(completionTokens);
        message.setLatencyMs(latencyMs);
        chatMessageMapper.insert(message);

        for (int i = 0; i < retrievalResult.chunks().size(); i++) {
            var chunk = retrievalResult.chunks().get(i);
            ChatAnswerReferenceEntity ref = new ChatAnswerReferenceEntity();
            ref.setMessageId(message.getId());
            ref.setChunkId(chunk.chunk().getId());
            ref.setScore(BigDecimal.valueOf(chunk.score()));
            ref.setRankNo(i + 1);
            chatAnswerReferenceMapper.insert(ref);
        }

        List<Long> documentIds = retrievalResult.chunks().stream()
                .map(item -> item.chunk().getDocumentId())
                .distinct()
                .toList();
        Map<Long, String> documentNameResolver = documentIds.isEmpty()
                ? Map.of()
                : documentMapper.selectBatchIds(documentIds)
                .stream()
                .collect(Collectors.toMap(DocumentEntity::getId, DocumentEntity::getDocName));

        List<ChatReferenceResponse> references = retrievalService.toReferenceResponses(
                retrievalResult.chunks(),
                documentId -> documentNameResolver.get(documentId)
        );

        return new ChatResponse(
                message.getId(),
                answer,
                references,
                new ChatUsageResponse(promptTokens, completionTokens)
        );
    }
}
