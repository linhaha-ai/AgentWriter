package com.agentwriter.workflow.step;

import com.agentwriter.agent.ResearchAgent;
import com.agentwriter.dto.WorkflowRequest;
import com.agentwriter.entity.Article;
import com.agentwriter.workflow.WorkflowContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResearchStepTest {

    @Test
    void researchesTheTopicEnrichedSubjectWithoutChangingArticleTitle() throws Exception {
        ResearchAgent researchAgent = mock(ResearchAgent.class);
        String researchSubject = "用户标题（参考热门话题：话题一；话题二）";
        when(researchAgent.research(researchSubject)).thenReturn("调研结果");

        Article article = new Article();
        article.setId(99L);
        article.setTitle("用户标题");
        WorkflowContext context = new WorkflowContext(
                article,
                new WorkflowRequest(11L, researchSubject, null));

        new ResearchStep(researchAgent).execute(context);

        verify(researchAgent).research(researchSubject);
        assertThat(context.getCurrentOutput()).isEqualTo("调研结果");
        assertThat(article.getTitle()).isEqualTo("用户标题");
    }
}
