package com.ragadmin.server.document.service;

import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.document.dto.UploadUrlRequest;
import com.ragadmin.server.document.dto.UploadUrlResponse;
import com.ragadmin.server.infra.storage.MinioProperties;
import com.ragadmin.server.infra.storage.support.MinioClientFactory;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class FileUploadService {

    @Autowired
    private MinioProperties minioProperties;

    @Autowired
    private MinioClientFactory minioClientFactory;

    public UploadUrlResponse createUploadUrl(UploadUrlRequest request) {
        if (!minioProperties.isConfigured()) {
            throw new BusinessException("MINIO_NOT_CONFIGURED", "MinIO 未完成配置", HttpStatus.BAD_REQUEST);
        }
        try {
            String objectKey = buildObjectKey(request.getBizType(), request.getFileName());
            String uploadUrl = minioClientFactory.createClient().getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(minioProperties.getBucketName())
                            .object(objectKey)
                            .expiry(15 * 60)
                            .build()
            );
            return new UploadUrlResponse(minioProperties.getBucketName(), objectKey, uploadUrl);
        } catch (Exception ex) {
            throw new BusinessException("UPLOAD_URL_GENERATE_FAILED", "生成上传地址失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String buildObjectKey(String bizType, String fileName) {
        String safeFileName = fileName.replaceAll("[^a-zA-Z0-9._-]", "-");
        String datePath = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return bizType.toLowerCase() + "/" + datePath + "/" + UUID.randomUUID() + "/" + safeFileName;
    }
}
