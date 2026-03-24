package com.ragadmin.server.infra.ai.chat;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StringUtils;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 统一负责提示词模板的加载与变量渲染。
 * 当前只支持最小模板能力，避免为简单占位引入额外模板引擎依赖。
 */
@Service
public class PromptTemplateService {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([a-zA-Z0-9_.-]+)}");

    private final ConcurrentHashMap<String, String> templateCache = new ConcurrentHashMap<>();

    public String load(Resource resource) {
        return render(resource, Map.of());
    }

    public String render(Resource resource, Map<String, String> variables) {
        String template = loadTemplate(resource);
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) {
            String variableName = matcher.group(1);
            String replacement = variables.get(variableName);
            if (replacement == null) {
                throw new IllegalArgumentException("提示词模板缺少变量：" + variableName + "，template=" + describe(resource));
            }
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(rendered);
        return rendered.toString().trim();
    }

    private String loadTemplate(Resource resource) {
        if (resource == null) {
            throw new IllegalArgumentException("提示词模板资源不能为空");
        }
        String cacheKey = describe(resource);
        return templateCache.computeIfAbsent(cacheKey, key -> readTemplate(resource));
    }

    private String readTemplate(Resource resource) {
        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            String content = FileCopyUtils.copyToString(reader);
            if (!StringUtils.hasText(content)) {
                throw new IllegalArgumentException("提示词模板内容为空，template=" + describe(resource));
            }
            return content.replace("\r\n", "\n");
        } catch (Exception ex) {
            throw new IllegalStateException("读取提示词模板失败，template=" + describe(resource), ex);
        }
    }

    private String describe(Resource resource) {
        return resource == null ? "unknown" : resource.getDescription();
    }
}
