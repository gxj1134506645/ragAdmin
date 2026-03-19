package com.ragadmin.server.app.dto;

import java.util.List;

public record AppChatSessionResponse(
        Long id,
        Long kbId,
        String sceneType,
        String sessionName,
        Long chatModelId,
        Boolean webSearchEnabled,
        List<Long> selectedKbIds,
        String status
) {
}
