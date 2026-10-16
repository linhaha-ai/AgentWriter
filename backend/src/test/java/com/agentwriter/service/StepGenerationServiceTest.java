package com.agentwriter.service;

import com.agentwriter.dto.StepGenerateRequest;
import com.agentwriter.dto.TopicResponse;
import com.agentwriter.dto.WorkflowRequest;
import com.agentwriter.entity.Article;
import com.agentwriter.entity.ExecutionLog;
import com.agentwriter.repository.ArticleRepository;
import com.agentwriter.repository.ExecutionLogRepository;
import com.agentwriter.workflow.WorkflowEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StepGenerationServiceTest {

    @Mock
    private ArticleRepository articleRepository;
    @Mock
    private ArticleService articleService;
    @Mock
    private TopicService topicService;
    @Mock
    private WorkflowEngine workflowEngine;
    @Mock
    private ExecutionLogRepository executionLogRepository;

    private StepGenerationService service;

    @BeforeEach
    void setUp() {
        service = new StepGenerationService(
                articleRepository,
                articleService,
                topicService,
                workflowEngine,
                new ObjectMapper(),
                executionLogRepository);
    }

    @Test
    void keepsUserTitleAndUsesAllSelectedTopicsAsResearchContext() {
        when(topicService.findById(11L)).thenReturn(topic(11L, "话题一"));
        when(topicService.findById(22L)).thenReturn(topic(22L, "话题二"));
        when(articleService.saveEntity(org.mockito.ArgumentMatchers.any(Article.class)))
                .thenAnswer(invocation -> {
                    Article article = invocation.getArgument(0);
                    article.setId(99L);
                    return article;
                });

        Long articleId = service.stepGenerate(
                new StepGenerateRequest(List.of(11L, 22L), "用户填写的文章标题", 3L, 4L));

        assertThat(articleId).isEqualTo(99L);

        var articleCaptor = ArgumentCaptor.forClass(Article.class);
        verify(articleService).saveEntity(articleCaptor.capture());
        assertThat(articleCaptor.getValue())
                .returns("用户填写的文章标题", Article::getTitle)
                .returns("topics:11(+1)", Article::getSource)
                .returns(3L, Article::getTemplateId)
                .returns(4L, Article::getStyleId);

        var workflowCaptor = ArgumentCaptor.forClass(WorkflowRequest.class);
        verify(workflowEngine).startAsync(eq(99L), workflowCaptor.capture());
        var lifecycle = inOrder(articleService, workflowEngine);
        lifecycle.verify(articleService).startGeneration(99L);
        lifecycle.verify(workflowEngine).startAsync(eq(99L), org.mockito.ArgumentMatchers.any());
        assertThat(workflowCaptor.getValue().topicId()).isEqualTo(11L);
        assertThat(workflowCaptor.getValue().title())
                .contains("用户填写的文章标题", "话题一", "话题二");
    }

    @Test
    void returnsOrderedSafeGenerationLogsWithProgressSnapshot() throws Exception {
        Article article = new Article();
        article.setId(41L);
        article.setStatus("generating");
        article.setGenerationProgress(new ObjectMapper().writeValueAsString(
                com.agentwriter.dto.GenerationProgress.running("writer", 2, 5)));
        when(articleRepository.findById(41L)).thenReturn(java.util.Optional.of(article));

        ExecutionLog research = new ExecutionLog();
        research.setId(1L);
        research.setStep("research");
        research.setStatus("success");
        research.setOutput("不应下发");
        ExecutionLog failedWriter = new ExecutionLog();
        failedWriter.setId(2L);
        failedWriter.setStep("writer");
        failedWriter.setStatus("failed");
        failedWriter.setErrorMsg("[RATE_LIMITED] token=synthetic-secret");
        ExecutionLog rag = new ExecutionLog();
        rag.setId(3L);
        rag.setStep("rag_store");
        rag.setStatus("success");
        when(executionLogRepository.findByArticleIdOrderByCreatedAtAsc(41L))
                .thenReturn(List.of(research, failedWriter, rag));

        var response = service.getProgress(41L);

        assertThat(response.articleId()).isEqualTo(41L);
        assertThat(response.generationProgress().currentStep()).isEqualTo("writer");
        assertThat(response.logs()).extracting(com.agentwriter.dto.GenerationLogResponse::step)
                .containsExactly("research", "writer");
        assertThat(response.logs().get(1).message())
                .isEqualTo("模型服务请求频率受限")
                .doesNotContain("synthetic-secret");
    }

    private static TopicResponse topic(Long id, String title) {
        return new TopicResponse(id, title, "manual", 80, "pending", null, null, null);
    }
}
