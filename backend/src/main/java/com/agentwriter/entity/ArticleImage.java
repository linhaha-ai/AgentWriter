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
 * 文章生成图片及其外部平台素材状态。
 */
@Entity
@Table(name = "article_image", indexes = {
        @Index(name = "idx_article_image_article_id", columnList = "article_id"),
        @Index(name = "idx_article_image_status", columnList = "status")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class ArticleImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ArticleImageRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ArticleImageStatus status = ArticleImageStatus.QUEUED;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "alt_text", nullable = false, length = 500)
    private String altText;

    @Column(name = "heading_anchor", length = 500)
    private String headingAnchor;

    @Column(name = "comfy_prompt_id", length = 100)
    private String comfyPromptId;

    @Column(name = "storage_key", length = 500)
    private String storageKey;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(nullable = false)
    private boolean inserted;

    @Column(name = "wechat_content_url", length = 1000)
    private String wechatContentUrl;

    @Column(name = "wechat_thumb_media_id", length = 500)
    private String wechatThumbMediaId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
