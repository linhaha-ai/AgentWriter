package com.agentwriter.service.impl;

import com.agentwriter.service.EmbeddingService;
import com.agentwriter.exception.EmbeddingDimensionException;
import com.agentwriter.exception.EmbeddingValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;

/**
 * OpenAI Embedding 服务实现 - 使用 text-embedding-ada-002 生成向量。
 *
 * <p>兼容 OpenAI Embeddings API 接口，支持 DeepSeek、Qwen 等兼容服务。</p>
 *
 * <p>启用方式: application.yml 中设置 agentwriter.embedding.enabled=true</p>
 */
@Service
@ConditionalOnProperty(name = "agentwriter.embedding.enabled", havingValue = "true")
public class OpenAiEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiEmbeddingService.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final int dimensions;

    public OpenAiEmbeddingService(
            @Value("${agentwriter.embedding.api-url:https://api.openai.com/v1}") String apiUrl,
            @Value("${agentwriter.embedding.api-key:}") String apiKey,
            @Value("${agentwriter.embedding.model:text-embedding-ada-002}") String model,
            @Value("${agentwriter.embedding.dimensions:1536}") int dimensions,
            @Value("${agentwriter.embedding.timeout:30}") int timeoutSeconds) {
        if (dimensions != CANONICAL_DIMENSIONS) {
            throw new IllegalArgumentException(
                    "Embedding 配置维度必须为 " + CANONICAL_DIMENSIONS + "，实际为 " + dimensions);
        }
        this.model = model;
        this.dimensions = CANONICAL_DIMENSIONS;
        this.objectMapper = new ObjectMapper();

        log.info("初始化 OpenAiEmbeddingService: apiUrl={}, model={}, dimensions={}",
                apiUrl, model, dimensions);

        this.webClient = WebClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("输入文本不能为空");
        }

        log.debug("Embedding 调用: model={}, textLength={}", model, text.length());

        ObjectNode requestBody = buildRequestBody(text);

        try {
            String responseBody = webClient.post()
                    .uri("/embeddings")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .retryWhen(Retry.backoff(2, Duration.ofSeconds(1))
                            .maxBackoff(Duration.ofSeconds(5)))
                    .block(Duration.ofSeconds(60));

            return extractEmbedding(responseBody);

        } catch (EmbeddingDimensionException | EmbeddingValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Embedding API 调用失败: {}", e.getMessage(), e);
            throw new RuntimeException("Embedding API 调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public int getDimensions() {
        return dimensions;
    }

    /**
     * 构造 OpenAI Embeddings 请求体。
     */
    private ObjectNode buildRequestBody(String text) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        root.put("input", text);
        root.put("dimensions", dimensions);
        return root;
    }

    /**
     * 从 OpenAI 响应体中提取 embedding 向量。
     */
    private float[] extractEmbedding(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) {
                throw new RuntimeException("Embedding 响应中没有 data 数组");
            }

            JsonNode firstItem = data.get(0);
            JsonNode embeddingArray = firstItem.path("embedding");
            if (!embeddingArray.isArray()) {
                throw new RuntimeException("Embedding 响应中的 embedding 不是数组");
            }

            // 将 JSON 数组转换为 float 数组
            float[] vector = new float[embeddingArray.size()];
            for (int i = 0; i < embeddingArray.size(); i++) {
                JsonNode value = embeddingArray.get(i);
                if (!value.isNumber()) {
                    throw new EmbeddingValidationException(
                            "Embedding 响应包含非数值元素: index=" + i);
                }
                double numericValue = value.doubleValue();
                if (!Double.isFinite(numericValue)) {
                    throw new EmbeddingValidationException(
                            "Embedding 响应包含非有限数值: index=" + i);
                }
                float floatValue = (float) numericValue;
                if (!Float.isFinite(floatValue)) {
                    throw new EmbeddingValidationException(
                            "Embedding 响应数值超出 float 有限范围: index=" + i);
                }
                vector[i] = floatValue;
            }
            EmbeddingService.requireCanonicalDimensions(vector);

            log.debug("Embedding 成功: dimensions={}, model={}",
                    dimensions, root.path("model").asText());

            return vector;

        } catch (EmbeddingDimensionException | EmbeddingValidationException e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof RuntimeException) throw (RuntimeException) e;
            throw new RuntimeException("解析 Embedding 响应失败: " + e.getMessage(), e);
        }
    }
}
