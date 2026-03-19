package com.ragadmin.server.auth.model;

import java.util.Arrays;
import java.util.List;

public enum AdminPermissionCode {

    DASHBOARD_VIEW,
    CHAT_CONSOLE_ACCESS,
    KB_MANAGE,
    MODEL_MANAGE,
    TASK_VIEW,
    TASK_OPERATE,
    AUDIT_VIEW,
    STATISTICS_VIEW,
    USER_MANAGE;

    public static List<String> allCodes() {
        return Arrays.stream(values())
                .map(AdminPermissionCode::name)
                .toList();
    }
}
