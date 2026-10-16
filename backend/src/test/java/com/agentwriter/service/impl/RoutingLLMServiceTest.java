package com.agentwriter.service.impl;

import com.agentwriter.common.ErrorCategory;
import com.agentwriter.dto.LlmRuntimeConfig;
import com.agentwriter.exception.LLMConfigurationException;
import com.agentwriter.exception.LLMProviderException;
import com.agentwriter.exception.LLMUnavailableException;
import com.agentwriter.service.SettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoutingLLMServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void mapsNvidiaAndOpenAiResponsesAndHotAppliesProviderSettings() throws Exception {
        try (LoopbackServer nvidia = LoopbackServer.responding(
                200, completion("nvidia-result"), 0);
             LoopbackServer openAi = LoopbackServer.responding(
                     200, completion("openai-result"), 0)) {
            SettingsService settings = mock(SettingsService.class);
            when(settings.getLlmRuntimeConfig())
                    .thenReturn(config("nvidia", nvidia.url(), 2))
                    .thenReturn(config("openai", openAi.url(), 2));
            MockEnvironment environment = new MockEnvironment()
                    .withProperty("NVIDIA_API_KEY", "synthetic-nvidia")
                    .withProperty("OPENAI_API_KEY", "synthetic-openai");
            RoutingLLMService service = new RoutingLLMService(
                    settings, environment, OBJECT_MAPPER);

            assertThat(service.generate("system", "first")).isEqualTo("nvidia-result");
            assertThat(service.generate("system", "second")).isEqualTo("openai-result");
            assertThat(nvidia.requests()).isEqualTo(1);
            assertThat(openAi.requests()).isEqualTo(1);
        }
    }

    @Test
    void mapsAgnesResponsesAndHotAppliesProviderSettings() throws Exception {
        try (LoopbackServer agnes = LoopbackServer.responding(
                200, completion("agnes-result"), 0);
             LoopbackServer openAi = LoopbackServer.responding(
                     200, completion("openai-result"), 0)) {
            SettingsService settings = mock(SettingsService.class);
            when(settings.getLlmRuntimeConfig())
                    .thenReturn(config("agnes", agnes.url(), 2))
                    .thenReturn(config("openai", openAi.url(), 2));
            MockEnvironment environment = new MockEnvironment()
                    .withProperty("AGNES_API_KEY", "synthetic-agnes")
                    .withProperty("OPENAI_API_KEY", "synthetic-openai");
            RoutingLLMService service = new RoutingLLMService(
                    settings, environment, OBJECT_MAPPER);

            assertThat(service.generate("system", "first")).isEqualTo("agnes-result");
            assertThat(service.generate("system", "second")).isEqualTo("openai-result");
            assertThat(agnes.requests()).isEqualTo(1);
            assertThat(openAi.requests()).isEqualTo(1);
        }
    }

    @Test
    void disabledMissingCredentialAndUnsupportedProviderMakeZeroRequests() throws Exception {
        try (LoopbackServer server = LoopbackServer.responding(
                200, completion("must-not-return"), 0)) {
            SettingsService settings = mock(SettingsService.class);
            MockEnvironment environment = new MockEnvironment();
            RoutingLLMService service = new RoutingLLMService(
                    settings, environment, OBJECT_MAPPER);

            when(settings.getLlmRuntimeConfig())
                    .thenReturn(new LlmRuntimeConfig(
                            false, "nvidia", server.url(), "model", 1));
            assertThatThrownBy(() -> service.generate("system", "disabled"))
                    .isInstanceOf(LLMUnavailableException.class)
                    .hasMessageContaining("已禁用");

            when(settings.getLlmRuntimeConfig()).thenReturn(config("nvidia", server.url(), 1));
            assertThatThrownBy(() -> service.generate("system", "missing"))
                    .isInstanceOf(LLMUnavailableException.class)
                    .hasMessageContaining("NVIDIA_API_KEY");

            when(settings.getLlmRuntimeConfig()).thenReturn(config("public-cloud", server.url(), 1));
            assertThatThrownBy(() -> service.generate("system", "unsupported"))
                    .isInstanceOf(LLMConfigurationException.class)
                    .hasMessageContaining("不支持");
            assertThat(server.requests()).isZero();
        }
    }

    @Test
    void usesConfiguredApiKeyWhenProviderEnvironmentVariableIsMissing() throws Exception {
        try (LoopbackServer server = LoopbackServer.responding(200, completion("configured-key-result"), 0)) {
            SettingsService settings = mock(SettingsService.class);
            when(settings.getLlmRuntimeConfig()).thenReturn(config("nvidia", server.url(), 1));
            when(settings.getLlmApiKey()).thenReturn("synthetic-configured-key");
            RoutingLLMService service = new RoutingLLMService(
                    settings, new MockEnvironment(), OBJECT_MAPPER);

            assertThat(service.generate("system", "user")).isEqualTo("configured-key-result");
            assertThat(server.requests()).isEqualTo(1);
        }
    }

    @Test
    void authenticationFailureIsTypedNotRetriedAndDoesNotLeakCredential() throws Exception {
        try (LoopbackServer server = LoopbackServer.responding(401, "unauthorized", 0)) {
            SettingsService settings = mock(SettingsService.class);
            when(settings.getLlmRuntimeConfig()).thenReturn(config("openai", server.url(), 1));
            MockEnvironment environment = new MockEnvironment()
                    .withProperty("OPENAI_API_KEY", "synthetic-secret-never-log");
            RoutingLLMService service = new RoutingLLMService(
                    settings, environment, OBJECT_MAPPER);

            assertThatThrownBy(() -> service.generate("system", "user"))
                    .isInstanceOf(LLMProviderException.class)
                    .hasMessageNotContaining("synthetic-secret-never-log")
                    .satisfies(error -> assertThat(((LLMProviderException) error).category())
                            .isEqualTo(ErrorCategory.AUTH_ERROR));
            assertThat(server.requests()).isEqualTo(1);
        }
    }

    @Test
    void providerAdapterPreservesTimeoutAndMalformedJsonCategories() throws Exception {
        NvidiaLLMService adapter = new NvidiaLLMService(OBJECT_MAPPER);
        try (LoopbackServer timeout = LoopbackServer.responding(
                200, completion("late"), 1_500)) {
            assertThatThrownBy(() -> adapter.generate(
                    config("nvidia", timeout.url(), 1),
                    "synthetic", "system", "user"))
                    .isInstanceOf(LLMProviderException.class)
                    .extracting(error -> ((LLMProviderException) error).category())
                    .isEqualTo(ErrorCategory.LLM_TIMEOUT);
            assertThat(timeout.requests()).isEqualTo(1);
        }

        OpenAiLLMService openAi = new OpenAiLLMService(OBJECT_MAPPER);
        try (LoopbackServer malformed = LoopbackServer.responding(200, "{invalid", 0)) {
            assertThatThrownBy(() -> openAi.generate(
                    config("openai", malformed.url(), 1),
                    "synthetic", "system", "user"))
                    .isInstanceOf(LLMProviderException.class)
                    .extracting(error -> ((LLMProviderException) error).category())
                    .isEqualTo(ErrorCategory.JSON_PARSE_ERROR);
            assertThat(malformed.requests()).isEqualTo(1);
        }
    }

    @Test
    void letsWorkflowOwnRetriesInsteadOfRetryingEachLlmCall() throws Exception {
        try (LoopbackServer timeout = LoopbackServer.responding(200, completion("late"), 1_500)) {
            SettingsService settings = mock(SettingsService.class);
            when(settings.getLlmRuntimeConfig()).thenReturn(config("nvidia", timeout.url(), 1));
            MockEnvironment environment = new MockEnvironment().withProperty("NVIDIA_API_KEY", "synthetic-key");
            RoutingLLMService service = new RoutingLLMService(
                    settings, environment, OBJECT_MAPPER);

            assertThatThrownBy(() -> service.generate("system", "user"))
                    .isInstanceOf(LLMProviderException.class)
                    .extracting(error -> ((LLMProviderException) error).category())
                    .isEqualTo(ErrorCategory.LLM_TIMEOUT);
            assertThat(timeout.requests()).isEqualTo(1);
        }
    }

    @Test
    void usesConfiguredTopicProxyForNvidiaRequests() throws Exception {
        try (LoopbackProxy proxy = new LoopbackProxy(completion("proxied-result"))) {
            SettingsService settings = mock(SettingsService.class);
            when(settings.getLlmRuntimeConfig()).thenReturn(config("nvidia", "http://unresolvable.test", 2));
            MockEnvironment environment = new MockEnvironment()
                    .withProperty("NVIDIA_API_KEY", "synthetic-key")
                    .withProperty("agentwriter.topic.proxy-host", "127.0.0.1")
                    .withProperty("agentwriter.topic.proxy-port", String.valueOf(proxy.port()));
            RoutingLLMService service = new RoutingLLMService(settings, environment, OBJECT_MAPPER);

            assertThat(service.generate("system", "user")).isEqualTo("proxied-result");
            assertThat(proxy.requests()).isEqualTo(1);
        }
    }

    private static LlmRuntimeConfig config(String provider, String url, int timeout) {
        return new LlmRuntimeConfig(true, provider, url, "test-model", timeout);
    }

    private static String completion(String content) {
        return """
                {"choices":[{"message":{"content":"%s"}}]}
                """.formatted(content);
    }

    private static final class LoopbackServer implements AutoCloseable {

        private final HttpServer server;
        private final AtomicInteger requests = new AtomicInteger();

        private LoopbackServer(int status, String body, long delayMillis) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/chat/completions", exchange -> {
                requests.incrementAndGet();
                if (delayMillis > 0) {
                    delay(delayMillis);
                }
                writeUnlessClientTimedOut(exchange, status, body);
            });
            server.start();
        }

        static LoopbackServer responding(int status, String body, long delayMillis)
                throws IOException {
            return new LoopbackServer(status, body, delayMillis);
        }

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        int requests() {
            return requests.get();
        }

        @Override
        public void close() {
            server.stop(0);
        }

        private static void delay(long delayMillis) {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }

        private static void writeUnlessClientTimedOut(
                HttpExchange exchange, int status, String body
        ) {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            try (exchange) {
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (IOException ignored) {
            }
        }
    }

    private static final class LoopbackProxy implements AutoCloseable {

        private final HttpServer server;
        private final AtomicInteger requests = new AtomicInteger();

        private LoopbackProxy(String body) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                requests.incrementAndGet();
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                try (exchange) {
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                } catch (IOException ignored) {
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
