package com.agentwriter.service.impl;

import com.agentwriter.entity.KnowledgeChunk;
import com.agentwriter.rag.ChunkStrategy;
import com.agentwriter.repository.KnowledgeChunkRepository;
import com.agentwriter.exception.EmbeddingDimensionException;
import com.agentwriter.service.EmbeddingService;
import com.agentwriter.exception.EmbeddingUnavailableException;
import com.agentwriter.exception.EmbeddingValidationException;
import com.agentwriter.service.RAGService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * RAG 服务实现 - 基于 pgvector 的向量检索。
 *
 * <p>使用 pgvector 进行语义检索，通过 EmbeddingService 生成查询向量，
 * 调用 PostgreSQL search_knowledge() 函数执行余弦相似度搜索。</p>
 *
 * <p>设计参考: doc/RAG_STRATEGY.md §4</p>
 */
@Service
public class DefaultRAGService implements RAGService {

    private static final Logger log = LoggerFactory.getLogger(DefaultRAGService.class);

    /** 默认相似度阈值 */
    private static final double DEFAULT_THRESHOLD = 0.5;
    private static final int MIN_TOP_K = 1;
    private static final int MAX_TOP_K = 100;

    private final KnowledgeChunkRepository chunkRepository;
    private final ChunkStrategy chunkStrategy;
    private final EmbeddingService embeddingService;

    public DefaultRAGService(KnowledgeChunkRepository chunkRepository,
                             ChunkStrategy chunkStrategy,
                             EmbeddingService embeddingService) {
        this.chunkRepository = chunkRepository;
        this.chunkStrategy = chunkStrategy;
        this.embeddingService = embeddingService;
    }

    @Override
    public String retrieve(String query, int topK) {
        requireValidTopK(topK);
        log.info("开始执行 RAG 检索: topK={}", topK);

        if (query == null || query.isBlank()) {
            return "查询内容为空，无法检索相关知识。";
        }

        try {
            // 1. 将查询文本转换为向量
            float[] queryVector = EmbeddingService.requireCanonicalDimensions(
                    embeddingService.embed(query));
            log.debug("查询向量化完成: dimensions={}", embeddingService.getDimensions());

            // 2. 调用 pgvector search_knowledge() 函数进行余弦相似度搜索
            List<Object[]> results = chunkRepository.searchByVector(
                    queryVector, topK, DEFAULT_THRESHOLD);

            if (results.isEmpty()) {
                log.info("未找到与查询相关的知识块");
                return "未找到与「" + query + "」相关的历史知识。";
            }

            // 3. 格式化返回结果
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < results.size(); i++) {
                Object[] row = results.get(i);
                Long id = ((Number) row[0]).longValue();
                Long articleId = ((Number) row[1]).longValue();
                String title = (String) row[2];
                String content = (String) row[3];
                Double similarity = ((Number) row[4]).doubleValue();

                if (i > 0) {
                    sb.append("\n\n---\n\n");
                }
                sb.append("【").append(title).append("】");
                sb.append(" (相似度: ").append(String.format("%.2f", similarity)).append(")\n");
                sb.append(content);

                log.debug("检索结果 #{}: id={}, articleId={}, similarity={}",
                        i + 1, id, articleId, similarity);
            }

            log.info("RAG 检索完成: results={}", results.size());
            return sb.toString();

        } catch (Exception e) {
            log.error("RAG 向量检索失败: type={}", e.getClass().getName());
            // 降级到关键词匹配
            return fallbackKeywordSearch(query, topK);
        }
    }

    /**
     * 降级方案：当向量检索失败时，使用关键词匹配。
     */
    private String fallbackKeywordSearch(String query, int topK) {
        log.warn("使用降级方案: 关键词匹配检索");
        PageRequest pageRequest = PageRequest.of(
                0,
                topK,
                Sort.by("id", "articleId", "chunkIndex", "title", "content").ascending()
        );
        return chunkRepository.findByContentContainingIgnoreCase(query, pageRequest).stream()
                .map(c -> "【" + c.getTitle() + "】\n" + c.getContent())
                .reduce((a, b) -> a + "\n\n---\n\n" + b)
                .orElse("未找到与「" + query + "」相关的历史知识。");
    }

    private static void requireValidTopK(int topK) {
        if (topK < MIN_TOP_K || topK > MAX_TOP_K) {
            throw new IllegalArgumentException("RAG 返回数量必须在 1 到 100 之间");
        }
    }

    @Override
    @Transactional
    public int storeArticle(Long articleId, String content) {
        log.info("RAGService.storeArticle() 被调用: articleId={}, contentLength={}",
                articleId, content != null ? content.length() : 0);

        if (content == null || content.isBlank()) {
            log.warn("文章内容为空，跳过 RAG 存储: articleId={}", articleId);
            return 0;
        }

        // 1. 删除旧知识块
        deleteByArticleId(articleId);

        // 2. 使用 ChunkStrategy 切分
        List<ChunkStrategy.Chunk> chunks = chunkStrategy.split("article:" + articleId, content);
        log.debug("切分得到 {} 个知识块", chunks.size());

        // 3. 先生成并校验所有向量，避免维度错误时产生部分写入
        List<KnowledgeChunk> entities = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            ChunkStrategy.Chunk c = chunks.get(i);
            KnowledgeChunk entity = new KnowledgeChunk();
            entity.setArticleId(articleId);
            entity.setChunkIndex(i);
            entity.setTitle(c.title());
            entity.setContent(c.content());
            entity.setTokensCount(chunkStrategy.estimateTokens(c.content()));

            // 生成向量嵌入
            try {
                float[] embedding = EmbeddingService.requireCanonicalDimensions(
                        embeddingService.embed(c.content()));
                entity.setEmbedding(embedding);
                log.debug("知识块向量化成功: chunkIndex={}, title={}", i, c.title());
            } catch (EmbeddingDimensionException | EmbeddingValidationException e) {
                throw e;
            } catch (EmbeddingUnavailableException e) {
                log.info("Embedding 不可用，将存储为纯文本: chunkIndex={}", i);
            } catch (Exception e) {
                log.warn("知识块向量化失败，将存储为纯文本: chunkIndex={}, error={}",
                        i, e.getMessage());
                // 向量化失败不影响存储，后续可通过关键词匹配检索
            }

            // 填充元数据
            entity.setMetadataJson("{\"level\": " + c.level() + "}");
            entities.add(entity);
        }

        int saved = 0;
        for (KnowledgeChunk entity : entities) {
            chunkRepository.save(entity);
            saved++;
        }

        log.info("RAG 存储完成: articleId={}, chunks={}", articleId, saved);
        return saved;
    }

    @Override
    @Transactional
    public void deleteByArticleId(Long articleId) {
        List<KnowledgeChunk> existing = chunkRepository.findByArticleId(articleId);
        if (!existing.isEmpty()) {
            chunkRepository.deleteAll(existing);
            log.debug("已删除文章 {} 的 {} 个旧知识块", articleId, existing.size());
        }
    }

    @Override
    public List<KnowledgeChunk> findByArticleId(Long articleId) {
        return chunkRepository.findByArticleId(articleId);
    }
}
