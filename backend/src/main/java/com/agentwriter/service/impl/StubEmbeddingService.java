package com.agentwriter.service.impl;

import com.agentwriter.service.EmbeddingService;
import com.agentwriter.exception.EmbeddingUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Embedding 禁用实现 - 明确报告服务不可用。
 *
 * <p>禁用状态不得生成随机向量；RAG 服务会降级到确定性关键词检索。</p>
 *
 * <p>启用方式: application.yml 中设置 agentwriter.embedding.enabled=false（默认）</p>
 */
@Service
@ConditionalOnProperty(name = "agentwriter.embedding.enabled", havingValue = "false", matchIfMissing = true)
public class StubEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(StubEmbeddingService.class);

    @Override
    public float[] embed(String text) {
        log.debug("Embedding 已禁用，RAG 将使用关键词降级");
        throw new EmbeddingUnavailableException("Embedding 服务不可用：功能已禁用");
    }

    @Override
    public int getDimensions() {
        return CANONICAL_DIMENSIONS;
    }
}
