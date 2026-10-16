package com.agentwriter.agent;

import com.agentwriter.agent.impl.DefaultResearchAgent;
import com.agentwriter.service.LLMService;
import com.agentwriter.service.PromptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultResearchAgentTest {

    @Mock
    private LLMService llmService;
    @Mock
    private PromptService promptService;

    private DefaultResearchAgent agent;

    @BeforeEach
    void setUp() {
        agent = new DefaultResearchAgent(llmService, promptService);
        ReflectionTestUtils.setField(agent, "searchUrlTemplate", "http://127.0.0.1:1/{topic}");
        ReflectionTestUtils.setField(agent, "connectTimeoutSeconds", 1);
        ReflectionTestUtils.setField(agent, "responseTimeoutSeconds", 1);
        ReflectionTestUtils.setField(agent, "maxSources", 5);
    }

    @Test
    void shouldReturnResearchResult() {
        when(promptService.loadPrompt("research")).thenReturn("You are a researcher");
        when(llmService.generate(anyString(), anyString()))
                .thenReturn("""
                        {"success": true, "data": {"summary": "Java 21 introduces virtual threads", "sources": [{"title": "Oracle Blog", "url": "https://example.com", "content": "Virtual threads..."}]}}
                        """);

        var result = agent.research("Java 21 virtual threads");

        assertThat(result).isNotNull();
        assertThat(result).contains("\"success\": true");
        assertThat(result).contains("Java 21 introduces virtual threads");
    }

    @Test
    void shouldIncludeTopicInResponse() {
        when(promptService.loadPrompt("research")).thenReturn("You are a researcher");
        when(llmService.generate(anyString(), anyString()))
                .thenReturn("""
                        {"success": true, "data": {"summary": "Research on Spring Boot 4", "sources": []}}
                        """);

        var result = agent.research("Spring Boot 4");

        assertThat(result).contains("Spring Boot 4");
    }

    @Test
    void shouldHandleEmptyTopic() {
        when(promptService.loadPrompt("research")).thenReturn("You are a researcher");
        when(llmService.generate(anyString(), anyString()))
                .thenReturn("{\"success\": true, \"data\": {\"summary\": \"\", \"sources\": []}}");

        var result = agent.research("");

        assertThat(result).isNotNull();
        assertThat(result).contains("\"success\": true");
    }

    @Test
    void shouldHandleLongTopic() {
        var longTopic = "A".repeat(500);
        when(promptService.loadPrompt("research")).thenReturn("You are a researcher");
        when(llmService.generate(anyString(), anyString()))
                .thenReturn("{\"success\": true, \"data\": {\"summary\": \"Long topic research\", \"sources\": []}}");

        var result = agent.research(longTopic);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldUseConfiguredTopicProxyForJinaSearch() throws Exception {
        try (LoopbackProxy proxy = new LoopbackProxy("[来源标题](https://example.test/source)")) {
            ReflectionTestUtils.setField(agent, "searchUrlTemplate", "http://reader.test/{topic}");
            ReflectionTestUtils.setField(agent, "connectTimeoutSeconds", 1);
            ReflectionTestUtils.setField(agent, "responseTimeoutSeconds", 1);
            ReflectionTestUtils.setField(agent, "proxyHost", "127.0.0.1");
            ReflectionTestUtils.setField(agent, "proxyPort", proxy.port());
            ReflectionTestUtils.setField(agent, "useLlm", false);

            String result = agent.research("代理调研");

            assertThat(proxy.requests()).isEqualTo(1);
            assertThat(result).contains("来源标题", "https://example.test/source");
        }
    }

    private static final class LoopbackProxy implements AutoCloseable {

        private final HttpServer server;
        private final AtomicInteger requests = new AtomicInteger();

        private LoopbackProxy(String response) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                requests.incrementAndGet();
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                try (exchange) {
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
            });
            server.start();
        }

        int port() {
            return server.getAddress().getPort();
        }

        int requests() {
            return requests.get();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
