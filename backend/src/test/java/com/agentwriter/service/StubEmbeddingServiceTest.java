package com.agentwriter.service;

import com.agentwriter.exception.EmbeddingUnavailableException;
import com.agentwriter.service.impl.StubEmbeddingService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StubEmbeddingServiceTest {

    private final StubEmbeddingService embeddingService = new StubEmbeddingService();

    @Test
    void disabledEmbedding_shouldBeExplicitlyUnavailableAndNeverReturnRandomData() {
        assertThatThrownBy(() -> embeddingService.embed("same input"))
                .isInstanceOf(EmbeddingUnavailableException.class)
                .hasMessageContaining("不可用");
        assertThat(embeddingService.getDimensions()).isEqualTo(1536);
    }
}
