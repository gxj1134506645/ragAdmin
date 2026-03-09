package com.ragadmin.server.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_audit_log")
public class AuditLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long operatorUserId;
    private String operatorUsername;
    private String actionType;
    private String bizType;
    private Long bizId;
    private String requestMethod;
    private String requestPath;
    private String requestIp;
    private String requestPayload;
    private String responseCode;
    private Boolean success;
    private LocalDateTime createdAt;
}
