package com.agentwriter.controller;

import com.agentwriter.common.ApiResponse;
import com.agentwriter.dto.WorkflowRequest;
import com.agentwriter.dto.WorkflowResponse;
import com.agentwriter.workflow.WorkflowEngine;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workflow")
public class WorkflowController {

    private final WorkflowEngine workflowEngine;

    public WorkflowController(WorkflowEngine workflowEngine) {
        this.workflowEngine = workflowEngine;
    }

    @PostMapping("/start")
    public ApiResponse<WorkflowResponse> start(@Valid @RequestBody WorkflowRequest request) {
        return ApiResponse.ok(workflowEngine.start(request));
    }
}
