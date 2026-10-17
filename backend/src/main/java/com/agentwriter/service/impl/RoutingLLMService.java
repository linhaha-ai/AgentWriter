package com.agentwriter.service.impl;

import com.agentwriter.dto.LlmRuntimeConfig;
import com.agentwriter.exception.LLMConfigurationException;
import com.agentwriter.exception.LLMProviderException;
import com.agentwriter.service.LLMService;
import com.agentwriter.exception.LLMUnavailableException;
import com.agentwriter.service.SettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class RoutingLLMService implements LLMService {

    private final SettingsService settingsService;
    private final Environment environment;
    private final NvidiaLLMService nvidia;
    private final OpenAiLLMService openAi;
    private final AgnesLLMService agnes;

    public RoutingLLMService(
            SettingsService settingsService,
            Environment environment,
            ObjectMapper objectMapper
    ) {
        this.settingsService = settingsService;
        this.environment = environment;
        String proxyHost = environment.getProperty("agentwriter.topic.proxy-host", "");
        int proxyPort = proxyPort(environment.getProperty("agentwriter.topic.proxy-port"));
        this.nvidia = new NvidiaLLMService(objectMapper, proxyHost, proxyPort);
        this.openAi = new OpenAiLLMService(objectMapper, proxyHost, proxyPort);
        this.agnes = new AgnesLLMService(objectMapper, proxyHost, proxyPort);
    }

    @Override
    public String generate(String systemPrompt, String userMessage) {
        LlmRuntimeConfig config = settingsService.getLlmRuntimeConfig();
        if (!config.enabled()) {
            throw new LLMUnavailableException("LLM 服务已禁用");
        }

        String provider = normalize(config.provider());
        String credentialName = switch (provider) {
            case "nvidia" -> "NVIDIA_API_KEY";
            case "openai" -> "OPENAI_API_KEY";
            case "agnes" -> "AGNES_API_KEY";
            default -> throw new LLMConfigurationException(
                    "不支持的 LLM 提供方: " + config.provider());
        };
        String credential = environment.getProperty(credentialName);
        if (credential == null || credential.isBlank()) {
            credential = settingsService.getLlmApiKey();
        }
        if (credential == null || credential.isBlank()) {
            throw new LLMUnavailableException(
                    provider + " LLM 凭据未在环境变量 " + credentialName
                            + "、系统设置界面或 agentwriter.llm.api-key 中配置");
        }
        String resolvedCredential = credential;

        return providerAdapter(provider).generate(
                config, resolvedCredential, systemPrompt, userMessage);
    }

    private OpenAiCompatibleLLMProvider providerAdapter(String provider) {
        return switch (provider) {
            case "nvidia" -> nvidia;
            case "openai" -> openAi;
            case "agnes" -> agnes;
            default -> throw new LLMConfigurationException(
                    "不支持的 LLM 提供方: " + provider);
        };
    }

    private static String normalize(String provider) {
        return provider == null ? "" : provider.toLowerCase(Locale.ROOT);
    }

    private static int proxyPort(String configuredPort) {
        if (configuredPort == null || configuredPort.isBlank()) {
            return 0;
        }
        try {
            int port = Integer.parseInt(configuredPort);
            return port > 0 && port <= 65535 ? port : 0;
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
