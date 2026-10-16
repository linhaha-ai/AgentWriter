package com.agentwriter.workflow;

import com.agentwriter.common.RetryPolicy;
import com.agentwriter.dto.ArticleResponse;
import com.agentwriter.dto.WorkflowRequest;
import com.agentwriter.entity.Article;
import com.agentwriter.entity.ExecutionLog;
import com.agentwriter.repository.ExecutionLogRepository;
import com.agentwriter.service.ArticleService;
import com.agentwriter.service.RAGService;
import com.agentwriter.service.TopicService;
import com.agentwriter.workflow.step.OutlineStep;
import com.agentwriter.workflow.step.ResearchStep;
import com.agentwriter.workflow.step.ReviewStep;
import com.agentwriter.workflow.step.SEOStep;
import com.agentwriter.workflow.step.WriterStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowEngineTest {

    @Mock
    private ArticleService articleService;
    @Mock
    private TopicService topicService;
    @Mock
    private ExecutionLogRepository executionLogRepository;
    @Mock
    private ResearchStep step1;
    @Mock
    private WriterStep step2;
    @Mock
    private OutlineStep outlineStep;
    @Mock
    private ReviewStep reviewStep;
    @Mock
    private SEOStep seoStep;
    @Mock
    private RAGService ragService;
    @Mock
    private com.agentwriter.repository.ArticleRepository articleRepository;

    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private RetryPolicy retryPolicy;
    private WorkflowEngine engine;

    @BeforeEach
    void setUp() {
        objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        retryPolicy = new RetryPolicy();
        engine = new WorkflowEngine(
                articleService, articleRepository, topicService, executionLogRepository,
                retryPolicy, ragService, objectMapper, step1, outlineStep, step2, reviewStep, seoStep
        );
    }

    @Test
    void shouldExecuteAllStepsSuccessfully() throws Exception {
        when(step1.name()).thenReturn("research");
        when(step2.name()).thenReturn("writer");

        var saved = new Article();
        saved.setId(1L);
        saved.setTitle("Test");
        when(articleService.saveEntity(any())).thenReturn(saved);
        stubLifecycle(saved);

        var log = new ExecutionLog();
        log.setId(10L);
        when(executionLogRepository.save(any())).thenReturn(log);

        var request = new WorkflowRequest(1L, "Test Article", "tech");
        var response = engine.start(request);

        assertThat(response.status()).isEqualTo("generated");
        verify(step1).execute(any(WorkflowContext.class));
        verify(step2).execute(any(WorkflowContext.class));
    }

    @Test
    void shouldStopOnStepFailure() throws Exception {
        when(step1.name()).thenReturn("research");

        var saved = new Article();
        saved.setId(1L);
        when(articleService.saveEntity(any())).thenReturn(saved);
        stubLifecycle(saved);

        var log = new ExecutionLog();
        log.setId(10L);
        when(executionLogRepository.save(any())).thenReturn(log);

        doThrow(new RuntimeException("Research failed")).when(step1).execute(any());

        var request = new WorkflowRequest(1L, "Fail Test", null);
        engine.start(request);

        verify(step1).execute(any(WorkflowContext.class));
        verify(step2, never()).execute(any());
    }

    @Test
    void shouldMarkArticleFailedOnStepError() throws Exception {
        when(step1.name()).thenReturn("research");

        var saved = new Article();
        saved.setId(1L);
        when(articleService.saveEntity(any())).thenReturn(saved);
        stubLifecycle(saved);

        var log = new ExecutionLog();
        log.setId(10L);
        when(executionLogRepository.save(any())).thenReturn(log);

        doThrow(new RuntimeException("Error")).when(step1).execute(any());

        var request = new WorkflowRequest(1L, "Fail Test", null);
        engine.start(request);

        verify(articleService).failGeneration(1L);
    }

    @Test
    void shouldCreateExecutionLogForEachStep() {
        when(step1.name()).thenReturn("research");
        when(step2.name()).thenReturn("writer");

        var saved = new Article();
        saved.setId(1L);
        when(articleService.saveEntity(any())).thenReturn(saved);
        stubLifecycle(saved);

        var log = new ExecutionLog();
        log.setId(10L);
        when(executionLogRepository.save(any())).thenReturn(log);

        var request = new WorkflowRequest(1L, "Log Test", null);
        engine.start(request);

        verify(executionLogRepository, atLeast(4)).save(any(ExecutionLog.class));
    }

    @Test
    void shouldMarkArticleGeneratedOnSuccess() {
        when(step1.name()).thenReturn("research");
        when(step2.name()).thenReturn("writer");

        var saved = new Article();
        saved.setId(1L);
        when(articleService.saveEntity(any())).thenReturn(saved);
        stubLifecycle(saved);

        var log = new ExecutionLog();
        log.setId(10L);
        when(executionLogRepository.save(any())).thenReturn(log);

        var request = new WorkflowRequest(1L, "Success Test", null);
        engine.start(request);

        verify(articleService).completeGeneration(eq(1L), any());
    }

    @Test
    void shouldAlwaysRunRequiredGenerationSteps() {
        when(executionLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var emptyEngine = new WorkflowEngine(
                articleService, articleRepository, topicService, executionLogRepository,
                retryPolicy, ragService, objectMapper, step1, outlineStep, step2, reviewStep, seoStep
        );

        var saved = new Article();
        saved.setId(1L);
        when(articleService.saveEntity(any())).thenReturn(saved);
        stubLifecycle(saved);

        var request = new WorkflowRequest(1L, "Empty", null);
        var response = emptyEngine.start(request);

        verify(articleService).completeGeneration(eq(1L), any());
    }

    private void stubLifecycle(Article article) {
        lenient().when(articleService.startGeneration(article.getId())).thenAnswer(invocation -> {
            article.setStatus("generating");
            return ArticleResponse.from(article);
        });
        lenient().when(articleService.completeGeneration(eq(article.getId()), any())).thenAnswer(invocation -> {
            article.setStatus("generated");
            article.setContentMd(invocation.getArgument(1));
            return ArticleResponse.from(article);
        });
        lenient().when(articleService.failGeneration(article.getId())).thenAnswer(invocation -> {
            article.setStatus("failed");
            return ArticleResponse.from(article);
        });
    }
}
