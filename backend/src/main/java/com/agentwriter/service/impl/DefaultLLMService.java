package com.agentwriter.service.impl;

import com.agentwriter.service.LLMService;
import com.agentwriter.exception.LLMUnavailableException;

/**
 * 显式测试夹具，不注册为生产 Spring Bean。
 */
public class DefaultLLMService implements LLMService {

    @Override
    public String generate(String systemPrompt, String userMessage) {
        throw new LLMUnavailableException("默认 LLM 测试夹具不可生成生产响应");
    }
}
