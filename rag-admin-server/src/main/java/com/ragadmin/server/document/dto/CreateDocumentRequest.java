package com.ragadmin.server.document.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
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
}
