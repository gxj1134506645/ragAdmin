package com.ragadmin.server.app.dto;

import lombok.Data;

import java.util.List;

@Data
public class AppRegenerateChatMessageRequest {

    private Long chatModelId;

    private List<Long> selectedKbIds;

    private Boolean webSearchEnabled;
}
