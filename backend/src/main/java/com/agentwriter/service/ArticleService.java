package com.agentwriter.service;

import com.agentwriter.dto.ArticleCreateRequest;
import com.agentwriter.dto.ArticleResponse;
import com.agentwriter.entity.Article;
import com.agentwriter.entity.ArticleStatus;
import com.agentwriter.exception.ArticleNotFoundException;
import com.agentwriter.exception.ArticleTransitionConflictException;
import com.agentwriter.repository.ArticleRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ArticleService {

    private final ArticleRepository articleRepository;

    public ArticleService(ArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
    }

    public ArticleResponse create(ArticleCreateRequest request) {
        var article = new Article();
        article.setTitle(request.title());
        article.setContentMd(request.contentMd());
        article.setSource(request.source());
        article.setCoverImage(request.coverImage());
        article.setStatus(ArticleStatus.DRAFT.databaseValue());
        var saved = articleRepository.save(article);
        return ArticleResponse.from(saved);
    }

    public ArticleResponse findById(Long id) {
        var article = articleRepository.findById(id)
                .orElseThrow(() -> new ArticleNotFoundException(id));
        return ArticleResponse.from(article);
    }

    public List<ArticleResponse> findAll() {
        return articleRepository.findAllByOrderByCreatedAtDescIdDesc().stream()
                .map(ArticleResponse::from)
                .toList();
    }

    public List<ArticleResponse> findByStatus(String status) {
        String databaseValue = ArticleStatus.parse(status).databaseValue();
        return articleRepository.findByStatusOrderByCreatedAtDescIdDesc(databaseValue).stream()
                .map(ArticleResponse::from)
                .toList();
    }

    public ArticleResponse updateContent(Long id, String contentMd, String contentHtml) {
        var article = articleRepository.findById(id)
                .orElseThrow(() -> new ArticleNotFoundException(id));
        article.setContentMd(contentMd);
        if (contentHtml != null) {
            article.setContentHtml(contentHtml);
        }
        var saved = articleRepository.save(article);
        return ArticleResponse.from(saved);
    }

    public ArticleResponse updateArticle(Long id, Map<String, Object> updates) {
        var article = articleRepository.findById(id)
                .orElseThrow(() -> new ArticleNotFoundException(id));
        if (updates.containsKey("title")) {
            article.setTitle((String) updates.get("title"));
        }
        if (updates.containsKey("contentMd")) {
            article.setContentMd((String) updates.get("contentMd"));
        }
        if (updates.containsKey("coverImage")) {
            article.setCoverImage((String) updates.get("coverImage"));
        }
        var saved = articleRepository.save(article);
        return ArticleResponse.from(saved);
    }

    public ArticleResponse startGeneration(Long id) {
        return transition(id, ArticleStatus.DRAFT, ArticleStatus.GENERATING);
    }

    public ArticleResponse completeGeneration(Long id) {
        return transition(id, ArticleStatus.GENERATING, ArticleStatus.GENERATED);
    }

    public ArticleResponse completeGeneration(Long id, String contentMd) {
        int updated = articleRepository.completeGenerationWithContent(
                id,
                ArticleStatus.GENERATING.databaseValue(),
                ArticleStatus.GENERATED.databaseValue(),
                contentMd);
        if (updated == 0) {
            if (!articleRepository.existsById(id)) {
                throw new ArticleNotFoundException(id);
            }
            throw new ArticleTransitionConflictException(
                    id, ArticleStatus.GENERATING, ArticleStatus.GENERATED);
        }
        return articleRepository.findById(id)
                .map(ArticleResponse::from)
                .orElseThrow(() -> new ArticleNotFoundException(id));
    }

    public ArticleResponse failGeneration(Long id) {
        return transition(id, ArticleStatus.GENERATING, ArticleStatus.FAILED);
    }

    public ArticleResponse startPublishing(Long id) {
        return transition(id, ArticleStatus.GENERATED, ArticleStatus.PUBLISHING);
    }

    public ArticleResponse restoreGeneratedAfterPublishFailure(Long id) {
        return transition(id, ArticleStatus.PUBLISHING, ArticleStatus.GENERATED);
    }

    ArticleResponse transition(Long id, ArticleStatus expected, ArticleStatus target) {
        if (!expected.canTransitionTo(target)) {
            throw new ArticleTransitionConflictException(id, expected, target);
        }
        int updated = articleRepository.transitionStatus(
                id, expected.databaseValue(), target.databaseValue());
        if (updated == 0) {
            if (!articleRepository.existsById(id)) {
                throw new ArticleNotFoundException(id);
            }
            throw new ArticleTransitionConflictException(id, expected, target);
        }
        return articleRepository.findById(id)
                .map(ArticleResponse::from)
                .orElseThrow(() -> new ArticleNotFoundException(id));
    }

    public Article findEntityById(Long id) {
        return articleRepository.findById(id)
                .orElseThrow(() -> new ArticleNotFoundException(id));
    }

    public Article saveEntity(Article article) {
        ArticleStatus.parse(article.getStatus());
        return articleRepository.save(article);
    }
}
