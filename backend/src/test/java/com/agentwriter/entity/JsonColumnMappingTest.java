package com.agentwriter.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class JsonColumnMappingTest {

    @Test
    void shouldBindJsonColumnsAsJsonInsteadOfVarchar() throws NoSuchFieldException {
        assertJsonMapping(ExecutionLog.class, "input");
        assertJsonMapping(ExecutionLog.class, "output");
        assertJsonMapping(Research.class, "rawData");
        assertJsonMapping(KnowledgeChunk.class, "metadataJson");
    }

    private static void assertJsonMapping(Class<?> entityType, String fieldName) throws NoSuchFieldException {
        Field field = entityType.getDeclaredField(fieldName);
        JdbcTypeCode jdbcType = field.getAnnotation(JdbcTypeCode.class);

        assertThat(jdbcType)
                .as("%s.%s 必须按 JSON JDBC 类型绑定", entityType.getSimpleName(), fieldName)
                .isNotNull();
        assertThat(jdbcType.value()).isEqualTo(SqlTypes.JSON);
    }
}
