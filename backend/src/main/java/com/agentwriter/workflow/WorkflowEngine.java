package com.agentwriter.workflow;

import com.agentwriter.common.ErrorCategory;
import com.agentwriter.common.RetryPolicy;
import com.agentwriter.dto.ArticleResponse;
import com.agentwriter.dto.GenerationEvent;
import com.agentwriter.dto.GenerationLogResponse;
import com.agentwriter.dto.GenerationProgress;
import com.agentwriter.dto.WorkflowRequest;
import com.agentwriter.dto.WorkflowResponse;
import com.agentwriter.entity.Article;
import com.agentwriter.entity.ExecutionLog;
import com.agentwriter.repository.ArticleRepository;
import com.agentwriter.repository.ExecutionLogRepository;
import com.agentwriter.service.ArticleService;
import com.agentwriter.service.RAGService;
import com.agentwriter.service.TopicService;
import com.agentwriter.workflow.step.OutlineStep;
import com.agentwriter.workflow.step.ResearchStep;
import com.agentwriter.workflow.step.ReviewStep;
import com.agentwriter.workflow.step.SEOStep;
import com.agentwriter.workflow.step.WriterStep;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 写作工作流引擎 - 编排所有智能体步骤。
 *
 * <p>每个步骤均通过 RetryPolicy 执行自动重试，根据 ErrorCategory
 * 区分可重试/不可重试错误，确保偶发故障不影响工作流连续性。</p>
 */
@Component
public class WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);

    private final ArticleService articleService;
    private final ArticleRepository articleRepository;
    private final TopicService topicService;
    private final ExecutionLogRepository executionLogRepository;
    private final RetryPolicy retryPolicy;
    private final RAGService ragService;
    private final ObjectMapper objectMapper;
    private final GenerationEventHub generationEventHub;
    private final List<WorkflowStep> steps;

    private static final List<String> STEP_NAMES = List.of("research", "outline", "writer", "review", "seo");

    @Autowired
    public WorkflowEngine(ArticleService articleService,
                          ArticleRepository articleRepository,
                          TopicService topicService,
                          ExecutionLogRepository executionLogRepository,
                          RetryPolicy retryPolicy,
                          RAGService ragService,
                          ObjectMapper objectMapper,
                          GenerationEventHub generationEventHub,
                          ResearchStep researchStep,
                          OutlineStep outlineStep,
                          WriterStep writerStep,
                          ReviewStep reviewStep,
                          SEOStep seoStep) {
        this.articleService = articleService;
        this.articleRepository = articleRepository;
        this.topicService = topicService;
        this.executionLogRepository = executionLogRepository;
        this.retryPolicy = retryPolicy;
        this.ragService = ragService;
        this.objectMapper = objectMapper;
        this.generationEventHub = generationEventHub;
        this.steps = List.of(researchStep, outlineStep, writerStep, reviewStep, seoStep);
    }

    public WorkflowEngine(ArticleService articleService,
                          ArticleRepository articleRepository,
                          TopicService topicService,
                          ExecutionLogRepository executionLogRepository,
                          RetryPolicy retryPolicy,
                          RAGService ragService,
                          ObjectMapper objectMapper,
                          ResearchStep researchStep,
                          OutlineStep outlineStep,
                          WriterStep writerStep,
                          ReviewStep reviewStep,
                          SEOStep seoStep) {
        this(articleService, articleRepository, topicService, executionLogRepository, retryPolicy, ragService,
                objectMapper, null, researchStep, outlineStep, writerStep, reviewStep, seoStep);
    }

    /**
     * 异步启动分步生成工作流 — 由 StepGenerationService 在文章创建后调用
     */
    @Async
    public void startAsync(Long articleId, WorkflowRequest request) {
        log.info("异步工作流启动: articleId={}, topicId={}", articleId, request.topicId());
        try {
            var article = articleService.findEntityById(articleId);
            executeWorkflow(article, request);
        } catch (Exception e) {
            log.error("异步工作流执行失败: articleId={}", articleId, e);
            var failed = articleService.failGeneration(articleId);
            publishTerminal("failed", articleId, failed.status(), null);
        }
    }

    /**
     * 启动完整写作工作流
     */
    public WorkflowResponse start(WorkflowRequest request) {
        var article = createAndExecute(request);
        return new WorkflowResponse(article.id(), article.status(), "工作流已启动");
    }

    public ArticleResponse generateFromTopic(Long topicId) {
        var topic = topicService.findById(topicId);
        return createAndExecute(new WorkflowRequest(topicId, topic.title(), null));
    }

    private ArticleResponse createAndExecute(WorkflowRequest request) {
        articleService.ensureNoDuplicateTitle(request.title());
        var article = new Article();
        article.setTitle(request.title());
        if (request.topicId() != null) {
            article.setSource("topic:" + request.topicId());
        }
        var saved = articleService.saveEntity(article);

        articleService.startGeneration(saved.getId());
        return executeWorkflow(saved, request);
    }

    /**
     * 串行执行所有工作流步骤，每步带重试和错误分类，并更新生成进度。
     */
    private ArticleResponse executeWorkflow(Article article, WorkflowRequest request) {
        var context = new WorkflowContext(article, request);
        int total = steps.size();

        for (int i = 0; i < total; i++) {
            var step = steps.get(i);
            int stepIndex = i;
            var logEntry = createLog(article.getId(), step.name());

            try {
                // 更新进度：步骤运行中
                updateProgress(article.getId(), GenerationProgress.running(STEP_NAMES.get(i), i, total));

                logEntry.setStatus("running");
                saveGenerationLog(article.getId(), logEntry, "generating", GenerationProgress.running(
                        STEP_NAMES.get(i), i, total));

                // 通过 RetryPolicy 执行步骤，自动重试可重试错误
                retryPolicy.executeWithRetry(() -> {
                    step.execute(context);
                    return true; // 成功信号
                }, step.name());

                // 更新进度：步骤成功
                updateProgress(article.getId(), GenerationProgress.completed(STEP_NAMES.get(i), i, total));

                logEntry.setStatus("success");
                logEntry.setOutput(context.getCurrentOutput());
                saveGenerationLog(article.getId(), logEntry, "generating", GenerationProgress.completed(
                        STEP_NAMES.get(i), i, total));
                log.info("步骤 {} 完成: articleId={}", step.name(), article.getId());

            } catch (Exception e) {
                ErrorCategory category = ErrorCategory.classify(e);
                logEntry.setStatus("failed");
                logEntry.setErrorMsg("[%s] %s".formatted(category, e.getMessage()));
                saveGenerationLog(article.getId(), logEntry, "generating", GenerationProgress.failed(
                        STEP_NAMES.get(i), i));

                log.error("步骤 {} 失败 [{}]: articleId={}, error={}",
                        step.name(), category, article.getId(), e.getMessage());

                // 更新进度：步骤失败
                GenerationProgress failedProgress = GenerationProgress.failed(STEP_NAMES.get(i), i);
                updateProgress(article.getId(), failedProgress);
                var failed = articleService.failGeneration(article.getId());
                publishTerminal("failed", article.getId(), failed.status(), failedProgress);
                return failed;
            }
        }

        // 全部完成
        GenerationProgress doneProgress = GenerationProgress.done();
        updateProgress(article.getId(), doneProgress);
        var completed = articleService.completeGeneration(article.getId(), context.getCurrentOutput());
        publishTerminal("completed", article.getId(), completed.status(), doneProgress);
        log.info("工作流完成: articleId={}, steps={}", article.getId(), total);
        storeInRag(article.getId(), completed.contentMd());
        return completed;
    }

    /**
     * 写入生成进度到 article.generation_progress 字段
     */
    private void updateProgress(Long articleId, GenerationProgress progress) {
        try {
            String json = objectMapper.writeValueAsString(progress);
            articleRepository.updateProgress(articleId, json);
            if (generationEventHub != null) {
                generationEventHub.publishProgress(new GenerationEvent(articleId, "generating", progress, null));
            }
        } catch (Exception e) {
            log.warn("更新生成进度失败: articleId={}", articleId, e);
        }
    }

    private void storeInRag(Long articleId, String contentMd) {
        var logEntry = createLog(articleId, "rag_store");
        try {
            logEntry.setStatus("running");
            executionLogRepository.save(logEntry);
            int chunks = ragService.storeArticle(articleId, contentMd);
            logEntry.setStatus("success");
            logEntry.setOutput("chunks=" + chunks);
            executionLogRepository.save(logEntry);
        } catch (RuntimeException e) {
            logEntry.setStatus("failed");
            logEntry.setErrorMsg(e.getMessage());
            executionLogRepository.save(logEntry);
            log.warn("RAG 存储失败（文章已生成）: articleId={}, error={}",
                    articleId, e.getMessage());
        }
    }

    private ExecutionLog createLog(Long articleId, String step) {
        var logEntry = new ExecutionLog();
        logEntry.setArticleId(articleId);
        logEntry.setStep(step);
        logEntry.setStatus("pending");
        return executionLogRepository.save(logEntry);
    }

    private void saveGenerationLog(
            Long articleId,
            ExecutionLog logEntry,
            String status,
            GenerationProgress progress
    ) {
        ExecutionLog saved = executionLogRepository.save(logEntry);
        if (generationEventHub != null) {
            generationEventHub.publishLog(new GenerationEvent(
                    articleId,
                    status,
                    progress,
                    GenerationLogResponse.from(saved)
            ));
        }
    }

    private void publishTerminal(String eventName, Long articleId, String status, GenerationProgress progress) {
        if (generationEventHub != null) {
            generationEventHub.publishTerminal(
                    eventName,
                    new GenerationEvent(articleId, status, progress, null)
            );
        }
    }
}
