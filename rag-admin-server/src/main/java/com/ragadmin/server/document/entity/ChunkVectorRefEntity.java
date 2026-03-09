package com.ragadmin.server.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("kb_chunk_vector_ref")
public class ChunkVectorRefEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long kbId;
    private Long chunkId;
    private Long embeddingModelId;
    private String collectionName;
    private String partitionName;
    private String vectorId;
    private Integer embeddingDim;
    private String status;
    private LocalDateTime createdAt;
}
