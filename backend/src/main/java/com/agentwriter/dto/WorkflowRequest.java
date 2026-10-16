package com.agentwriter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 触发完整写作工作流的请求
 */
public record WorkflowRequest(
        @NotNull Long topicId,
        @NotBlank String title,
        String style // 可选：文章风格
) {}
