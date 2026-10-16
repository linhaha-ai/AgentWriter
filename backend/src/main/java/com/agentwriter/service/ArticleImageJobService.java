package com.agentwriter.service;

import com.agentwriter.entity.ArticleImage;
import com.agentwriter.entity.ArticleImageRole;
import com.agentwriter.entity.ArticleImageStatus;
import com.agentwriter.repository.ArticleImageRepository;
import com.agentwriter.repository.ArticleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 在独立线程中执行耗时的 ComfyUI 图片任务。
 */
@Service
public class ArticleImageJobService {

    private static final Logger log = LoggerFactory.getLogger(ArticleImageJobService.class);

    private final ArticleImageRepository articleImageRepository;
    private final ArticleRepository articleRepository;
    private final ComfyUiClient comfyUiClient;
    private final GeneratedImageStorage imageStorage;

    public ArticleImageJobService(
            ArticleImageRepository articleImageRepository,
            ArticleRepository articleRepository,
            ComfyUiClient comfyUiClient,
            GeneratedImageStorage imageStorage
    ) {
        this.articleImageRepository = articleImageRepository;
        this.articleRepository = articleRepository;
        this.comfyUiClient = comfyUiClient;
        this.imageStorage = imageStorage;
    }

    @Async
    @Transactional
    public void generateAsync(Long imageId) {
        ArticleImage image = articleImageRepository.findById(imageId).orElse(null);
        if (image == null || image.getStatus() != ArticleImageStatus.QUEUED) {
            return;
        }
        image.setStatus(ArticleImageStatus.GENERATING);
        articleImageRepository.save(image);
        try {
            ComfyGeneratedImage generated = comfyUiClient.generate(image.getPrompt());
            GeneratedImageStorage.StoredImage stored = imageStorage.store(
                    image.getArticle().getId(), image.getId(), generated
            );
            image.setComfyPromptId(generated.promptId());
            image.setStorageKey(stored.storageKey());
            image.setContentType(stored.contentType());
            image.setErrorMessage(null);
            image.setStatus(ArticleImageStatus.READY);
            articleImageRepository.save(image);
            if (image.getRole() == ArticleImageRole.COVER) {
                image.getArticle().setCoverImage("/api/article-images/" + image.getId() + "/content");
                articleRepository.save(image.getArticle());
            }
        } catch (RuntimeException exception) {
            image.setStatus(ArticleImageStatus.FAILED);
            image.setErrorMessage(safeMessage(exception));
            articleImageRepository.save(image);
            log.warn("文章图片生成失败: imageId={}, type={}", imageId, exception.getClass().getSimpleName());
        }
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "图片生成失败，请检查 ComfyUI 服务状态";
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
