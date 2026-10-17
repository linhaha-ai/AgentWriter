package com.agentwriter.dto;

/**
 * 图像生成设置的更新载荷。
 *
 * <p>{@code apiKey} 语义与 LLM 凭据一致：null=保留原值、空串=清除、非空=覆盖。
 * 视图层永不回传密钥，仅以 {@code credentialConfigured} 标识是否已配置。</p>
 */
public record ImageSettingsUpdate(
        boolean enabled,
        String apiUrl,
        String model,
        String size,
        String apiKey
) {
}
