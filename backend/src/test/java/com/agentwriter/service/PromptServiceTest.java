package com.agentwriter.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class PromptServiceTest {

    @Test
    void shouldReturnDefaultPromptForUnknownAgent() {
        var service = new PromptService();

        ReflectionTestUtils.setField(service, "promptsLocation", "classpath:prompts/");
        ReflectionTestUtils.setField(service, "defaultVersion", "v1");

        service.init();

        var prompt = service.loadPrompt("nonexistent");
        assertThat(prompt).contains("nonexistent 智能体");
    }

    @Test
    void shouldLoadRealPromptFiles() {
        var service = new PromptService();
        ReflectionTestUtils.setField(service, "promptsLocation", "classpath:prompts/");
        ReflectionTestUtils.setField(service, "defaultVersion", "v1");

        service.init();

        assertThat(service.getLoadedCount()).isEqualTo(7);
    }

    @Test
    void shouldLoadSpecificAgentPrompt() {
        var service = new PromptService();
        ReflectionTestUtils.setField(service, "promptsLocation", "classpath:prompts/");
        ReflectionTestUtils.setField(service, "defaultVersion", "v1");

        service.init();

        var prompt = service.loadPrompt("topic");
        assertThat(prompt).contains("资深技术内容编辑");
    }

    @Test
    void shouldReturnDefaultWhenCustomVersionMissing() {
        var service = new PromptService();
        ReflectionTestUtils.setField(service, "promptsLocation", "classpath:prompts/");
        ReflectionTestUtils.setField(service, "defaultVersion", "v1");

        service.init();

        var prompt = service.loadPrompt("topic", "v99");
        assertThat(prompt).contains("资深技术内容编辑");
    }

    @Test
    void shouldReloadPrompts() {
        var service = new PromptService();
        ReflectionTestUtils.setField(service, "promptsLocation", "classpath:prompts/");
        ReflectionTestUtils.setField(service, "defaultVersion", "v1");

        service.init();
        int before = service.getLoadedCount();

        service.reload();
        int after = service.getLoadedCount();

        assertThat(before).isEqualTo(after);
    }

    @Test
    void shouldCachePromptAfterLoading() {
        var service = new PromptService();
        ReflectionTestUtils.setField(service, "promptsLocation", "classpath:prompts/");
        ReflectionTestUtils.setField(service, "defaultVersion", "v1");

        service.init();

        var first = service.loadPrompt("research");
        var second = service.loadPrompt("research");
        assertThat(first).isEqualTo(second);
    }
}
