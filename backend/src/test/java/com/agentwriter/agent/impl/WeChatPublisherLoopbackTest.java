package com.agentwriter.agent.impl;

import com.agentwriter.dto.PublishResult;
import com.agentwriter.dto.ArticleResponse;
import com.agentwriter.exception.PublisherUnavailableException;
import com.agentwriter.entity.Article;
import com.agentwriter.entity.ExecutionLog;
import com.agentwriter.repository.ExecutionLogRepository;
import com.agentwriter.service.ArticlePublicationService;
import com.agentwriter.service.ArticleService;
import com.agentwriter.exception.PublicationFailedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class WeChatPublisherLoopbackTest {

    private HttpServer server;
    private final AtomicInteger tokenCalls = new AtomicInteger();
    private final AtomicInteger draftCalls = new AtomicInteger();
    private final AtomicInteger submitCalls = new AtomicInteger();
    private volatile int tokenStatus;
    private volatile boolean malformedToken;
    private volatile boolean delayToken;
    private volatile long tokenDelayMillis;

    @BeforeEach
    void startServer() throws IOException {
        tokenStatus = 200;
        malformedToken = false;
        delayToken = false;
        tokenDelayMillis = 0;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/cgi-bin/token", this::token);
        server.createContext("/cgi-bin/draft/add", this::draft);
        server.createContext("/cgi-bin/freepublish/submit", this::submit);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void loopbackPublishesTokenAndDraftWithoutFreepublishSubmitCall() {
        PublishResult result = publisher().publish(article());

        assertThat(tokenCalls).hasValue(1);
        assertThat(draftCalls).hasValue(1);
        assertThat(submitCalls).hasValue(0);
        assertThat(result.platform()).isEqualTo("wechat");
        assertThat(result.url()).isNull();
    }

    @Test
    void loopback401FailsAfterOneTokenAttemptWithoutDraftOrSubmitRetry() {
        tokenStatus = 401;

        assertThatThrownBy(() -> publisher().publish(article()))
                .isInstanceOf(RuntimeException.class);

        assertThat(tokenCalls).hasValue(1);
        assertThat(draftCalls).hasValue(0);
        assertThat(submitCalls).hasValue(0);
    }

    @Test
    void malformedTokenResponseFailsAfterOneAttempt() {
        malformedToken = true;

        assertThatThrownBy(() -> publisher().publish(article()))
                .isInstanceOf(RuntimeException.class);

        assertThat(tokenCalls).hasValue(1);
        assertThat(draftCalls).hasValue(0);
        assertThat(submitCalls).hasValue(0);
    }

    @Test
    void delayedTokenFailsAfterOneAttemptWithoutRetry() {
        delayToken = true;

        assertThatThrownBy(() -> publisher().publish(article()))
                .isInstanceOf(RuntimeException.class);

        assertThat(tokenCalls).hasValue(1);
        assertThat(draftCalls).hasValue(0);
        assertThat(submitCalls).hasValue(0);
    }

    @Test
    void loopback401RestoresGeneratedAndRecordsSecretFreeLog() {
        tokenStatus = 401;
        ArticleService articleService = mock(ArticleService.class);
        ExecutionLogRepository logs = mock(ExecutionLogRepository.class);
        Article publicationArticle = article();
        when(articleService.startPublishing(1L)).thenReturn(response());
        when(articleService.findEntityById(1L)).thenReturn(publicationArticle);
        ArticlePublicationService service = new ArticlePublicationService(articleService, publisher(), logs, new ObjectMapper());

        assertThatThrownBy(() -> service.publish(1L)).isInstanceOf(PublicationFailedException.class);

        verify(articleService).restoreGeneratedAfterPublishFailure(1L);
        ArgumentCaptor<ExecutionLog> captured = ArgumentCaptor.forClass(ExecutionLog.class);
        verify(logs).save(captured.capture());
        assertThat(captured.getValue().getErrorMsg()).doesNotContain("synthetic-loopback-secret");
        assertThat(tokenCalls).hasValue(1);
        assertThat(draftCalls).hasValue(0);
        assertThat(submitCalls).hasValue(0);
    }

    @Test
    void enabledPublisherWithMissingEnvironmentCredentialIsExplicitlyUnavailable() {
        WeChatPublisher missingCredentialPublisher = new WeChatPublisher(
                "loopback-app", "", "AgentWriter", "",
                "http://127.0.0.1:" + server.getAddress().getPort(),
                WebClient.builder().build(), new ObjectMapper());

        assertThatThrownBy(() -> missingCredentialPublisher.publish(article()))
                .isInstanceOf(PublisherUnavailableException.class);

        assertThat(tokenCalls).hasValue(0);
    }

    @Test
    void concurrentCacheMissRequestsTokenOnlyOnce() throws Exception {
        tokenDelayMillis = 200;
        WeChatPublisher publisher = publisher();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<String> tokens = new ArrayList<>();

        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = new ArrayList<java.util.concurrent.Future<String>>();
            for (int index = 0; index < 2; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return publisher.getAccessToken();
                }));
            }
            ready.await();
            start.countDown();
            for (var future : futures) {
                tokens.add(future.get());
            }
        }

        assertThat(tokens).containsExactly("loopback-token", "loopback-token");
        assertThat(tokenCalls).hasValue(1);
    }

    private WeChatPublisher publisher() {
        return new WeChatPublisher(
                "loopback-app", "synthetic-loopback-secret", "AgentWriter", "",
                "http://127.0.0.1:" + server.getAddress().getPort(),
                WebClient.builder().build(), new ObjectMapper());
    }

    private static Article article() {
        Article article = new Article();
        article.setId(1L);
        article.setTitle("Loopback");
        article.setContentMd("# Test");
        article.setStatus("publishing");
        return article;
    }

    private void token(HttpExchange exchange) throws IOException {
        tokenCalls.incrementAndGet();
        if (tokenDelayMillis > 0) {
            try {
                Thread.sleep(tokenDelayMillis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("令牌响应等待被中断", exception);
            }
        }
        if (delayToken) {
            try {
                Thread.sleep(10_500);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("令牌响应等待被中断", exception);
            }
        }
        if (malformedToken) {
            respond(exchange, 200, "{");
            return;
        }
        respond(exchange, tokenStatus, tokenStatus == 200
                ? "{\"access_token\":\"loopback-token\",\"expires_in\":7200}"
                : "{\"errcode\":401,\"errmsg\":\"unauthorized\"}");
    }

    private void draft(HttpExchange exchange) throws IOException {
        draftCalls.incrementAndGet();
        respond(exchange, 200, "{\"media_id\":\"loopback-media\"}");
    }

    private void submit(HttpExchange exchange) throws IOException {
        submitCalls.incrementAndGet();
        respond(exchange, 200, "{\"publish_id\":\"loopback-publish\"}");
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static ArticleResponse response() {
        return new ArticleResponse(1L, "Loopback", null, null, "publishing", "manual", null, null, null, null, null, null, null, null, null);
    }
}
