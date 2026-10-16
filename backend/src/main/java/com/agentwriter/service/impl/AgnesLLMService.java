package com.agentwriter.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Agnes AI（https://apihub.agnes-ai.com/v1，OpenAI 兼容接口）的 LLM 适配器。
 */
public final class AgnesLLMService extends OpenAiCompatibleLLMProvider {

    public AgnesLLMService(ObjectMapper objectMapper) {
        super("agnes", objectMapper);
    }

    public AgnesLLMService(ObjectMapper objectMapper, String proxyHost, int proxyPort) {
        super("agnes", objectMapper, proxyHost, proxyPort);
    }
}
