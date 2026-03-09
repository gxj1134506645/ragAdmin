package com.ragadmin.server.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateModelProviderRequest {

    @NotBlank(message = "providerCode 不能为空")
    private String providerCode;

    @NotBlank(message = "providerName 不能为空")
    private String providerName;

    private String baseUrl;
    private String apiKeySecretRef;
    @NotBlank(message = "status 不能为空")
    private String status;
}
