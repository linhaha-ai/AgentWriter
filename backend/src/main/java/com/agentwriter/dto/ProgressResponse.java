package com.agentwriter.dto;

import java.util.List;

/**
 * 生成进度响应
 */
public record ProgressResponse(
        Long articleId,
        String status,
        GenerationProgress generationProgress,
        List<GenerationLogResponse> logs
) {}
