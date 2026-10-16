package com.agentwriter.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class OpenAiLLMService extends OpenAiCompatibleLLMProvider {

    public OpenAiLLMService(ObjectMapper objectMapper) {
        super("openai", objectMapper);
    }

    public OpenAiLLMService(ObjectMapper objectMapper, String proxyHost, int proxyPort) {
        super("openai", objectMapper, proxyHost, proxyPort);
    }
}
