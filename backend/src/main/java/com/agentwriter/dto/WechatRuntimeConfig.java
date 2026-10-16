package com.agentwriter.dto;

public record WechatRuntimeConfig(
        boolean enabled,
        String appId,
        String appSecret,
        String author,
        String thumbMediaId
) {
}
