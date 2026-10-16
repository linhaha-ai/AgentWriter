package com.agentwriter.dto;

import com.agentwriter.entity.Article;
import java.time.LocalDateTime;

public record ArticleResponse(
        Long id,
        String title,
        String contentMd,
        String contentHtml,
        String status,
        String source,
        String coverImage,
        Long templateId,
        Long styleId,
        String generationProgress,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ArticleResponse from(Article article) {
        return new ArticleResponse(
                article.getId(),
                article.getTitle(),
                article.getContentMd(),
                article.getContentHtml(),
                article.getStatus(),
                article.getSource(),
                article.getCoverImage(),
                article.getTemplateId(),
                article.getStyleId(),
                article.getGenerationProgress(),
                article.getCreatedAt(),
                article.getUpdatedAt()
        );
    }
}
