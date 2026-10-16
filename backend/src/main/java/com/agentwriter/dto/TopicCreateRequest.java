package com.agentwriter.dto;

import jakarta.validation.constraints.NotBlank;

public record TopicCreateRequest(
        @NotBlank String title,
        @NotBlank String source,
        Integer score,
        String link,
        String reason
) {}
