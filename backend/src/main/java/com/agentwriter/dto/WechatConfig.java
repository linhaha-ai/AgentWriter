package com.agentwriter.dto;

public record WechatConfig(
        boolean enabled,
        String appId,
        String appSecret,
        String author
) {
}
