package com.agentwriter.repository;

import com.agentwriter.entity.Article;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ArticleRepository extends JpaRepository<Article, Long> {

    List<Article> findByStatus(String status);

    List<Article> findAllByOrderByCreatedAtDescIdDesc();

    List<Article> findByStatusOrderByCreatedAtDescIdDesc(String status);

    /** 按标题查找非失败状态的文章（用于生成前去重；忽略大小写与首尾空格） */
    List<Article> findByTitleIgnoreCaseAndStatusNot(String title, String status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update Article article
            set article.status = :targetStatus
            where article.id = :id and article.status = :expectedStatus
            """)
    int transitionStatus(
            @Param("id") Long id,
            @Param("expectedStatus") String expectedStatus,
            @Param("targetStatus") String targetStatus
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update Article article
            set article.status = :targetStatus, article.contentMd = :contentMd
            where article.id = :id and article.status = :expectedStatus
            """)
    int completeGenerationWithContent(
            @Param("id") Long id,
            @Param("expectedStatus") String expectedStatus,
            @Param("targetStatus") String targetStatus,
            @Param("contentMd") String contentMd
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update Article article
            set article.generationProgress = :progress
            where article.id = :id
            """)
    int updateProgress(@Param("id") Long id, @Param("progress") String progress);

    long countByCreatedAtAfter(LocalDateTime after);

    long countByStatusAndUpdatedAtAfter(String status, LocalDateTime after);
}
