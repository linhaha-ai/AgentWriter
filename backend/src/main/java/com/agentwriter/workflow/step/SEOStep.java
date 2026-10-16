package com.agentwriter.workflow.step;

import com.agentwriter.agent.SEOAgent;
import com.agentwriter.workflow.WorkflowContext;
import com.agentwriter.workflow.WorkflowStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * SEO 优化步骤 - 调用 SEOAgent 优化文章 SEO
 */
@Component
public class SEOStep implements WorkflowStep {

    private static final Logger log = LoggerFactory.getLogger(SEOStep.class);

    private final SEOAgent seoAgent;

    public SEOStep(SEOAgent seoAgent) {
        this.seoAgent = seoAgent;
    }

    @Override
    public String name() {
        return "seo";
    }

    @Override
    public void execute(WorkflowContext context) throws Exception {
        var article = context.getArticle();
        log.info("SEO 优化: articleId={}", article.getId());

        var optimized = seoAgent.optimize(context.getCurrentOutput());
        context.setCurrentOutput(optimized);
    }
}
