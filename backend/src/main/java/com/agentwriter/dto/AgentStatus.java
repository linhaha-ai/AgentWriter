package com.agentwriter.dto;

public record AgentStatus(
        String name,
        String label,
        String status,
        String lastRun,
        String duration
) {
}
