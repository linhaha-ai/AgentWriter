package com.agentwriter.controller;

import com.agentwriter.common.GlobalExceptionHandler;
import com.agentwriter.dto.ArticleResponse;
import com.agentwriter.dto.StepGenerateRequest;
import com.agentwriter.service.ArticlePublicationService;
import com.agentwriter.service.ArticleService;
import com.agentwriter.service.StepGenerationService;
import com.agentwriter.exception.TopicNotFoundException;
import com.agentwriter.workflow.WorkflowEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ArticleGenerationContractAcceptanceTest {

    private ArticleService articleService;
    private ArticlePublicationService articlePublicationService;
    private WorkflowEngine workflowEngine;
    private StepGenerationService stepGenerationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        articleService = mock(ArticleService.class);
        articlePublicationService = mock(ArticlePublicationService.class);
        workflowEngine = mock(WorkflowEngine.class);
        stepGenerationService = mock(StepGenerationService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ArticleController(articleService, articlePublicationService, workflowEngine, stepGenerationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void usesPersistedTopicTitleInsteadOfSynthesizingOne() throws Exception {
        when(workflowEngine.generateFromTopic(42L))
                .thenReturn(response(9L, "Persisted Topic", "generated", "# Final"));

        mockMvc.perform(generateRequest(42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Persisted Topic"));
        verify(workflowEngine).generateFromTopic(42L);
    }

    @Test
    void returns404WhenPersistedTopicDoesNotExist() throws Exception {
        when(workflowEngine.generateFromTopic(404L)).thenThrow(new TopicNotFoundException(404L));

        mockMvc.perform(generateRequest(404L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void returnsFinalGeneratedArticleSynchronously() throws Exception {
        when(workflowEngine.generateFromTopic(42L))
                .thenReturn(response(10L, "Persisted Topic", "generated", "# Final"));

        mockMvc.perform(generateRequest(42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Persisted Topic"))
                .andExpect(jsonPath("$.data.status").value("generated"))
                .andExpect(jsonPath("$.data.contentMd").value("# Final"));
    }

    @Test
    void acceptsMultipleTopicsAndUserProvidedTitleForStepGeneration() throws Exception {
        when(stepGenerationService.stepGenerate(any(StepGenerateRequest.class))).thenReturn(19L);

        mockMvc.perform(post("/api/articles/step-generate")
                        .contentType("application/json")
                        .content("""
                                {
                                  "topicIds": [11, 22],
                                  "title": "用户填写的文章标题",
                                  "templateId": 3,
                                  "styleId": 4
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(19));

        verify(stepGenerationService).stepGenerate(
                new StepGenerateRequest(List.of(11L, 22L), "用户填写的文章标题", 3L, 4L));
    }

    @Test
    void rejectsStepGenerationWithoutTopics() throws Exception {
        mockMvc.perform(post("/api/articles/step-generate")
                        .contentType("application/json")
                        .content("""
                                {
                                  "topicIds": [],
                                  "title": "用户填写的文章标题",
                                  "styleId": 4
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(stepGenerationService, never()).stepGenerate(any(StepGenerateRequest.class));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder generateRequest(
            Long topicId) {
        return post("/api/articles/generate")
                .contentType("application/json")
                .content("{\"topicId\":" + topicId + "}");
    }

    private static ArticleResponse response(Long id, String title, String status, String content) {
        return new ArticleResponse(id, title, content, null, status, "topic:42", null, null, null, null, null, null, null, null, null);
    }
}
