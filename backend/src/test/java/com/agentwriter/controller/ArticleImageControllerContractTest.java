package com.agentwriter.controller;

import com.agentwriter.dto.ArticleImageResponse;
import com.agentwriter.dto.ArticleResponse;
import com.agentwriter.dto.ImageSuggestion;
import com.agentwriter.entity.ArticleImageRole;
import com.agentwriter.entity.ArticleImageStatus;
import com.agentwriter.service.ArticleImageService;
import com.agentwriter.service.ArticlePublicationService;
import com.agentwriter.service.ArticleService;
import com.agentwriter.service.StepGenerationService;
import com.agentwriter.workflow.WorkflowEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ArticleImageControllerContractTest {

    @Mock
    private ArticleService articleService;

    @Mock
    private ArticlePublicationService articlePublicationService;

    @Mock
    private WorkflowEngine workflowEngine;

    @Mock
    private StepGenerationService stepGenerationService;

    @Mock
    private ArticleImageService articleImageService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ArticleController(
                articleService, articlePublicationService, workflowEngine, stepGenerationService, articleImageService
        )).build();
    }

    @Test
    void returnsImageSuggestionsAndQueuesImageTask() throws Exception {
        when(articleImageService.suggest(1L)).thenReturn(List.of(
                new ImageSuggestion(ArticleImageRole.COVER, null, "cover prompt", "封面")
        ));
        when(articleImageService.queue(any(), any())).thenReturn(new ArticleImageResponse(
                2L, ArticleImageRole.COVER, ArticleImageStatus.QUEUED, "cover prompt", "封面", null,
                false, null, null, null, null
        ));

        mockMvc.perform(post("/api/articles/1/image-suggestions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].role").value("COVER"));

        mockMvc.perform(post("/api/articles/1/images")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"COVER","headingAnchor":null,"prompt":"cover prompt","altText":"封面"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("QUEUED"));
    }

    @Test
    void insertsReadyInlineImage() throws Exception {
        when(articleImageService.insert(1L, 2L)).thenReturn(new ArticleResponse(
                1L, "标题", "## 架构\n\n![图](/api/article-images/2/content)", null,
                "generated", "manual", null, null, null, null, null, null, null, null, null
        ));

        mockMvc.perform(post("/api/articles/1/images/2/insert"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.contentMd").value(org.hamcrest.Matchers.containsString("article-images/2/content")));
    }
}
