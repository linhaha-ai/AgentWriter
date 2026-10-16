package com.agentwriter.agent.impl;

import com.agentwriter.agent.ReviewAgent;
import com.agentwriter.service.LLMService;
import com.agentwriter.service.PromptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DefaultReviewAgent implements ReviewAgent {

    private static final Logger log = LoggerFactory.getLogger(DefaultReviewAgent.class);

    private final LLMService llmService;
    private final PromptService promptService;

    public DefaultReviewAgent(LLMService llmService, PromptService promptService) {
        this.llmService = llmService;
        this.promptService = promptService;
    }

    @Override
    public String review(String title, String content) {
        log.info("ReviewAgent.review() 被调用: title={}", title);

        String systemPrompt = promptService.loadPrompt("review");
        String userMessage = """
                {
                  "title": "%s",
                  "content": "%s"
                }
                """.formatted(title, content);

        String response = llmService.generate(systemPrompt, userMessage);
        log.debug("ReviewAgent 返回: {}", response);
        return response;
    }
}
