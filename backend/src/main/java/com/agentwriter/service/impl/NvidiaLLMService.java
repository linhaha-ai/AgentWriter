package com.agentwriter.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class NvidiaLLMService extends OpenAiCompatibleLLMProvider {

    public NvidiaLLMService(ObjectMapper objectMapper) {
        super("nvidia", objectMapper);
    }

    public NvidiaLLMService(ObjectMapper objectMapper, String proxyHost, int proxyPort) {
        super("nvidia", objectMapper, proxyHost, proxyPort);
    }
}
