package com.agentwriter.workflow.step;

import com.agentwriter.agent.OutlineAgent;
import com.agentwriter.workflow.WorkflowContext;
import com.agentwriter.workflow.WorkflowStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 大纲生成步骤 - 调用 OutlineAgent 生成文章大纲
 */
@Component
public class OutlineStep implements WorkflowStep {

    private static final Logger log = LoggerFactory.getLogger(OutlineStep.class);

    private final OutlineAgent outlineAgent;

    public OutlineStep(OutlineAgent outlineAgent) {
        this.outlineAgent = outlineAgent;
    }

    @Override
    public String name() {
        return "outline";
    }

    @Override
    public void execute(WorkflowContext context) throws Exception {
        var article = context.getArticle();
        log.info("生成大纲: articleId={}, title={}", article.getId(), article.getTitle());

        var outline = outlineAgent.generateOutline(article.getTitle(), context.getCurrentOutput());
        context.setCurrentOutput(outline);
    }
}
