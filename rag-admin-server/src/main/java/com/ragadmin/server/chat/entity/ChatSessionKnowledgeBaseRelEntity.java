package com.ragadmin.server.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_session_kb_rel")
public class ChatSessionKnowledgeBaseRelEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private Long kbId;
    private Integer sortNo;
    private LocalDateTime createdAt;
}
