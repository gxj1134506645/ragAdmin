package com.ragadmin.server.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("kb_chunk")
public class ChunkEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long kbId;
    private Long documentId;
    private Long documentVersionId;
    private Integer chunkNo;
    private String chunkText;
    private Integer tokenCount;
    private Integer charCount;
    @TableField("metadata_json")
    private String metadataJson;
    private Boolean enabled;
    private Long parentChunkId;
    private String chunkStrategy;
    private LocalDateTime createdAt;
}
