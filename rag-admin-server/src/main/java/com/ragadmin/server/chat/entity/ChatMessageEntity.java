package com.ragadmin.server.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_message")
public class ChatMessageEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private Long userId;
    private String messageType;
    private String questionText;
    private String answerText;
    private Long modelId;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer latencyMs;
    private LocalDateTime createdAt;
}
