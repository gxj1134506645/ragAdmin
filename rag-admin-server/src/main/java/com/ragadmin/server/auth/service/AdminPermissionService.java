package com.ragadmin.server.auth.service;

import com.ragadmin.server.auth.mapper.SysRoleMapper;
import com.ragadmin.server.auth.model.AdminPermissionCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class AdminPermissionService {

    @Autowired
    private SysRoleMapper sysRoleMapper;

    /**
     * 后台权限矩阵首期保持静态收口，避免过早引入数据库动态编排复杂度。
     */
    public List<String> listPermissions(Long userId) {
        List<String> roleCodes = sysRoleMapper.selectRoleCodesByUserId(userId);
        return listPermissionsByRoleCodes(roleCodes);
    }

    public List<String> listPermissionsByRoleCodes(List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Collections.emptyList();
        }
        if (roleCodes.contains("ADMIN")) {
            return AdminPermissionCode.allCodes();
        }
        Set<String> permissions = new LinkedHashSet<>();
        if (roleCodes.contains("KB_ADMIN")) {
            permissions.add(AdminPermissionCode.DASHBOARD_VIEW.name());
            permissions.add(AdminPermissionCode.CHAT_CONSOLE_ACCESS.name());
            permissions.add(AdminPermissionCode.KB_MANAGE.name());
            permissions.add(AdminPermissionCode.MODEL_MANAGE.name());
            permissions.add(AdminPermissionCode.TASK_VIEW.name());
            permissions.add(AdminPermissionCode.TASK_OPERATE.name());
            permissions.add(AdminPermissionCode.STATISTICS_VIEW.name());
        }
        if (roleCodes.contains("AUDITOR")) {
            permissions.add(AdminPermissionCode.DASHBOARD_VIEW.name());
            permissions.add(AdminPermissionCode.TASK_VIEW.name());
            permissions.add(AdminPermissionCode.AUDIT_VIEW.name());
            permissions.add(AdminPermissionCode.STATISTICS_VIEW.name());
        }
        return List.copyOf(permissions);
    }
}
