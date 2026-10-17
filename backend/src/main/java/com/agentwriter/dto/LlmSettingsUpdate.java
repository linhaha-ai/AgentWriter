package com.agentwriter.dto;

public record LlmSettingsUpdate(
        boolean enabled,
        String provider,
        String apiUrl,
        String model,
        int timeout,
        /** 界面手动填写的 API Key：null=保持不变，空串=清除已保存凭据，非空=覆盖 */
        String apiKey
) {
}
