package com.agentwriter.service;

import com.agentwriter.exception.EmbeddingDimensionException;

/**
 * 向量嵌入服务 - 将文本转换为向量表示。
 *
 * <p>用于 RAG 系统的知识块向量化和查询向量化。
 * 默认使用 OpenAI text-embedding-ada-002（1536维）。</p>
 *
 * <p>设计参考: doc/RAG_STRATEGY.md §3</p>
 */
public interface EmbeddingService {

    int CANONICAL_DIMENSIONS = 1536;

    /**
     * 将文本转换为向量嵌入。
     *
     * @param text 输入文本
     * @return 1536维 float 数组向量（OpenAI ada-002）
     */
    float[] embed(String text);

    /**
     * 获取向量维度。
     *
     * @return 向量维度（默认 1536）
     */
    int getDimensions();

    static float[] requireCanonicalDimensions(float[] vector) {
        int actualDimensions = vector == null ? 0 : vector.length;
        if (actualDimensions != CANONICAL_DIMENSIONS) {
            throw new EmbeddingDimensionException(
                    "Embedding 维度不匹配: 期望 " + CANONICAL_DIMENSIONS
                            + ", 实际 " + actualDimensions);
        }
        return vector;
    }
}
