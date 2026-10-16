package com.agentwriter.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 话题表实体
 */
@Entity
@Table(name = "topic", indexes = {
        @Index(name = "idx_topic_source", columnList = "source"),
        @Index(name = "idx_topic_score", columnList = "score"),
        @Index(name = "idx_topic_status", columnList = "status")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Topic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, length = 100)
    private String source;

    @Column(nullable = false)
    private Integer score = 0;

    @Column(nullable = false, length = 20)
    private String status = "pending";

    @Column(length = 1000)
    private String link;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
