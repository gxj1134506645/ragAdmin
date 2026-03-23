package com.ragadmin.server.app.controller;

import com.ragadmin.server.app.dto.AppChatRequest;
import com.ragadmin.server.app.dto.AppRegenerateChatMessageRequest;
import com.ragadmin.server.app.dto.AppChatSessionResponse;
import com.ragadmin.server.app.dto.AppCreateChatSessionRequest;
import com.ragadmin.server.app.dto.AppUpdateChatSessionRequest;
import com.ragadmin.server.app.dto.AppUpdateSessionKnowledgeBasesRequest;
import com.ragadmin.server.app.service.AppChatService;
import com.ragadmin.server.auth.model.AuthenticatedUser;
import com.ragadmin.server.auth.service.AuthService;
import com.ragadmin.server.chat.dto.ChatFeedbackRequest;
import com.ragadmin.server.chat.dto.ChatMessageResponse;
import com.ragadmin.server.chat.dto.ChatResponse;
import com.ragadmin.server.chat.dto.ChatStreamEventResponse;
import com.ragadmin.server.common.model.ApiResponse;
import com.ragadmin.server.common.model.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api/app/chat")
public class AppChatController {

    @Autowired
    private AppChatService appChatService;

    @PostMapping("/sessions")
    public ApiResponse<AppChatSessionResponse> createSession(
            @Valid @RequestBody AppCreateChatSessionRequest request,
            HttpServletRequest httpServletRequest
    ) {
        return ApiResponse.success(appChatService.createSession(request, currentUser(httpServletRequest)));
    }

    @GetMapping("/sessions")
    public ApiResponse<PageResponse<AppChatSessionResponse>> listSessions(
            @RequestParam(required = false) Long kbId,
            @RequestParam(required = false) String sceneType,
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            HttpServletRequest httpServletRequest
    ) {
        return ApiResponse.success(appChatService.listSessions(kbId, sceneType, currentUser(httpServletRequest), pageNo, pageSize));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<List<ChatMessageResponse>> listMessages(
            @PathVariable Long sessionId,
            HttpServletRequest httpServletRequest
    ) {
        return ApiResponse.success(appChatService.listMessages(sessionId, currentUser(httpServletRequest)));
    }

    @PutMapping("/sessions/{sessionId}")
    public ApiResponse<AppChatSessionResponse> updateSession(
            @PathVariable Long sessionId,
            @Valid @RequestBody AppUpdateChatSessionRequest request,
            HttpServletRequest httpServletRequest
    ) {
        return ApiResponse.success(appChatService.updateSession(
                sessionId,
                request,
                currentUser(httpServletRequest)
        ));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Void> deleteSession(
            @PathVariable Long sessionId,
            HttpServletRequest httpServletRequest
    ) {
        appChatService.deleteSession(sessionId, currentUser(httpServletRequest));
        return ApiResponse.success(null);
    }

    @PutMapping("/sessions/{sessionId}/knowledge-bases")
    public ApiResponse<AppChatSessionResponse> updateSessionKnowledgeBases(
            @PathVariable Long sessionId,
            @RequestBody AppUpdateSessionKnowledgeBasesRequest request,
            HttpServletRequest httpServletRequest
    ) {
        return ApiResponse.success(appChatService.updateSessionKnowledgeBases(
                sessionId,
                request.getSelectedKbIds(),
                currentUser(httpServletRequest)
        ));
    }

    @PostMapping("/sessions/{sessionId}/messages")
    public ApiResponse<ChatResponse> chat(
            @PathVariable Long sessionId,
            @Valid @RequestBody AppChatRequest request,
            HttpServletRequest httpServletRequest
    ) {
        return ApiResponse.success(appChatService.chat(sessionId, request, currentUser(httpServletRequest)));
    }

    @PostMapping(value = "/sessions/{sessionId}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatStreamEventResponse> streamChat(
            @PathVariable Long sessionId,
            @Valid @RequestBody AppChatRequest request,
            HttpServletRequest httpServletRequest
    ) {
        return appChatService.streamChat(sessionId, request, currentUser(httpServletRequest));
    }

    @PostMapping(value = "/messages/{messageId}/regenerate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatStreamEventResponse> regenerateMessage(
            @PathVariable Long messageId,
            @RequestBody AppRegenerateChatMessageRequest request,
            HttpServletRequest httpServletRequest
    ) {
        return appChatService.regenerateMessage(messageId, request, currentUser(httpServletRequest));
    }

    @PostMapping("/messages/{messageId}/feedback")
    public ApiResponse<Void> feedback(
            @PathVariable Long messageId,
            @Valid @RequestBody ChatFeedbackRequest request,
            HttpServletRequest httpServletRequest
    ) {
        appChatService.submitFeedback(messageId, request, currentUser(httpServletRequest));
        return ApiResponse.success(null);
    }

    private AuthenticatedUser currentUser(HttpServletRequest request) {
        return (AuthenticatedUser) request.getAttribute(AuthService.REQUEST_ATTRIBUTE);
    }
}
