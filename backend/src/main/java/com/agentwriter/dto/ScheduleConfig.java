package com.agentwriter.dto;

public record ScheduleConfig(
        boolean enabled,
        String cron
) {
}
