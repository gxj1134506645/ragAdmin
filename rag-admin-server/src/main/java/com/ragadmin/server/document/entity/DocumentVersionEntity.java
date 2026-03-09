package com.ragadmin.server.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("kb_document_version")
public class DocumentVersionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long documentId;
    private Integer versionNo;
    private String storageBucket;
    private String storageObjectKey;
    private String contentHash;
    private String parseStatus;
    private LocalDateTime parseStartedAt;
    private LocalDateTime parseFinishedAt;
    private Long createdBy;
    private LocalDateTime createdAt;
}
