package com.ragadmin.server.document.parser;

import org.springframework.ai.document.Document;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(30)
public class SemanticPreservingCleaner implements DocumentCleanerStep {

    @Override
    public boolean supports(DocumentCleanContext context) {
        return context.policy().semanticCleanEnabled();
    }

    @Override
    public List<Document> clean(List<Document> documents, DocumentCleanContext context) {
        // 当前阶段只建立策略与扩展点，不做激进语义改写。
        return documents;
    }
}
