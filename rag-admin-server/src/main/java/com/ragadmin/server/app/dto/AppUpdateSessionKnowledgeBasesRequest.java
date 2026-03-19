package com.ragadmin.server.app.dto;

import lombok.Data;

import java.util.List;

@Data
public class AppUpdateSessionKnowledgeBasesRequest {

    private List<Long> selectedKbIds;
}
