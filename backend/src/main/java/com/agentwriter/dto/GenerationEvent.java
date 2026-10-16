package com.agentwriter.dto;

/**
 * SSE 增量事件。快照事件使用 ProgressResponse，其他事件按需携带单条日志。
 */
public record GenerationEvent(
        Long articleId,
        String status,
        GenerationProgress generationProgress,
        GenerationLogResponse log
) {}
