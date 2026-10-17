package com.agentwriter.dto;

/**
 * 图库标签聚合视图。
 */
public record TagCount(
        String tag,
        long count
) {
}
