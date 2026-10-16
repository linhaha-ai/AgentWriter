package com.agentwriter.workflow;

import com.agentwriter.dto.PublishResult;
import com.agentwriter.dto.WorkflowRequest;
import com.agentwriter.entity.Article;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowContextTest {

    @Test
    void shouldRetrieveStructuredResultWithoutChangingMarkdownOutput() {
        WorkflowContext context = context();
        PublishResult expected = result();
        context.setCurrentOutput("# Markdown article");

        context.putResult("publishResult", expected);

        assertThat(context.getResult("publishResult", PublishResult.class)).isSameAs(expected);
        assertThat(context.getCurrentOutput()).isEqualTo("# Markdown article");
    }

    @Test
    void shouldRejectMissingStructuredResult() {
        assertThatThrownBy(() -> context().getResult("missing", PublishResult.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未找到键对应的结构化结果");
    }

    @Test
    void shouldRejectWrongStructuredResultType() {
        WorkflowContext context = context();
        context.putResult("publishResult", "not a publish result");

        assertThatThrownBy(() -> context.getResult("publishResult", PublishResult.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不是");
    }

    private WorkflowContext context() {
        return new WorkflowContext(new Article(), new WorkflowRequest(1L, "Title", null));
    }

    private PublishResult result() {
        return new PublishResult("test", "https://example.test", LocalDateTime.of(2026, 7, 27, 0, 0), 1L, null);
    }
}
