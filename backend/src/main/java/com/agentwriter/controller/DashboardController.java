package com.agentwriter.controller;

import com.agentwriter.common.ApiResponse;
import com.agentwriter.dto.Activity;
import com.agentwriter.dto.AgentStatus;
import com.agentwriter.dto.DashboardStats;
import com.agentwriter.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard/stats")
    public ApiResponse<DashboardStats> getStats() {
        return ApiResponse.ok(dashboardService.getStats());
    }

    @GetMapping("/activities")
    public ApiResponse<List<Activity>> getActivities() {
        return ApiResponse.ok(dashboardService.getRecentActivities());
    }

    @GetMapping("/agents/status")
    public ApiResponse<List<AgentStatus>> getAgentStatuses() {
        return ApiResponse.ok(dashboardService.getAgentStatuses());
    }
}
