package com.ragadmin.server.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ragadmin.server.audit.dto.AuditLogResponse;
import com.ragadmin.server.audit.entity.AuditLogEntity;
import com.ragadmin.server.audit.mapper.AuditLogMapper;
import com.ragadmin.server.common.model.PageResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class AuditLogService {

    @Autowired
    private AuditLogMapper auditLogMapper;

    public void save(AuditLogEntity entity) {
        auditLogMapper.insert(entity);
    }

    public PageResponse<AuditLogResponse> list(
            String operator,
            String bizType,
            LocalDateTime startTime,
            LocalDateTime endTime,
            long pageNo,
            long pageSize
    ) {
        Page<AuditLogEntity> page = auditLogMapper.selectPage(Page.of(pageNo, pageSize),
                new LambdaQueryWrapper<AuditLogEntity>()
                        .like(StringUtils.hasText(operator), AuditLogEntity::getOperatorUsername, operator)
                        .eq(StringUtils.hasText(bizType), AuditLogEntity::getBizType, bizType)
                        .ge(startTime != null, AuditLogEntity::getCreatedAt, startTime)
                        .le(endTime != null, AuditLogEntity::getCreatedAt, endTime)
                        .orderByDesc(AuditLogEntity::getId));
        return new PageResponse<>(
                page.getRecords().stream().map(this::toResponse).toList(),
                pageNo,
                pageSize,
                page.getTotal()
        );
    }

    private AuditLogResponse toResponse(AuditLogEntity entity) {
        return new AuditLogResponse(
                entity.getId(),
                entity.getOperatorUserId(),
                entity.getOperatorUsername(),
                entity.getActionType(),
                entity.getBizType(),
                entity.getBizId(),
                entity.getRequestMethod(),
                entity.getRequestPath(),
                entity.getRequestIp(),
                entity.getResponseCode(),
                entity.getSuccess(),
                entity.getCreatedAt()
        );
    }
}
