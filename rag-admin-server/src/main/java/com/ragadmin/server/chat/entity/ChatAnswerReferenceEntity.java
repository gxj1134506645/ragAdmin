package com.ragadmin.server.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("chat_answer_reference")
public class ChatAnswerReferenceEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long messageId;
    private Long chunkId;
    private BigDecimal score;
    private Integer rankNo;
    private LocalDateTime createdAt;
}
