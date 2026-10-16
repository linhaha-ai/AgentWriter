package com.agentwriter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ComfyUI 图片生成运行配置。
 */
@Component
@ConfigurationProperties(prefix = "agentwriter.image")
public class ImageProperties {

    private boolean enabled;
    private String comfyBaseUrl = "http://localhost:8188";
    private String workflowTemplate = "";
    private String storageDirectory = "data/generated-images";
    private int pollIntervalMillis = 1000;
    private int timeoutSeconds = 300;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getComfyBaseUrl() {
        return comfyBaseUrl;
    }

    public void setComfyBaseUrl(String comfyBaseUrl) {
        this.comfyBaseUrl = comfyBaseUrl;
    }

    public String getWorkflowTemplate() {
        return workflowTemplate;
    }

    public void setWorkflowTemplate(String workflowTemplate) {
        this.workflowTemplate = workflowTemplate;
    }

    public String getStorageDirectory() {
        return storageDirectory;
    }

    public void setStorageDirectory(String storageDirectory) {
        this.storageDirectory = storageDirectory;
    }

    public int getPollIntervalMillis() {
        return pollIntervalMillis;
    }

    public void setPollIntervalMillis(int pollIntervalMillis) {
        this.pollIntervalMillis = pollIntervalMillis;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
