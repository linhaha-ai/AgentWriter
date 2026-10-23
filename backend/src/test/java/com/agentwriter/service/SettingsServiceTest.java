package com.agentwriter.service;

import com.agentwriter.dto.LlmSettingsUpdate;
import com.agentwriter.dto.PublisherSettingsUpdate;
import com.agentwriter.dto.ScheduleSettingsUpdate;
import com.agentwriter.dto.WechatSettingsUpdate;
import com.agentwriter.entity.SystemConfig;
import com.agentwriter.exception.SystemConfigMalformedException;
import com.agentwriter.exception.SystemConfigValidationException;
import com.agentwriter.repository.SystemConfigRepository;
import com.agentwriter.service.event.ScheduleConfigurationChangedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class SettingsServiceTest {

    @Mock
    private SystemConfigRepository repository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SettingsService service;

    @BeforeEach
    void setUp() {
        service = new SettingsService(
                repository, new ObjectMapper(),
                true, "nvidia", "https://llm.example.test/v1", "synthetic-only", "test-model", 120,
                false, "", "synthetic-only", "", "1024x1024",
                true, "wx-app", "synthetic-only", "AgentWriter", "thumb-id",
                true, "0 0 8 * * ?", eventPublisher
        );
    }

    @Test
    void updatePersistsNonSecretFieldsAndManualApiKeyWhileViewOnlyExposesStatus() {
        var updated = service.updateLlmConfig(new LlmSettingsUpdate(
                true, "openai", "https://api.example.test/v1", "gpt-test", 60, "sk-manual"
        ));

        ArgumentCaptor<SystemConfig> captor = ArgumentCaptor.forClass(SystemConfig.class);
        verify(repository).save(captor.capture());
        String savedJson = captor.getValue().getConfigValue();

        assertThat(savedJson).contains("openai", "api.example.test", "gpt-test", "sk-manual");
        assertThat(updated.credentialConfigured()).isTrue();
    }

    @Test
    void updatePublisherPersistsAppIdAuthorThumbAndManualAppSecret() {
        service.updatePublisherConfig(new PublisherSettingsUpdate(new WechatSettingsUpdate(
                true, "new-app", "New Author", "new-thumb", "wx-secret-manual"
        )));

        ArgumentCaptor<SystemConfig> captor = ArgumentCaptor.forClass(SystemConfig.class);
        verify(repository).save(captor.capture());
        String savedJson = captor.getValue().getConfigValue();

        assertThat(savedJson).contains("new-app", "New Author", "new-thumb", "wx-secret-manual");
    }

    @Test
    void updateRejectsInvalidProviderUrlTimeoutAndCron() {
        assertThatThrownBy(() -> service.updateLlmConfig(new LlmSettingsUpdate(
                true, "unsupported", "https://api.example.test", "model", 60, null
        ))).isInstanceOf(SystemConfigValidationException.class);
        assertThatThrownBy(() -> service.updateLlmConfig(new LlmSettingsUpdate(
                true, "nvidia", "not-a-url", "model", 60, null
        ))).isInstanceOf(SystemConfigValidationException.class);
        assertThatThrownBy(() -> service.updateLlmConfig(new LlmSettingsUpdate(
                true, "nvidia", "https://api.example.test", "model", 0, null
        ))).isInstanceOf(SystemConfigValidationException.class);
        assertThatThrownBy(() -> service.updateLlmConfig(new LlmSettingsUpdate(
                true, "nvidia", "https://api.example.test", "model", 5, null
        ))).isInstanceOf(SystemConfigValidationException.class);
        assertThatThrownBy(() -> service.updateScheduleConfig(new ScheduleSettingsUpdate(true, "not cron")))
                .isInstanceOf(SystemConfigValidationException.class);
    }

    @Test
    void scheduleUpdatePersistsBeforeItReschedules() {
        ScheduleSettingsUpdate update = new ScheduleSettingsUpdate(true, "0 5 8 * * ?");

        service.updateScheduleConfig(update);

        var ordered = inOrder(repository, eventPublisher);
        ordered.verify(repository).save(org.mockito.ArgumentMatchers.any(SystemConfig.class));
        ordered.verify(eventPublisher).publishEvent(new ScheduleConfigurationChangedEvent(
                new com.agentwriter.dto.ScheduleRuntimeConfig(true, "0 5 8 * * ?")));
    }

    @Test
    void schedulerFailureRestoresThePriorPersistedSchedule() {
        doThrow(new IllegalStateException("scheduler unavailable"))
                .when(eventPublisher).publishEvent(new ScheduleConfigurationChangedEvent(
                        new com.agentwriter.dto.ScheduleRuntimeConfig(true, "0 5 8 * * ?")));

        assertThatThrownBy(() -> service.updateScheduleConfig(new ScheduleSettingsUpdate(true, "0 5 8 * * ?")))
                .isInstanceOf(IllegalStateException.class);

        ArgumentCaptor<SystemConfig> saved = ArgumentCaptor.forClass(SystemConfig.class);
        verify(repository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(1).getConfigValue()).contains("0 0 8 * * ?");
    }

    @Test
    void malformedStoredJsonIsAnExplicitTypedConfigurationError() {
        when(repository.findById("llm")).thenReturn(Optional.of(new SystemConfig("llm", "{invalid")));

        assertThatThrownBy(service::getLlmConfig)
                .isInstanceOf(SystemConfigMalformedException.class)
                .hasMessageContaining("llm");
    }

    @Test
    void storedApiKeyOverridesYmlFallback() {
        when(repository.findById("llm")).thenReturn(Optional.of(new SystemConfig(
                "llm", "{\"enabled\":true,\"provider\":\"openai\",\"apiUrl\":\"https://api.example.test/v1\",\"model\":\"model\",\"timeout\":60,\"apiKey\":\"sk-stored\"}")));

        assertThat(service.getLlmConfig().credentialConfigured()).isTrue();
        assertThat(service.getLlmApiKey()).isEqualTo("sk-stored");
        assertThat(service.getLlmRuntimeConfig().provider()).isEqualTo("openai");
    }

    @Test
    void ymlApiKeyIsUsedWhenNoStoredCredential() {
        when(repository.findById("llm")).thenReturn(Optional.empty());

        assertThat(service.getLlmConfig().credentialConfigured()).isTrue();
        assertThat(service.getLlmApiKey()).isEqualTo("synthetic-only");
    }

    @Test
    void blankApiKeyOnUpdateKeepsPreviouslyStoredCredential() {
        when(repository.findById("llm")).thenReturn(Optional.of(new SystemConfig(
                "llm", "{\"enabled\":true,\"provider\":\"openai\",\"apiUrl\":\"https://api.example.test/v1\",\"model\":\"model\",\"timeout\":60,\"apiKey\":\"sk-existing\"}")));

        service.updateLlmConfig(new LlmSettingsUpdate(
                true, "openai", "https://api.example.test/v1", "model", 60, null));

        ArgumentCaptor<SystemConfig> captor = ArgumentCaptor.forClass(SystemConfig.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getConfigValue()).contains("sk-existing");
    }

    @Test
    void explicitEmptyApiKeyClearsStoredCredential() {
        when(repository.findById("llm")).thenReturn(Optional.of(new SystemConfig(
                "llm", "{\"enabled\":true,\"provider\":\"openai\",\"apiUrl\":\"https://api.example.test/v1\",\"model\":\"model\",\"timeout\":60,\"apiKey\":\"sk-existing\"}")));

        var updated = service.updateLlmConfig(new LlmSettingsUpdate(
                true, "openai", "https://api.example.test/v1", "model", 60, ""));

        ArgumentCaptor<SystemConfig> captor = ArgumentCaptor.forClass(SystemConfig.class);
        verify(repository).save(captor.capture());
        String savedJson = captor.getValue().getConfigValue();
        assertThat(savedJson).doesNotContain("sk-existing").contains("\"apiKey\":null");
        // 清除后视图状态回退到 yml 兜底凭据
        assertThat(updated.credentialConfigured()).isTrue();
    }

    @Test
    void runtimeConfigContainsOnlyLatestNonSecretSettings() {
        service.updateLlmConfig(new LlmSettingsUpdate(
                true, "openai", "https://api.example.test", "model", 60, null
        ));

        var runtime = service.getLlmRuntimeConfig();
        assertThat(runtime.provider()).isEqualTo("nvidia");
        assertThat(runtime.apiUrl()).isEqualTo("https://llm.example.test/v1");
    }

    @Test
    void legacyShortTimeoutIsRaisedForRuntimeWithoutRejectingStoredSettings() {
        when(repository.findById("llm")).thenReturn(Optional.of(new SystemConfig(
                "llm", "{\"enabled\":true,\"provider\":\"nvidia\",\"apiUrl\":\"https://api.example.test/v1\",\"model\":\"model\",\"timeout\":5}")));

        assertThat(service.getLlmRuntimeConfig().timeout()).isEqualTo(120);
        assertThat(service.getLlmConfig().timeout()).isEqualTo(120);
    }
}
