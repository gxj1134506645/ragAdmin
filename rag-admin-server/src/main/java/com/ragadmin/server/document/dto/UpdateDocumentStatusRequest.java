package com.ragadmin.server.document.dto;

import jakarta.validation.constraints.NotNull;

public class UpdateDocumentStatusRequest {

    @NotNull(message = "enabled 不能为空")
    private Boolean enabled;

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
