package com.ragadmin.server.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_session")
public class ChatSessionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long kbId;
    private Long userId;
    private String sessionName;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
