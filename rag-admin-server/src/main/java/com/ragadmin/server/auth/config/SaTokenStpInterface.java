package com.ragadmin.server.auth.config;

import cn.dev33.satoken.stp.StpInterface;
import com.ragadmin.server.auth.mapper.SysRoleMapper;
import com.ragadmin.server.auth.service.AdminPermissionService;
import com.ragadmin.server.auth.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class SaTokenStpInterface implements StpInterface {

    @Autowired
    private SysRoleMapper sysRoleMapper;

    @Autowired
    private AdminPermissionService adminPermissionService;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        if (!AuthService.ADMIN_LOGIN_TYPE.equals(loginType)) {
            return Collections.emptyList();
        }
        return adminPermissionService.listPermissions(Long.valueOf(String.valueOf(loginId)));
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return sysRoleMapper.selectRoleCodesByUserId(Long.valueOf(String.valueOf(loginId)));
    }
}
