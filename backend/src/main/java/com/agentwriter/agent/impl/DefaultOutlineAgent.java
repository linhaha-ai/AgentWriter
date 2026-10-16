package com.agentwriter.agent.impl;

import com.agentwriter.agent.OutlineAgent;
import com.agentwriter.service.LLMService;
import com.agentwriter.service.PromptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DefaultOutlineAgent implements OutlineAgent {

    private static final Logger log = LoggerFactory.getLogger(DefaultOutlineAgent.class);

    private final LLMService llmService;
    private final PromptService promptService;

    public DefaultOutlineAgent(LLMService llmService, PromptService promptService) {
        this.llmService = llmService;
        this.promptService = promptService;
    }

    @Override
    public String generateOutline(String title, String research) {
        log.info("OutlineAgent.generateOutline() 被调用: title={}", title);

        String systemPrompt = promptService.loadPrompt("outline");
        String userMessage = """
                {
                  "title": "%s",
                  "researchSummary": "%s"
                }
                """.formatted(title, research);

        String response = llmService.generate(systemPrompt, userMessage);
        log.debug("OutlineAgent 返回: {}", response);
        return response;
    }
}
