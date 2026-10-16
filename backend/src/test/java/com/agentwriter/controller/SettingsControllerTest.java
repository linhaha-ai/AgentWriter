package com.agentwriter.controller;

import com.agentwriter.common.GlobalExceptionHandler;
import com.agentwriter.dto.LlmSettingsView;
import com.agentwriter.dto.PublisherSettingsView;
import com.agentwriter.dto.ScheduleSettingsView;
import com.agentwriter.dto.WechatSettingsView;
import com.agentwriter.entity.SystemConfig;
import com.agentwriter.repository.SystemConfigRepository;
import com.agentwriter.service.SettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SettingsControllerTest {

    private SystemConfigRepository repository;
    private MockMvc storageQaMvc;

    @BeforeEach
    void setUpStorageQa() {
        repository = mock(SystemConfigRepository.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        SettingsService service = new SettingsService(
                repository, new ObjectMapper(),
                true, "nvidia", "https://llm.example.test/v1", "synthetic-only", "model", 60,
                false, "", "synthetic-only", "AgentWriter", "",
                true, "0 0 8 * * ?", eventPublisher
        );
        storageQaMvc = MockMvcBuilders.standaloneSetup(new SettingsController(service, new ObjectMapper()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getPayloadsNeverExposeCredentialPropertiesAndSecretInputIsRejected() throws Exception {
        SettingsService service = mock(SettingsService.class);
        when(service.getLlmConfig()).thenReturn(new LlmSettingsView(
                true, "nvidia", "https://llm.example.test", "model", 60, true
        ));
        when(service.getPublisherConfig()).thenReturn(new PublisherSettingsView(
                new WechatSettingsView(true, "wx-app", "AgentWriter", "thumb", true)
        ));
        when(service.getScheduleConfig()).thenReturn(new ScheduleSettingsView(true, "0 0 8 * * ?"));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SettingsController(service, new ObjectMapper()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/api/settings/llm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.apiKey").doesNotExist())
                .andExpect(jsonPath("$.data.credentialConfigured").value(true));
        mockMvc.perform(get("/api/settings/publisher"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.wechat.appSecret").doesNotExist())
                .andExpect(jsonPath("$.data.wechat.credentialConfigured").value(true));
        mockMvc.perform(put("/api/settings/llm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"provider\":\"nvidia\",\"apiUrl\":\"https://api.example.test\",\"model\":\"x\",\"timeout\":30,\"apiKey\":\"synthetic-only\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void manualApiStorageQaPersistsValidSettingsAndRejectsInvalidCronAndSecrets() throws Exception {
        storageQaMvc.perform(put("/api/settings/llm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"provider\":\"openai\",\"apiUrl\":\"https://api.example.test/v1\",\"model\":\"model\",\"timeout\":60}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.apiKey").doesNotExist())
                .andExpect(jsonPath("$.data.credentialConfigured").value(true));

        ArgumentCaptor<SystemConfig> savedConfig = ArgumentCaptor.forClass(SystemConfig.class);
        verify(repository).save(savedConfig.capture());
        assertThat(savedConfig.getValue().getConfigValue()).doesNotContain("apiKey", "synthetic-only");

        storageQaMvc.perform(put("/api/settings/schedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"cron\":\"invalid cron\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        storageQaMvc.perform(put("/api/settings/publisher")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"wechat\":{\"enabled\":false,\"appId\":\"\",\"appSecret\":\"synthetic-only\",\"author\":\"AgentWriter\",\"thumbMediaId\":\"\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        storageQaMvc.perform(put("/api/settings/llm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        verifyNoMoreInteractions(repository);
    }
}
