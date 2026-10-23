package com.agentwriter.service;

import com.agentwriter.agent.Publisher;
import com.agentwriter.exception.PublicationFailedException;
import com.agentwriter.exception.PublisherUnavailableException;
import com.agentwriter.dto.PublishResult;
import com.agentwriter.entity.ExecutionLog;
import com.agentwriter.repository.ExecutionLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class ArticlePublicationService {

    private static final Pattern CREDENTIAL = Pattern.compile(
            "(?i)(access[_-]?token|token|secret|app[_-]?secret|authorization)\\s*[=:]\\s*[^\\s,;]+"
    );

    private final ArticleService articleService;
    private final Publisher publisher;
    private final ExecutionLogRepository executionLogRepository;
    private final ObjectMapper objectMapper;

    public ArticlePublicationService(
            ArticleService articleService,
            Publisher publisher,
            ExecutionLogRepository executionLogRepository,
            ObjectMapper objectMapper
    ) {
        this.articleService = articleService;
        this.publisher = publisher;
        this.executionLogRepository = executionLogRepository;
        this.objectMapper = objectMapper;
    }

    public PublishResult publish(Long articleId) {
        articleService.startPublishing(articleId);
        var article = articleService.findEntityById(articleId);
        PublishResult result;
        try {
            result = publisher.publish(article);
        } catch (PublisherUnavailableException exception) {
            restoreAndLogFailure(articleId, exception);
            throw exception;
        } catch (RuntimeException exception) {
            restoreAndLogFailure(articleId, exception);
            throw new PublicationFailedException("保存公众号草稿失败", exception);
        }

        try {
            articleService.completePublishing(articleId);
        } catch (RuntimeException exception) {
            throw new PublicationFailedException("草稿已保存，但文章状态更新失败", exception);
        }

        try {
            saveSuccess(articleId, result);
        } catch (RuntimeException exception) {
            throw new PublicationFailedException("草稿已保存，但审计记录保存失败", exception);
        }
        return result;
    }

    private void restoreAndLogFailure(Long articleId, RuntimeException exception) {
        try {
            articleService.restoreGeneratedAfterPublishFailure(articleId);
        } catch (RuntimeException compensationFailure) {
            exception.addSuppressed(compensationFailure);
        }
        try {
            ExecutionLog entry = baseLog(articleId);
            entry.setStatus("failed");
            entry.setErrorMsg(redact(exception.getMessage()));
            executionLogRepository.save(entry);
        } catch (RuntimeException auditFailure) {
            exception.addSuppressed(auditFailure);
        }
    }

    private void saveSuccess(Long articleId, PublishResult result) {
        ExecutionLog entry = baseLog(articleId);
        entry.setStatus("succeeded");
        entry.setOutput(serializeRedacted(result));
        executionLogRepository.save(entry);
    }

    private static ExecutionLog baseLog(Long articleId) {
        ExecutionLog entry = new ExecutionLog();
        entry.setStep("publish");
        entry.setAgentName("Publisher");
        entry.setArticleId(articleId);
        entry.setInput("{\"articleId\":" + articleId + "}");
        return entry;
    }

    private static String redact(String message) {
        if (message == null || message.isBlank()) {
            return "发布器未提供错误详情";
        }
        return CREDENTIAL.matcher(message).replaceAll("$1=[REDACTED]");
    }

    private String serializeRedacted(PublishResult result) {
        ObjectNode tree = objectMapper.createObjectNode();
        tree.put("platform", result.platform());
        if (result.url() == null) {
            tree.putNull("url");
        } else {
            tree.put("url", result.url());
        }
        if (result.publishedAt() == null) {
            tree.putNull("publishedAt");
        } else {
            tree.put("publishedAt", result.publishedAt().toString());
        }
        tree.put("articleId", result.articleId());
        if (result.coverImage() == null) {
            tree.putNull("coverImage");
        } else {
            tree.put("coverImage", result.coverImage());
        }
        redactTree(tree);
        try {
            return objectMapper.writeValueAsString(tree);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化发布审计结果", exception);
        }
    }

    private static void redactTree(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            objectNode.properties().forEach(entry -> {
                if (entry.getKey().matches("(?i).*?(token|secret|authorization|credential|password).*")) {
                    objectNode.put(entry.getKey(), "[REDACTED]");
                } else if (entry.getValue().isTextual()) {
                    objectNode.put(entry.getKey(), redact(entry.getValue().asText()));
                } else {
                    redactTree(entry.getValue());
                }
            });
        } else if (node.isArray()) {
            node.forEach(ArticlePublicationService::redactTree);
        }
    }
}
