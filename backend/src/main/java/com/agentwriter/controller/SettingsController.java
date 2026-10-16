package com.agentwriter.controller;

import com.agentwriter.common.ApiResponse;
import com.agentwriter.dto.LlmSettingsUpdate;
import com.agentwriter.dto.LlmSettingsView;
import com.agentwriter.dto.PublisherSettingsUpdate;
import com.agentwriter.dto.PublisherSettingsView;
import com.agentwriter.dto.ScheduleSettingsUpdate;
import com.agentwriter.dto.ScheduleSettingsView;
import com.agentwriter.service.SettingsService;
import com.agentwriter.exception.SystemConfigValidationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private static final Set<String> LLM_SECRET_FIELDS = Set.of("apiKey", "api-key");
    private static final Set<String> PUBLISHER_SECRET_FIELDS = Set.of("appSecret", "app-secret");

    private final SettingsService settingsService;
    private final ObjectMapper objectMapper;

    public SettingsController(SettingsService settingsService, ObjectMapper objectMapper) {
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/llm")
    public ApiResponse<LlmSettingsView> getLlmConfig() {
        return ApiResponse.ok(settingsService.getLlmConfig());
    }

    @PutMapping("/llm")
    public ApiResponse<LlmSettingsView> updateLlmConfig(@RequestBody String payload) {
        JsonNode request = readTree(payload);
        rejectSecretFields(request, LLM_SECRET_FIELDS);
        return ApiResponse.ok(settingsService.updateLlmConfig(read(request, LlmSettingsUpdate.class)));
    }

    @GetMapping("/publisher")
    public ApiResponse<PublisherSettingsView> getPublisherConfig() {
        return ApiResponse.ok(settingsService.getPublisherConfig());
    }

    @PutMapping("/publisher")
    public ApiResponse<PublisherSettingsView> updatePublisherConfig(@RequestBody String payload) {
        JsonNode request = readTree(payload);
        rejectSecretFields(request, PUBLISHER_SECRET_FIELDS);
        return ApiResponse.ok(settingsService.updatePublisherConfig(read(request, PublisherSettingsUpdate.class)));
    }

    @GetMapping("/schedule")
    public ApiResponse<ScheduleSettingsView> getScheduleConfig() {
        return ApiResponse.ok(settingsService.getScheduleConfig());
    }

    @PutMapping("/schedule")
    public ApiResponse<ScheduleSettingsView> updateScheduleConfig(@RequestBody String payload) {
        return ApiResponse.ok(settingsService.updateScheduleConfig(read(readTree(payload), ScheduleSettingsUpdate.class)));
    }

    private <T> T read(JsonNode payload, Class<T> type) {
        try {
            return objectMapper.treeToValue(payload, type);
        } catch (JsonProcessingException ex) {
            throw new SystemConfigValidationException("设置请求格式无效", ex);
        }
    }

    private JsonNode readTree(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException ex) {
            throw new SystemConfigValidationException("设置请求格式无效", ex);
        }
    }

    private void rejectSecretFields(JsonNode payload, Set<String> forbiddenFields) {
        if (!payload.isObject()) {
            throw new SystemConfigValidationException("设置请求必须是 JSON 对象");
        }
        for (String field : forbiddenFields) {
            if (containsField(payload, field)) {
                throw new SystemConfigValidationException("凭据字段必须通过环境变量提供");
            }
        }
    }

    private boolean containsField(JsonNode node, String field) {
        if (node.isObject() && node.has(field)) {
            return true;
        }
        for (JsonNode child : node) {
            if (containsField(child, field)) {
                return true;
            }
        }
        return false;
    }
}
