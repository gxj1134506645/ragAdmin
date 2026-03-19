package com.ragadmin.server.app.service;

import com.ragadmin.server.auth.dto.CurrentUserResponse;
import com.ragadmin.server.auth.dto.LoginRequest;
import com.ragadmin.server.auth.dto.LoginResponse;
import com.ragadmin.server.auth.dto.RefreshTokenResponse;
import com.ragadmin.server.auth.model.AuthenticatedUser;
import com.ragadmin.server.auth.service.AuthService;
import com.ragadmin.server.common.model.PageResponse;
import com.ragadmin.server.knowledge.dto.KnowledgeBaseResponse;
import com.ragadmin.server.knowledge.service.KnowledgeBaseService;
import com.ragadmin.server.model.dto.ModelResponse;
import com.ragadmin.server.model.service.ModelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AppPortalService {

    @Autowired
    private AuthService authService;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private ModelService modelService;

    public LoginResponse login(LoginRequest request) {
        return authService.loginForAppPortal(request);
    }

    public RefreshTokenResponse refresh(String refreshToken) {
        return authService.refreshForAppPortal(refreshToken);
    }

    public CurrentUserResponse getCurrentUser(Long userId) {
        return authService.getCurrentUserForAppPortal(userId);
    }

    public void logout(AuthenticatedUser authenticatedUser) {
        authService.logout(authenticatedUser);
    }

    /**
     * 前台只暴露已启用知识库，避免把禁用中的配置直接暴露给终端用户。
     */
    public PageResponse<KnowledgeBaseResponse> listVisibleKnowledgeBases(String keyword, long pageNo, long pageSize) {
        return knowledgeBaseService.list(keyword, "ENABLED", pageNo, pageSize);
    }

    /**
     * 前台只允许选择已启用的聊天模型，不暴露向量模型和禁用模型。
     */
    public PageResponse<ModelResponse> listAvailableChatModels(String providerCode, long pageNo, long pageSize) {
        return modelService.list(providerCode, "TEXT_GENERATION", "ENABLED", pageNo, pageSize);
    }
}
