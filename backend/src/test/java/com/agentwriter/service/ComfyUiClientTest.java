package com.agentwriter.service;

import com.agentwriter.config.ImageProperties;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ComfyUiClientTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void replacesWorkflowTokensAndDownloadsCompletedOutput() throws Exception {
        Path workflow = temporaryDirectory.resolve("workflow.json");
        Files.writeString(workflow, """
                {
                  "1":{"class_type":"CLIPTextEncode","inputs":{"text":"__AGENTWRITER_PROMPT__"}},
                  "2":{"class_type":"CLIPTextEncode","inputs":{"text":"__AGENTWRITER_NEGATIVE_PROMPT__"}},
                  "3":{"class_type":"EmptyLatentImage","inputs":{"width":"__AGENTWRITER_WIDTH__","height":"__AGENTWRITER_HEIGHT__"}}
                }
                """);
        AtomicReference<String> submittedWorkflow = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/prompt", exchange -> {
            submittedWorkflow.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            write(exchange, "{\"prompt_id\":\"job-1\"}", "application/json");
        });
        server.createContext("/history/job-1", exchange -> write(exchange, """
                {"job-1":{"status":{"completed":true},"outputs":{"9":{"images":[{"filename":"result.png","subfolder":"","type":"output"}]}}}}
                """, "application/json"));
        server.createContext("/view", exchange -> write(exchange, "image", "image/png"));
        server.start();
        try {
            ImageProperties properties = new ImageProperties();
            properties.setEnabled(true);
            properties.setComfyBaseUrl("http://localhost:" + server.getAddress().getPort());
            properties.setWorkflowTemplate(workflow.toUri().toString());
            properties.setPollIntervalMillis(100);
            properties.setTimeoutSeconds(5);
            ComfyUiClient client = new ComfyUiClient(
                    WebClient.builder(), new ObjectMapper(), new DefaultResourceLoader(), properties
            );

            ComfyGeneratedImage result = client.generate("Spring Boot service diagram");

            assertThat(result.promptId()).isEqualTo("job-1");
            assertThat(result.bytes()).isEqualTo("image".getBytes(StandardCharsets.UTF_8));
            assertThat(submittedWorkflow.get())
                    .contains("Spring Boot service diagram")
                    .contains("\"width\":1024")
                    .contains("\"height\":576")
                    .doesNotContain("__AGENTWRITER_");
        } finally {
            server.stop(0);
        }
    }

    private static void write(com.sun.net.httpserver.HttpExchange exchange, String value, String contentType) throws java.io.IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
