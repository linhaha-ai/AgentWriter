package com.agentwriter.dto;

public record Activity(
        long id,
        String type,
        String message,
        Long articleId,
        String time
) {
}
