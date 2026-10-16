package com.agentwriter.repository;

import com.agentwriter.entity.ArticleTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 文章模板 Repository
 */
@Repository
public interface ArticleTemplateRepository extends JpaRepository<ArticleTemplate, Long> {

    /** 查询所有已启用的模板 */
    List<ArticleTemplate> findByEnabledTrue();
}