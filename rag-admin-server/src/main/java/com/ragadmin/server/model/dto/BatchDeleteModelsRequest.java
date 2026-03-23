package com.ragadmin.server.model.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class BatchDeleteModelsRequest {

    @NotEmpty(message = "modelIds 不能为空")
    private List<@NotNull(message = "modelId 不能为空") Long> modelIds;
}
