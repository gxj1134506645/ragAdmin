package com.ragadmin.server.task.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.ragadmin.server.task.dto.TaskRealtimeEventResponse;
import com.ragadmin.server.task.service.TaskRealtimeEventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/admin/events")
@SaCheckLogin(type = "admin")
public class TaskEventController {

    @Autowired
    private TaskRealtimeEventService taskRealtimeEventService;

    @GetMapping(value = "/knowledge-bases/{kbId}/documents", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @SaCheckPermission("KB_MANAGE")
    public Flux<TaskRealtimeEventResponse> subscribeKnowledgeBaseDocuments(@PathVariable Long kbId) {
        return taskRealtimeEventService.subscribeKnowledgeBase(kbId);
    }

    @GetMapping(value = "/documents/{documentId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @SaCheckPermission("KB_MANAGE")
    public Flux<TaskRealtimeEventResponse> subscribeDocument(@PathVariable Long documentId) {
        return taskRealtimeEventService.subscribeDocument(documentId);
    }

    @GetMapping(value = "/tasks", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @SaCheckPermission("TASK_VIEW")
    public Flux<TaskRealtimeEventResponse> subscribeTasks() {
        return taskRealtimeEventService.subscribeTasks();
    }
}
