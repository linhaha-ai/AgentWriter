package com.agentwriter.agent;

import com.agentwriter.agent.impl.RoutingPublisher;
import com.agentwriter.dto.PublisherRuntimeConfig;
import com.agentwriter.dto.WechatRuntimeConfig;
import com.agentwriter.entity.Article;
import com.agentwriter.exception.PublisherUnavailableException;
import com.agentwriter.service.SettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoutingPublisherTest {

    private HttpServer server;
    private SettingsService settingsService;
    private Environment environment;
    private AtomicReference<String> draftRequest;
    private AtomicReference<String> appId;

    @BeforeEach
    void setUp() throws IOException {
        settingsService = mock(SettingsService.class);
        environment = mock(Environment.class);
        draftRequest = new AtomicReference<>();
        appId = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/cgi-bin/token", this::token);
        server.createContext("/cgi-bin/draft/add", this::draft);
        server.createContext("/cgi-bin/freepublish/submit", exchange -> respond(exchange, "{\"publish_id\":\"task\"}"));
        server.start();
        when(environment.getProperty("WECHAT_APP_SECRET")).thenReturn("synthetic-runtime-secret");
        when(environment.getProperty("agentwriter.publisher.wechat.api-base-url"))
                .thenReturn("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void readsCurrentPersistedPublisherSettingsForEveryPublishCommand() {
        when(settingsService.getPublisherRuntimeConfig()).thenReturn(config(true, "first-app", "First author", "first-thumb"));
        RoutingPublisher publisher = new RoutingPublisher(settingsService, environment, new ObjectMapper(), WebClient.builder());

        publisher.publish(article());

        assertThat(appId.get()).isEqualTo("first-app");
        assertThat(draftRequest.get()).contains("\"author\":\"First author\"");
        assertThat(draftRequest.get()).contains("\"thumb_media_id\":\"first-thumb\"");

        when(settingsService.getPublisherRuntimeConfig()).thenReturn(config(false, "second-app", "Second author", "second-thumb"));
        assertThatThrownBy(() -> publisher.publish(article())).isInstanceOf(PublisherUnavailableException.class);
        assertThat(appId.get()).isEqualTo("first-app");
    }

    private PublisherRuntimeConfig config(boolean enabled, String app, String configuredAuthor, String thumb) {
        return new PublisherRuntimeConfig(new WechatRuntimeConfig(enabled, app, "not-used-at-runtime", configuredAuthor, thumb));
    }

    private static Article article() {
        Article article = new Article();
        article.setId(1L);
        article.setTitle("Runtime routing");
        article.setContentMd("body");
        return article;
    }

    private void token(HttpExchange exchange) throws IOException {
        appId.set(exchange.getRequestURI().getQuery().replaceFirst(".*appid=([^&]+).*", "$1"));
        respond(exchange, "{\"access_token\":\"runtime-token\",\"expires_in\":7200}");
    }

    private void draft(HttpExchange exchange) throws IOException {
        String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        draftRequest.set(request);
        respond(exchange, "{\"media_id\":\"runtime-media\"}");
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
