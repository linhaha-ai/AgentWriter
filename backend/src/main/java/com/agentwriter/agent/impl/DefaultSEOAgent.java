package com.agentwriter.agent.impl;

import com.agentwriter.agent.SEOAgent;
import com.agentwriter.service.LLMService;
import com.agentwriter.service.PromptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DefaultSEOAgent implements SEOAgent {

    private static final Logger log = LoggerFactory.getLogger(DefaultSEOAgent.class);

    private final LLMService llmService;
    private final PromptService promptService;

    public DefaultSEOAgent(LLMService llmService, PromptService promptService) {
        this.llmService = llmService;
        this.promptService = promptService;
    }

    @Override
    public String optimize(String content) {
        log.info("SEOAgent.optimize() 被调用");

        String systemPrompt = promptService.loadPrompt("seo");
        String userMessage = """
                {
                  "content": "%s"
                }
                """.formatted(content);

        String response = llmService.generate(systemPrompt, userMessage);
        log.debug("SEOAgent 返回: {}", response);
        return response;
    }
}
