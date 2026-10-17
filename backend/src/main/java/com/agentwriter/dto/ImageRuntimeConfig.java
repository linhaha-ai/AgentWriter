package com.agentwriter.dto;

/**
 * 图像生成运行时配置（不含密钥）。
 */
public record ImageRuntimeConfig(
        boolean enabled,
        String apiUrl,
        String model,
        String size
) {
}
