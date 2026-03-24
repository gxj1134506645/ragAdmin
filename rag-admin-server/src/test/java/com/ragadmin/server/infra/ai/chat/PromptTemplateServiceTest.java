package com.ragadmin.server.infra.ai.chat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PromptTemplateServiceTest {

    private final PromptTemplateService promptTemplateService = new PromptTemplateService();

    @Test
    void shouldRenderTemplateWithVariables() {
        ByteArrayResource resource = new ByteArrayResource(
                "问题：${question}\n答案：${answer}".getBytes(StandardCharsets.UTF_8)
        ) {
            @Override
            public String getDescription() {
                return "prompt-template-test";
            }
        };

        String rendered = promptTemplateService.render(resource, Map.of(
                "question", "今天无锡天气如何？",
                "answer", "晴，最高 21 度。"
        ));

        assertEquals("问题：今天无锡天气如何？\n答案：晴，最高 21 度。", rendered);
    }

    @Test
    void shouldFailFastWhenTemplateVariableMissing() {
        ByteArrayResource resource = new ByteArrayResource(
                "问题：${question}\n答案：${answer}".getBytes(StandardCharsets.UTF_8)
        ) {
            @Override
            public String getDescription() {
                return "prompt-template-test-missing-variable";
            }
        };

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> promptTemplateService.render(resource, Map.of("question", "今天无锡天气如何？"))
        );

        assertEquals("提示词模板缺少变量：answer，template=prompt-template-test-missing-variable", exception.getMessage());
    }
}
