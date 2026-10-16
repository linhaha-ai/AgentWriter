package com.agentwriter.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArticleStatusTest {

    @Test
    void parsesEveryPersistedValue() {
        for (ArticleStatus status : ArticleStatus.values()) {
            assertThat(ArticleStatus.parse(status.databaseValue())).isEqualTo(status);
        }
    }

    @Test
    void rejectsArbitraryAndLegacyPublishedValues() {
        assertThatThrownBy(() -> ArticleStatus.parse("writing"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ArticleStatus.parse("published"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ArticleStatus.parse("anything"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
