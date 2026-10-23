package com.agentwriter.service;

import com.agentwriter.common.MarkdownCollapseExpander;
import com.agentwriter.common.MarkdownHeadingNumberer;
import com.agentwriter.dto.ArticleCreateRequest;
import com.agentwriter.dto.ArticleResponse;
import com.agentwriter.dto.GenerationLogDetail;
import com.agentwriter.dto.GenerationLogSummary;
import com.agentwriter.entity.Article;
import com.agentwriter.entity.ArticleStatus;
import com.agentwriter.entity.Topic;
import com.agentwriter.exception.ArticleNotFoundException;
import com.agentwriter.exception.ArticleTransitionConflictException;
import com.agentwriter.exception.DuplicateArticleException;
import com.agentwriter.repository.ArticleRepository;
import com.agentwriter.repository.ExecutionLogRepository;
import com.agentwriter.repository.TopicRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final ExecutionLogRepository executionLogRepository;
    private final TopicRepository topicRepository;

    public ArticleService(ArticleRepository articleRepository,
                          ExecutionLogRepository executionLogRepository,
                          TopicRepository topicRepository) {
        this.articleRepository = articleRepository;
        this.executionLogRepository = executionLogRepository;
        this.topicRepository = topicRepository;
    }

    /**
     * 查询文章的生成环节日志详情：每个环节的输入（做了什么）、产出（输出）、状态与耗时。
     */
    public List<GenerationLogDetail> findGenerationLogs(Long id) {
        // 文章不存在时抛出 404
        findById(id);
        return executionLogRepository.findByArticleIdOrderByCreatedAtAsc(id).stream()
                .map(GenerationLogDetail::from)
                .toList();
    }

    /**
     * 查询全部文章的生成日志概览（按最近执行时间倒序）。
     */
    public List<GenerationLogSummary> findGenerationLogSummaries() {
        return executionLogRepository.findArticleLogSummaries().stream()
                .map(GenerationLogSummary::from)
                .toList();
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

    /**
     * 生成前的去重校验：若已存在相同标题（忽略大小写、首尾空格）且非失败状态的文章，则抛出
     * {@link DuplicateArticleException} 跳过本次生成；仅存在 FAILED 文章时允许重新生成。
     */
    public void ensureNoDuplicateTitle(String title) {
        if (title == null || title.isBlank()) {
            return;
        }
        List<Article> existing = articleRepository.findByTitleIgnoreCaseAndStatusNot(
                title.trim(), ArticleStatus.FAILED.databaseValue());
        if (!existing.isEmpty()) {
            Article first = existing.getFirst();
            throw new DuplicateArticleException(title.trim(), first.getId(), first.getStatus());
        }
    }

    public ArticleResponse findById(Long id) {
        var article = articleRepository.findById(id)
                .orElseThrow(() -> new ArticleNotFoundException(id));
        return enrichTopic(ArticleResponse.from(article));
    }

    public List<ArticleResponse> findAll() {
        return enrichTopics(articleRepository.findAllByOrderByCreatedAtDescIdDesc().stream()
                .map(ArticleResponse::from)
                .toList());
    }

    public List<ArticleResponse> findByStatus(String status) {
        String databaseValue = ArticleStatus.parse(status).databaseValue();
        return enrichTopics(articleRepository.findByStatusOrderByCreatedAtDescIdDesc(databaseValue).stream()
                .map(ArticleResponse::from)
                .toList());
    }

    /**
     * 批量补齐话题引用信息：从 source 解析出的 topicId 批量查询话题标题与热度。
     */
    private List<ArticleResponse> enrichTopics(List<ArticleResponse> responses) {
        var topicIds = responses.stream()
                .map(ArticleResponse::topicId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (topicIds.isEmpty()) {
            return responses;
        }
        Map<Long, Topic> topics = topicRepository.findAllById(topicIds).stream()
                .collect(Collectors.toMap(Topic::getId, Function.identity()));
        return responses.stream()
                .map(response -> {
                    var topic = response.topicId() != null ? topics.get(response.topicId()) : null;
                    return topic != null
                            ? response.withTopic(topic.getTitle(), topic.getScore())
                            : response;
                })
                .toList();
    }

    /**
     * 单条补齐话题引用信息：详情接口使用。
     */
    private ArticleResponse enrichTopic(ArticleResponse response) {
        if (response.topicId() == null) {
            return response;
        }
        return topicRepository.findById(response.topicId())
                .map(topic -> response.withTopic(topic.getTitle(), topic.getScore()))
                .orElse(response);
    }

    public ArticleResponse updateContent(Long id, String contentMd, String contentHtml) {
        var article = articleRepository.findById(id)
                .orElseThrow(() -> new ArticleNotFoundException(id));
        // 编辑保存同样展开折叠块，保证代码块默认全部展开
        article.setContentMd(MarkdownCollapseExpander.expand(contentMd));
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
            article.setContentMd(MarkdownCollapseExpander.expand((String) updates.get("contentMd")));
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
        // 生成完成统一规范化：标题编号 + 展开折叠块（保证代码块默认全部展开）
        String normalized = MarkdownCollapseExpander.expand(MarkdownHeadingNumberer.numberize(contentMd));
        int updated = articleRepository.completeGenerationWithContent(
                id,
                ArticleStatus.GENERATING.databaseValue(),
                ArticleStatus.GENERATED.databaseValue(),
                normalized);
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

    /**
     * 草稿保存成功后回到已生成状态（公众号不支持接口一键发布，成功语义为已存草稿箱）。
     */
    public ArticleResponse completePublishing(Long id) {
        return transition(id, ArticleStatus.PUBLISHING, ArticleStatus.GENERATED);
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
