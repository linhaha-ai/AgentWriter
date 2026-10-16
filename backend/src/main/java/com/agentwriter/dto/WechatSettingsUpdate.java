package com.agentwriter.dto;

public record WechatSettingsUpdate(
        boolean enabled,
        String appId,
        String author,
        String thumbMediaId
) {
}
