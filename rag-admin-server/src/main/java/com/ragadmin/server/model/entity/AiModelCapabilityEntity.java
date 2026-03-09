package com.ragadmin.server.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_model_capability")
public class AiModelCapabilityEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long modelId;
    private String capabilityType;
    private Boolean enabled;
    private LocalDateTime createdAt;
}
