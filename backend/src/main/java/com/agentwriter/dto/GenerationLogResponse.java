package com.agentwriter.dto;

import com.agentwriter.common.ErrorCategory;
import com.agentwriter.entity.ExecutionLog;

import java.time.LocalDateTime;

/**
 * 面向前端的生成阶段日志。包含该阶段的实际产出内容（output），用于生成过程中逐环节展示。
 */
public record GenerationLogResponse(
        Long id,
        String step,
        String status,
        String message,
        String output,
        LocalDateTime createdAt
) {
    public static GenerationLogResponse from(ExecutionLog log) {
        return new GenerationLogResponse(
                log.getId(),
                log.getStep(),
                log.getStatus(),
                messageFor(log),
                log.getOutput(),
                log.getCreatedAt()
        );
    }

    private static String messageFor(ExecutionLog log) {
        return switch (log.getStatus()) {
            case "pending" -> "等待执行";
            case "running" -> "正在执行";
            case "success", "succeeded" -> "执行完成";
            case "failed" -> failureMessage(log.getErrorMsg());
            default -> "状态已更新";
        };
    }

    private static String failureMessage(String errorMessage) {
        ErrorCategory category = errorCategory(errorMessage);
        return switch (category) {
            case LLM_TIMEOUT -> "模型服务响应超时";
            case NETWORK_ERROR -> "网络连接异常";
            case RATE_LIMITED -> "模型服务请求频率受限";
            case LLM_EMPTY_RESPONSE -> "模型未返回有效内容";
            case JSON_PARSE_ERROR -> "模型结果解析失败";
            case INVALID_INPUT -> "生成参数无效";
            case AUTH_ERROR -> "模型服务认证失败";
            case QUALITY_FAILURE -> "内容质量未达到要求";
            case DATABASE_ERROR -> "数据存储异常";
            case UNKNOWN -> "步骤执行失败，请检查服务端日志";
        };
    }

    private static ErrorCategory errorCategory(String errorMessage) {
        if (errorMessage == null || !errorMessage.startsWith("[")) {
            return ErrorCategory.UNKNOWN;
        }
        int closingBracket = errorMessage.indexOf(']');
        if (closingBracket < 2) {
            return ErrorCategory.UNKNOWN;
        }
        try {
            return ErrorCategory.valueOf(errorMessage.substring(1, closingBracket));
        } catch (IllegalArgumentException ignored) {
            return ErrorCategory.UNKNOWN;
        }
    }
}
