package com.agentwriter.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 后台风格创建/更新请求
 */
public record AdminStyleRequest(
        @NotBlank String name,
        String description,
        String tonePrompt,
        String features,
        Boolean enabled
) {}