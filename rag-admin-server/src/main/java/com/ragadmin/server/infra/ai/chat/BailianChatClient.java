package com.ragadmin.server.infra.ai.chat;

import com.ragadmin.server.infra.ai.SpringAiModelFactory;
import com.ragadmin.server.infra.ai.SpringAiModelSupport;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "rag.ai.bailian", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BailianChatClient implements ChatModelClient {

    private final SpringAiModelFactory springAiModelFactory;

    public BailianChatClient(SpringAiModelFactory springAiModelFactory) {
        this.springAiModelFactory = springAiModelFactory;
    }

    @Override
    public boolean supports(String providerCode) {
        return "BAILIAN".equalsIgnoreCase(providerCode);
    }

    @Override
    public ChatCompletionResult chat(String modelCode, List<ChatMessage> messages) {
        Prompt prompt = new Prompt(SpringAiModelSupport.toSpringMessages(messages));
        var chatModel = springAiModelFactory.createChatModel("BAILIAN", modelCode);
        return SpringAiModelSupport.toChatCompletionResult(chatModel.call(prompt));
    }
}
