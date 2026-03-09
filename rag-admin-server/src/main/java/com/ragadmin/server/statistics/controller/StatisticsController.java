package com.ragadmin.server.statistics.controller;

import com.ragadmin.server.common.model.ApiResponse;
import com.ragadmin.server.statistics.dto.KnowledgeBaseChatStatisticsResponse;
import com.ragadmin.server.statistics.dto.ModelCallStatisticsResponse;
import com.ragadmin.server.statistics.service.StatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/statistics")
public class StatisticsController {

    @Autowired
    private StatisticsService statisticsService;

    @GetMapping("/model-calls")
    public ApiResponse<List<ModelCallStatisticsResponse>> modelCalls() {
        return ApiResponse.success(statisticsService.modelCalls());
    }

    @GetMapping("/knowledge-bases/{kbId}/chat")
    public ApiResponse<KnowledgeBaseChatStatisticsResponse> knowledgeBaseChat(@PathVariable Long kbId) {
        return ApiResponse.success(statisticsService.knowledgeBaseChat(kbId));
    }
}
