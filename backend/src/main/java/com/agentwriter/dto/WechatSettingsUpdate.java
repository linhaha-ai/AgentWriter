package com.agentwriter.dto;

public record WechatSettingsUpdate(
        boolean enabled,
        String appId,
        String author,
        String thumbMediaId,
        /** 界面手动填写的 AppSecret：null=保持不变，空串=清除已保存凭据，非空=覆盖 */
        String appSecret
) {
}
