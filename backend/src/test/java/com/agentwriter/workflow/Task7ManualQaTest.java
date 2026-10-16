package com.agentwriter.workflow;

import com.agentwriter.agent.TopicAgent;
import com.agentwriter.agent.impl.DefaultOutlineAgent;
import com.agentwriter.agent.impl.DefaultResearchAgent;
import com.agentwriter.agent.impl.DefaultReviewAgent;
import com.agentwriter.agent.impl.DefaultSEOAgent;
import com.agentwriter.agent.impl.DefaultWriterAgent;
import com.agentwriter.common.GlobalExceptionHandler;
import com.agentwriter.common.RetryPolicy;
import com.agentwriter.controller.ArticleController;
import com.agentwriter.entity.Article;
import com.agentwriter.entity.ExecutionLog;
import com.agentwriter.entity.Topic;
import com.agentwriter.repository.ArticleRepository;
import com.agentwriter.repository.ExecutionLogRepository;
import com.agentwriter.repository.TopicRepository;
import com.agentwriter.service.ArticleService;
import com.agentwriter.service.LLMService;
import com.agentwriter.service.PromptService;
import com.agentwriter.service.RAGService;
import com.agentwriter.service.WriterStyleService;
import com.agentwriter.service.TopicService;
import com.agentwriter.workflow.step.OutlineStep;
import com.agentwriter.workflow.step.ResearchStep;
import com.agentwriter.workflow.step.ReviewStep;
import com.agentwriter.workflow.step.SEOStep;
import com.agentwriter.workflow.step.WriterStep;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class Task7ManualQaTest {

    @Test
    void seededTopicMockLlmMockMvcAndDataInspection() throws Exception {
        var articles = new LinkedHashMap<Long, Article>();
        var logs = new LinkedHashMap<Long, ExecutionLog>();
        var articleIds = new AtomicInteger(100);
        var logIds = new AtomicInteger(1);
        var articleRepository = mock(ArticleRepository.class);
        var topicRepository = mock(TopicRepository.class);
        var logRepository = mock(ExecutionLogRepository.class);
        var ragService = mock(RAGService.class);
        var topicAgent = mock(TopicAgent.class);
        var writerStyleService = mock(WriterStyleService.class);
        var om = new com.fasterxml.jackson.databind.ObjectMapper();

        when(articleRepository.save(any())).thenAnswer(invocation -> {
            Article article = invocation.getArgument(0);
            if (article.getId() == null) {
                article.setId((long) articleIds.incrementAndGet());
            }
            articles.put(article.getId(), article);
            return article;
        });
        when(articleRepository.findById(any())).thenAnswer(
                invocation -> Optional.ofNullable(articles.get(invocation.getArgument(0))));
        when(articleRepository.transitionStatus(any(), any(), any())).thenAnswer(invocation -> {
            Article article = articles.get(invocation.getArgument(0));
            String expectedStatus = invocation.getArgument(1);
            String targetStatus = invocation.getArgument(2);
            if (article == null || !expectedStatus.equals(article.getStatus())) {
                return 0;
            }
            article.setStatus(targetStatus);
            return 1;
        });
        when(articleRepository.completeGenerationWithContent(any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    Article article = articles.get(invocation.getArgument(0));
                    String expectedStatus = invocation.getArgument(1);
                    String targetStatus = invocation.getArgument(2);
                    if (article == null || !expectedStatus.equals(article.getStatus())) {
                        return 0;
                    }
                    article.setStatus(targetStatus);
                    article.setContentMd(invocation.getArgument(3));
                    return 1;
                });
        when(logRepository.save(any())).thenAnswer(invocation -> {
            ExecutionLog entry = invocation.getArgument(0);
            if (entry.getId() == null) {
                entry.setId((long) logIds.getAndIncrement());
            }
            logs.put(entry.getId(), entry);
            return entry;
        });
        when(logRepository.findByArticleId(any())).thenAnswer(invocation -> logs.values().stream()
                .filter(entry -> entry.getArticleId().equals(invocation.getArgument(0)))
                .toList());

        var topic = new Topic();
        topic.setId(42L);
        topic.setTitle("Seeded Persisted Topic");
        topic.setSource("manual-qa");
        when(topicRepository.findById(42L)).thenReturn(Optional.of(topic));
        when(topicRepository.findById(404L)).thenReturn(Optional.empty());
        when(ragService.storeArticle(any(), any())).thenReturn(2);

        var llm = mock(LLMService.class);
        var llmCalls = new AtomicInteger();
        when(llm.generate(any(), any())).thenAnswer(invocation -> switch (llmCalls.incrementAndGet()) {
            case 1 -> "research-data";
            case 2 -> "outline-data";
            case 3 -> "# Draft Markdown";
            case 4 -> "# Reviewed Markdown";
            case 5 -> "# Final SEO Markdown";
            default -> throw new IllegalStateException("unexpected LLM call");
        });
        var prompts = mock(PromptService.class);
        when(prompts.loadPrompt(any())).thenReturn("deterministic-prompt");

        var articleService = new ArticleService(articleRepository);
        var topicService = new TopicService(topicRepository, topicAgent, om);
        var research = new ResearchStep(new DefaultResearchAgent(llm, prompts));
        var outline = new OutlineStep(new DefaultOutlineAgent(llm, prompts));
        var writer = new WriterStep(new DefaultWriterAgent(llm, prompts, writerStyleService));
        var review = new ReviewStep(new DefaultReviewAgent(llm, prompts));
        var seo = new SEOStep(new DefaultSEOAgent(llm, prompts));
        var engine = new WorkflowEngine(
                articleService,
                articleRepository,
                topicService,
                logRepository,
                new RetryPolicy(),
                ragService,
                new com.fasterxml.jackson.databind.ObjectMapper(),
                research,
                outline,
                writer,
                review,
                seo);
        var mockMvc = MockMvcBuilders
                .standaloneSetup(new ArticleController(articleService, null, engine, null))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/api/articles/generate")
                        .contentType("application/json")
                        .content("{\"topicId\":42}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Seeded Persisted Topic"))
                .andExpect(jsonPath("$.data.status").value("generated"))
                .andExpect(jsonPath("$.data.contentMd").value("# Final SEO Markdown"));
        Long successId = articles.keySet().iterator().next();
        mockMvc.perform(get("/api/articles/{id}", successId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentMd").value("# Final SEO Markdown"));

        List<ExecutionLog> successLogs = logRepository.findByArticleId(successId);
        assertThat(successLogs).extracting(ExecutionLog::getStep)
                .containsExactly("research", "outline", "writer", "review", "seo", "rag_store");
        assertThat(successLogs).extracting(ExecutionLog::getStatus)
                .containsOnly("success");
        verify(ragService).storeArticle(successId, "# Final SEO Markdown");
        System.out.println("MANUAL_SUCCESS articleId=" + successId
                + " title=" + articles.get(successId).getTitle()
                + " status=" + articles.get(successId).getStatus()
                + " content=" + articles.get(successId).getContentMd()
                + " logs=" + steps(successLogs));

        mockMvc.perform(post("/api/articles/generate")
                        .contentType("application/json")
                        .content("{\"topicId\":404}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        assertThat(articles).hasSize(1);
        System.out.println("MANUAL_UNKNOWN_TOPIC http=404 articlesCreated=0");

        llmCalls.set(0);
        var failingReview = mock(ReviewStep.class);
        when(failingReview.name()).thenReturn("review");
        doThrow(new IllegalArgumentException("manual review rejection"))
                .when(failingReview).execute(any());
        var skippedSeo = mock(SEOStep.class);
        var failingEngine = new WorkflowEngine(
                articleService,
                articleRepository,
                topicService,
                logRepository,
                new RetryPolicy(),
                ragService,
                new com.fasterxml.jackson.databind.ObjectMapper(),
                research,
                outline,
                writer,
                failingReview,
                skippedSeo);
        var failureMvc = MockMvcBuilders
                .standaloneSetup(new ArticleController(articleService, null, failingEngine, null))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        failureMvc.perform(post("/api/articles/generate")
                        .contentType("application/json")
                        .content("{\"topicId\":42}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("failed"))
                .andExpect(jsonPath("$.data.contentMd").doesNotExist());
        Long failedId = articles.keySet().stream().skip(1).findFirst().orElseThrow();
        List<ExecutionLog> failureLogs = logRepository.findByArticleId(failedId);
        assertThat(failureLogs).extracting(ExecutionLog::getStep)
                .containsExactly("research", "outline", "writer", "review");
        verify(skippedSeo, times(0)).execute(any());
        verify(ragService, times(1)).storeArticle(any(), any());
        System.out.println("MANUAL_FAILURE articleId=" + failedId
                + " status=" + articles.get(failedId).getStatus()
                + " logs=" + steps(failureLogs)
                + " laterSteps=false ragCalls=0");

        llmCalls.set(0);
        when(ragService.storeArticle(any(), any()))
                .thenThrow(new IllegalStateException("manual vector outage"));
        var ragFailureEngine = new WorkflowEngine(
                articleService,
                articleRepository,
                topicService,
                logRepository,
                new RetryPolicy(),
                ragService,
                new com.fasterxml.jackson.databind.ObjectMapper(),
                research,
                outline,
                writer,
                review,
                seo);
        var ragFailureMvc = MockMvcBuilders
                .standaloneSetup(new ArticleController(articleService, null, ragFailureEngine, null))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        ragFailureMvc.perform(post("/api/articles/generate")
                        .contentType("application/json")
                        .content("{\"topicId\":42}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("generated"))
                .andExpect(jsonPath("$.data.contentMd").value("# Final SEO Markdown"));
        Long ragFailureId = articles.keySet().stream().skip(2).findFirst().orElseThrow();
        List<ExecutionLog> ragFailureLogs = logRepository.findByArticleId(ragFailureId);
        assertThat(ragFailureLogs).extracting(ExecutionLog::getStep)
                .containsExactly("research", "outline", "writer", "review", "seo", "rag_store");
        assertThat(ragFailureLogs.get(ragFailureLogs.size() - 1).getStatus()).isEqualTo("failed");
        assertThat(ragFailureLogs.get(ragFailureLogs.size() - 1).getErrorMsg())
                .isEqualTo("manual vector outage");
        verify(ragService, times(2)).storeArticle(any(), any());
        System.out.println("MANUAL_RAG_FAILURE articleId=" + ragFailureId
                + " status=" + articles.get(ragFailureId).getStatus()
                + " content=" + articles.get(ragFailureId).getContentMd()
                + " logs=" + steps(ragFailureLogs)
                + " ragCalls=1");
    }

    private static String steps(List<ExecutionLog> entries) {
        return entries.stream()
                .map(entry -> entry.getStep() + ":" + entry.getStatus())
                .collect(Collectors.joining(","));
    }
}
