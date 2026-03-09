package com.ragadmin.server.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_provider")
public class AiProviderEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String providerCode;
    private String providerName;
    private String baseUrl;
    private String apiKeySecretRef;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
