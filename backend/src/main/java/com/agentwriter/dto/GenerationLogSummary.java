package com.agentwriter.dto;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 文章生成日志概览：用于"生成日志"总览页列表展示。
 *
 * @param articleId    文章 ID
 * @param title        文章标题
 * @param articleStatus 文章状态（generated / generating / failed 等）
 * @param stepCount    主生成环节数（排除知识入库与发布）
 * @param failedCount  失败环节数
 * @param totalElapsedMs 主环节总耗时（毫秒，可能为空）
 * @param lastRunAt    最近一次执行时间
 */
public record GenerationLogSummary(
        Long articleId,
        String title,
        String articleStatus,
        Long stepCount,
        Long failedCount,
        Long totalElapsedMs,
        LocalDateTime lastRunAt
) {
    public static GenerationLogSummary from(Object[] row) {
        return new GenerationLogSummary(
                ((Number) row[0]).longValue(),
                (String) row[1],
                (String) row[2],
                ((Number) row[3]).longValue(),
                ((Number) row[4]).longValue(),
                row[5] == null ? null : ((Number) row[5]).longValue(),
                row[6] == null ? null : toLocalDateTime(row[6])
        );
    }

    /** 兼容 JDBC 返回的多种时间类型。 */
    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt;
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        }
        return null;
    }
}
