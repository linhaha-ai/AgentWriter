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
                true, "wx-app", "synthetic-only", "AgentWriter", "thumb-id",
                true, "0 0 8 * * ?", eventPublisher
        );
    }

    @Test
    void updatePersistsOnlyAllowlistedNonSecretFieldsAndViewOnlyExposesCredentialStatus() {
        var updated = service.updateLlmConfig(new LlmSettingsUpdate(
                true, "openai", "https://api.example.test/v1", "gpt-test", 60
        ));

        ArgumentCaptor<SystemConfig> captor = ArgumentCaptor.forClass(SystemConfig.class);
        verify(repository).save(captor.capture());
        String savedJson = captor.getValue().getConfigValue();

        assertThat(savedJson).contains("openai", "api.example.test", "gpt-test");
        assertThat(savedJson).doesNotContain("apiKey", "synthetic-only");
        assertThat(updated.credentialConfigured()).isTrue();
    }

    @Test
    void updatePublisherPersistsAppIdAuthorAndThumbButNeverCredential() {
        service.updatePublisherConfig(new PublisherSettingsUpdate(new WechatSettingsUpdate(
                true, "new-app", "New Author", "new-thumb"
        )));

        ArgumentCaptor<SystemConfig> captor = ArgumentCaptor.forClass(SystemConfig.class);
        verify(repository).save(captor.capture());
        String savedJson = captor.getValue().getConfigValue();

        assertThat(savedJson).contains("new-app", "New Author", "new-thumb");
        assertThat(savedJson).doesNotContain("appSecret", "synthetic-only");
    }

    @Test
    void updateRejectsInvalidProviderUrlTimeoutAndCron() {
        assertThatThrownBy(() -> service.updateLlmConfig(new LlmSettingsUpdate(
                true, "unsupported", "https://api.example.test", "model", 60
        ))).isInstanceOf(SystemConfigValidationException.class);
        assertThatThrownBy(() -> service.updateLlmConfig(new LlmSettingsUpdate(
                true, "nvidia", "not-a-url", "model", 60
        ))).isInstanceOf(SystemConfigValidationException.class);
        assertThatThrownBy(() -> service.updateLlmConfig(new LlmSettingsUpdate(
                true, "nvidia", "https://api.example.test", "model", 0
        ))).isInstanceOf(SystemConfigValidationException.class);
        assertThatThrownBy(() -> service.updateLlmConfig(new LlmSettingsUpdate(
                true, "nvidia", "https://api.example.test", "model", 5
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
    void storedCredentialFieldsAreRejectedInsteadOfBeingSilentlyIgnored() {
        when(repository.findById("llm")).thenReturn(Optional.of(new SystemConfig("llm", "{\"apiKey\":\"synthetic-only\"}")));

        assertThatThrownBy(service::getLlmConfig)
                .isInstanceOf(SystemConfigMalformedException.class)
                .hasMessageContaining("llm");
    }

    @Test
    void runtimeConfigContainsOnlyLatestNonSecretSettings() {
        service.updateLlmConfig(new LlmSettingsUpdate(
                true, "openai", "https://api.example.test", "model", 60
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
