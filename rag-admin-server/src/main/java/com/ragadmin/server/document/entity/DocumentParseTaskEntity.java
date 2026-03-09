package com.ragadmin.server.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("kb_document_parse_task")
public class DocumentParseTaskEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long kbId;
    private Long documentId;
    private Long documentVersionId;
    private String taskStatus;
    private String errorMessage;
    private Integer retryCount;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
