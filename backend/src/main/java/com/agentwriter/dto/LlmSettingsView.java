package com.agentwriter.dto;

public record LlmSettingsView(
        boolean enabled,
        String provider,
        String apiUrl,
        String model,
        int timeout,
        boolean credentialConfigured
) {
}
