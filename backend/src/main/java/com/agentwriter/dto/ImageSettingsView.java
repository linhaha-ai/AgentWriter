package com.agentwriter.dto;

/**
 * 图像生成设置的安全视图（不含密钥）。
 */
public record ImageSettingsView(
        boolean enabled,
        String apiUrl,
        String model,
        String size,
        boolean credentialConfigured
) {
}
