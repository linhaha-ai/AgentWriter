package com.agentwriter.dto;

import com.agentwriter.entity.ArticleImageRole;

/**
 * 图片生成前供用户确认的建议。
 */
public record ImageSuggestion(
        ArticleImageRole role,
        String headingAnchor,
        String prompt,
        String altText
) {
}
