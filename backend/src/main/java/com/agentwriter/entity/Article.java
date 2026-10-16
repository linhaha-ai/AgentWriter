package com.agentwriter.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 文章表实体
 */
@Entity
@Table(name = "article", indexes = {
        @Index(name = "idx_article_status", columnList = "status"),
        @Index(name = "idx_article_created_at", columnList = "createdAt")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(name = "content_md", columnDefinition = "TEXT")
    private String contentMd;

    @Column(name = "content_html", columnDefinition = "TEXT")
    private String contentHtml;

    @Column(nullable = false, length = 20)
    private String status = "draft";

    @Column(length = 100)
    private String source;

    @Column(name = "cover_image", length = 500)
    private String coverImage;

    /** 关联的文章模板 ID */
    @Column(name = "template_id")
    private Long templateId;

    /** 关联的写作风格 ID */
    @Column(name = "style_id")
    private Long styleId;

    /** 生成进度 JSON，如 {"currentStep":"writer","totalSteps":5,"progress":40} */
    @Column(name = "generation_progress", columnDefinition = "TEXT")
    private String generationProgress;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
