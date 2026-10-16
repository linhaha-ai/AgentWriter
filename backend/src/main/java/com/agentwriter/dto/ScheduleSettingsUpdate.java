package com.agentwriter.dto;

public record ScheduleSettingsUpdate(
        boolean enabled,
        String cron
) {
}
