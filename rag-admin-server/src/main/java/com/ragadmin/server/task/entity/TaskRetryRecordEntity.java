package com.ragadmin.server.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("job_retry_record")
public class TaskRetryRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Integer retryNo;
    private String retryReason;
    private String retryResult;
    private LocalDateTime createdAt;
}
