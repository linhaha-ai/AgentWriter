package com.agentwriter.dto;

public record DashboardStats(
        int todayArticles,
        int activeAgents,
        int topicCount,
        int publishedToday,
        String systemUptime,
        String lastRunTime,
        String nextRunTime,
        boolean writingEnabled
) {
}
