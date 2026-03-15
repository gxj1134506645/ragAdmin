package com.ragadmin.server.document.parser;

import com.ragadmin.server.common.exception.BusinessException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class TesseractOcrService {

    private final DocumentOcrProperties ocrProperties;

    public TesseractOcrService(DocumentOcrProperties ocrProperties) {
        this.ocrProperties = ocrProperties;
    }

    public boolean isEnabled() {
        return ocrProperties.isEnabled();
    }

    public String extractImageText(InputStream inputStream, String extension) throws Exception {
        byte[] bytes = StreamUtils.copyToByteArray(inputStream);
        if (bytes.length == 0) {
            return "";
        }
        String normalizedExtension = StringUtils.hasText(extension) ? extension.trim().toLowerCase() : "png";
        Path tempInput = Files.createTempFile("ragadmin-ocr-image-", "." + normalizedExtension);
        Path tempOutput = Files.createTempFile("ragadmin-ocr-image-out-", "");
        try {
            Files.write(tempInput, bytes);
            return runTesseract(tempInput, tempOutput);
        } finally {
            Files.deleteIfExists(tempInput);
            Files.deleteIfExists(tempOutput);
            Files.deleteIfExists(Path.of(tempOutput.toString() + ".txt"));
        }
    }

    public String extractPdfText(InputStream inputStream) throws Exception {
        byte[] bytes = StreamUtils.copyToByteArray(inputStream);
        if (bytes.length == 0) {
            return "";
        }
        try (PDDocument document = PDDocument.load(bytes)) {
            PDFRenderer renderer = new PDFRenderer(document);
            StringBuilder builder = new StringBuilder();
            int pageCount = Math.min(document.getNumberOfPages(), Math.max(1, ocrProperties.getMaxPdfPages()));
            for (int page = 0; page < pageCount; page++) {
                BufferedImage image = renderer.renderImageWithDPI(page, 180, ImageType.RGB);
                Path tempImage = Files.createTempFile("ragadmin-ocr-pdf-page-", ".png");
                Path tempOutput = Files.createTempFile("ragadmin-ocr-pdf-out-", "");
                try {
                    ImageIO.write(image, "png", tempImage.toFile());
                    String pageText = runTesseract(tempImage, tempOutput);
                    if (StringUtils.hasText(pageText)) {
                        if (!builder.isEmpty()) {
                            builder.append("\n\n");
                        }
                        builder.append(pageText.trim());
                    }
                } finally {
                    Files.deleteIfExists(tempImage);
                    Files.deleteIfExists(tempOutput);
                    Files.deleteIfExists(Path.of(tempOutput.toString() + ".txt"));
                }
            }
            return builder.toString();
        } catch (IOException ex) {
            throw new BusinessException("DOCUMENT_OCR_FAILED", "PDF OCR 解析失败", HttpStatus.BAD_GATEWAY);
        }
    }

    private String runTesseract(Path inputFile, Path outputBase) throws Exception {
        Process process = new ProcessBuilder(
                ocrProperties.getTesseractCommand(),
                inputFile.toString(),
                outputBase.toString(),
                "-l",
                ocrProperties.getLanguage()
        ).redirectErrorStream(true).start();
        String commandOutput;
        try (InputStream processStream = process.getInputStream()) {
            commandOutput = StreamUtils.copyToString(processStream, StandardCharsets.UTF_8);
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new BusinessException("DOCUMENT_OCR_FAILED", buildOcrError(commandOutput), HttpStatus.BAD_GATEWAY);
        }
        Path outputFile = Path.of(outputBase.toString() + ".txt");
        if (!Files.exists(outputFile)) {
            return "";
        }
        return Files.readString(outputFile, StandardCharsets.UTF_8);
    }

    private String buildOcrError(String commandOutput) {
        if (!StringUtils.hasText(commandOutput)) {
            return "Tesseract OCR 执行失败";
        }
        String message = commandOutput.trim();
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
