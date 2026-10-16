package com.agentwriter.repository;

import com.agentwriter.entity.ArticleImage;
import com.agentwriter.entity.ArticleImageRole;
import com.agentwriter.entity.ArticleImageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ArticleImageRepository extends JpaRepository<ArticleImage, Long> {

    List<ArticleImage> findByArticleIdOrderByCreatedAtAsc(Long articleId);

    List<ArticleImage> findByArticleIdAndInsertedTrueOrderByCreatedAtAsc(Long articleId);

    Optional<ArticleImage> findByIdAndArticleId(Long id, Long articleId);

    Optional<ArticleImage> findFirstByArticleIdAndRoleAndStatusOrderByCreatedAtDesc(
            Long articleId,
            ArticleImageRole role,
            ArticleImageStatus status
    );
}
