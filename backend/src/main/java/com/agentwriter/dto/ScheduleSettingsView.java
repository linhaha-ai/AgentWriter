package com.agentwriter.dto;

public record ScheduleSettingsView(
        boolean enabled,
        String cron
) {
}
