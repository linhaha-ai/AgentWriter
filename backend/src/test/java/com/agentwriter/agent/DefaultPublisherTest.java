package com.agentwriter.agent;

import com.agentwriter.agent.impl.DefaultPublisher;
import com.agentwriter.entity.Article;
import com.agentwriter.exception.PublisherUnavailableException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultPublisherTest {

    @Test
    void shouldFailWithTypedUnavailableException() {
        Article article = new Article();
        article.setId(1L);
        article.setTitle("Article");

        assertThatThrownBy(() -> new DefaultPublisher().publish(article))
                .isInstanceOf(PublisherUnavailableException.class)
                .hasMessageContaining("未配置");
    }
}
