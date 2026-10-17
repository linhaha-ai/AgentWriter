package com.agentwriter.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 智能体调用日志表实体
 */
@Entity
@Table(name = "agent_log", indexes = {
        @Index(name = "idx_al_log_id", columnList = "logId"),
        @Index(name = "idx_al_agent_name", columnList = "agentName")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class AgentLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "log_id", nullable = false)
    private Long logId;

    @Column(name = "agent_name", nullable = false, length = 100)
    private String agentName;

    @Column(columnDefinition = "TEXT")
    private String prompt;

    @Column(columnDefinition = "TEXT")
    private String response;

    @Column(name = "tokens_in")
    private Integer tokensIn = 0;

    @Column(name = "tokens_out")
    private Integer tokensOut = 0;

    @Column(name = "latency_ms")
    private Integer latencyMs = 0;

    @Column(nullable = false, length = 20)
    private String status = "success";

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
