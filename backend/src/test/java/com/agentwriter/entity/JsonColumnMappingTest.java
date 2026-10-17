package com.agentwriter.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class JsonColumnMappingTest {

    @Test
    void shouldBindStructuredJsonColumnsAsJson() throws NoSuchFieldException {
        // 结构化数据仍按 JSON 绑定
        assertJsonMapping(Research.class, "rawData");
        assertJsonMapping(KnowledgeChunk.class, "metadataJson");
    }

    @Test
    void shouldBindExecutionLogTextFieldsAsText() throws NoSuchFieldException {
        // execution_log 的 input/output 存储各步骤自由文本（Markdown 等），
        // 必须为 TEXT，不能按 JSON 绑定，否则写入非 JSON 文本会报
        // "invalid input syntax for type json"。
        assertTextMapping(ExecutionLog.class, "input");
        assertTextMapping(ExecutionLog.class, "output");
    }

    private static void assertJsonMapping(Class<?> entityType, String fieldName) throws NoSuchFieldException {
        Field field = entityType.getDeclaredField(fieldName);
        JdbcTypeCode jdbcType = field.getAnnotation(JdbcTypeCode.class);

        assertThat(jdbcType)
                .as("%s.%s 必须按 JSON JDBC 类型绑定", entityType.getSimpleName(), fieldName)
                .isNotNull();
        assertThat(jdbcType.value()).isEqualTo(SqlTypes.JSON);
    }

    private static void assertTextMapping(Class<?> entityType, String fieldName) throws NoSuchFieldException {
        Field field = entityType.getDeclaredField(fieldName);
        assertThat(field.isAnnotationPresent(JdbcTypeCode.class))
                .as("%s.%s 不应使用 JSON JDBC 类型绑定", entityType.getSimpleName(), fieldName)
                .isFalse();
    }
}
