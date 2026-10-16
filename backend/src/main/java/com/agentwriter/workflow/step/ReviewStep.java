package com.agentwriter.workflow.step;

import com.agentwriter.agent.ReviewAgent;
import com.agentwriter.workflow.WorkflowContext;
import com.agentwriter.workflow.WorkflowStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 审校步骤 - 调用 ReviewAgent 审校文章质量
 */
@Component
public class ReviewStep implements WorkflowStep {

    private static final Logger log = LoggerFactory.getLogger(ReviewStep.class);

    private final ReviewAgent reviewAgent;

    public ReviewStep(ReviewAgent reviewAgent) {
        this.reviewAgent = reviewAgent;
    }

    @Override
    public String name() {
        return "review";
    }

    @Override
    public void execute(WorkflowContext context) throws Exception {
        var article = context.getArticle();
        log.info("审校文章: articleId={}", article.getId());

        var reviewed = reviewAgent.review(article.getTitle(), context.getCurrentOutput());
        context.setCurrentOutput(reviewed);
    }
}
