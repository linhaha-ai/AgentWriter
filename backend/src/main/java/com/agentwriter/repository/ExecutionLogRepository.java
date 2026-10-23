package com.agentwriter.repository;

import com.agentwriter.entity.ExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExecutionLogRepository extends JpaRepository<ExecutionLog, Long> {

    List<ExecutionLog> findByArticleId(Long articleId);

    List<ExecutionLog> findByArticleIdOrderByCreatedAtAsc(Long articleId);

    List<ExecutionLog> findByStep(String step);

    List<ExecutionLog> findByStatus(String status);

    List<ExecutionLog> findTop20ByOrderByCreatedAtDesc();

    /**
     * 聚合每篇文章的生成日志概览（用于"生成日志"总览页）：
     * 环节数/总耗时只统计主生成环节（排除知识入库 rag_store 与发布 publish）。
     */
    @Query(value = """
            SELECT l.article_id AS article_id, a.title AS title, a.status AS article_status,
                   COUNT(*) FILTER (WHERE l.step NOT IN ('rag_store', 'publish')) AS step_count,
                   COUNT(*) FILTER (WHERE l.status = 'failed') AS failed_count,
                   SUM(l.elapsed_ms) FILTER (WHERE l.step NOT IN ('rag_store', 'publish')) AS total_ms,
                   MAX(l.created_at) AS last_run_at
            FROM execution_log l
            JOIN article a ON a.id = l.article_id
            GROUP BY l.article_id, a.title, a.status
            ORDER BY last_run_at DESC
            """, nativeQuery = true)
    List<Object[]> findArticleLogSummaries();
}
