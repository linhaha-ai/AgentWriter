package com.agentwriter.agent.impl;

import com.agentwriter.agent.ReviewAgent;
import com.agentwriter.common.LlmResponseParser;
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
        // 信封格式为 {"success":true,"data":{"fixedContent":...}}（部分模型用 content）；
        // 提取修正后的全文供后续 SEO 使用。提取失败时：若响应疑似损坏的 JSON 信封
        // （以 { 开头或带代码围栏），回退到输入的原始文章以免污染下游；否则模型直接
        // 输出的 Markdown 正文本身即可作为结果。
        String fixedContent = LlmResponseParser.extractDataField(response, "fixedContent", "content");
        if (fixedContent != null) {
            return fixedContent;
        }
        return LlmResponseParser.looksLikeJsonEnvelope(response) ? content : response;
    }
}
