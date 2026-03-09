package com.ragadmin.server.document.dto;

public record UploadUrlResponse(String bucket, String objectKey, String uploadUrl) {
}
