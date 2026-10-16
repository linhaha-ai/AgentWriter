package com.agentwriter.dto;

public record LlmRuntimeConfig(
        boolean enabled,
        String provider,
        String apiUrl,
        String model,
        int timeout
) {
}
