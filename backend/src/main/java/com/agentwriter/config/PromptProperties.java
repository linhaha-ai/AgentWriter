package com.agentwriter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 提示词配置属性 - 对应 application.yml 中的 agentwriter.prompts.*
 */
@Component
@ConfigurationProperties(prefix = "agentwriter.prompts")
public class PromptProperties {

    /** 提示词文件所在目录（classpath 路径） */
    private String location = "classpath:prompts/";

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}
