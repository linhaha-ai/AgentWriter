package com.agentwriter.schedule;

import com.agentwriter.agent.TopicAgent;
import com.agentwriter.common.GlobalExceptionHandler;
import com.agentwriter.controller.SettingsController;
import com.agentwriter.dto.ScheduleSettingsUpdate;
import com.agentwriter.dto.TopicResponse;
import com.agentwriter.dto.WorkflowResponse;
import com.agentwriter.repository.SystemConfigRepository;
import com.agentwriter.service.SettingsService;
import com.agentwriter.service.TopicService;
import com.agentwriter.workflow.WorkflowEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SchedulerRuntimeHttpAcceptanceTest {

    private AnnotationConfigApplicationContext context;
    private ThreadPoolTaskScheduler taskScheduler;

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
        if (taskScheduler != null) {
            taskScheduler.shutdown();
            assertThat(taskScheduler.isRunning()).isFalse();
        }
    }

    @Test
    void persistedScheduleSurvivesInvalidHttpThenDisablesAndReenablesToOneRealFuture() throws Exception {
        String persistedCron = cronAt(ZonedDateTime.now().plusSeconds(4));
        RuntimeFixture fixture = startRuntime(persistedCron);
        CountDownLatch firstGeneration = new CountDownLatch(1);
        CountDownLatch secondGeneration = new CountDownLatch(1);
        AtomicInteger generations = new AtomicInteger();
        when(fixture.workflowEngine().start(any())).thenAnswer(invocation -> {
            if (generations.incrementAndGet() == 1) {
                firstGeneration.countDown();
            } else {
                secondGeneration.countDown();
            }
            return new WorkflowResponse(1L, "generated", null);
        });
        fixture.dailyWritingScheduler().start();
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                        new SettingsController(fixture.settingsService(), fixture.objectMapper()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        assertThat(taskScheduler.getScheduledThreadPoolExecutor().getQueue()).hasSize(1);
        mockMvc.perform(put("/api/settings/schedule")
                        .contentType(APPLICATION_JSON)
                        .content("{\"enabled\":true,\"cron\":\"invalid cron\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        assertThat(taskScheduler.getScheduledThreadPoolExecutor().getQueue()).hasSize(1);
        assertThat(firstGeneration.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(generations).hasValue(1);

        mockMvc.perform(put("/api/settings/schedule")
                        .contentType(APPLICATION_JSON)
                        .content("{\"enabled\":false,\"cron\":\"" + persistedCron + "\"}"))
                .andExpect(status().isOk());
        assertThat(taskScheduler.getScheduledThreadPoolExecutor().getQueue()).isEmpty();

        String reenabledCron = cronAt(ZonedDateTime.now().plusSeconds(4));
        mockMvc.perform(put("/api/settings/schedule")
                        .contentType(APPLICATION_JSON)
                        .content("{\"enabled\":true,\"cron\":\"" + reenabledCron + "\"}"))
                .andExpect(status().isOk());
        assertThat(taskScheduler.getScheduledThreadPoolExecutor().getQueue()).hasSize(1);
        assertThat(secondGeneration.await(10, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(300);
        assertThat(generations).hasValue(2);
    }

    @Test
    void startupUsesApplicationDefaultWhenNoPersistedScheduleExists() {
        RuntimeFixture fixture = startRuntime(null);

        fixture.dailyWritingScheduler().start();

        assertThat(fixture.settingsService().getScheduleRuntimeConfig().enabled()).isTrue();
        assertThat(fixture.settingsService().getScheduleRuntimeConfig().cron()).isEqualTo("0 0 8 * * ?");
        assertThat(taskScheduler.getScheduledThreadPoolExecutor().getQueue()).hasSize(1);
    }

    private RuntimeFixture startRuntime(String persistedCron) {
        taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(1);
        taskScheduler.setThreadNamePrefix("task-9-scheduler-");
        taskScheduler.setRemoveOnCancelPolicy(true);
        taskScheduler.initialize();

        SystemConfigRepository repository = mock(SystemConfigRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        if (persistedCron == null) {
            when(repository.findById("schedule")).thenReturn(Optional.empty());
        } else {
            String payload;
            try {
                payload = objectMapper.writeValueAsString(new ScheduleSettingsUpdate(true, persistedCron));
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
            when(repository.findById("schedule")).thenReturn(Optional.of(
                    new com.agentwriter.entity.SystemConfig("schedule", payload)
            ));
        }

        TopicAgent topicAgent = mock(TopicAgent.class);
        TopicService topicService = mock(TopicService.class);
        WorkflowEngine workflowEngine = mock(WorkflowEngine.class);
        try {
            when(topicAgent.collectTrendingTopics()).thenReturn("""
                    {"success": true, "data": {"topics": [{"title": "Scheduler topic", "source": "test", "score": 1, "reason": "qa"}]}}
                    """);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        when(topicService.create(any())).thenReturn(new TopicResponse(
                1L, "Scheduler topic", "test", 1, "pending", "qa", null, null
        ));

        context = new AnnotationConfigApplicationContext();
        context.registerBean(SystemConfigRepository.class, () -> repository);
        context.registerBean(ObjectMapper.class, () -> objectMapper);
        context.registerBean(org.springframework.scheduling.TaskScheduler.class, () -> taskScheduler);
        context.registerBean(TopicAgent.class, () -> topicAgent);
        context.registerBean(TopicService.class, () -> topicService);
        context.registerBean(WorkflowEngine.class, () -> workflowEngine);
        context.register(SettingsService.class, DailyWritingScheduler.class);
        context.refresh();

        return new RuntimeFixture(
                context.getBean(SettingsService.class),
                context.getBean(DailyWritingScheduler.class),
                workflowEngine,
                objectMapper
        );
    }

    private static String cronAt(ZonedDateTime time) {
        return "%d %d %d %d %d ?".formatted(
                time.getSecond(), time.getMinute(), time.getHour(), time.getDayOfMonth(), time.getMonthValue()
        );
    }

    private record RuntimeFixture(
            SettingsService settingsService,
            DailyWritingScheduler dailyWritingScheduler,
            WorkflowEngine workflowEngine,
            ObjectMapper objectMapper
    ) {
    }
}
