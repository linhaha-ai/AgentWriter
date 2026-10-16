package com.agentwriter.dto;

public record WechatSettingsView(
        boolean enabled,
        String appId,
        String author,
        String thumbMediaId,
        boolean credentialConfigured
) {
}
