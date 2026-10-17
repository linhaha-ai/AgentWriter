package com.agentwriter.dto;

import java.util.List;

/**
 * 图库素材视图（面向前端）。
 */
public record MediaAssetView(
        Long id,
        String name,
        String url,
        String prompt,
        String source,
        Long categoryId,
        String categoryName,
        List<String> tags,
        Integer width,
        Integer height,
        Long fileSize,
        String contentType,
        String createdAt,
        String updatedAt
) {
}
