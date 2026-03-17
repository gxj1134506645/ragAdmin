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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Component
public class TesseractOcrService {

    private final DocumentOcrProperties ocrProperties;

    public TesseractOcrService(DocumentOcrProperties ocrProperties) {
        this.ocrProperties = ocrProperties;
    }

    public boolean isEnabled() {
        return ocrProperties.isEnabled();
    }

    public OcrCapability describeCapability() {
        if (!ocrProperties.isEnabled()) {
            return new OcrCapability(false, false, "OCR 已禁用", ocrProperties.getLanguage(), ocrProperties.getMaxPdfPages());
        }
        if (!StringUtils.hasText(ocrProperties.getTesseractCommand())) {
            return new OcrCapability(true, false, "Tesseract 命令未配置", ocrProperties.getLanguage(), ocrProperties.getMaxPdfPages());
        }
        Path tessdataDir = resolveDataPath();
        if (StringUtils.hasText(ocrProperties.getDataPath()) && (tessdataDir == null || !Files.isDirectory(tessdataDir))) {
            String resolvedPath = tessdataDir == null ? ocrProperties.getDataPath().trim() : tessdataDir.toString();
            return new OcrCapability(
                    true,
                    false,
                    "OCR 数据目录不存在，请检查 tessdata 路径: " + resolvedPath,
                    ocrProperties.getLanguage(),
                    ocrProperties.getMaxPdfPages()
            );
        }
        try {
            String output = runCommandForOutput(buildCommand("--version"));
            String firstLine = output.lines().findFirst().orElse("Tesseract 可用").trim();
            List<String> availableLanguages = listAvailableLanguages();
            List<String> missingLanguages = configuredLanguages().stream()
                    .filter(language -> !availableLanguages.contains(language))
                    .toList();
            if (!missingLanguages.isEmpty()) {
                return new OcrCapability(
                        true,
                        false,
                        "缺少 OCR 语言包: " + String.join(", ", missingLanguages),
                        ocrProperties.getLanguage(),
                        ocrProperties.getMaxPdfPages()
                );
            }
            return new OcrCapability(true, true, firstLine, ocrProperties.getLanguage(), ocrProperties.getMaxPdfPages());
        } catch (Exception ex) {
            return new OcrCapability(
                    true,
                    false,
                    normalizeMessage(ex.getMessage(), "Tesseract 检查失败"),
                    ocrProperties.getLanguage(),
                    ocrProperties.getMaxPdfPages()
            );
        }
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
        List<String> command = buildCommand(
                inputFile.toString(),
                outputBase.toString(),
                "-l",
                ocrProperties.getLanguage()
        );
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
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

    private String runCommandForOutput(List<String> command) throws Exception {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        String output;
        try (InputStream processStream = process.getInputStream()) {
            output = StreamUtils.copyToString(processStream, StandardCharsets.UTF_8);
        }
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Tesseract 检查超时");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException(normalizeMessage(output, "Tesseract 检查失败"));
        }
        return output;
    }

    private List<String> listAvailableLanguages() throws Exception {
        String output = runCommandForOutput(buildCommand("--list-langs"));
        return output.lines()
                .skip(1)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private List<String> configuredLanguages() {
        return Arrays.stream(ocrProperties.getLanguage().split("\\+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private List<String> buildCommand(String... args) {
        List<String> command = new ArrayList<>();
        command.add(ocrProperties.getTesseractCommand());
        command.addAll(List.of(args));
        Path tessdataDir = resolveDataPath();
        if (tessdataDir != null) {
            command.add("--tessdata-dir");
            command.add(tessdataDir.toString());
        }
        return command;
    }

    private String buildOcrError(String commandOutput) {
        return normalizeMessage(commandOutput, "Tesseract OCR 执行失败");
    }

    private String normalizeMessage(String rawMessage, String defaultMessage) {
        if (!StringUtils.hasText(rawMessage)) {
            return defaultMessage;
        }
        String message = rawMessage.trim();
        String lowercaseMessage = message.toLowerCase(Locale.ROOT);
        if (lowercaseMessage.contains("no such file or directory") && lowercaseMessage.contains("tessdata")) {
            Path tessdataDir = resolveDataPath();
            String resolvedPath = tessdataDir == null ? ocrProperties.getDataPath() : tessdataDir.toString();
            return "OCR 数据目录不存在，请检查 tessdata 路径: " + resolvedPath;
        }
        if (lowercaseMessage.contains("filesystem_error")) {
            return defaultMessage;
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    Path resolveDataPath() {
        return resolveDataPath(Paths.get("").toAbsolutePath().normalize());
    }

    Path resolveDataPath(Path workingDirectory) {
        if (!StringUtils.hasText(ocrProperties.getDataPath())) {
            return null;
        }
        Path configuredPath = Path.of(ocrProperties.getDataPath().trim());
        if (configuredPath.isAbsolute()) {
            return configuredPath.normalize();
        }
        Path directPath = workingDirectory.resolve(configuredPath).normalize();
        if (Files.isDirectory(directPath)) {
            return directPath;
        }
        Path modulePath = workingDirectory.resolve("rag-admin-server").resolve(configuredPath).normalize();
        if (Files.isDirectory(modulePath)) {
            return modulePath;
        }
        return directPath;
    }
}
