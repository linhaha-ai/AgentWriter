package com.agentwriter.workflow.step;

import com.agentwriter.agent.WriterAgent;
import com.agentwriter.workflow.WorkflowContext;
import com.agentwriter.workflow.WorkflowStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 写作步骤 - 调用 WriterAgent 根据大纲撰写全文
 */
@Component
public class WriterStep implements WorkflowStep {

    private static final Logger log = LoggerFactory.getLogger(WriterStep.class);

    private final WriterAgent writerAgent;

    public WriterStep(WriterAgent writerAgent) {
        this.writerAgent = writerAgent;
    }

    @Override
    public String name() {
        return "writer";
    }

    @Override
    public void execute(WorkflowContext context) throws Exception {
        var article = context.getArticle();
        log.info("撰写正文: articleId={}, title={}, styleId={}", article.getId(), article.getTitle(), article.getStyleId());

        var content = writerAgent.write(article.getTitle(), context.getCurrentOutput(), article.getStyleId());
        context.setCurrentOutput(content);
    }
}
