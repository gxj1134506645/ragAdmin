package com.ragadmin.server.infra.ai.chat;

import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.infra.ai.SpringAiModelFactory;
import com.ragadmin.server.infra.ai.SpringAiModelSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class SpringAiConversationChatClient implements ConversationChatClient {

    @Autowired
    private SpringAiModelFactory springAiModelFactory;

    @Autowired
    private ChatMemory chatMemory;

    @Autowired
    private ChatClientAdvisorProperties chatClientAdvisorProperties;

    @Override
    public <T> T chatEntity(
            String providerCode,
            String modelCode,
            List<ChatModelClient.ChatMessage> promptMessages,
            Class<T> responseType
    ) {
        validatePromptMessages(promptMessages);
        if (responseType == null) {
            throw new BusinessException("CHAT_RESPONSE_TYPE_EMPTY", "结构化输出目标类型不能为空", HttpStatus.BAD_REQUEST);
        }

        var chatModel = springAiModelFactory.createChatModel(providerCode, modelCode);
        ChatClient chatClient = buildStatelessChatClient(chatModel);
        return chatClient.prompt()
                .messages(SpringAiModelSupport.toSpringMessages(promptMessages))
                .call()
                .entity(responseType);
    }

    @Override
    public ChatModelClient.ChatCompletionResult chat(
            String providerCode,
            String modelCode,
            String conversationId,
            List<ChatModelClient.ChatMessage> promptMessages,
            List<ChatModelClient.ChatMessage> historyMessages
    ) {
        if (!StringUtils.hasText(conversationId)) {
            throw new BusinessException("CHAT_CONVERSATION_ID_INVALID", "会话记忆 conversationId 不能为空", HttpStatus.BAD_REQUEST);
        }
        validatePromptMessages(promptMessages);

        // 已有历史业务消息的旧会话首次切到 memory 链路时，需要先补种，避免上下文突然丢失。
        seedConversationMemoryIfNecessary(conversationId, historyMessages);

        var chatModel = springAiModelFactory.createChatModel(providerCode, modelCode);
        ChatClient chatClient = buildChatClient(chatModel);
        var response = chatClient.prompt()
                .messages(SpringAiModelSupport.toSpringMessages(promptMessages))
                .advisors(spec -> spec
                        .param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .chatResponse();
        return SpringAiModelSupport.toChatCompletionResult(response);
    }

    @Override
    public Flux<org.springframework.ai.chat.model.ChatResponse> stream(
            String providerCode,
            String modelCode,
            String conversationId,
            List<ChatModelClient.ChatMessage> promptMessages,
            List<ChatModelClient.ChatMessage> historyMessages
    ) {
        if (!StringUtils.hasText(conversationId)) {
            throw new BusinessException("CHAT_CONVERSATION_ID_INVALID", "会话记忆 conversationId 不能为空", HttpStatus.BAD_REQUEST);
        }
        validatePromptMessages(promptMessages);

        seedConversationMemoryIfNecessary(conversationId, historyMessages);

        var chatModel = springAiModelFactory.createChatModel(providerCode, modelCode);
        ChatClient chatClient = buildChatClient(chatModel);
        return chatClient.prompt()
                .messages(SpringAiModelSupport.toSpringMessages(promptMessages))
                .advisors(spec -> spec
                        .param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .chatResponse();
    }

    ChatClient buildChatClient(org.springframework.ai.chat.model.ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(buildDefaultAdvisors())
                .build();
    }

    ChatClient buildStatelessChatClient(org.springframework.ai.chat.model.ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(buildStatelessAdvisors())
                .build();
    }

    List<Advisor> buildDefaultAdvisors() {
        List<Advisor> advisors = new ArrayList<>();
        advisors.add(MessageChatMemoryAdvisor.builder(chatMemory).build());

        if (chatClientAdvisorProperties.isSimpleLoggerAdvisorEnabled()) {
            // 放在 memory advisor 之后，确保日志里看到的是已经注入会话记忆后的最终请求。
            advisors.add(SimpleLoggerAdvisor.builder()
                    .requestToString(this::formatChatClientRequest)
                    .responseToString(this::formatChatResponse)
                    .order(Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER + 10)
                    .build());
        }

        return List.copyOf(advisors);
    }

    List<Advisor> buildStatelessAdvisors() {
        if (!chatClientAdvisorProperties.isSimpleLoggerAdvisorEnabled()) {
            return List.of();
        }
        return List.of(SimpleLoggerAdvisor.builder()
                .requestToString(this::formatChatClientRequest)
                .responseToString(this::formatChatResponse)
                .build());
    }

    String formatChatClientRequest(ChatClientRequest request) {
        List<Message> messages = request.prompt().getInstructions();
        return "ChatClientRequest{"
                + "messageCount=" + messages.size()
                + ", contextKeys=" + request.context().keySet().stream().sorted().toList()
                + ", messages=" + summarizeMessages(messages)
                + "}";
    }

    String formatChatResponse(ChatResponse response) {
        if (response == null) {
            return "ChatResponse{empty=true}";
        }

        List<String> resultSummaries = response.getResults().stream()
                .map(result -> summarizeAssistantMessage(result.getOutput()))
                .toList();

        String usageSummary = "";
        if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
            usageSummary = ", usage={promptTokens=" + safeInteger(response.getMetadata().getUsage().getPromptTokens())
                    + ", completionTokens=" + safeInteger(response.getMetadata().getUsage().getCompletionTokens())
                    + ", totalTokens=" + safeInteger(response.getMetadata().getUsage().getTotalTokens())
                    + "}";
        }

        String modelSummary = "";
        if (response.getMetadata() != null && StringUtils.hasText(response.getMetadata().getModel())) {
            modelSummary = ", model=" + response.getMetadata().getModel();
        }

        return "ChatResponse{"
                + "resultCount=" + response.getResults().size()
                + modelSummary
                + usageSummary
                + ", results=" + resultSummaries
                + "}";
    }

    private List<String> summarizeMessages(List<Message> messages) {
        return messages.stream()
                .map(this::summarizeMessage)
                .collect(Collectors.toList());
    }

    private String summarizeMessage(Message message) {
        StringBuilder builder = new StringBuilder();
        builder.append("{role=").append(message.getMessageType());
        builder.append(", text=").append(renderTextSummary(
                message.getText(),
                chatClientAdvisorProperties.isSimpleLoggerRequestBodyEnabled()
        ));
        builder.append(", metadataKeys=").append(safeMetadataKeys(message.getMetadata()));

        if (message instanceof AssistantMessage assistantMessage) {
            builder.append(", toolCallCount=").append(assistantMessage.getToolCalls().size());
        }

        builder.append("}");
        return builder.toString();
    }

    private String summarizeAssistantMessage(AssistantMessage message) {
        return "{text=" + renderTextSummary(
                message.getText(),
                chatClientAdvisorProperties.isSimpleLoggerResponseBodyEnabled()
        )
                + ", toolCallCount=" + message.getToolCalls().size()
                + ", metadataKeys=" + safeMetadataKeys(message.getMetadata())
                + "}";
    }

    private List<String> safeMetadataKeys(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return List.of();
        }
        return metadata.keySet().stream().sorted().toList();
    }

    private String renderTextSummary(String text, boolean bodyEnabled) {
        if (!StringUtils.hasText(text)) {
            return "\"\"";
        }

        String normalized = text.replaceAll("\\s+", " ").trim();
        String digest = sha256Digest(normalized);
        if (!bodyEnabled) {
            return "\"(hidden,len=" + normalized.length() + ",sha256=" + digest + ")\"";
        }

        int maxLength = Math.max(1, chatClientAdvisorProperties.getSimpleLoggerMaxTextLength());
        if (normalized.length() <= maxLength) {
            return "\"" + normalized + "\"";
        }
        return "\"" + normalized.substring(0, maxLength)
                + "...(truncated,total=" + normalized.length()
                + ",sha256=" + digest
                + ")\"";
    }

    private int safeInteger(Integer value) {
        return value == null ? 0 : value;
    }

    private void validatePromptMessages(List<ChatModelClient.ChatMessage> promptMessages) {
        if (promptMessages == null || promptMessages.isEmpty()) {
            throw new BusinessException("CHAT_PROMPT_EMPTY", "聊天提示消息不能为空", HttpStatus.BAD_REQUEST);
        }
    }

    private String sha256Digest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes).substring(0, 16);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    private void seedConversationMemoryIfNecessary(
            String conversationId,
            List<ChatModelClient.ChatMessage> historyMessages
    ) {
        if (historyMessages == null || historyMessages.isEmpty()) {
            return;
        }
        if (!chatMemory.get(conversationId).isEmpty()) {
            return;
        }
        chatMemory.add(conversationId, SpringAiModelSupport.toSpringMessages(historyMessages));
    }
}
