package com.agentwriter.service;

import com.agentwriter.entity.Article;
import com.agentwriter.entity.ArticleStatus;
import com.agentwriter.exception.ArticleNotFoundException;
import com.agentwriter.exception.ArticleTransitionConflictException;
import com.agentwriter.exception.DuplicateArticleException;
import com.agentwriter.repository.ArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleServiceTest {

    @Mock
    private ArticleRepository articleRepository;

    private ArticleService articleService;

    @BeforeEach
    void setUp() {
        articleService = new ArticleService(articleRepository);
    }

    @Test
    void permitsEveryAllowedTransition() {
        assertAllowed(ArticleStatus.DRAFT, ArticleStatus.GENERATING,
                () -> articleService.startGeneration(1L));
        assertAllowed(ArticleStatus.GENERATING, ArticleStatus.GENERATED,
                () -> articleService.completeGeneration(2L));
        assertAllowed(ArticleStatus.GENERATING, ArticleStatus.FAILED,
                () -> articleService.failGeneration(3L));
        assertAllowed(ArticleStatus.GENERATED, ArticleStatus.PUBLISHING,
                () -> articleService.startPublishing(4L));
        assertAllowed(ArticleStatus.PUBLISHING, ArticleStatus.GENERATED,
                () -> articleService.restoreGeneratedAfterPublishFailure(5L));
    }

    @Test
    void rejectsDraftToPublishedAndGeneratedToGenerated() {
        assertThatThrownBy(() -> articleService.transition(
                1L, ArticleStatus.DRAFT, ArticleStatus.PUBLISHING))
                .isInstanceOf(ArticleTransitionConflictException.class);
        assertThatThrownBy(() -> articleService.transition(
                1L, ArticleStatus.GENERATED, ArticleStatus.GENERATED))
                .isInstanceOf(ArticleTransitionConflictException.class);

        verify(articleRepository, never()).transitionStatus(
                1L, ArticleStatus.DRAFT.databaseValue(), ArticleStatus.PUBLISHING.databaseValue());
    }

    @Test
    void startPublishingRejectsDuplicateClaim() {
        when(articleRepository.transitionStatus(1L, "generated", "publishing"))
                .thenReturn(0);
        when(articleRepository.existsById(1L)).thenReturn(true);

        assertThatThrownBy(() -> articleService.startPublishing(1L))
                .isInstanceOf(ArticleTransitionConflictException.class);
    }

    @Test
    void transitionOfUnknownArticleIsNotFound() {
        when(articleRepository.transitionStatus(99L, "generated", "publishing"))
                .thenReturn(0);
        when(articleRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> articleService.startPublishing(99L))
                .isInstanceOf(ArticleNotFoundException.class);
    }

    @Test
    void completeGenerationWritesMarkdownWithTheGeneratingStateClaim() {
        when(articleRepository.completeGenerationWithContent(2L, "generating", "generated", "# Final"))
                .thenReturn(1);
        when(articleRepository.findById(2L))
                .thenReturn(Optional.of(article(2L, "generated", "# Final")));

        var response = articleService.completeGeneration(2L, "# Final");

        assertThat(response.status()).isEqualTo("generated");
        assertThat(response.contentMd()).isEqualTo("# Final");
        verify(articleRepository).completeGenerationWithContent(2L, "generating", "generated", "# Final");
    }

    @Test
    void completeGenerationNumberizesHeadingsBeforeSaving() {
        when(articleRepository.completeGenerationWithContent(
                2L, "generating", "generated", "## 1. 引言\n### 1.1 背景\n"))
                .thenReturn(1);
        when(articleRepository.findById(2L))
                .thenReturn(Optional.of(article(2L, "generated", "## 1. 引言\n### 1.1 背景\n")));

        var response = articleService.completeGeneration(2L, "## 引言\n### 背景\n");

        assertThat(response.contentMd()).isEqualTo("## 1. 引言\n### 1.1 背景\n");
        verify(articleRepository).completeGenerationWithContent(
                2L, "generating", "generated", "## 1. 引言\n### 1.1 背景\n");
    }

    @Test
    void rejectsMalformedStatusFilter() {
        assertThatThrownBy(() -> articleService.findByStatus("published"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("published");
        verify(articleRepository, never()).findByStatusOrderByCreatedAtDescIdDesc("published");
    }

    @Test
    void listsArticlesByNewestCreationTimeThenId() {
        when(articleRepository.findAllByOrderByCreatedAtDescIdDesc())
                .thenReturn(List.of(article(3L, "draft"), article(2L, "draft")));

        var articles = articleService.findAll();

        assertThat(articles).extracting(com.agentwriter.dto.ArticleResponse::id)
                .containsExactly(3L, 2L);
        verify(articleRepository).findAllByOrderByCreatedAtDescIdDesc();
    }

    @Test
    void listsArticlesByStatusUsingNewestFirstOrder() {
        when(articleRepository.findByStatusOrderByCreatedAtDescIdDesc("generated"))
                .thenReturn(List.of(article(4L, "generated")));

        var articles = articleService.findByStatus("generated");

        assertThat(articles).extracting(com.agentwriter.dto.ArticleResponse::id)
                .containsExactly(4L);
        verify(articleRepository).findByStatusOrderByCreatedAtDescIdDesc("generated");
    }

    @Test
    void saveEntityCannotBypassPolicyWithPublishedStatus() {
        Article article = article(1L, "published");

        assertThatThrownBy(() -> articleService.saveEntity(article))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("published");
        verify(articleRepository, never()).save(article);
    }

    @Test
    void duplicateTitleSkipsGenerationWhenSameTitleExistsInNonFailedState() {
        Article existing = article(7L, ArticleStatus.GENERATED.databaseValue());
        existing.setTitle("Java 21 新特性速览");
        when(articleRepository.findByTitleIgnoreCaseAndStatusNot(
                "Java 21 新特性速览", ArticleStatus.FAILED.databaseValue()))
                .thenReturn(List.of(existing));

        assertThatThrownBy(() -> articleService.ensureNoDuplicateTitle("  Java 21 新特性速览  "))
                .isInstanceOf(DuplicateArticleException.class)
                .hasMessageContaining("ID=7")
                .hasMessageContaining("已存在相同标题的文章");
    }

    @Test
    void duplicateTitleAllowsRegenerationWhenOnlyFailedArticlesShareTheTitle() {
        // 仓库按状态排除 failed，仅存在失败文章时返回空 → 允许重新生成
        when(articleRepository.findByTitleIgnoreCaseAndStatusNot(
                "Java 21 新特性速览", ArticleStatus.FAILED.databaseValue()))
                .thenReturn(List.of());

        assertThatCode(() -> articleService.ensureNoDuplicateTitle("Java 21 新特性速览"))
                .doesNotThrowAnyException();
    }

    @Test
    void duplicateTitleAllowsWhenNoMatchingArticleExists() {
        when(articleRepository.findByTitleIgnoreCaseAndStatusNot(
                "全新话题", ArticleStatus.FAILED.databaseValue()))
                .thenReturn(List.of());

        assertThatCode(() -> articleService.ensureNoDuplicateTitle("全新话题"))
                .doesNotThrowAnyException();
    }

    @Test
    void duplicateTitleIgnoresBlankTitle() {
        assertThatCode(() -> articleService.ensureNoDuplicateTitle("   "))
                .doesNotThrowAnyException();
    }

    @Test
    void concurrentPublishingClaimsHaveExactlyOneWinner() throws Exception {
        AtomicReference<String> state = new AtomicReference<>("generated");
        when(articleRepository.transitionStatus(1L, "generated", "publishing"))
                .thenAnswer(invocation -> state.compareAndSet("generated", "publishing") ? 1 : 0);
        when(articleRepository.existsById(1L)).thenReturn(true);
        when(articleRepository.findById(1L))
                .thenAnswer(invocation -> Optional.of(article(1L, state.get())));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Object> outcomes = Collections.synchronizedList(new ArrayList<>());
        try (var executor = Executors.newFixedThreadPool(2)) {
            for (int index = 0; index < 2; index++) {
                executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        outcomes.add(articleService.startPublishing(1L));
                    } catch (ArticleTransitionConflictException ex) {
                        outcomes.add(ex);
                    }
                    return null;
                });
            }
            ready.await();
            start.countDown();
        }

        assertThat(outcomes).hasSize(2);
        assertThat(outcomes.stream().filter(ArticleTransitionConflictException.class::isInstance))
                .hasSize(1);
        assertThat(outcomes.stream().filter(result -> !(result instanceof Exception)))
                .singleElement()
                .satisfies(result -> assertThat(
                        ((com.agentwriter.dto.ArticleResponse) result).status())
                        .isEqualTo("publishing"));
    }

    private void assertAllowed(
            ArticleStatus expected,
            ArticleStatus target,
            TransitionCommand command
    ) {
        Long id = switch (target) {
            case GENERATING -> 1L;
            case GENERATED -> expected == ArticleStatus.GENERATING ? 2L : 5L;
            case FAILED -> 3L;
            case PUBLISHING -> 4L;
            default -> throw new IllegalStateException("Unexpected target");
        };
        when(articleRepository.transitionStatus(
                id, expected.databaseValue(), target.databaseValue())).thenReturn(1);
        when(articleRepository.findById(id))
                .thenReturn(Optional.of(article(id, target.databaseValue())));

        assertThat(command.execute().status()).isEqualTo(target.databaseValue());
    }

    private static Article article(Long id, String status) {
        return article(id, status, null);
    }

    private static Article article(Long id, String status, String contentMd) {
        Article article = new Article();
        article.setId(id);
        article.setTitle("Test");
        article.setStatus(status);
        article.setContentMd(contentMd);
        return article;
    }

    @FunctionalInterface
    private interface TransitionCommand {
        com.agentwriter.dto.ArticleResponse execute();
    }
}
