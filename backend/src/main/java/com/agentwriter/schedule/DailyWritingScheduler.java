package com.agentwriter.schedule;

import com.agentwriter.agent.TopicAgent;
import com.agentwriter.dto.ScheduleRuntimeConfig;
import com.agentwriter.dto.WorkflowRequest;
import com.agentwriter.service.SettingsService;
import com.agentwriter.service.TopicService;
import com.agentwriter.service.event.ScheduleConfigurationChangedEvent;
import com.agentwriter.workflow.WorkflowEngine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 每日定时写作任务 - 自动选题并驱动完整写作工作流。
 *
 * <p>调度规则（默认）：每天上午 8:00 执行一次，自动收集热度话题、
 * 选择最高分话题、触发完整写作流水线。</p>
 *
 * <p>可通过配置关闭：agentwriter.schedule.enabled=false</p>
 */
@Component
public class DailyWritingScheduler {

    private static final Logger log = LoggerFactory.getLogger(DailyWritingScheduler.class);

    private final TopicAgent topicAgent;
    private final TopicService topicService;
    private final WorkflowEngine workflowEngine;
    private final ObjectMapper objectMapper;
    private final TaskScheduler taskScheduler;
    private final SettingsService settingsService;
    private final Object scheduleMonitor = new Object();
    private final AtomicBoolean taskRunning = new AtomicBoolean();

    private ScheduledFuture<?> scheduledFuture;

    public DailyWritingScheduler(TopicAgent topicAgent,
                                  TopicService topicService,
                                  WorkflowEngine workflowEngine,
                                  ObjectMapper objectMapper,
                                  TaskScheduler taskScheduler,
                                  SettingsService settingsService) {
        this.topicAgent = topicAgent;
        this.topicService = topicService;
        this.workflowEngine = workflowEngine;
        this.objectMapper = objectMapper;
        this.taskScheduler = taskScheduler;
        this.settingsService = settingsService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        reschedule(settingsService.getScheduleRuntimeConfig());
    }

    @EventListener
    public void onScheduleConfigurationChanged(ScheduleConfigurationChangedEvent event) {
        reschedule(event.config());
    }

    public void reschedule(ScheduleRuntimeConfig config) {
        if (config == null || config.cron() == null || !CronExpression.isValidExpression(config.cron())) {
            throw new IllegalArgumentException("调度 cron 表达式无效");
        }

        synchronized (scheduleMonitor) {
            if (!config.enabled()) {
                cancelCurrentFuture();
                return;
            }

            ScheduledFuture<?> replacement = taskScheduler.schedule(this::dailyWritingTask, new CronTrigger(config.cron()));
            if (replacement == null) {
                throw new IllegalStateException("任务调度器未接受新的调度配置");
            }

            ScheduledFuture<?> previous = scheduledFuture;
            scheduledFuture = replacement;
            if (previous != null) {
                previous.cancel(false);
            }
        }
    }

    private void cancelCurrentFuture() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
            scheduledFuture = null;
        }
    }

    /**
     * 每日定时执行：收集话题 → 选择最佳话题 → 启动写作工作流。
     *
     * <p>cron 说明：秒 分 时 日 月 周<br>
     * 默认 0 0 8 * * ? = 每天早上 8:00</p>
     */
    public void dailyWritingTask() {
        if (!taskRunning.compareAndSet(false, true)) {
            log.warn("每日写作任务仍在执行，跳过重叠触发");
            return;
        }

        log.info("===== 每日写作任务开始 =====");

        try {
            // 步骤 1：收集热度话题
            log.info("步骤 1：收集热度话题...");
            String topicsJson = topicAgent.collectTrendingTopics();
            JsonNode root = objectMapper.readTree(topicsJson);
            JsonNode topics = root.path("data").path("topics");

            if (topics == null || !topics.isArray() || topics.isEmpty()) {
                log.warn("未收集到任何话题，跳过本次写作");
                return;
            }

            // 步骤 2：选择评分最高的话题
            JsonNode bestTopic = null;
            int bestScore = -1;
            for (JsonNode topic : topics) {
                int score = topic.path("score").asInt(0);
                if (score > bestScore) {
                    bestScore = score;
                    bestTopic = topic;
                }
            }

            if (bestTopic == null) {
                log.warn("无法选择最佳话题，跳过本次写作");
                return;
            }

            String title = bestTopic.path("title").asText("未命名话题");
            String source = bestTopic.path("source").asText("unknown");
            String link = bestTopic.path("link").asText("");
            String reason = bestTopic.path("reason").asText("");
            log.info("步骤 2：选择话题 - title={}, score={}, source={}", title, bestScore, source);

            // 步骤 3：保存话题到数据库
            var createRequest = new com.agentwriter.dto.TopicCreateRequest(title, source, bestScore, link, reason);
            var savedTopic = topicService.create(createRequest);

            // 步骤 4：启动写作工作流
            log.info("步骤 4：启动写作工作流 - topicId={}, title={}", savedTopic.id(), title);
            var workflowRequest = new WorkflowRequest(savedTopic.id(), title, null);
            var response = workflowEngine.start(workflowRequest);

            log.info("===== 每日写作任务完成: articleId={}, status={} =====",
                    response.articleId(), response.status());

        } catch (Exception e) {
            log.error("每日写作任务失败: {}", e.getMessage(), e);
        } finally {
            taskRunning.set(false);
        }
    }
}
