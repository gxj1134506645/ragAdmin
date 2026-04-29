package com.ragadmin.server.retrieval.service;

import com.ragadmin.server.retrieval.config.QueryPreprocessProperties;
import com.ragadmin.server.retrieval.model.PreprocessResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QueryPreprocessServiceTest {

    private QueryPreprocessService service;
    private QueryPreprocessProperties properties;
    private ContentFilterService contentFilterService;

    @BeforeEach
    void setUp() throws Exception {
        service = new QueryPreprocessService();
        contentFilterService = new ContentFilterService();
        properties = new QueryPreprocessProperties();

        injectField(service, "properties", properties);
        injectField(service, "contentFilterService", contentFilterService);
        injectField(contentFilterService, "properties", properties);
    }

    private void injectField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    // --- PII 脱敏测试 ---

    @Test
    void shouldMaskIdCard() {
        PreprocessResult result = service.preprocess("我的身份证号是 110101199001011234 请查一下");
        assertTrue(result.isModified());
        assertTrue(result.getQuery().contains("[身份证号]"));
        assertFalse(result.getQuery().contains("110101199001011234"));
    }

    @Test
    void shouldMaskPhone() {
        PreprocessResult result = service.preprocess("手机号 13800138000 还能收到通知吗");
        assertTrue(result.isModified());
        assertTrue(result.getQuery().contains("[手机号]"));
        assertFalse(result.getQuery().contains("13800138000"));
    }

    @Test
    void shouldMaskEmail() {
        PreprocessResult result = service.preprocess("发邮件到 test@example.com 通知我");
        assertTrue(result.isModified());
        assertTrue(result.getQuery().contains("[邮箱]"));
        assertFalse(result.getQuery().contains("test@example.com"));
    }

    @Test
    void shouldMaskBankCard() {
        PreprocessResult result = service.preprocess("银行卡号 6222021234567890123 已绑定");
        assertTrue(result.isModified());
        assertTrue(result.getQuery().contains("[银行卡号]"));
        assertFalse(result.getQuery().contains("6222021234567890123"));
    }

    @Test
    void shouldMaskIpAddress() {
        PreprocessResult result = service.preprocess("服务器地址是 192.168.1.100 连不上");
        assertTrue(result.isModified());
        assertTrue(result.getQuery().contains("[IP地址]"));
        assertFalse(result.getQuery().contains("192.168.1.100"));
    }

    @Test
    void shouldNotMaskNormalText() {
        PreprocessResult result = service.preprocess("公司的年假政策是什么");
        assertFalse(result.isModified());
        assertEquals("公司的年假政策是什么", result.getQuery());
    }

    // --- 内容过滤测试 ---

    @Test
    void shouldFilterBlockedWord() {
        properties.getContentFilter().setBlockedWords(List.of("脏话A", "脏话B"));

        PreprocessResult result = service.preprocess("你这个脏话A怎么想的");
        assertTrue(result.isModified());
        assertTrue(result.getQuery().contains("***"));
        assertFalse(result.getQuery().contains("脏话A"));
    }

    @Test
    void shouldFilterCaseInsensitive() {
        properties.getContentFilter().setBlockedWords(List.of("badword"));

        PreprocessResult result = service.preprocess("You are BADWORD really");
        assertTrue(result.isModified());
        assertFalse(result.getQuery().toLowerCase().contains("badword"));
    }

    @Test
    void shouldNotBlockWhenBlockDisabled() {
        properties.getContentFilter().setBlockedWords(List.of("脏话"));
        properties.getContentFilter().setBlockEnabled(false);

        PreprocessResult result = service.preprocess("你说脏话了");
        assertTrue(result.isModified());
        assertFalse(result.isBlocked());
    }

    @Test
    void shouldBlockWhenBlockEnabled() {
        properties.getContentFilter().setBlockedWords(List.of("脏话"));
        properties.getContentFilter().setBlockEnabled(true);

        PreprocessResult result = service.preprocess("你说脏话了");
        assertTrue(result.isBlocked());
    }

    // --- 组合场景 ---

    @Test
    void shouldMaskPiiAndFilterContent() {
        properties.getContentFilter().setBlockedWords(List.of("蠢货"));

        PreprocessResult result = service.preprocess("你这个蠢货，手机号 13800138000 被盗了");
        assertTrue(result.isModified());
        assertTrue(result.getQuery().contains("[手机号]"));
        assertFalse(result.getQuery().contains("13800138000"));
        assertFalse(result.getQuery().contains("蠢货"));
    }

    // --- 边界场景 ---

    @Test
    void shouldHandleEmptyQuery() {
        PreprocessResult result = service.preprocess("");
        assertFalse(result.isModified());
        assertEquals("", result.getQuery());
    }

    @Test
    void shouldHandleNullQuery() {
        PreprocessResult result = service.preprocess(null);
        assertFalse(result.isModified());
        assertNull(result.getQuery());
    }

    @Test
    void shouldSkipWhenDisabled() {
        properties.setEnabled(false);
        PreprocessResult result = service.preprocess("手机号 13800138000");
        assertFalse(result.isModified());
        assertEquals("手机号 13800138000", result.getQuery());
    }

    @Test
    void shouldSkipPiiWhenPiiDisabled() {
        properties.getPiiMask().setEnabled(false);
        PreprocessResult result = service.preprocess("手机号 13800138000");
        assertFalse(result.isModified());
        assertEquals("手机号 13800138000", result.getQuery());
    }
}
