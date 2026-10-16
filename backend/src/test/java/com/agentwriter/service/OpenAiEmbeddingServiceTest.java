package com.agentwriter.service;

import com.agentwriter.exception.EmbeddingValidationException;
import com.agentwriter.service.impl.OpenAiEmbeddingService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiEmbeddingServiceTest {

    private HttpServer server;
    private final AtomicReference<String> responseBody = new AtomicReference<>();
    private final AtomicInteger requestCount = new AtomicInteger();

    @BeforeEach
    void startLoopbackProvider() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/embeddings", this::respond);
        server.start();
    }

    @AfterEach
    void stopLoopbackProvider() {
        server.stop(0);
    }

    @Test
    void providerResponse_shouldReturnExactly1536Values() {
        responseBody.set(embeddingResponse(1536));
        OpenAiEmbeddingService service = serviceWithDimensions(1536);

        assertThat(service.embed("canonical vector")).hasSize(1536);
        assertThat(requestCount).hasValue(1);
    }

    @Test
    void providerResponse_shouldReject1535Values() {
        responseBody.set(embeddingResponse(1535));
        OpenAiEmbeddingService service = serviceWithDimensions(1536);

        assertThatThrownBy(() -> service.embed("short vector"))
                .hasMessageContaining("期望 1536")
                .hasMessageContaining("实际 1535");
    }

    @Test
    void providerResponse_shouldReject2048Values() {
        responseBody.set(embeddingResponse(2048));
        OpenAiEmbeddingService service = serviceWithDimensions(1536);

        assertThatThrownBy(() -> service.embed("long vector"))
                .hasMessageContaining("期望 1536")
                .hasMessageContaining("实际 2048");
    }

    @Test
    void providerResponse_shouldRejectMalformedNumericPayload() {
        responseBody.set("{\"data\":[{\"embedding\":[\"not-a-number\"]}],\"model\":\"loopback\"}");
        OpenAiEmbeddingService service = serviceWithDimensions(1536);

        assertThatThrownBy(() -> service.embed("malformed vector"))
                .isInstanceOf(EmbeddingValidationException.class)
                .hasMessageContaining("非数值元素");
        assertThat(requestCount).hasValue(1);
    }

    @Test
    void providerResponse_shouldRejectNumericValueThatOverflowsFloat() {
        String values = "3.5e38," + String.join(",", Collections.nCopies(1535, "0.25"));
        responseBody.set("{\"data\":[{\"embedding\":[" + values + "]}],\"model\":\"loopback\"}");
        OpenAiEmbeddingService service = serviceWithDimensions(1536);

        assertThatThrownBy(() -> service.embed("overflow vector"))
                .isInstanceOf(EmbeddingValidationException.class)
                .hasMessageContaining("float");
        assertThat(requestCount).hasValue(1);
    }

    @Test
    void configuration_shouldRejectNonCanonicalDimensionBeforeAnyRequest() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> serviceWithDimensions(2048))
                .withMessageContaining("1536")
                .withMessageContaining("2048");
        assertThat(requestCount).hasValue(0);
    }

    private OpenAiEmbeddingService serviceWithDimensions(int dimensions) {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        return new OpenAiEmbeddingService(baseUrl, "synthetic-key", "test-model", dimensions, 2);
    }

    private void respond(HttpExchange exchange) throws IOException {
        requestCount.incrementAndGet();
        exchange.getRequestBody().readAllBytes();
        byte[] body = responseBody.get().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static String embeddingResponse(int dimensions) {
        String values = String.join(",", Collections.nCopies(dimensions, "0.25"));
        return "{\"data\":[{\"embedding\":[" + values + "]}],\"model\":\"loopback\"}";
    }
}
