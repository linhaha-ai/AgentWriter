package com.agentwriter.workflow.step;

import com.agentwriter.agent.ResearchAgent;
import com.agentwriter.workflow.WorkflowContext;
import com.agentwriter.workflow.WorkflowStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 调研步骤 - 调用 ResearchAgent 进行内容调研
 */
@Component
public class ResearchStep implements WorkflowStep {

    private static final Logger log = LoggerFactory.getLogger(ResearchStep.class);

    private final ResearchAgent researchAgent;

    public ResearchStep(ResearchAgent researchAgent) {
        this.researchAgent = researchAgent;
    }

    @Override
    public String name() {
        return "research";
    }

    @Override
    public void execute(WorkflowContext context) throws Exception {
        var article = context.getArticle();
        String researchSubject = context.getRequest().title();
        log.info("深度调研: articleId={}, subject={}", article.getId(), researchSubject);

        var researchResult = researchAgent.research(researchSubject);
        context.setCurrentOutput(researchResult);
    }
}
