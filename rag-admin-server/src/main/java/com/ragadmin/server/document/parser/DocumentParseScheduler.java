package com.ragadmin.server.document.parser;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DocumentParseScheduler {

    private final DocumentParseProcessor documentParseProcessor;

    public DocumentParseScheduler(DocumentParseProcessor documentParseProcessor) {
        this.documentParseProcessor = documentParseProcessor;
    }

    @Scheduled(fixedDelayString = "${rag.document.parse.poll-interval-ms:5000}")
    public void poll() {
        documentParseProcessor.processWaitingTasks();
    }
}
