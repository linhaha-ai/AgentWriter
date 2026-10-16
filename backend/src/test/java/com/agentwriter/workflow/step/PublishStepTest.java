package com.agentwriter.workflow.step;

import com.agentwriter.agent.Publisher;
import com.agentwriter.dto.PublishResult;
import com.agentwriter.dto.WorkflowRequest;
import com.agentwriter.entity.Article;
import com.agentwriter.workflow.WorkflowContext;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublishStepTest {

    @Test
    void shouldStorePublishResultSeparatelyFromMarkdown() throws Exception {
        Article article = new Article();
        article.setId(1L);
        WorkflowContext context = new WorkflowContext(article, new WorkflowRequest(1L, "Title", null));
        context.setCurrentOutput("# Markdown article");
        PublishResult expected = new PublishResult("test", "https://example.test", LocalDateTime.of(2026, 7, 27, 0, 0), 1L, null);
        Publisher publisher = mock(Publisher.class);
        when(publisher.publish(article)).thenReturn(expected);

        new PublishStep(publisher).execute(context);

        assertThat(context.getResult("publishResult", PublishResult.class)).isSameAs(expected);
        assertThat(context.getCurrentOutput()).isEqualTo("# Markdown article");
    }
}
