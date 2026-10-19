package com.agentwriter.controller;

import com.agentwriter.common.ApiResponse;
import com.agentwriter.dto.ArticleCreateRequest;
import com.agentwriter.dto.ArticleImageGenerateRequest;
import com.agentwriter.dto.ArticleImageResponse;
import com.agentwriter.dto.ArticleResponse;
import com.agentwriter.dto.GenerationLogDetail;
import com.agentwriter.dto.ImageSuggestion;
import com.agentwriter.dto.ProgressResponse;
import com.agentwriter.dto.PublishResult;
import com.agentwriter.dto.StepGenerateRequest;
import com.agentwriter.service.ArticlePublicationService;
import com.agentwriter.service.ArticleImageService;
import com.agentwriter.service.ArticleService;
import com.agentwriter.service.StepGenerationService;
import com.agentwriter.workflow.WorkflowEngine;
import com.agentwriter.workflow.GenerationEventHub;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/articles")
public class ArticleController {

    private final ArticleService articleService;
    private final ArticlePublicationService articlePublicationService;
    private final WorkflowEngine workflowEngine;
    private final StepGenerationService stepGenerationService;
    private final ArticleImageService articleImageService;
    private final GenerationEventHub generationEventHub;

    @Autowired
    public ArticleController(ArticleService articleService,
                             ArticlePublicationService articlePublicationService,
                             WorkflowEngine workflowEngine,
                             StepGenerationService stepGenerationService,
                             ArticleImageService articleImageService,
                             GenerationEventHub generationEventHub) {
        this.articleService = articleService;
        this.articlePublicationService = articlePublicationService;
        this.workflowEngine = workflowEngine;
        this.stepGenerationService = stepGenerationService;
        this.articleImageService = articleImageService;
        this.generationEventHub = generationEventHub;
    }

    public ArticleController(ArticleService articleService,
                             ArticlePublicationService articlePublicationService,
                             WorkflowEngine workflowEngine,
                             StepGenerationService stepGenerationService,
                             ArticleImageService articleImageService) {
        this(articleService, articlePublicationService, workflowEngine, stepGenerationService, articleImageService, null);
    }

    public ArticleController(ArticleService articleService,
                             ArticlePublicationService articlePublicationService,
                             WorkflowEngine workflowEngine,
                             StepGenerationService stepGenerationService) {
        this(articleService, articlePublicationService, workflowEngine, stepGenerationService, null, null);
    }

    public ArticleController(ArticleService articleService, ArticlePublicationService articlePublicationService) {
        this(articleService, articlePublicationService, null, null, null, null);
    }

    public ArticleController(ArticleService articleService) {
        this(articleService, null, null, null, null, null);
    }

    @PostMapping
    public ApiResponse<ArticleResponse> create(@Valid @RequestBody ArticleCreateRequest request) {
        return ApiResponse.ok(articleService.create(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<ArticleResponse> findById(@PathVariable Long id) {
        return ApiResponse.ok(articleService.findById(id));
    }

    @GetMapping("/{id}/logs")
    public ApiResponse<List<GenerationLogDetail>> findGenerationLogs(@PathVariable Long id) {
        return ApiResponse.ok(articleService.findGenerationLogs(id));
    }

    @GetMapping
    public ApiResponse<List<ArticleResponse>> findAll(@RequestParam(required = false) String status) {
        if (status != null) {
            return ApiResponse.ok(articleService.findByStatus(status));
        }
        return ApiResponse.ok(articleService.findAll());
    }

    @PatchMapping("/{id}")
    public ApiResponse<ArticleResponse> update(@PathVariable Long id, @RequestBody Map<String, Object> updates) {
        return ApiResponse.ok(articleService.updateArticle(id, updates));
    }

    @PostMapping("/generate")
    public ApiResponse<ArticleResponse> generate(@RequestBody Map<String, Long> body) {
        return ApiResponse.ok(workflowEngine.generateFromTopic(body.get("topicId")));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<PublishResult> publish(@PathVariable Long id) {
        return ApiResponse.ok(articlePublicationService.publish(id));
    }

    @PostMapping("/step-generate")
    public ApiResponse<Map<String, Long>> stepGenerate(@Valid @RequestBody StepGenerateRequest request) {
        Long articleId = stepGenerationService.stepGenerate(request);
        return ApiResponse.ok(Map.of("id", articleId));
    }

    @GetMapping("/{id}/progress")
    public ApiResponse<ProgressResponse> getProgress(@PathVariable Long id) {
        return ApiResponse.ok(stepGenerationService.getProgress(id));
    }

    @GetMapping(value = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> generationEvents(@PathVariable Long id) {
        if (generationEventHub == null || stepGenerationService == null) {
            throw new IllegalStateException("生成进度功能未初始化");
        }
        SseEmitter emitter = generationEventHub.subscribe(id, stepGenerationService.getProgress(id));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .header("X-Accel-Buffering", "no")
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(emitter);
    }

    @PostMapping("/{id}/image-suggestions")
    public ApiResponse<List<ImageSuggestion>> imageSuggestions(@PathVariable Long id) {
        return ApiResponse.ok(imageService().suggest(id));
    }

    @PostMapping("/{id}/images")
    public ApiResponse<ArticleImageResponse> generateImage(
            @PathVariable Long id,
            @Valid @RequestBody ArticleImageGenerateRequest request
    ) {
        return ApiResponse.ok(imageService().queue(id, request));
    }

    @GetMapping("/{id}/images")
    public ApiResponse<List<ArticleImageResponse>> images(@PathVariable Long id) {
        return ApiResponse.ok(imageService().findAll(id));
    }

    @PostMapping("/{id}/images/{imageId}/insert")
    public ApiResponse<ArticleResponse> insertImage(@PathVariable Long id, @PathVariable Long imageId) {
        return ApiResponse.ok(imageService().insert(id, imageId));
    }

    private ArticleImageService imageService() {
        if (articleImageService == null) {
            throw new IllegalStateException("图片生成功能未初始化");
        }
        return articleImageService;
    }
}
