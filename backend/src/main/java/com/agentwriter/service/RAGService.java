package com.agentwriter.service;

import com.agentwriter.entity.KnowledgeChunk;

import java.util.List;

/**
 * RAG 检索服务 - 知识块的存储、切分与检索。
 *
 * <p>设计参考: doc/RAG_STRATEGY.md</p>
 */
public interface RAGService {

    /**
     * 检索与查询最相关的知识块。
     *
     * @param query 查询文本
     * @param topK  返回的知识块数量
     * @return 相关文本段落（拼接在一起的分隔段落）
     */
    String retrieve(String query, int topK);

    /**
     * 将文章内容切分为知识块并存入向量库。
     *
     * @param articleId 文章 ID
     * @param content   文章 Markdown 全文
     * @return 生成的知识块数量
     */
    int storeArticle(Long articleId, String content);

    /**
     * 删除文章关联的所有知识块（用于文章更新时重建索引）。
     *
     * @param articleId 文章 ID
     */
    void deleteByArticleId(Long articleId);

    /**
     * 获取文章的所有知识块。
     *
     * @param articleId 文章 ID
     * @return 知识块列表
     */
    List<KnowledgeChunk> findByArticleId(Long articleId);
}
