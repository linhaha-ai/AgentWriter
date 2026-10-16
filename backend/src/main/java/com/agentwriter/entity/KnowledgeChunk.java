package com.agentwriter.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 知识块表实体（RAG 分块存储 + pgvector 向量）
 *
 * <p>设计参考: doc/DATABASE.md §3.5</p>
 * <p>使用 pgvector VECTOR(1536) 存储 OpenAI ada-002 向量嵌入</p>
 */
@Entity
@Table(name = "knowledge_chunk", indexes = {
        @Index(name = "idx_kc_article_id", columnList = "articleId"),
        @Index(name = "idx_kc_chunk_index", columnList = "chunkIndex")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class KnowledgeChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "article_id", nullable = false)
    private Long articleId;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "tokens_count", nullable = false)
    private Integer tokensCount;

    /**
     * pgvector 向量嵌入（OpenAI ada-002: 1536维）
     * 使用 Hibernate 7 vector 模块映射为 float[]
     */
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Column(name = "embedding", columnDefinition = "vector(1536)")
    private float[] embedding;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", columnDefinition = "JSONB")
    private String metadataJson;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
