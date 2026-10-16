package com.agentwriter.controller;

import com.agentwriter.common.GlobalExceptionHandler;
import com.agentwriter.dto.ArticleResponse;
import com.agentwriter.dto.PublishResult;
import com.agentwriter.exception.ArticleNotFoundException;
import com.agentwriter.service.ArticlePublicationService;
import com.agentwriter.exception.PublicationFailedException;
import com.agentwriter.service.ArticleService;
import com.agentwriter.exception.ArticleTransitionConflictException;
import com.agentwriter.entity.ArticleStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ArticleControllerTest {

    private ArticleService articleService;
    private ArticlePublicationService articlePublicationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        articleService = mock(ArticleService.class);
        articlePublicationService = mock(ArticlePublicationService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ArticleController(articleService, articlePublicationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void mapsUnknownArticleTo404() throws Exception {
        when(articleService.findById(99L)).thenThrow(new ArticleNotFoundException(99L));

        mockMvc.perform(get("/api/articles/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void mapsTransitionConflictTo409() throws Exception {
        when(articlePublicationService.publish(1L)).thenThrow(
                new ArticleTransitionConflictException(
                        1L, ArticleStatus.GENERATED, ArticleStatus.PUBLISHING));

        mockMvc.perform(post("/api/articles/1/publish"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void publishCommandReportsPublishingAndNeverPublished() throws Exception {
        when(articlePublicationService.publish(1L)).thenReturn(
                new PublishResult("wechat", null, LocalDateTime.of(2026, 7, 27, 0, 0), 1L, null));

        mockMvc.perform(post("/api/articles/1/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.platform").value("wechat"))
                .andExpect(jsonPath("$.data.url").doesNotExist());
    }

    @Test
    void mapsProviderFailureTo503WithoutLeakingProviderDetails() throws Exception {
        when(articlePublicationService.publish(1L)).thenThrow(
                new PublicationFailedException("文章发布失败", new IllegalStateException("token=synthetic-secret")));

        mockMvc.perform(post("/api/articles/1/publish"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("发布服务暂不可用"));
    }

    @Test
    void arbitraryStatusMutationRouteIsNotAvailable() throws Exception {
        mockMvc.perform(patch("/api/articles/1/status").param("status", "published"))
                .andExpect(status().isNotFound());
    }

    @Test
    void malformedStatusFilterMapsTo400() throws Exception {
        when(articleService.findByStatus("published"))
                .thenThrow(new IllegalArgumentException("未知文章状态: published"));

        mockMvc.perform(get("/api/articles").param("status", "published"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    private static ArticleResponse response(Long id, String status) {
        return new ArticleResponse(
                id, "Test", null, null, status, "manual", null, null, null, null, null, null);
    }
}
