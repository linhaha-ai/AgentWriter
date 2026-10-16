package com.agentwriter.dto;

import com.agentwriter.entity.ArticleImage;
import com.agentwriter.entity.ArticleImageRole;
import com.agentwriter.entity.ArticleImageStatus;

import java.time.LocalDateTime;

/**
 * 暴露给前端的文章图片状态。
 */
public record ArticleImageResponse(
        Long id,
        ArticleImageRole role,
        ArticleImageStatus status,
        String prompt,
        String altText,
        String headingAnchor,
        boolean inserted,
        String contentUrl,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ArticleImageResponse from(ArticleImage image) {
        String contentUrl = image.getStatus() == ArticleImageStatus.READY
                ? "/api/article-images/" + image.getId() + "/content"
                : null;
        return new ArticleImageResponse(
                image.getId(), image.getRole(), image.getStatus(), image.getPrompt(), image.getAltText(),
                image.getHeadingAnchor(), image.isInserted(), contentUrl, image.getErrorMessage(),
                image.getCreatedAt(), image.getUpdatedAt()
        );
    }
}
