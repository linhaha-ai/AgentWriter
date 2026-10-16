package com.agentwriter.dto;

import com.agentwriter.entity.ArticleImageRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 提交单张文章图片生成任务。
 */
public record ArticleImageGenerateRequest(
        @NotNull ArticleImageRole role,
        String headingAnchor,
        @NotBlank String prompt,
        @NotBlank String altText
) {
}
