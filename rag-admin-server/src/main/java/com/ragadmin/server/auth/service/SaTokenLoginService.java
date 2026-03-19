package com.ragadmin.server.auth.service;

import cn.dev33.satoken.stp.StpLogic;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SaTokenLoginService {

    private static final StpLogic ADMIN_STP_LOGIC = new StpLogic(AuthService.ADMIN_LOGIN_TYPE);
    private static final StpLogic APP_STP_LOGIC = new StpLogic(AuthService.APP_LOGIN_TYPE);

    public String login(Long userId, String loginType) {
        StpLogic stpLogic = resolve(loginType);
        stpLogic.login(userId);
        return stpLogic.getTokenValue();
    }

    public Long getLoginId(String tokenValue, String loginType) {
        if (!StringUtils.hasText(tokenValue)) {
            return null;
        }
        Object loginId = resolve(loginType).getLoginIdByToken(tokenValue);
        if (loginId == null) {
            return null;
        }
        return Long.valueOf(String.valueOf(loginId));
    }

    public void logoutByTokenValue(String tokenValue, String loginType) {
        if (!StringUtils.hasText(tokenValue)) {
            return;
        }
        resolve(loginType).logoutByTokenValue(tokenValue);
    }

    private StpLogic resolve(String loginType) {
        if (AuthService.ADMIN_LOGIN_TYPE.equals(loginType)) {
            return ADMIN_STP_LOGIC;
        }
        if (AuthService.APP_LOGIN_TYPE.equals(loginType)) {
            return APP_STP_LOGIC;
        }
        throw new IllegalArgumentException("不支持的登录类型: " + loginType);
    }
}
