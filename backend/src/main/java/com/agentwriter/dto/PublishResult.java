package com.agentwriter.dto;

import java.time.LocalDateTime;

/**
 * 发布结果 - Publisher 返回的结构化结果。
 *
 * <p>对应 AGENTS.md §9.4 输出规范：</p>
 * <pre>
 * {
 *   "platform": "wechat",
 *   "url": "https://...",
 *   "publishedAt": "2026-07-03T12:00:00",
 *   "articleId": "xxx",
 *   "coverImage": "media_id"
 * }
 * </pre>
 */
public record PublishResult(
        String platform,
        String url,
        LocalDateTime publishedAt,
        Long articleId,
        String coverImage
) {}
