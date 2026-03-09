package com.ragadmin.server.document.controller;

import com.ragadmin.server.auth.model.AuthenticatedUser;
import com.ragadmin.server.auth.service.AuthService;
import com.ragadmin.server.common.model.ApiResponse;
import com.ragadmin.server.document.dto.CreateDocumentRequest;
import com.ragadmin.server.document.dto.DocumentResponse;
import com.ragadmin.server.document.dto.ParseDocumentResponse;
import com.ragadmin.server.document.service.DocumentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/knowledge-bases/{kbId}/documents")
    public ApiResponse<DocumentResponse> createDocument(
            @PathVariable Long kbId,
            @Valid @RequestBody CreateDocumentRequest request,
            HttpServletRequest httpServletRequest
    ) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) httpServletRequest.getAttribute(AuthService.REQUEST_ATTRIBUTE);
        return ApiResponse.success(documentService.createDocument(kbId, request, authenticatedUser.userId()));
    }

    @PostMapping("/documents/{documentId}/parse")
    public ApiResponse<ParseDocumentResponse> parseDocument(@PathVariable Long documentId) {
        return ApiResponse.success(documentService.submitParseTask(documentId));
    }
}
