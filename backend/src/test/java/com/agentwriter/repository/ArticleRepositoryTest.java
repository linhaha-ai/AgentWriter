package com.agentwriter.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

class ArticleRepositoryTest {

    @Test
    void conditionalTransitionMatchesIdAndCurrentStatusAtomically() throws Exception {
        var method = ArticleRepository.class.getMethod(
                "transitionStatus", Long.class, String.class, String.class);
        String query = method.getAnnotation(Query.class).value()
                .replaceAll("\\s+", " ")
                .toLowerCase();

        assertThat(query)
                .contains("where article.id = :id")
                .contains("article.status = :expectedstatus")
                .contains("set article.status = :targetstatus");
        assertThat(method.getAnnotation(Modifying.class)).isNotNull();
        assertThat(method.getAnnotation(Transactional.class)).isNotNull();
        assertThat(method.getReturnType()).isEqualTo(int.class);
    }
}
