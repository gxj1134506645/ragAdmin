package com.ragadmin.server.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("kb_document")
public class DocumentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long kbId;
    private String docName;
    private String docType;
    private String storageBucket;
    private String storageObjectKey;
    private Integer currentVersion;
    private String parseStatus;
    private Boolean enabled;
    private Long fileSize;
    private String contentHash;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
