package com.agentwriter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 图片生成与存储运行配置。
 *
 * <p>provider 决定生成通道：{@code comfy} 走本地 ComfyUI（原实现），
 * {@code openai} 走 OpenAI 兼容的 /images/generations 接口（资源图库使用）。</p>
 */
@Component
@ConfigurationProperties(prefix = "agentwriter.image")
public class ImageProperties {

    private boolean enabled;
    /** 生成通道：comfy（本地 ComfyUI）或 openai（OpenAI 兼容接口） */
    private String provider = "comfy";
    /** OpenAI 兼容图像接口地址（如 https://api.example.com/v1） */
    private String apiUrl = "";
    /** OpenAI 兼容图像接口密钥 */
    private String apiKey = "";
    /** 图像模型名（如 gpt-image-1、dall-e-3 或兼容服务模型） */
    private String model = "";
    /** 生成尺寸（如 1024x1024），缺省由服务端决定 */
    private String size = "1024x1024";
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

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
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
