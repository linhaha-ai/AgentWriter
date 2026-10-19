package com.agentwriter.dto;

import com.agentwriter.entity.ExecutionLog;

import java.time.LocalDateTime;

/**
 * 生成环节日志详情：记录每个环节"做了什么（输入）"与"产出什么（输出）"。
 */
public record GenerationLogDetail(
        Long id,
        String step,
        String agentName,
        String status,
        String input,
        String output,
        String errorMsg,
        Long elapsedMs,
        LocalDateTime createdAt
) {
    public static GenerationLogDetail from(ExecutionLog log) {
        return new GenerationLogDetail(
                log.getId(),
                log.getStep(),
                log.getAgentName(),
                log.getStatus(),
                log.getInput(),
                log.getOutput(),
                log.getErrorMsg(),
                log.getElapsedMs(),
                log.getCreatedAt()
        );
    }
}
