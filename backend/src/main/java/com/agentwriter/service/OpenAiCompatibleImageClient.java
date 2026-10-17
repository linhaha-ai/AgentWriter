package com.agentwriter.service;

import com.agentwriter.dto.ImageRuntimeConfig;
import com.agentwriter.exception.ImageGenerationUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

/**
 * OpenAI 兼容图像生成客户端。
 *
 * <p>调用 {@code POST {api-url}/images/generations} 生成图片，兼容两种返回：
 * {@code data[0].url}（下载字节）与 {@code data[0].b64_json}（Base64 解码）。
 * 运行时凭据（API URL/Key/模型）来自 {@link SettingsService}：界面保存值优先，
 * yml 兜底；未启用或未配置密钥时抛出 {@link ImageGenerationUnavailableException}。</p>
 */
@Service
public class OpenAiCompatibleImageClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(180);

    private final SettingsService settingsService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatibleImageClient(SettingsService settingsService, ObjectMapper objectMapper) {
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * 调用图像生成接口并返回图片字节。
     *
     * @param prompt 生成提示词
     * @param size   目标尺寸（如 1024x1024），为空时使用配置默认
     * @return 生成的图片字节与 MIME 类型
     */
    public GeneratedImage generate(String prompt, String size) {
        ImageRuntimeConfig config = settingsService.getImageRuntimeConfig();
        String apiKey = settingsService.getImageApiKey();
        if (!config.enabled()) {
            throw new ImageGenerationUnavailableException("图像生成未启用，请先在系统设置中启用并配置图像生成服务");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new ImageGenerationUnavailableException("未配置图像生成 API Key，请先在系统设置中填写");
        }
        if (prompt == null || prompt.isBlank()) {
            throw new ImageGenerationUnavailableException("生成提示词不能为空");
        }
        String effectiveSize = (size == null || size.isBlank()) ? config.size() : size;

        JsonNode data = submitGeneration(config, apiKey, prompt, effectiveSize);
        return resolveImage(data);
    }

    private JsonNode submitGeneration(ImageRuntimeConfig config, String apiKey, String prompt, String size) {
        try {
            String baseUrl = config.apiUrl().replaceAll("/+$", "");
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/images/generations"))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(
                            requestBody(config.model(), prompt, size))))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            requireSuccess(response);
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) {
                throw new ImageGenerationUnavailableException("图像生成服务未返回图片数据");
            }
            return data.get(0);
        } catch (ImageGenerationUnavailableException ex) {
            throw ex;
        } catch (java.net.http.HttpTimeoutException ex) {
            throw new ImageGenerationUnavailableException("图像生成请求超时，请稍后重试", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ImageGenerationUnavailableException("图像生成请求被中断", ex);
        } catch (IOException | IllegalArgumentException ex) {
            throw new ImageGenerationUnavailableException("图像生成请求失败：" + ex.getMessage(), ex);
        }
    }

    private Object requestBody(String model, String prompt, String size) {
        return objectMapper.createObjectNode()
                .put("model", model)
                .put("prompt", prompt)
                .put("size", size)
                .put("n", 1)
                .put("response_format", "url");
    }

    private void requireSuccess(HttpResponse<String> response) {
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return;
        }
        String body = response.body() == null ? "" : response.body().trim();
        String detail = body.length() > 200 ? body.substring(0, 200) : body;
        throw new ImageGenerationUnavailableException("图像生成服务返回 HTTP " + status + (detail.isEmpty() ? "" : "：" + detail));
    }

    private GeneratedImage resolveImage(JsonNode data) {
        String url = data.path("url").asText(null);
        if (url != null && !url.isBlank()) {
            return download(url);
        }
        String b64 = data.path("b64_json").asText(null);
        if (b64 != null && !b64.isBlank()) {
            try {
                byte[] bytes = Base64.getDecoder().decode(b64);
                return new GeneratedImage(bytes, "image/png");
            } catch (IllegalArgumentException ex) {
                throw new ImageGenerationUnavailableException("图像生成服务返回的 Base64 数据无效", ex);
            }
        }
        throw new ImageGenerationUnavailableException("图像生成服务未返回图片地址或数据");
    }

    private GeneratedImage download(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String contentType = response.headers().firstValue("Content-Type").orElse("image/png");
                return new GeneratedImage(response.body(), contentType);
            }
            throw new ImageGenerationUnavailableException("下载生成图片失败：HTTP " + response.statusCode());
        } catch (ImageGenerationUnavailableException ex) {
            throw ex;
        } catch (java.net.http.HttpTimeoutException ex) {
            throw new ImageGenerationUnavailableException("下载生成图片超时", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ImageGenerationUnavailableException("下载生成图片被中断", ex);
        } catch (IOException | IllegalArgumentException ex) {
            throw new ImageGenerationUnavailableException("下载生成图片失败：" + ex.getMessage(), ex);
        }
    }

    /** 生成结果：图片字节与 MIME 类型 */
    public record GeneratedImage(byte[] bytes, String contentType) {
    }
}
