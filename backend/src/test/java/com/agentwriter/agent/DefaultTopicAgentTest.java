package com.agentwriter.agent;

import com.agentwriter.agent.impl.DefaultTopicAgent;
import com.agentwriter.service.LLMService;
import com.agentwriter.service.PromptService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultTopicAgentTest {

    @Mock
    private LLMService llmService;
    @Mock
    private PromptService promptService;

    private DefaultTopicAgent agent;

    @BeforeEach
    void setUp() {
        agent = new DefaultTopicAgent(llmService, promptService);
    }

    @Test
    void shouldReturnTopicsJson() {
        when(promptService.loadPrompt("topic")).thenReturn("You are a topic editor");
        when(llmService.generate(anyString(), anyString()))
                .thenReturn("""
                        {"success": true, "data": [{"title": "Java 21 Features", "source": "github", "url": "https://github.com/example", "hotLevel": 95, "reason": "Popular"}]}
                        """);

        var result = agent.collectTrendingTopics();

        assertThat(result).isNotNull();
        assertThat(result).contains("\"success\": true");
        assertThat(result).contains("Java 21 Features");
    }

    @Test
    void shouldHandleEmptyResponse() {
        when(promptService.loadPrompt("topic")).thenReturn("You are a topic editor");
        when(llmService.generate(anyString(), anyString()))
                .thenReturn("");

        var result = agent.collectTrendingTopics();

        assertThat(result).isNotNull();
    }

    @Test
    void shouldIncludeSourceField() {
        when(promptService.loadPrompt("topic")).thenReturn("You are a topic editor");
        when(llmService.generate(anyString(), anyString()))
                .thenReturn("""
                        {"success": true, "data": [{"title": "Rust in Linux", "source": "github", "url": "https://github.com/rust-lang", "hotLevel": 90, "reason": "Trending"}]}
                        """);

        var result = agent.collectTrendingTopics();

        assertThat(result).contains("\"source\": \"github\"");
    }

    @Test
    void shouldHandleLlmFailure() {
        when(promptService.loadPrompt("topic")).thenReturn("You are a topic editor");
        when(llmService.generate(anyString(), anyString()))
                .thenThrow(new RuntimeException("LLM unavailable"));

        var result = agent.collectTrendingTopics();

        assertThat(result).isNotNull();
    }

    @Test
    void shouldCollectTopicsThroughConfiguredProxy() throws IOException {
        var proxy = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        proxy.createContext("/", exchange -> {
            String body = exchange.getRequestURI().toString().contains("trending")
                    ? """
                    <article class="Box-row">
                      <h2><a href="/openai/codex">openai / codex</a></h2>
                      <p>Agentic coding</p>
                    </article>
                    """
                    : """
                    <rss><channel><item>
                      <title>Java networking</title>
                      <link>https://example.com/java</link>
                      <description>Proxy support</description>
                    </item></channel></rss>
                    """;
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        proxy.start();

        try {
            ReflectionTestUtils.setField(agent, "githubTrendingUrl", "http://github.invalid/trending");
            ReflectionTestUtils.setField(agent, "rssFeeds", new String[]{"http://rss.invalid/feed"});
            ReflectionTestUtils.setField(agent, "useLlm", false);
            ReflectionTestUtils.setField(agent, "proxyHost", "127.0.0.1");
            ReflectionTestUtils.setField(agent, "proxyPort", proxy.getAddress().getPort());

            var result = agent.collectTrendingTopics();

            assertThat(result).contains("\"source\":\"github\"");
            assertThat(result).contains("\"source\":\"rss\"");
        } finally {
            proxy.stop(0);
        }
    }
}
