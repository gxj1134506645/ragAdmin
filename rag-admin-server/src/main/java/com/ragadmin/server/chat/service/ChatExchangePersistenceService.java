package com.ragadmin.server.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ragadmin.server.chat.ChatContentTypes;
import com.ragadmin.server.chat.dto.ChatAnswerMetadataResponse;
import com.ragadmin.server.chat.dto.ChatReferenceResponse;
import com.ragadmin.server.chat.dto.ChatResponse;
import com.ragadmin.server.chat.dto.ChatUsageResponse;
import com.ragadmin.server.chat.entity.ChatAnswerReferenceEntity;
import com.ragadmin.server.chat.entity.ChatFeedbackEntity;
import com.ragadmin.server.chat.entity.ChatMessageEntity;
import com.ragadmin.server.chat.entity.ChatSessionEntity;
import com.ragadmin.server.chat.mapper.ChatAnswerReferenceMapper;
import com.ragadmin.server.chat.mapper.ChatFeedbackMapper;
import com.ragadmin.server.chat.mapper.ChatMessageMapper;
import com.ragadmin.server.document.entity.DocumentEntity;
import com.ragadmin.server.document.mapper.DocumentMapper;
import com.ragadmin.server.infra.ai.chat.ChatAnswerMetadata;
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
    private ChatFeedbackMapper chatFeedbackMapper;

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
            ChatAnswerMetadata answerMetadata,
            RetrievalService.RetrievalResult retrievalResult
    ) {
        ChatMessageEntity message = new ChatMessageEntity();
        message.setSessionId(session.getId());
        message.setUserId(userId);
        message.setMessageType("RAG");
        message.setQuestionText(question);
        message.setAnswerText(answer);
        message.setModelId(modelId);
        message.setAnswerConfidence(answerMetadata == null ? null : answerMetadata.confidence());
        message.setHasKnowledgeBaseEvidence(answerMetadata == null ? null : answerMetadata.hasKnowledgeBaseEvidence());
        message.setNeedFollowUp(answerMetadata == null ? null : answerMetadata.needFollowUp());
        message.setPromptTokens(promptTokens);
        message.setCompletionTokens(completionTokens);
        message.setLatencyMs(latencyMs);
        chatMessageMapper.insert(message);

        persistReferences(message.getId(), retrievalResult);
        return buildChatResponse(message.getId(), answer, promptTokens, completionTokens, answerMetadata, retrievalResult);
    }

    /**
     * 重新生成只允许覆盖既有消息，避免同一轮问答在会话里落出重复记录。
     */
    @Transactional
    public ChatResponse replaceExchange(
            ChatMessageEntity message,
            String answer,
            Long modelId,
            Integer promptTokens,
            Integer completionTokens,
            int latencyMs,
            ChatAnswerMetadata answerMetadata,
            RetrievalService.RetrievalResult retrievalResult
    ) {
        message.setAnswerText(answer);
        message.setModelId(modelId);
        message.setAnswerConfidence(answerMetadata == null ? null : answerMetadata.confidence());
        message.setHasKnowledgeBaseEvidence(answerMetadata == null ? null : answerMetadata.hasKnowledgeBaseEvidence());
        message.setNeedFollowUp(answerMetadata == null ? null : answerMetadata.needFollowUp());
        message.setPromptTokens(promptTokens);
        message.setCompletionTokens(completionTokens);
        message.setLatencyMs(latencyMs);
        chatMessageMapper.updateById(message);

        chatAnswerReferenceMapper.delete(new LambdaQueryWrapper<ChatAnswerReferenceEntity>()
                .eq(ChatAnswerReferenceEntity::getMessageId, message.getId()));
        chatFeedbackMapper.delete(new LambdaQueryWrapper<ChatFeedbackEntity>()
                .eq(ChatFeedbackEntity::getMessageId, message.getId()));
        persistReferences(message.getId(), retrievalResult);
        return buildChatResponse(message.getId(), answer, promptTokens, completionTokens, answerMetadata, retrievalResult);
    }

    private void persistReferences(Long messageId, RetrievalService.RetrievalResult retrievalResult) {
        for (int i = 0; i < retrievalResult.chunks().size(); i++) {
            var chunk = retrievalResult.chunks().get(i);
            ChatAnswerReferenceEntity ref = new ChatAnswerReferenceEntity();
            ref.setMessageId(messageId);
            ref.setChunkId(chunk.chunk().getId());
            ref.setScore(BigDecimal.valueOf(chunk.score()));
            ref.setRankNo(i + 1);
            chatAnswerReferenceMapper.insert(ref);
        }
    }

    private ChatResponse buildChatResponse(
            Long messageId,
            String answer,
            Integer promptTokens,
            Integer completionTokens,
            ChatAnswerMetadata answerMetadata,
            RetrievalService.RetrievalResult retrievalResult
    ) {
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
                messageId,
                answer,
                ChatContentTypes.MARKDOWN,
                references,
                new ChatUsageResponse(promptTokens, completionTokens),
                toMetadataResponse(answerMetadata)
        );
    }

    private ChatAnswerMetadataResponse toMetadataResponse(ChatAnswerMetadata answerMetadata) {
        if (answerMetadata == null) {
            return null;
        }
        return new ChatAnswerMetadataResponse(
                answerMetadata.confidence(),
                answerMetadata.hasKnowledgeBaseEvidence(),
                answerMetadata.needFollowUp()
        );
    }
}
