package com.agentwriter.service;

import com.agentwriter.config.ImageProperties;
import com.agentwriter.exception.ImageGenerationUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;

/**
 * ComfyUI 本地 API 客户端。
 */
@Component
public class ComfyUiClient {

    private static final String PROMPT_TOKEN = "__AGENTWRITER_PROMPT__";
    private static final String NEGATIVE_PROMPT_TOKEN = "__AGENTWRITER_NEGATIVE_PROMPT__";
    private static final String WIDTH_TOKEN = "__AGENTWRITER_WIDTH__";
    private static final String HEIGHT_TOKEN = "__AGENTWRITER_HEIGHT__";

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final ImageProperties properties;

    public ComfyUiClient(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            ResourceLoader resourceLoader,
            ImageProperties properties
    ) {
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.properties = properties;
    }

    public ComfyGeneratedImage generate(String prompt) {
        ensureAvailable();
        WebClient client = webClientBuilder.clone().baseUrl(normalizeBaseUrl()).build();
        JsonNode workflow = loadWorkflow(prompt);
        String queueRequest = writeQueueRequest(workflow);
        String queuedBody = client.post()
                .uri("/prompt")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(queueRequest)
                .retrieve()
                .bodyToMono(String.class)
                .block(requestTimeout());
        JsonNode queued = readJson(queuedBody, "ComfyUI 排队响应无效");
        String promptId = requiredText(queued, "prompt_id", "ComfyUI 未返回任务 ID");
        JsonNode result = waitForResult(client, promptId);
        JsonNode image = firstOutputImage(result, promptId);
        String filename = requiredText(image, "filename", "ComfyUI 输出未包含文件名");
        String subfolder = image.path("subfolder").asText("");
        String type = image.path("type").asText("output");
        byte[] bytes = client.get()
                .uri(builder -> builder.path("/view")
                        .queryParam("filename", filename)
                        .queryParam("subfolder", subfolder)
                        .queryParam("type", type)
                        .build())
                .retrieve()
                .bodyToMono(byte[].class)
                .block(requestTimeout());
        if (bytes == null || bytes.length == 0) {
            throw new ImageGenerationUnavailableException("ComfyUI 未返回图片文件内容");
        }
        return new ComfyGeneratedImage(promptId, filename, bytes, imageContentType(filename));
    }

    public void validateConfiguration() {
        ensureAvailable();
    }

    private JsonNode waitForResult(WebClient client, String promptId) {
        long deadline = System.nanoTime() + Duration.ofSeconds(properties.getTimeoutSeconds()).toNanos();
        while (System.nanoTime() < deadline) {
            String historyBody = client.get()
                    .uri("/history/{promptId}", promptId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(requestTimeout());
            JsonNode history = readJson(historyBody, "ComfyUI 任务状态响应无效");
            JsonNode entry = history == null ? null : history.path(promptId);
            if (entry != null && !entry.isMissingNode()) {
                boolean completed = entry.path("status").path("completed").asBoolean(false);
                if (completed || entry.path("outputs").size() > 0) {
                    return entry;
                }
                JsonNode status = entry.path("status");
                if (status.hasNonNull("status_str") && "error".equalsIgnoreCase(status.path("status_str").asText())) {
                    throw new ImageGenerationUnavailableException("ComfyUI 图片任务执行失败");
                }
            }
            sleep();
        }
        throw new ImageGenerationUnavailableException("ComfyUI 图片任务超时");
    }

    private JsonNode loadWorkflow(String prompt) {
        try {
            Resource resource = resourceLoader.getResource(properties.getWorkflowTemplate());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ImageGenerationUnavailableException("ComfyUI 工作流模板不可读取");
            }
            JsonNode root = objectMapper.readTree(resource.getInputStream());
            TokenReplacementCount replacements = new TokenReplacementCount();
            replaceTokens(root, prompt, replacements);
            if (!replacements.complete()) {
                throw new ImageGenerationUnavailableException("ComfyUI 工作流模板缺少 AgentWriter 占位标记");
            }
            return root;
        } catch (IOException exception) {
            throw new ImageGenerationUnavailableException("无法读取 ComfyUI 工作流模板", exception);
        }
    }

    private JsonNode readJson(String body, String message) {
        if (body == null || body.isBlank()) {
            throw new ImageGenerationUnavailableException(message);
        }
        try {
            return objectMapper.readTree(body);
        } catch (IOException exception) {
            throw new ImageGenerationUnavailableException(message, exception);
        }
    }

    private String writeQueueRequest(JsonNode workflow) {
        try {
            return objectMapper.writeValueAsString(Map.of("prompt", workflow));
        } catch (Exception exception) {
            throw new ImageGenerationUnavailableException("无法序列化 ComfyUI 工作流", exception);
        }
    }

    private void replaceTokens(JsonNode node, String prompt, TokenReplacementCount replacements) {
        if (node instanceof ObjectNode objectNode) {
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode value = field.getValue();
                if (value.isTextual()) {
                    switch (value.asText()) {
                        case PROMPT_TOKEN -> {
                            objectNode.put(field.getKey(), prompt);
                            replacements.prompt++;
                        }
                        case NEGATIVE_PROMPT_TOKEN -> {
                            objectNode.put(field.getKey(), "text, watermark, logo, blurry, distorted");
                            replacements.negativePrompt++;
                        }
                        case WIDTH_TOKEN -> {
                            objectNode.put(field.getKey(), 1024);
                            replacements.width++;
                        }
                        case HEIGHT_TOKEN -> {
                            objectNode.put(field.getKey(), 576);
                            replacements.height++;
                        }
                        default -> replaceTokens(value, prompt, replacements);
                    }
                } else {
                    replaceTokens(value, prompt, replacements);
                }
            }
        } else if (node.isArray()) {
            node.forEach(value -> replaceTokens(value, prompt, replacements));
        }
    }

    private JsonNode firstOutputImage(JsonNode result, String promptId) {
        Iterator<JsonNode> outputs = result.path("outputs").elements();
        while (outputs.hasNext()) {
            JsonNode images = outputs.next().path("images");
            if (images.isArray() && !images.isEmpty()) {
                return images.get(0);
            }
        }
        throw new ImageGenerationUnavailableException("ComfyUI 任务未产生图片输出: " + promptId);
    }

    private void ensureAvailable() {
        if (!properties.isEnabled()) {
            throw new ImageGenerationUnavailableException("图片生成功能未启用");
        }
        if (isBlank(properties.getWorkflowTemplate())) {
            throw new ImageGenerationUnavailableException("未配置 ComfyUI 工作流模板");
        }
        if (properties.getTimeoutSeconds() < 1 || properties.getPollIntervalMillis() < 100) {
            throw new ImageGenerationUnavailableException("图片生成超时或轮询配置无效");
        }
    }

    private Duration requestTimeout() {
        return Duration.ofSeconds(Math.max(5, properties.getTimeoutSeconds()));
    }

    private String normalizeBaseUrl() {
        String baseUrl = properties.getComfyBaseUrl();
        if (isBlank(baseUrl)) {
            throw new ImageGenerationUnavailableException("未配置 ComfyUI 服务地址");
        }
        return baseUrl.replaceAll("/$", "");
    }

    private void sleep() {
        try {
            Thread.sleep(properties.getPollIntervalMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ImageGenerationUnavailableException("图片生成任务被中断", exception);
        }
    }

    private static String requiredText(JsonNode node, String field, String message) {
        String value = node == null ? null : node.path(field).asText();
        if (isBlank(value)) {
            throw new ImageGenerationUnavailableException(message);
        }
        return value;
    }

    private static String imageContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG_VALUE;
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return MediaType.IMAGE_PNG_VALUE;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static final class TokenReplacementCount {
        private int prompt;
        private int negativePrompt;
        private int width;
        private int height;

        private boolean complete() {
            return prompt > 0 && negativePrompt > 0 && width > 0 && height > 0;
        }
    }
}
