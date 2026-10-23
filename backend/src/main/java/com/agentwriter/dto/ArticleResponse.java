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
        LocalDateTime updatedAt,
        Long topicId,       // 引用的热点话题 ID（从 source 解析）
        String topicTitle,  // 话题标题
        Integer topicScore  // 话题热度
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
                article.getUpdatedAt(),
                parseTopicId(article.getSource()),
                null,
                null
        );
    }

    /**
     * 携带话题信息的拷贝：列表/详情接口补齐话题标题与热度后返回。
     */
    public ArticleResponse withTopic(String topicTitle, Integer topicScore) {
        return new ArticleResponse(
                id, title, contentMd, contentHtml, status, source, coverImage,
                templateId, styleId, generationProgress, createdAt, updatedAt,
                topicId, topicTitle, topicScore
        );
    }

    /**
     * 解析 source 中的话题 ID，格式如 "topic:123"；非话题来源返回 null。
     */
    private static Long parseTopicId(String source) {
        if (source == null || !source.startsWith("topic:")) {
            return null;
        }
        try {
            return Long.parseLong(source.substring("topic:".length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
