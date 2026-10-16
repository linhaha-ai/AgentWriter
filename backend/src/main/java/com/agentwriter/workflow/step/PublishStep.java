package com.agentwriter.workflow.step;

import com.agentwriter.agent.Publisher;
import com.agentwriter.dto.PublishResult;
import com.agentwriter.workflow.WorkflowContext;
import com.agentwriter.workflow.WorkflowStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 发布步骤 - 调用 Publisher 发布文章
 */
@Component
public class PublishStep implements WorkflowStep {

    private static final Logger log = LoggerFactory.getLogger(PublishStep.class);

    private final Publisher publisher;

    public PublishStep(Publisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public String name() {
        return "publish";
    }

    @Override
    public void execute(WorkflowContext context) throws Exception {
        var article = context.getArticle();
        log.info("发布文章: articleId={}", article.getId());

        PublishResult result = publisher.publish(article);
        // 将发布结果存入上下文
        context.putResult("publishResult", result);
        log.info("发布完成: platform={}, publishId={}", result.platform(), result.url());
    }
}
