package com.ragadmin.server.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateModelRequest {

    @NotNull(message = "providerId 不能为空")
    private Long providerId;

    @NotBlank(message = "modelCode 不能为空")
    private String modelCode;

    @NotBlank(message = "modelName 不能为空")
    private String modelName;

    @NotEmpty(message = "capabilityTypes 不能为空")
    private List<String> capabilityTypes;

    @NotBlank(message = "modelType 不能为空")
    private String modelType;

    private Integer maxTokens;
    private BigDecimal temperatureDefault;

    @NotBlank(message = "status 不能为空")
    private String status;
}
