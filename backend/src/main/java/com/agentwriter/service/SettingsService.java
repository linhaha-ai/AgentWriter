package com.agentwriter.service;

import com.agentwriter.dto.LlmRuntimeConfig;
import com.agentwriter.dto.LlmSettingsUpdate;
import com.agentwriter.dto.LlmSettingsView;
import com.agentwriter.dto.PublisherRuntimeConfig;
import com.agentwriter.dto.PublisherSettingsUpdate;
import com.agentwriter.dto.PublisherSettingsView;
import com.agentwriter.dto.ScheduleRuntimeConfig;
import com.agentwriter.dto.ScheduleSettingsUpdate;
import com.agentwriter.dto.ScheduleSettingsView;
import com.agentwriter.dto.WechatRuntimeConfig;
import com.agentwriter.dto.WechatSettingsUpdate;
import com.agentwriter.dto.WechatSettingsView;
import com.agentwriter.entity.SystemConfig;
import com.agentwriter.exception.SystemConfigMalformedException;
import com.agentwriter.exception.SystemConfigValidationException;
import com.agentwriter.repository.SystemConfigRepository;
import com.agentwriter.service.event.ScheduleConfigurationChangedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

@Service
public class SettingsService {

    private static final Set<String> SUPPORTED_PROVIDERS = Set.of("nvidia", "openai", "agnes");
    private static final int MIN_TIMEOUT_SECONDS = 60;
    private static final int DEFAULT_LLM_TIMEOUT_SECONDS = 120;
    private static final int LEGACY_MIN_TIMEOUT_SECONDS = 1;
    private static final int MAX_TIMEOUT_SECONDS = 300;

    private final SystemConfigRepository configRepository;
    private final ObjectMapper objectMapper;
    private final LlmSettingsUpdate defaultLlm;
    private final PublisherSettingsUpdate defaultPublisher;
    private final ScheduleSettingsUpdate defaultSchedule;
    private final String llmApiKey;
    private final String wechatAppSecret;
    private final ApplicationEventPublisher eventPublisher;

    public SettingsService(
            SystemConfigRepository configRepository,
            ObjectMapper objectMapper,
            @Value("${agentwriter.llm.enabled:true}") boolean llmEnabled,
            @Value("${agentwriter.llm.provider:agnes}") String llmProvider,
            @Value("${agentwriter.llm.api-url:}") String llmApiUrl,
            @Value("${agentwriter.llm.api-key:}") String llmApiKey,
            @Value("${agentwriter.llm.model:agnes-2.5-flash}") String llmModel,
            @Value("${agentwriter.llm.timeout:120}") int llmTimeout,
            @Value("${agentwriter.publisher.wechat.enabled:false}") boolean wxEnabled,
            @Value("${agentwriter.publisher.wechat.app-id:}") String wxAppId,
            @Value("${agentwriter.publisher.wechat.app-secret:}") String wxAppSecret,
            @Value("${agentwriter.publisher.wechat.author:AgentWriter}") String wxAuthor,
            @Value("${agentwriter.publisher.wechat.thumb-media-id:}") String wxThumbMediaId,
            @Value("${agentwriter.schedule.enabled:true}") boolean schedEnabled,
            @Value("${agentwriter.schedule.cron:0 0 8 * * ?}") String schedCron,
            ApplicationEventPublisher eventPublisher
    ) {
        this.configRepository = configRepository;
        this.objectMapper = objectMapper;
        this.defaultLlm = new LlmSettingsUpdate(llmEnabled, llmProvider, llmApiUrl, llmModel, llmTimeout);
        this.defaultPublisher = new PublisherSettingsUpdate(new WechatSettingsUpdate(wxEnabled, wxAppId, wxAuthor, wxThumbMediaId));
        this.defaultSchedule = new ScheduleSettingsUpdate(schedEnabled, schedCron);
        this.llmApiKey = llmApiKey;
        this.wechatAppSecret = wxAppSecret;
        this.eventPublisher = eventPublisher;
    }

    public LlmSettingsView getLlmConfig() {
        LlmSettingsUpdate config = readConfig("llm", LlmSettingsUpdate.class, defaultLlm);
        validateLlm(config, true);
        return toLlmView(config);
    }

    public LlmSettingsView updateLlmConfig(LlmSettingsUpdate config) {
        validateLlm(config, false);
        saveConfig("llm", config);
        return toLlmView(config);
    }

    public LlmRuntimeConfig getLlmRuntimeConfig() {
        LlmSettingsUpdate config = readConfig("llm", LlmSettingsUpdate.class, defaultLlm);
        validateLlm(config, true);
        return new LlmRuntimeConfig(
                config.enabled(), config.provider(), config.apiUrl(), config.model(), effectiveLlmTimeout(config.timeout()));
    }

    public String getLlmApiKey() {
        return llmApiKey;
    }

    public PublisherSettingsView getPublisherConfig() {
        PublisherSettingsUpdate config = readConfig("publisher", PublisherSettingsUpdate.class, defaultPublisher);
        validatePublisher(config, true);
        return toPublisherView(config);
    }

    public PublisherSettingsView updatePublisherConfig(PublisherSettingsUpdate config) {
        validatePublisher(config, false);
        saveConfig("publisher", config);
        return toPublisherView(config);
    }

    public PublisherRuntimeConfig getPublisherRuntimeConfig() {
        PublisherSettingsUpdate config = readConfig("publisher", PublisherSettingsUpdate.class, defaultPublisher);
        validatePublisher(config, true);
        WechatSettingsUpdate wechat = config.wechat();
        return new PublisherRuntimeConfig(new WechatRuntimeConfig(
                wechat.enabled(), wechat.appId(), wechatAppSecret, wechat.author(), wechat.thumbMediaId()
        ));
    }

    public ScheduleSettingsView getScheduleConfig() {
        ScheduleSettingsUpdate config = readConfig("schedule", ScheduleSettingsUpdate.class, defaultSchedule);
        validateSchedule(config, true);
        return new ScheduleSettingsView(config.enabled(), config.cron());
    }

    public synchronized ScheduleSettingsView updateScheduleConfig(ScheduleSettingsUpdate config) {
        validateSchedule(config, false);
        ScheduleSettingsUpdate previous = readConfig("schedule", ScheduleSettingsUpdate.class, defaultSchedule);
        saveConfig("schedule", config);
        try {
            eventPublisher.publishEvent(new ScheduleConfigurationChangedEvent(
                    new ScheduleRuntimeConfig(config.enabled(), config.cron())));
        } catch (RuntimeException ex) {
            saveConfig("schedule", previous);
            throw ex;
        }
        return new ScheduleSettingsView(config.enabled(), config.cron());
    }

    public ScheduleRuntimeConfig getScheduleRuntimeConfig() {
        ScheduleSettingsUpdate config = readConfig("schedule", ScheduleSettingsUpdate.class, defaultSchedule);
        validateSchedule(config, true);
        return new ScheduleRuntimeConfig(config.enabled(), config.cron());
    }

    private LlmSettingsView toLlmView(LlmSettingsUpdate config) {
        return new LlmSettingsView(
                config.enabled(), config.provider(), config.apiUrl(), config.model(), effectiveLlmTimeout(config.timeout()), hasText(llmApiKey)
        );
    }

    private PublisherSettingsView toPublisherView(PublisherSettingsUpdate config) {
        WechatSettingsUpdate wechat = config.wechat();
        return new PublisherSettingsView(new WechatSettingsView(
                wechat.enabled(), wechat.appId(), wechat.author(), wechat.thumbMediaId(), hasText(wechatAppSecret)
        ));
    }

    private <T> T readConfig(String key, Class<T> type, T fallback) {
        return configRepository.findById(key)
                .map(config -> deserialize(key, config.getConfigValue(), type))
                .orElse(fallback);
    }

    private <T> T deserialize(String key, String value, Class<T> type) {
        try {
            JsonNode payload = objectMapper.readTree(value);
            if (containsCredentialField(payload)) {
                throw new SystemConfigMalformedException(key, new IllegalArgumentException("存储配置中禁止包含凭据字段"));
            }
            return objectMapper.treeToValue(payload, type);
        } catch (SystemConfigMalformedException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new SystemConfigMalformedException(key, ex);
        }
    }

    private void saveConfig(String key, Object value) {
        try {
            configRepository.save(new SystemConfig(key, objectMapper.writeValueAsString(value)));
        } catch (JsonProcessingException ex) {
            throw new SystemConfigValidationException("无法序列化系统配置: " + key, ex);
        }
    }

    private void validateLlm(LlmSettingsUpdate config, boolean stored) {
        if (config == null || !SUPPORTED_PROVIDERS.contains(normalize(config == null ? null : config.provider()))) {
            invalid(stored, "LLM 提供方必须为 nvidia、openai 或 agnes");
        }
        if (!hasText(config.model())) {
            invalid(stored, "LLM 模型不能为空");
        }
        int minimumTimeout = stored ? LEGACY_MIN_TIMEOUT_SECONDS : MIN_TIMEOUT_SECONDS;
        if (config.timeout() < minimumTimeout || config.timeout() > MAX_TIMEOUT_SECONDS) {
            invalid(stored, "LLM 超时时间必须在 60 到 300 秒之间");
        }
        if (config.enabled() && !isHttpUrl(config.apiUrl())) {
            invalid(stored, "启用 LLM 时 API URL 必须是绝对 http 或 https 地址");
        }
        if (!config.enabled() && hasText(config.apiUrl()) && !isHttpUrl(config.apiUrl())) {
            invalid(stored, "提供 LLM API URL 时必须使用绝对 http 或 https 地址");
        }
    }

    private void validatePublisher(PublisherSettingsUpdate config, boolean stored) {
        if (config == null || config.wechat() == null) {
            invalid(stored, "必须提供微信公众号发布配置");
        }
        WechatSettingsUpdate wechat = config.wechat();
        if (wechat.enabled() && !hasText(wechat.appId())) {
            invalid(stored, "启用微信公众号发布器时 appId 不能为空");
        }
        if (!hasText(wechat.author())) {
            invalid(stored, "微信公众号作者不能为空");
        }
    }

    private void validateSchedule(ScheduleSettingsUpdate config, boolean stored) {
        if (config == null || !hasText(config.cron()) || !CronExpression.isValidExpression(config.cron())) {
            invalid(stored, "调度 cron 表达式无效");
        }
    }

    private void invalid(boolean stored, String message) {
        if (stored) {
            throw new SystemConfigMalformedException("已存储配置", new IllegalArgumentException(message));
        }
        throw new SystemConfigValidationException(message);
    }

    private static boolean isHttpUrl(String value) {
        try {
            URI uri = new URI(value);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) && hasText(uri.getHost());
        } catch (URISyntaxException | NullPointerException ex) {
            return false;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean containsCredentialField(JsonNode node) {
        if (node.isObject() && (node.has("apiKey") || node.has("appSecret") || node.has("api-key") || node.has("app-secret"))) {
            return true;
        }
        for (JsonNode child : node) {
            if (containsCredentialField(child)) {
                return true;
            }
        }
        return false;
    }

    private static int effectiveLlmTimeout(int timeout) {
        return timeout < MIN_TIMEOUT_SECONDS ? DEFAULT_LLM_TIMEOUT_SECONDS : timeout;
    }
}
