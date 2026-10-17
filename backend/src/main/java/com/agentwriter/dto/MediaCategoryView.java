package com.agentwriter.dto;

/**
 * 图库分类视图（含素材计数）。
 */
public record MediaCategoryView(
        Long id,
        String name,
        String description,
        Integer sortOrder,
        Long assetCount,
        String createdAt
) {
}
