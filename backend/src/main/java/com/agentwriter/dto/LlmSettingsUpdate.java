package com.agentwriter.dto;

public record LlmSettingsUpdate(
        boolean enabled,
        String provider,
        String apiUrl,
        String model,
        int timeout
) {
}
