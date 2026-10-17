package com.agentwriter.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 执行日志表实体（工作流审计）
 */
@Entity
@Table(name = "execution_log", indexes = {
        @Index(name = "idx_el_step", columnList = "step"),
        @Index(name = "idx_el_status", columnList = "status"),
        @Index(name = "idx_el_article_id", columnList = "articleId"),
        @Index(name = "idx_el_created_at", columnList = "createdAt")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class ExecutionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String step;

    @Column(name = "agent_name", length = 100)
    private String agentName;

    /**
     * 输入/输出为各步骤的自由文本（Markdown、调试文本等），使用 TEXT 存储。
     * 原先声明为 JSON，但写入内容并非合法 JSON（如 research 输出为 Markdown），
     * 会导致 PostgreSQL 报 "invalid input syntax for type json"。
     */
    @Column(columnDefinition = "TEXT")
    private String input;

    @Column(columnDefinition = "TEXT")
    private String output;

    @Column(nullable = false, length = 20)
    private String status = "pending";

    @Column(name = "error_msg", columnDefinition = "TEXT")
    private String errorMsg;

    @Column(name = "article_id")
    private Long articleId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
