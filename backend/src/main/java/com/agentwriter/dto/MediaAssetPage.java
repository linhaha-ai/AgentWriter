package com.agentwriter.dto;

import java.util.List;

/**
 * 图库分页结果（与前端 {@code MediaAssetPage} 类型对应）。
 */
public record MediaAssetPage(
        List<MediaAssetView> items,
        int page,
        int pageSize,
        long total
) {
}
