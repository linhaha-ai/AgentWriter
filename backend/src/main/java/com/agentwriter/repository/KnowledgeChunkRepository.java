package com.agentwriter.repository;

import com.agentwriter.entity.KnowledgeChunk;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long> {

    List<KnowledgeChunk> findByArticleId(Long articleId);

    List<KnowledgeChunk> findByContentContainingIgnoreCase(String content, Pageable pageable);

    /**
     * 使用 PostgreSQL search_knowledge() 函数进行向量相似度搜索。
     *
     * @param queryVector 查询向量（OpenAI ada-002: 1536维）
     * @param matchCount  返回的最大结果数
     * @param threshold   相似度阈值（0-1），低于此值的结果被过滤
     * @return 相似度搜索结果列表
     */
    @Query(value = "SELECT * FROM search_knowledge(:queryVector, :matchCount, :threshold)", nativeQuery = true)
    List<Object[]> searchByVector(
            @Param("queryVector") float[] queryVector,
            @Param("matchCount") int matchCount,
            @Param("threshold") double threshold
    );
}
