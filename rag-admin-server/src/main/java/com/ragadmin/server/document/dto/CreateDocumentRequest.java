package com.ragadmin.server.document.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateDocumentRequest {

    @NotBlank(message = "docName 不能为空")
    private String docName;

    @NotBlank(message = "docType 不能为空")
    private String docType;

    @NotBlank(message = "storageBucket 不能为空")
    private String storageBucket;

    @NotBlank(message = "storageObjectKey 不能为空")
    private String storageObjectKey;

    private Long fileSize;
    private String contentHash;

    public String getDocName() {
        return docName;
    }

    public void setDocName(String docName) {
        this.docName = docName;
    }

    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

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

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }
}
