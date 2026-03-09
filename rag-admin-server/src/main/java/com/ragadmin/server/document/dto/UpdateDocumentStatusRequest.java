package com.ragadmin.server.document.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateDocumentStatusRequest {

    @NotNull(message = "enabled 不能为空")
    private Boolean enabled;
}
