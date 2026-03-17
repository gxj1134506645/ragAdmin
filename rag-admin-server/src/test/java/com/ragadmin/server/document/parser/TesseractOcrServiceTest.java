package com.ragadmin.server.document.parser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TesseractOcrServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldResolveTessdataDirectoryUnderServerModuleWhenStartedFromRepoRoot() throws Exception {
        DocumentOcrProperties properties = new DocumentOcrProperties();
        properties.setEnabled(true);
        properties.setDataPath(".ocr-data/tessdata");

        Path expectedPath = tempDir.resolve("rag-admin-server").resolve(".ocr-data").resolve("tessdata");
        Files.createDirectories(expectedPath);

        TesseractOcrService service = new TesseractOcrService(properties);

        assertEquals(expectedPath.normalize(), service.resolveDataPath(tempDir));
    }
}
