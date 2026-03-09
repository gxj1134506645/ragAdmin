package com.ragadmin.server.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("job_task_step_record")
public class TaskStepRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private String stepCode;
    private String stepName;
    private String stepStatus;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
}
