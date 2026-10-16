package com.agentwriter.service;

import com.agentwriter.dto.ArticleImageGenerateRequest;
import com.agentwriter.dto.ArticleImageResponse;
import com.agentwriter.dto.ArticleResponse;
import com.agentwriter.dto.ImageSuggestion;
import com.agentwriter.entity.Article;
import com.agentwriter.entity.ArticleImage;
import com.agentwriter.entity.ArticleImageRole;
import com.agentwriter.entity.ArticleImageStatus;
import com.agentwriter.repository.ArticleImageRepository;
import com.agentwriter.repository.ArticleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文章图片的建议、任务查询与 Markdown 插入。
 */
@Service
public class ArticleImageService {

    private final ArticleRepository articleRepository;
    private final ArticleImageRepository articleImageRepository;
    private final ImageSuggestionService suggestionService;
    private final ComfyUiClient comfyUiClient;
    private final ArticleImageJobService jobService;

    public ArticleImageService(
            ArticleRepository articleRepository,
            ArticleImageRepository articleImageRepository,
            ImageSuggestionService suggestionService,
            ComfyUiClient comfyUiClient,
            ArticleImageJobService jobService
    ) {
        this.articleRepository = articleRepository;
        this.articleImageRepository = articleImageRepository;
        this.suggestionService = suggestionService;
        this.comfyUiClient = comfyUiClient;
        this.jobService = jobService;
    }

    @Transactional(readOnly = true)
    public List<ImageSuggestion> suggest(Long articleId) {
        return suggestionService.suggest(requireArticle(articleId));
    }

    @Transactional
    public ArticleImageResponse queue(Long articleId, ArticleImageGenerateRequest request) {
        comfyUiClient.validateConfiguration();
        Article article = requireArticle(articleId);
        validateRequest(request);
        ArticleImage image = new ArticleImage();
        image.setArticle(article);
        image.setRole(request.role());
        image.setHeadingAnchor(normalizeHeading(request.headingAnchor()));
        image.setPrompt(request.prompt().trim());
        image.setAltText(request.altText().trim());
        image.setStatus(ArticleImageStatus.QUEUED);
        ArticleImage saved = articleImageRepository.save(image);
        scheduleGeneration(saved.getId());
        return ArticleImageResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ArticleImageResponse> findAll(Long articleId) {
        requireArticle(articleId);
        return articleImageRepository.findByArticleIdOrderByCreatedAtAsc(articleId).stream()
                .map(ArticleImageResponse::from)
                .toList();
    }

    @Transactional
    public ArticleResponse insert(Long articleId, Long imageId) {
        Article article = requireArticle(articleId);
        ArticleImage image = articleImageRepository.findByIdAndArticleId(imageId, articleId)
                .orElseThrow(() -> new IllegalArgumentException("文章图片不存在"));
        if (image.getRole() != ArticleImageRole.INLINE || image.getStatus() != ArticleImageStatus.READY) {
            throw new IllegalArgumentException("仅可插入已生成的正文插图");
        }
        String marker = "/api/article-images/" + image.getId() + "/content";
        String content = article.getContentMd();
        if (content == null || !content.contains(marker)) {
            article.setContentMd(insertAfterHeading(content, image, marker));
        }
        article.setContentHtml(null);
        image.setInserted(true);
        articleImageRepository.save(image);
        return ArticleResponse.from(articleRepository.save(article));
    }

    @Transactional(readOnly = true)
    public ArticleImage requireReadyImage(Long imageId) {
        ArticleImage image = articleImageRepository.findById(imageId)
                .orElseThrow(() -> new IllegalArgumentException("文章图片不存在"));
        if (image.getStatus() != ArticleImageStatus.READY) {
            throw new IllegalArgumentException("图片尚未生成完成");
        }
        return image;
    }

    private Article requireArticle(Long articleId) {
        return articleRepository.findById(articleId)
                .orElseThrow(() -> new com.agentwriter.exception.ArticleNotFoundException(articleId));
    }

    private static void validateRequest(ArticleImageGenerateRequest request) {
        if (request.prompt().trim().length() > 4000 || request.altText().trim().length() > 500) {
            throw new IllegalArgumentException("图片提示词或替代文本过长");
        }
        if (request.role() == ArticleImageRole.INLINE && normalizeHeading(request.headingAnchor()) == null) {
            throw new IllegalArgumentException("正文插图必须指定二级标题");
        }
        if (request.role() == ArticleImageRole.COVER && normalizeHeading(request.headingAnchor()) != null) {
            throw new IllegalArgumentException("封面图片不能指定正文标题");
        }
    }

    private static String insertAfterHeading(String content, ArticleImage image, String marker) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("文章正文为空，无法插入图片");
        }
        Pattern pattern = Pattern.compile("(?m)^##\\s+" + Pattern.quote(image.getHeadingAnchor()) + "\\s*$");
        Matcher matcher = pattern.matcher(content);
        if (!matcher.find()) {
            throw new IllegalArgumentException("未找到图片对应的二级标题");
        }
        String markdown = "![" + image.getAltText().replace("]", "") + "](" + marker + ")";
        return content.substring(0, matcher.end()) + "\n\n" + markdown + "\n" + content.substring(matcher.end());
    }

    private static String normalizeHeading(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void scheduleGeneration(Long imageId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            jobService.generateAsync(imageId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                jobService.generateAsync(imageId);
            }
        });
    }
}
