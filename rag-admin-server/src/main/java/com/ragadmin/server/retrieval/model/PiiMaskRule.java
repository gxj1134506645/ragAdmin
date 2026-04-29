package com.ragadmin.server.retrieval.model;

import java.util.List;
import java.util.regex.Pattern;

public record PiiMaskRule(String name, Pattern pattern, String replacement) {

    public static final List<PiiMaskRule> DEFAULT_RULES = List.of(
            new PiiMaskRule("身份证号",
                    Pattern.compile("(?<!\\d)\\d{17}[\\dXx](?!\\d)"),
                    "[身份证号]"),
            new PiiMaskRule("手机号",
                    Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)"),
                    "[手机号]"),
            new PiiMaskRule("邮箱",
                    Pattern.compile("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}"),
                    "[邮箱]"),
            new PiiMaskRule("银行卡号",
                    Pattern.compile("(?<!\\d)\\d{16,19}(?!\\d)"),
                    "[银行卡号]"),
            new PiiMaskRule("IPv4地址",
                    Pattern.compile("(?<!\\d)\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}(?!\\d)"),
                    "[IP地址]")
    );
}
