package com.agentwriter.service;

import com.agentwriter.dto.*;
import com.agentwriter.entity.ExecutionLog;
import com.agentwriter.repository.*;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 仪表盘服务 — 聚合统计数据、最近活动、智能体状态
 */
@Service
public class DashboardService {

    private final ArticleRepository articleRepository;
    private final TopicRepository topicRepository;
    private final ExecutionLogRepository executionLogRepository;
    private final SettingsService settingsService;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    public DashboardService(
            ArticleRepository articleRepository,
            TopicRepository topicRepository,
            ExecutionLogRepository executionLogRepository,
            SettingsService settingsService
    ) {
        this.articleRepository = articleRepository;
        this.topicRepository = topicRepository;
        this.executionLogRepository = executionLogRepository;
        this.settingsService = settingsService;
    }

    /** 仪表盘统计 */
    public DashboardStats getStats() {
        var todayStart = LocalDate.now().atStartOfDay();
        var todayArticles = articleRepository.countByCreatedAtAfter(todayStart);
        var publishedToday = articleRepository.countByStatusAndUpdatedAtAfter("published", todayStart);
        var topicCount = (int) topicRepository.count();
        var activeAgents = countActiveAgents(todayStart);
        var uptime = formatUptime();
        var lastRun = getLastRunTime();
        var schedule = settingsService.getScheduleConfig();

        return new DashboardStats(
                (int) todayArticles,
                activeAgents,
                topicCount,
                (int) publishedToday,
                uptime,
                lastRun,
                schedule.cron(),
                schedule.enabled()
        );
    }

    /** 最近活动列表 */
    public List<Activity> getRecentActivities() {
        return executionLogRepository.findTop20ByOrderByCreatedAtDesc().stream()
                .map(this::toActivity)
                .collect(Collectors.toList());
    }

    /** 智能体状态列表 */
    public List<AgentStatus> getAgentStatuses() {
        // 已知智能体列表
        var knownAgents = List.of(
                new AgentDef("topic", "话题智能体"),
                new AgentDef("research", "调研智能体"),
                new AgentDef("writer", "撰写智能体"),
                new AgentDef("review", "审核智能体"),
                new AgentDef("seo", "SEO 智能体"),
                new AgentDef("publisher", "发布智能体")
        );

        // 从执行日志获取每种智能体最近一次运行记录
        var allLogs = executionLogRepository.findAll();

        return knownAgents.stream().map(def -> {
            var latest = allLogs.stream()
                    .filter(l -> def.name.equals(l.getAgentName()))
                    .max(Comparator.comparing(ExecutionLog::getCreatedAt));

            if (latest.isPresent()) {
                var log = latest.get();
                return new AgentStatus(
                        def.name,
                        def.label,
                        log.getStatus(),
                        log.getCreatedAt().format(TIME_FMT),
                        formatDuration(log.getCreatedAt())
                );
            }
            return new AgentStatus(def.name, def.label, "idle", "--", "--");
        }).collect(Collectors.toList());
    }

    // ==================== Internal ====================

    private record AgentDef(String name, String label) {}

    private int countActiveAgents(LocalDateTime since) {
        var logs = executionLogRepository.findAll();
        return (int) logs.stream()
                .filter(l -> l.getCreatedAt() != null && l.getCreatedAt().isAfter(since))
                .map(ExecutionLog::getAgentName)
                .filter(Objects::nonNull)
                .distinct()
                .count();
    }

    private String getLastRunTime() {
        return executionLogRepository.findTop20ByOrderByCreatedAtDesc().stream()
                .findFirst()
                .map(l -> l.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                .orElse("--");
    }

    private String formatUptime() {
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        long hours = TimeUnit.MILLISECONDS.toHours(uptimeMs);
        long mins = TimeUnit.MILLISECONDS.toMinutes(uptimeMs) % 60;
        return hours + "h " + mins + "m";
    }

    private String formatDuration(LocalDateTime from) {
        if (from == null) return "--";
        var seconds = ChronoUnit.SECONDS.between(from, LocalDateTime.now());
        if (seconds < 60) return seconds + "s";
        return (seconds / 60) + "m " + (seconds % 60) + "s";
    }

    private Activity toActivity(ExecutionLog log) {
        String type;
        if ("topic_discovery".equals(log.getStep()) || "topic".equals(log.getAgentName())) {
            type = "topic";
        } else if ("publish".equals(log.getAgentName()) || "publisher".equals(log.getAgentName())) {
            type = "publish";
        } else if ("writer".equals(log.getAgentName())) {
            type = "write";
        } else if ("review".equals(log.getAgentName())) {
            type = "review";
        } else {
            type = "info";
        }

        // 有错误就标记为 error
        if ("failed".equals(log.getStatus()) && log.getErrorMsg() != null) {
            type = "error";
        }

        String message = (type.equals("error"))
                ? "[" + log.getStep() + "] " + log.getErrorMsg()
                : "[" + log.getStep() + "] " + (log.getOutput() != null ? log.getOutput() : "执行完成");

        // 截断过长消息
        if (message.length() > 100) {
            message = message.substring(0, 97) + "...";
        }

        return new Activity(
                log.getId(),
                type,
                message,
                log.getArticleId(),
                log.getCreatedAt() != null ? log.getCreatedAt().format(TIME_FMT) : "--"
        );
    }
}
