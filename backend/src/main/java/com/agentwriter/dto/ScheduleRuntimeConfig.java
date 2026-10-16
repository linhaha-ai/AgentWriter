package com.agentwriter.dto;

public record ScheduleRuntimeConfig(
        boolean enabled,
        String cron
) {
}
