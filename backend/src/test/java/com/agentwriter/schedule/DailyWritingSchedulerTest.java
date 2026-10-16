package com.agentwriter.schedule;

import com.agentwriter.agent.TopicAgent;
import com.agentwriter.common.RetryPolicy;
import com.agentwriter.dto.ArticleResponse;
import com.agentwriter.dto.TopicCreateRequest;
import com.agentwriter.dto.TopicResponse;
import com.agentwriter.dto.ScheduleRuntimeConfig;
import com.agentwriter.dto.WorkflowResponse;
import com.agentwriter.entity.Article;
import com.agentwriter.entity.ExecutionLog;
import com.agentwriter.repository.ExecutionLogRepository;
import com.agentwriter.service.ArticleService;
import com.agentwriter.service.RAGService;
import com.agentwriter.service.SettingsService;
import com.agentwriter.service.TopicService;
import com.agentwriter.service.event.ScheduleConfigurationChangedEvent;
import com.agentwriter.workflow.WorkflowEngine;
import com.agentwriter.workflow.step.OutlineStep;
import com.agentwriter.workflow.step.PublishStep;
import com.agentwriter.workflow.step.ResearchStep;
import com.agentwriter.workflow.step.ReviewStep;
import com.agentwriter.workflow.step.SEOStep;
import com.agentwriter.workflow.step.WriterStep;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.CronTrigger;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DailyWritingSchedulerTest {

    @Mock
    private TopicAgent topicAgent;
    @Mock
    private TopicService topicService;
    @Mock
    private WorkflowEngine workflowEngine;
    @Mock
    private TaskScheduler taskScheduler;
    @Mock
    private SettingsService settingsService;
    @Mock
    private ScheduledFuture<?> firstFuture;
    @Mock
    private ScheduledFuture<?> replacementFuture;

    private ObjectMapper objectMapper;
    private DailyWritingScheduler scheduler;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        scheduler = new DailyWritingScheduler(topicAgent, topicService, workflowEngine, objectMapper, taskScheduler, settingsService);
    }

    @Test
    void startupLoadsPersistedScheduleAndOwnsExactlyOneFuture() {
        when(settingsService.getScheduleRuntimeConfig()).thenReturn(new ScheduleRuntimeConfig(true, "0 0 8 * * ?"));
        returnScheduledFuture(firstFuture);

        scheduler.start();

        verify(taskScheduler).schedule(any(Runnable.class), (Trigger) argThat(trigger ->
                trigger instanceof CronTrigger cron && "0 0 8 * * ?".equals(cron.getExpression())));
        verifyNoInteractions(firstFuture);
    }

    @Test
    void disablingCancelsTheOnlyActiveFuture() {
        scheduleInitially();

        scheduler.reschedule(new ScheduleRuntimeConfig(false, "0 0 8 * * ?"));

        verify(firstFuture).cancel(false);
        verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    void cronChangeSchedulesReplacementBeforeCancellingPriorFuture() {
        scheduleInitially();
        returnScheduledFuture(replacementFuture);

        scheduler.reschedule(new ScheduleRuntimeConfig(true, "0 5 8 * * ?"));

        verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(Trigger.class));
        verify(firstFuture).cancel(false);
        verifyNoInteractions(replacementFuture);
    }

    @Test
    void scheduleChangeEventUsesTheSameReschedulePath() {
        returnScheduledFuture(firstFuture);

        scheduler.onScheduleConfigurationChanged(new ScheduleConfigurationChangedEvent(
                new ScheduleRuntimeConfig(true, "0 5 8 * * ?")));

        verify(taskScheduler).schedule(any(Runnable.class), (Trigger) argThat(trigger ->
                trigger instanceof CronTrigger cron && "0 5 8 * * ?".equals(cron.getExpression())));
    }

    @Test
    void failedReplacementKeepsThePriorFutureActive() {
        scheduleInitially();
        returnScheduledFuture(null);

        assertThatThrownBy(() -> scheduler.reschedule(new ScheduleRuntimeConfig(true, "0 5 8 * * ?")))
                .isInstanceOf(IllegalStateException.class);

        verify(firstFuture, never()).cancel(anyBoolean());
        verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    void invalidCronDoesNotCreateOrCancelAnyFuture() {
        scheduleInitially();

        assertThatThrownBy(() -> scheduler.reschedule(new ScheduleRuntimeConfig(true, "invalid cron")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Trigger.class));
        verify(firstFuture, never()).cancel(anyBoolean());
    }

    @Test
    void shouldExecuteFullWorkflow() throws Exception {
        when(topicAgent.collectTrendingTopics()).thenReturn("""
                {"success": true, "data": {"topics": [{"title": "Java 21", "source": "github", "score": 95, "reason": "popular"}]}}
                """);
        when(topicService.create(any(TopicCreateRequest.class)))
                .thenReturn(new TopicResponse(1L, "Java 21", "github", 95, "pending", "popular", null, null));
        when(workflowEngine.start(any()))
                .thenReturn(new WorkflowResponse(1L, "generated", null));

        scheduler.dailyWritingTask();

        verify(topicAgent).collectTrendingTopics();
        verify(topicService).create(any(TopicCreateRequest.class));
        verify(workflowEngine).start(any());
    }

    @Test
    void shouldSkipWhenNoTopics() throws Exception {
        when(topicAgent.collectTrendingTopics()).thenReturn("""
                {"success": true, "data": {"topics": []}}
                """);

        scheduler.dailyWritingTask();

        verify(topicService, never()).create(any());
        verify(workflowEngine, never()).start(any());
    }

    @Test
    void shouldSkipWhenTopicsNull() throws Exception {
        when(topicAgent.collectTrendingTopics()).thenReturn("""
                {"success": true, "data": {"topics": null}}
                """);

        scheduler.dailyWritingTask();

        verify(topicService, never()).create(any());
        verify(workflowEngine, never()).start(any());
    }

    @Test
    void shouldSelectHighestScoreTopic() throws Exception {
        when(topicAgent.collectTrendingTopics()).thenReturn("""
                {"success": true, "data": {"topics": [
                    {"title": "Low Score", "source": "rss", "score": 30, "reason": "low"},
                    {"title": "High Score", "source": "github", "score": 95, "reason": "high"}
                ]}}
                """);
        when(topicService.create(any(TopicCreateRequest.class)))
                .thenReturn(new TopicResponse(2L, "High Score", "github", 95, "pending", "high", null, null));
        when(workflowEngine.start(any()))
                .thenReturn(new WorkflowResponse(2L, "generated", null));

        scheduler.dailyWritingTask();

        verify(topicService).create(argThat(req -> "High Score".equals(req.title())));
    }

    @Test
    void shouldHandleTopicAgentFailure() throws Exception {
        when(topicAgent.collectTrendingTopics()).thenThrow(new RuntimeException("API error"));

        scheduler.dailyWritingTask();

        verify(topicService, never()).create(any());
        verify(workflowEngine, never()).start(any());
    }

    @Test
    void shouldHandleJsonParseError() throws Exception {
        when(topicAgent.collectTrendingTopics()).thenReturn("invalid json");

        scheduler.dailyWritingTask();

        verify(topicService, never()).create(any());
        verify(workflowEngine, never()).start(any());
    }

    @Test
    void overlappingInvocationIsSuppressed() throws Exception {
        when(topicAgent.collectTrendingTopics()).thenAnswer(invocation -> {
            scheduler.dailyWritingTask();
            return """
                    {"success": true, "data": {"topics": [{"title": "Java 21", "source": "github", "score": 95, "reason": "popular"}]}}
                    """;
        });
        when(topicService.create(any(TopicCreateRequest.class)))
                .thenReturn(new TopicResponse(1L, "Java 21", "github", 95, "pending", "popular", null, null));
        when(workflowEngine.start(any())).thenReturn(new WorkflowResponse(1L, "generated", null));

        scheduler.dailyWritingTask();

        verify(topicAgent, times(1)).collectTrendingTopics();
        verify(workflowEngine, times(1)).start(any());
    }

    @Test
    void scheduledProductionGenerationGraphExecutesWithoutPublishStep() throws Exception {
        when(topicAgent.collectTrendingTopics()).thenReturn("""
                {"success": true, "data": {"topics": [{"title": "Near future", "source": "test", "score": 1, "reason": "qa"}]}}
                """);
        when(topicService.create(any(TopicCreateRequest.class)))
                .thenReturn(new TopicResponse(9L, "Near future", "test", 1, "pending", "qa", null, null));

        Article savedArticle = new Article();
        savedArticle.setId(9L);
        savedArticle.setTitle("Near future");
        savedArticle.setStatus("draft");
        ArticleResponse generatedArticle = new ArticleResponse(9L, "Near future", null, null,
                "generated", "topic:9", null, null, null, null, null, null);
        ArticleService articleService = mock(ArticleService.class);
        ExecutionLogRepository executionLogs = mock(ExecutionLogRepository.class);
        RAGService ragService = mock(RAGService.class);
        when(articleService.saveEntity(any(Article.class))).thenReturn(savedArticle);
        when(articleService.completeGeneration(eq(9L), nullable(String.class))).thenReturn(generatedArticle);
        when(executionLogs.save(any(ExecutionLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ragService.storeArticle(eq(9L), nullable(String.class))).thenReturn(0);

        WorkflowEngine productionEngine = new WorkflowEngine(
                articleService, mock(com.agentwriter.repository.ArticleRepository.class), topicService, executionLogs, new RetryPolicy(), ragService, new ObjectMapper(),
                mock(ResearchStep.class), mock(OutlineStep.class), mock(WriterStep.class),
                mock(ReviewStep.class), mock(SEOStep.class)
        );
        DailyWritingScheduler productionScheduler = new DailyWritingScheduler(
                topicAgent, topicService, productionEngine, objectMapper, taskScheduler, settingsService
        );

        productionScheduler.dailyWritingTask();

        verify(articleService).startGeneration(9L);
        verify(articleService).completeGeneration(eq(9L), nullable(String.class));
        assertThat(Arrays.stream(WorkflowEngine.class.getConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes())))
                .doesNotContain(PublishStep.class);
    }

    private void scheduleInitially() {
        returnScheduledFuture(firstFuture);
        scheduler.reschedule(new ScheduleRuntimeConfig(true, "0 0 8 * * ?"));
    }

    private void returnScheduledFuture(ScheduledFuture<?> future) {
        doReturn(future).when(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
    }
}
