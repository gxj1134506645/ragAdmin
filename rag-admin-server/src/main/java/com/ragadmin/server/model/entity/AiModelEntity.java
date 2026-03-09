package com.ragadmin.server.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ai_model")
public class AiModelEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long providerId;
    private String modelCode;
    private String modelName;
    private String modelType;
    private Integer maxTokens;
    private BigDecimal temperatureDefault;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
