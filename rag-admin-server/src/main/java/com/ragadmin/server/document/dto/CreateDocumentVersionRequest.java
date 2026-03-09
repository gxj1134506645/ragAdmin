package com.ragadmin.server.document.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateDocumentVersionRequest {

    @NotBlank(message = "storageBucket 不能为空")
    private String storageBucket;

    @NotBlank(message = "storageObjectKey 不能为空")
    private String storageObjectKey;

    private String contentHash;
    private Long fileSize;

    public String getStorageBucket() {
        return storageBucket;
    }

    public void setStorageBucket(String storageBucket) {
        this.storageBucket = storageBucket;
    }

    public String getStorageObjectKey() {
        return storageObjectKey;
    }

    public void setStorageObjectKey(String storageObjectKey) {
        this.storageObjectKey = storageObjectKey;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }
}
