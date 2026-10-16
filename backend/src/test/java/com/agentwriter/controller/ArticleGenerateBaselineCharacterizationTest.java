package com.agentwriter.controller;

import com.agentwriter.common.GlobalExceptionHandler;
import com.agentwriter.dto.ArticleResponse;
import com.agentwriter.service.ArticlePublicationService;
import com.agentwriter.service.ArticleService;
import com.agentwriter.service.StepGenerationService;
import com.agentwriter.workflow.WorkflowEngine;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ArticleGenerateBaselineCharacterizationTest {

    @Test
    void currentlyBuildsPlaceholderTitleAndReturnsStartGenerationResult() throws Exception {
        var articleService = mock(ArticleService.class);
        var workflowEngine = mock(WorkflowEngine.class);
        var articlePublicationService = mock(ArticlePublicationService.class);
        var stepGenerationService = mock(StepGenerationService.class);
        when(workflowEngine.generateFromTopic(42L))
                .thenReturn(response(3L, "Persisted Topic", "generated", "# Final"));
        var mockMvc = MockMvcBuilders
                .standaloneSetup(new ArticleController(articleService, articlePublicationService, workflowEngine, stepGenerationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/api/articles/generate")
                        .contentType("application/json")
                        .content("{\"topicId\":42}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Persisted Topic"))
                .andExpect(jsonPath("$.data.status").value("generated"));
    }

    private static ArticleResponse response(Long id, String title, String status, String content) {
        return new ArticleResponse(id, title, content, null, status, "manual", null, null, null, null, null, null);
    }
}
