package com.agentwriter.agent.impl;

import com.agentwriter.agent.WriterAgent;
import com.agentwriter.entity.WriterStyle;
import com.agentwriter.service.LLMService;
import com.agentwriter.service.PromptService;
import com.agentwriter.service.WriterStyleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DefaultWriterAgent implements WriterAgent {

    private static final Logger log = LoggerFactory.getLogger(DefaultWriterAgent.class);

    private final LLMService llmService;
    private final PromptService promptService;
    private final WriterStyleService writerStyleService;

    public DefaultWriterAgent(LLMService llmService,
                              PromptService promptService,
                              WriterStyleService writerStyleService) {
        this.llmService = llmService;
        this.promptService = promptService;
        this.writerStyleService = writerStyleService;
    }

    @Override
    public String write(String title, String outline) {
        return write(title, outline, null);
    }

    /**
     * 带风格的工作流：根据 styleId 注入写作风格提示词
     */
    public String write(String title, String outline, Long styleId) {
        log.info("WriterAgent.write() 被调用: title={}, styleId={}", title, styleId);

        String basePrompt = promptService.loadPrompt("writer");
        String stylePrompt = "";

        // 如果指定了写作风格，加载其 tonePrompt 并拼接到 system prompt 中
        if (styleId != null) {
            try {
                WriterStyle style = writerStyleService.findById(styleId);
                if (style.getTonePrompt() != null && !style.getTonePrompt().isBlank()) {
                    stylePrompt = "\n\n【写作风格要求】\n" + style.getTonePrompt();
                    log.info("已应用写作风格: {} (id={})", style.getName(), styleId);
                }
            } catch (Exception e) {
                log.warn("加载写作风格失败, 使用默认风格: styleId={}", styleId, e);
            }
        }

        String systemPrompt = basePrompt + stylePrompt;
        String userMessage = """
                {
                  "title": "%s",
                  "sections": [{"id": "outline", "content": "%s"}],
                  "research": {"summary": "%s"}
                }
                """.formatted(title, outline, outline);

        String response = llmService.generate(systemPrompt, userMessage);
        log.debug("WriterAgent 返回: {} 字符", response != null ? response.length() : 0);
        return response;
    }
}
