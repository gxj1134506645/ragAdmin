package com.ragadmin.server.app.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class AppCreateChatSessionRequest {

    private Long kbId;

    /**
     * 前台默认偏首页通用会话；如果显式带了 kbId 但未传 sceneType，服务端会推断为知识库内会话。
     */
    private String sceneType;

    @NotBlank(message = "sessionName 不能为空")
    private String sessionName;

    private Long chatModelId;

    private Boolean webSearchEnabled = Boolean.FALSE;

    private List<Long> selectedKbIds;
}
