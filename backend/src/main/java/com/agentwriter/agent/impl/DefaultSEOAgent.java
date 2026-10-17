package com.agentwriter.agent.impl;

import com.agentwriter.agent.SEOAgent;
import com.agentwriter.common.LlmResponseParser;
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
        // 信封格式为 {"success":true,"data":{"content":...}}，提取 SEO 优化后的全文。
        // 提取失败时：若响应疑似损坏的 JSON 信封，回退到输入的原始文章以免污染下游；
        // 否则模型直接输出的 Markdown 正文本身即可作为结果。
        String optimizedContent = LlmResponseParser.extractDataField(response, "content");
        if (optimizedContent != null) {
            return optimizedContent;
        }
        return LlmResponseParser.looksLikeJsonEnvelope(response) ? content : response;
    }
}
