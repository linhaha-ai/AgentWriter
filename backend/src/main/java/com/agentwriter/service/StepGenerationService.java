package com.agentwriter.service;

import com.agentwriter.dto.GenerationProgress;
import com.agentwriter.dto.ProgressResponse;
import com.agentwriter.dto.StepGenerateRequest;
import com.agentwriter.dto.TopicResponse;
import com.agentwriter.dto.WorkflowRequest;
import com.agentwriter.entity.Article;
import com.agentwriter.entity.ArticleStatus;
import com.agentwriter.exception.ArticleNotFoundException;
import com.agentwriter.repository.ArticleRepository;
import com.agentwriter.repository.ExecutionLogRepository;
import com.agentwriter.workflow.WorkflowEngine;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 分步生成服务 - 接收用户选择的话题、标题、模板和风格，创建文章并异步触发工作流
 */
@Service
public class StepGenerationService {

    private static final Logger log = LoggerFactory.getLogger(StepGenerationService.class);

    private final ArticleRepository articleRepository;
    private final ArticleService articleService;
    private final TopicService topicService;
    private final WorkflowEngine workflowEngine;
    private final ObjectMapper objectMapper;
    private final ExecutionLogRepository executionLogRepository;

    @Autowired
    public StepGenerationService(ArticleRepository articleRepository,
                                 ArticleService articleService,
                                 TopicService topicService,
                                 WorkflowEngine workflowEngine,
                                 ObjectMapper objectMapper,
                                 ExecutionLogRepository executionLogRepository) {
        this.articleRepository = articleRepository;
        this.articleService = articleService;
        this.topicService = topicService;
        this.workflowEngine = workflowEngine;
        this.objectMapper = objectMapper;
        this.executionLogRepository = executionLogRepository;
    }

    public StepGenerationService(ArticleRepository articleRepository,
                                 ArticleService articleService,
                                 TopicService topicService,
                                 WorkflowEngine workflowEngine,
                                 ObjectMapper objectMapper) {
        this(articleRepository, articleService, topicService, workflowEngine, objectMapper, null);
    }

    /**
     * 提交分步生成请求，创建文章后异步触发工作流
     *
     * @return 创建的文章 ID
     */
    public Long stepGenerate(StepGenerateRequest request) {
        var topics = request.topicIds().stream()
                .map(topicService::findById)
                .toList();

        // 2. 创建文章
        var article = new Article();
        article.setTitle(request.title().trim());
        article.setSource(buildTopicSource(request.topicIds()));
        article.setTemplateId(request.templateId());
        article.setStyleId(request.styleId());
        article.setStatus(ArticleStatus.DRAFT.databaseValue());

        // 初始化进度
        try {
            article.setGenerationProgress(objectMapper.writeValueAsString(GenerationProgress.initial()));
        } catch (JsonProcessingException e) {
            log.warn("序列化初始进度失败", e);
        }

        var saved = articleService.saveEntity(article);

        // 3. 工作流只能从 generating 状态完成或失败
        articleService.startGeneration(saved.getId());

        // 4. 异步触发工作流
        var workflowRequest = new WorkflowRequest(
                request.topicIds().getFirst(),
                buildResearchSubject(article.getTitle(), topics),
                null);
        workflowEngine.startAsync(saved.getId(), workflowRequest);

        log.info("分步生成已提交: articleId={}, topicIds={}, templateId={}, styleId={}",
                saved.getId(), request.topicIds(), request.templateId(), request.styleId());

        return saved.getId();
    }

    private String buildTopicSource(List<Long> topicIds) {
        Long firstTopicId = topicIds.getFirst();
        if (topicIds.size() == 1) {
            return "topic:" + firstTopicId;
        }
        return "topics:%d(+%d)".formatted(firstTopicId, topicIds.size() - 1);
    }

    private String buildResearchSubject(
            String articleTitle,
            List<TopicResponse> topics
    ) {
        String topicTitles = topics.stream()
                .map(TopicResponse::title)
                .collect(Collectors.joining("；"));
        return "%s（参考热门话题：%s）".formatted(articleTitle, topicTitles);
    }

    /**
     * 查询生成进度
     */
    public ProgressResponse getProgress(Long articleId) {
        var article = articleRepository.findById(articleId)
                .orElseThrow(() -> new ArticleNotFoundException(articleId));

        GenerationProgress progress = null;
        if (article.getGenerationProgress() != null) {
            try {
                progress = objectMapper.readValue(article.getGenerationProgress(), GenerationProgress.class);
            } catch (JsonProcessingException e) {
                log.warn("解析生成进度 JSON 失败: articleId={}", articleId, e);
            }
        }

        var logs = executionLogRepository == null ? List.<com.agentwriter.dto.GenerationLogResponse>of()
                : executionLogRepository.findByArticleIdOrderByCreatedAtAsc(articleId).stream()
                .filter(entry -> isGenerationStep(entry.getStep()))
                .map(com.agentwriter.dto.GenerationLogResponse::from)
                .toList();
        return new ProgressResponse(article.getId(), article.getStatus(), progress, logs);
    }

    private static boolean isGenerationStep(String step) {
        return List.of("research", "outline", "writer", "review", "seo").contains(step);
    }
}
