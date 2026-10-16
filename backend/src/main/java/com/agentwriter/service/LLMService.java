package com.agentwriter.service;

/**
 * LLM 调用服务 - 统一封装 LLM API 调用
 */
public interface LLMService {

    /**
     * 调用 LLM 生成文本
     * @param systemPrompt 系统提示词
     * @param userMessage 用户消息
     * @return LLM 返回的文本
     */
    String generate(String systemPrompt, String userMessage);
}
