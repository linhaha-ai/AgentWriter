package com.agentwriter.service.impl;

import com.agentwriter.common.ErrorCategory;
import com.agentwriter.dto.LlmRuntimeConfig;
import com.agentwriter.exception.LLMConfigurationException;
import com.agentwriter.exception.LLMProviderException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

abstract class OpenAiCompatibleLLMProvider {

    private final String provider;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    OpenAiCompatibleLLMProvider(String provider, ObjectMapper objectMapper) {
        this(provider, objectMapper, "", 0);
    }

    OpenAiCompatibleLLMProvider(String provider, ObjectMapper objectMapper, String proxyHost, int proxyPort) {
        this.provider = provider;
        this.objectMapper = objectMapper;
        HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NEVER);
        if (hasProxy(proxyHost, proxyPort)) {
            clientBuilder.proxy(ProxySelector.of(new InetSocketAddress(proxyHost, proxyPort)));
        }
        this.httpClient = clientBuilder
                .build();
    }

    String generate(
            LlmRuntimeConfig config,
            String credential,
            String systemPrompt,
            String userMessage
    ) {
        HttpRequest request = buildRequest(config, credential, systemPrompt, userMessage);
        try {
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());
            requireSuccess(response);
            return extractContent(response.body());
        } catch (java.net.http.HttpConnectTimeoutException ex) {
            throw new LLMProviderException(
                    ErrorCategory.NETWORK_ERROR, provider + " LLM 连接超时，请检查代理或网络连接", ex);
        } catch (HttpTimeoutException ex) {
            throw new LLMProviderException(
                    ErrorCategory.LLM_TIMEOUT, provider + " LLM 请求超时", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new LLMProviderException(
                    ErrorCategory.NETWORK_ERROR, provider + " LLM 请求被中断", ex);
        } catch (IOException ex) {
            throw new LLMProviderException(
                    ErrorCategory.NETWORK_ERROR, provider + " LLM 网络错误", ex);
        }
    }

    private HttpRequest buildRequest(
            LlmRuntimeConfig config,
            String credential,
            String systemPrompt,
            String userMessage
    ) {
        try {
            String baseUrl = config.apiUrl().replaceAll("/+$", "");
            return HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofSeconds(config.timeout()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + credential)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(
                                    requestBody(config.model(), systemPrompt, userMessage))))
                    .build();
        } catch (IllegalArgumentException | JsonProcessingException ex) {
            throw new LLMConfigurationException(
                    provider + " LLM 请求配置无效", ex);
        }
    }

    private ObjectNode requestBody(String model, String systemPrompt, String userMessage) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        root.put("temperature", 0.7);
        root.put("max_tokens", 4096);
        ArrayNode messages = root.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userMessage);
        return root;
    }

    private void requireSuccess(HttpResponse<String> response) {
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return;
        }
        ErrorCategory category = switch (status) {
            case 401, 403 -> ErrorCategory.AUTH_ERROR;
            case 429 -> ErrorCategory.RATE_LIMITED;
            default -> status >= 500 ? ErrorCategory.NETWORK_ERROR : ErrorCategory.UNKNOWN;
        };
        throw new LLMProviderException(
                category, provider + " LLM 返回 HTTP 状态码 " + status);
    }

    private String extractContent(String body) {
        final JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JsonProcessingException ex) {
            throw new LLMProviderException(
                    ErrorCategory.JSON_PARSE_ERROR, provider + " LLM 响应不是有效 JSON", ex);
        }
        JsonNode choices = root.path("choices");
        JsonNode first = choices.isArray() && !choices.isEmpty() ? choices.get(0) : null;
        JsonNode content = first == null ? null : first.path("message").path("content");
        if (content == null || !content.isTextual() || content.textValue().isBlank()) {
            throw new LLMProviderException(
                    ErrorCategory.LLM_EMPTY_RESPONSE, provider + " LLM 响应内容为空");
        }
        return content.textValue();
    }

    private static boolean hasProxy(String proxyHost, int proxyPort) {
        return proxyHost != null && !proxyHost.isBlank() && proxyPort > 0 && proxyPort <= 65535;
    }
}
