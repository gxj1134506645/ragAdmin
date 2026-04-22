package com.ragadmin.server.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("kb_knowledge_base")
public class KnowledgeBaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String kbCode;
    private String kbName;
    private String description;
    private Long embeddingModelId;
    private Long chatModelId;
    private Integer retrieveTopK;
    private Boolean rerankEnabled;
    private String retrievalMode;
    private String retrievalQueryRewritingMode;
    private String status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
