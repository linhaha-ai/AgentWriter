package com.agentwriter.service;

import com.agentwriter.agent.Publisher;
import com.agentwriter.exception.ArticleTransitionConflictException;
import com.agentwriter.exception.PublicationFailedException;
import com.agentwriter.exception.PublisherUnavailableException;
import com.agentwriter.dto.ArticleResponse;
import com.agentwriter.dto.PublishResult;
import com.agentwriter.entity.Article;
import com.agentwriter.entity.ExecutionLog;
import com.agentwriter.repository.ExecutionLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ArticlePublicationServiceTest {

    @Mock
    private ArticleService articleService;
    @Mock
    private Publisher publisher;
    @Mock
    private ExecutionLogRepository executionLogRepository;

    private ArticlePublicationService publicationService;

    @BeforeEach
    void setUp() {
        publicationService = new ArticlePublicationService(articleService, publisher, executionLogRepository, new ObjectMapper());
    }

    @Test
    void claimsGeneratedThenPublishesExactlyOnceAndPersistsFullPublishResult() throws Exception {
        Article article = article(1L);
        PublishResult expected = new PublishResult("wechat", null, LocalDateTime.of(2026, 7, 27, 0, 0), 1L, null);
        when(articleService.startPublishing(1L)).thenReturn(response("publishing"));
        when(articleService.findEntityById(1L)).thenReturn(article);
        when(publisher.publish(article)).thenReturn(expected);

        assertThat(publicationService.publish(1L)).isSameAs(expected);

        verify(articleService).startPublishing(1L);
        verify(publisher).publish(article);
        ArgumentCaptor<ExecutionLog> log = ArgumentCaptor.forClass(ExecutionLog.class);
        verify(executionLogRepository).save(log.capture());
        assertThat(log.getValue().getStatus()).isEqualTo("succeeded");
        var serializedResult = new ObjectMapper().readTree(log.getValue().getOutput());
        assertThat(serializedResult.path("platform").asText()).isEqualTo("wechat");
        assertThat(serializedResult.path("url").isNull()).isTrue();
        assertThat(serializedResult.path("publishedAt").asText()).isEqualTo(expected.publishedAt().toString());
        assertThat(serializedResult.path("articleId").asLong()).isEqualTo(1L);
        assertThat(serializedResult.path("coverImage").isNull()).isTrue();
    }

    @Test
    void providerFailureRestoresGeneratedAndPersistsRedactedFailureLog() {
        Article article = article(1L);
        String secret = "synthetic-secret-must-not-leak";
        when(articleService.startPublishing(1L)).thenReturn(response("publishing"));
        when(articleService.findEntityById(1L)).thenReturn(article);
        when(publisher.publish(article)).thenThrow(new IllegalStateException("401 token=" + secret));

        assertThatThrownBy(() -> publicationService.publish(1L))
                .isInstanceOf(PublicationFailedException.class);

        verify(articleService).restoreGeneratedAfterPublishFailure(1L);
        ArgumentCaptor<ExecutionLog> log = ArgumentCaptor.forClass(ExecutionLog.class);
        verify(executionLogRepository).save(log.capture());
        assertThat(log.getValue().getStatus()).isEqualTo("failed");
        assertThat(log.getValue().getErrorMsg()).doesNotContain(secret).contains("[REDACTED]");
    }

    @Test
    void successfulResultLogRedactsCredentialBearingUrlWithoutDroppingDtoFields() throws Exception {
        Article article = article(1L);
        String secret = "synthetic-result-secret";
        when(articleService.startPublishing(1L)).thenReturn(response("publishing"));
        when(articleService.findEntityById(1L)).thenReturn(article);
        when(publisher.publish(article)).thenReturn(new PublishResult(
                "wechat", "https://example.test/result?access_token=" + secret,
                LocalDateTime.of(2026, 7, 27, 0, 0), 1L, "cover"));

        publicationService.publish(1L);

        ArgumentCaptor<ExecutionLog> log = ArgumentCaptor.forClass(ExecutionLog.class);
        verify(executionLogRepository).save(log.capture());
        var output = new ObjectMapper().readTree(log.getValue().getOutput());
        assertThat(output.path("platform").asText()).isEqualTo("wechat");
        assertThat(output.path("url").asText()).doesNotContain(secret).contains("[REDACTED]");
        assertThat(output.path("publishedAt").isTextual()).isTrue();
        assertThat(output.path("articleId").asLong()).isEqualTo(1L);
        assertThat(output.path("coverImage").asText()).isEqualTo("cover");
    }

    @Test
    void unavailablePublisherRestoresGeneratedAndPreservesTypedError() {
        Article article = article(1L);
        when(articleService.startPublishing(1L)).thenReturn(response("publishing"));
        when(articleService.findEntityById(1L)).thenReturn(article);
        when(publisher.publish(article)).thenThrow(new PublisherUnavailableException("not configured"));

        assertThatThrownBy(() -> publicationService.publish(1L))
                .isInstanceOf(PublisherUnavailableException.class);

        verify(articleService).restoreGeneratedAfterPublishFailure(1L);
    }

    @Test
    void compensationFailureDoesNotHideTheOriginalPublishFailure() {
        Article article = article(1L);
        RuntimeException publishFailure = new IllegalStateException("发布端失败");
        RuntimeException compensationFailure = new IllegalStateException("状态恢复失败");
        when(articleService.startPublishing(1L)).thenReturn(response("publishing"));
        when(articleService.findEntityById(1L)).thenReturn(article);
        when(publisher.publish(article)).thenThrow(publishFailure);
        doThrow(compensationFailure).when(articleService).restoreGeneratedAfterPublishFailure(1L);

        assertThatThrownBy(() -> publicationService.publish(1L))
                .isInstanceOf(PublicationFailedException.class)
                .hasCause(publishFailure);

        assertThat(publishFailure.getSuppressed()).contains(compensationFailure);
    }

    @Test
    void successfulRemotePublishDoesNotRestoreGeneratedWhenAuditSaveFails() {
        Article article = article(1L);
        PublishResult result = new PublishResult(
                "wechat", null, LocalDateTime.of(2026, 7, 27, 0, 0), 1L, null);
        RuntimeException auditFailure = new IllegalStateException("审计记录保存失败");
        when(articleService.startPublishing(1L)).thenReturn(response("publishing"));
        when(articleService.findEntityById(1L)).thenReturn(article);
        when(publisher.publish(article)).thenReturn(result);
        when(executionLogRepository.save(org.mockito.ArgumentMatchers.any(ExecutionLog.class)))
                .thenThrow(auditFailure);

        assertThatThrownBy(() -> publicationService.publish(1L))
                .isInstanceOf(PublicationFailedException.class)
                .hasMessage("文章已发布，但审计记录保存失败")
                .hasCause(auditFailure);

        verify(articleService, never()).restoreGeneratedAfterPublishFailure(1L);
    }

    @Test
    void concurrentCommandsReachTheProviderExactlyOnce() throws Exception {
        Article article = article(1L);
        AtomicBoolean claimed = new AtomicBoolean();
        when(articleService.startPublishing(1L)).thenAnswer(invocation -> {
            if (!claimed.compareAndSet(false, true)) {
                throw new ArticleTransitionConflictException(
                        1L, com.agentwriter.entity.ArticleStatus.GENERATED,
                        com.agentwriter.entity.ArticleStatus.PUBLISHING);
            }
            return response("publishing");
        });
        when(articleService.findEntityById(1L)).thenReturn(article);
        when(publisher.publish(article)).thenReturn(new PublishResult("wechat", null, LocalDateTime.now(), 1L, null));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Object> outcomes = Collections.synchronizedList(new ArrayList<>());
        try (var executor = Executors.newFixedThreadPool(2)) {
            for (int index = 0; index < 2; index++) {
                executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        outcomes.add(publicationService.publish(1L));
                    } catch (RuntimeException exception) {
                        outcomes.add(exception);
                    }
                    return null;
                });
            }
            ready.await();
            start.countDown();
        }

        assertThat(outcomes).hasSize(2);
        assertThat(outcomes.stream().filter(ArticleTransitionConflictException.class::isInstance)).hasSize(1);
        verify(publisher, times(1)).publish(article);
    }

    private static Article article(Long id) {
        Article article = new Article();
        article.setId(id);
        article.setTitle("Test");
        article.setStatus("publishing");
        return article;
    }

    private static ArticleResponse response(String status) {
        return new ArticleResponse(1L, "Test", null, null, status, "manual", null, null, null, null, null, null);
    }
}
