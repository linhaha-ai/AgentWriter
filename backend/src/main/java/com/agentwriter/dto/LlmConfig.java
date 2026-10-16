package com.agentwriter.dto;

public record LlmConfig(
        boolean enabled,
        String provider,
        String apiUrl,
        String apiKey,
        String model,
        int timeout
) {
}
