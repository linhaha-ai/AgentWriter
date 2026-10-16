package com.agentwriter.workflow;

import com.agentwriter.common.RetryPolicy;
import com.agentwriter.dto.ArticleResponse;
import com.agentwriter.dto.GenerationEvent;
import com.agentwriter.dto.TopicResponse;
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

import java.util.ArrayList;
import java.util.LinkedHashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WorkflowEngineGenerationPipelineTest {

    @Mock
    private ArticleService articleService;
    @Mock
    private TopicService topicService;
    @Mock
    private ExecutionLogRepository logRepository;
    @Mock
    private RAGService ragService;
    @Mock
    private ResearchStep research;
    @Mock
    private OutlineStep outline;
    @Mock
    private WriterStep writer;
    @Mock
    private ReviewStep review;
    @Mock
    private SEOStep seo;
    @Mock
    private GenerationEventHub generationEventHub;

    private WorkflowEngine engine;
    private ArrayList<String> observed;
    private Article savedArticle;
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        observed = new ArrayList<>();
        lenient().when(research.name()).thenReturn("research");
        lenient().when(outline.name()).thenReturn("outline");
        lenient().when(writer.name()).thenReturn("writer");
        lenient().when(review.name()).thenReturn("review");
        lenient().when(seo.name()).thenReturn("seo");
        when(logRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(articleService.saveEntity(any())).thenAnswer(invocation -> {
            Article article = invocation.getArgument(0);
            if (article.getId() == null) {
                article.setId(11L);
            }
            savedArticle = article;
            return article;
        });
        when(articleService.startGeneration(any())).thenAnswer(invocation -> {
            savedArticle.setStatus("generating");
            return ArticleResponse.from(savedArticle);
        });
        lenient().when(articleService.completeGeneration(any(), any())).thenAnswer(invocation -> {
            savedArticle.setStatus("generated");
            savedArticle.setContentMd(invocation.getArgument(1));
            return ArticleResponse.from(savedArticle);
        });
        lenient().when(articleService.failGeneration(any())).thenAnswer(invocation -> {
            savedArticle.setStatus("failed");
            return ArticleResponse.from(savedArticle);
        });
        engine = new WorkflowEngine(
                articleService,
                mock(com.agentwriter.repository.ArticleRepository.class),
                topicService,
                logRepository,
                new RetryPolicy(),
                ragService,
                objectMapper,
                generationEventHub,
                research,
                outline,
                writer,
                review,
                seo);
    }

    @Test
    void generatesFromPersistedTopicInExactOrderAndStoresFinalMarkdownOnce() throws Exception {
        when(topicService.findById(42L)).thenReturn(topic(42L, "Persisted Topic"));
        makeSuccessfulPipeline("# Final Markdown");
        when(ragService.storeArticle(11L, "# Final Markdown")).thenReturn(3);

        var response = engine.generateFromTopic(42L);

        assertThat(response.title()).isEqualTo("Persisted Topic");
        assertThat(response.source()).isEqualTo("topic:42");
        assertThat(response.status()).isEqualTo("generated");
        assertThat(response.contentMd()).isEqualTo("# Final Markdown");
        assertThat(observed).containsExactly("research", "outline", "writer", "review", "seo");
        verify(ragService).storeArticle(11L, "# Final Markdown");

        var logs = ArgumentCaptor.forClass(ExecutionLog.class);
        verify(logRepository, org.mockito.Mockito.atLeastOnce()).save(logs.capture());
        var firstSeenSteps = new LinkedHashSet<String>();
        logs.getAllValues().forEach(entry -> firstSeenSteps.add(entry.getStep()));
        assertThat(firstSeenSteps)
                .containsExactly("research", "outline", "writer", "review", "seo", "rag_store");
        assertThat(logs.getAllValues())
                .anySatisfy(entry -> {
                    assertThat(entry.getStep()).isEqualTo("rag_store");
                    assertThat(entry.getStatus()).isEqualTo("success");
                    assertThat(entry.getOutput()).isEqualTo("chunks=3");
                });
    }

    @Test
    void delegatesGenerationLifecycleToConditionalArticleServiceMethods() throws Exception {
        when(topicService.findById(42L)).thenReturn(topic(42L, "Persisted Topic"));
        makeSuccessfulPipeline("# Final Markdown");
        when(ragService.storeArticle(11L, "# Final Markdown")).thenReturn(3);

        engine.generateFromTopic(42L);

        verify(articleService).startGeneration(11L);
        verify(articleService).completeGeneration(11L, "# Final Markdown");
    }

    @Test
    void reviewFailureMarksFailedAndSkipsSeoAndRag() throws Exception {
        when(topicService.findById(42L)).thenReturn(topic(42L, "Persisted Topic"));
        doAnswer(invocation -> setOutput(invocation.getArgument(0), "research")).when(research).execute(any());
        doAnswer(invocation -> setOutput(invocation.getArgument(0), "outline")).when(outline).execute(any());
        doAnswer(invocation -> setOutput(invocation.getArgument(0), "draft")).when(writer).execute(any());
        doThrow(new IllegalArgumentException("review rejected")).when(review).execute(any());

        var response = engine.generateFromTopic(42L);

        assertThat(response.status()).isEqualTo("failed");
        assertThat(response.contentMd()).isNull();
        verify(seo, never()).execute(any());
        verify(ragService, never()).storeArticle(any(), any());
        var logs = ArgumentCaptor.forClass(ExecutionLog.class);
        verify(logRepository, org.mockito.Mockito.atLeastOnce()).save(logs.capture());
        assertThat(logs.getAllValues()).noneMatch(entry -> "rag_store".equals(entry.getStep()));

        var events = ArgumentCaptor.forClass(GenerationEvent.class);
        verify(generationEventHub, org.mockito.Mockito.atLeastOnce()).publishLog(events.capture());
        assertThat(events.getAllValues())
                .anySatisfy(event -> {
                    assertThat(event.log().step()).isEqualTo("review");
                    assertThat(event.log().message()).doesNotContain("review rejected");
                });
        verify(generationEventHub).publishTerminal(org.mockito.ArgumentMatchers.eq("failed"), any());
    }

    @Test
    void publishesCompletionOnlyAfterGeneratedArticleIsPersisted() throws Exception {
        when(topicService.findById(42L)).thenReturn(topic(42L, "Persisted Topic"));
        makeSuccessfulPipeline("# Final Markdown");
        when(ragService.storeArticle(11L, "# Final Markdown")).thenReturn(3);

        engine.generateFromTopic(42L);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(articleService, generationEventHub);
        order.verify(articleService).completeGeneration(11L, "# Final Markdown");
        order.verify(generationEventHub).publishTerminal(
                org.mockito.ArgumentMatchers.eq("completed"),
                org.mockito.ArgumentMatchers.argThat(event ->
                        event.status().equals("generated") && event.generationProgress().progress() == 100));
    }

    @Test
    void ragFailureIsLoggedAndDoesNotUndoGeneratedArticle() throws Exception {
        when(topicService.findById(42L)).thenReturn(topic(42L, "Persisted Topic"));
        makeSuccessfulPipeline("# Final Markdown");
        when(ragService.storeArticle(11L, "# Final Markdown"))
                .thenThrow(new IllegalStateException("vector store unavailable"));

        var response = engine.generateFromTopic(42L);

        assertThat(response.status()).isEqualTo("generated");
        assertThat(response.contentMd()).isEqualTo("# Final Markdown");
        verify(ragService).storeArticle(11L, "# Final Markdown");
        var logs = ArgumentCaptor.forClass(ExecutionLog.class);
        verify(logRepository, org.mockito.Mockito.atLeastOnce()).save(logs.capture());
        assertThat(logs.getAllValues())
                .anySatisfy(entry -> {
                    assertThat(entry.getStep()).isEqualTo("rag_store");
                    assertThat(entry.getStatus()).isEqualTo("failed");
                    assertThat(entry.getErrorMsg()).isEqualTo("vector store unavailable");
                });
    }

    @Test
    void workflowStartUsesExplicitRequestTitle() throws Exception {
        makeSuccessfulPipeline("# Explicit");

        var response = engine.start(new WorkflowRequest(77L, "Explicit Workflow Title", "tech"));

        assertThat(response.status()).isEqualTo("generated");
        var articles = ArgumentCaptor.forClass(Article.class);
        verify(articleService, org.mockito.Mockito.atLeastOnce()).saveEntity(articles.capture());
        assertThat(articles.getAllValues().get(0).getTitle()).isEqualTo("Explicit Workflow Title");
        verify(topicService, never()).findById(any());
    }

    private void makeSuccessfulPipeline(String finalMarkdown) throws Exception {
        doAnswer(invocation -> record(invocation.getArgument(0), "research", "research"))
                .when(research).execute(any());
        doAnswer(invocation -> record(invocation.getArgument(0), "outline", "outline"))
                .when(outline).execute(any());
        doAnswer(invocation -> record(invocation.getArgument(0), "writer", "draft"))
                .when(writer).execute(any());
        doAnswer(invocation -> record(invocation.getArgument(0), "review", "reviewed"))
                .when(review).execute(any());
        doAnswer(invocation -> record(invocation.getArgument(0), "seo", finalMarkdown))
                .when(seo).execute(any());
    }

    private Object record(WorkflowContext context, String step, String output) {
        observed.add(step);
        context.setCurrentOutput(output);
        return null;
    }

    private Object setOutput(WorkflowContext context, String output) {
        context.setCurrentOutput(output);
        return null;
    }

    private static TopicResponse topic(Long id, String title) {
        return new TopicResponse(id, title, "seed", 100, "selected", null, null, null);
    }
}
