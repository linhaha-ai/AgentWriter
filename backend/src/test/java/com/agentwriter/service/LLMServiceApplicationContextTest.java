package com.agentwriter.service;

import com.agentwriter.common.RetryPolicy;
import com.agentwriter.dto.LlmRuntimeConfig;
import com.agentwriter.service.impl.RoutingLLMService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LLMServiceApplicationContextTest {

    @ParameterizedTest
    @ValueSource(strings = {"disabled", "nvidia", "openai", "agnes"})
    void normalContextHasExactlyOneRoutingLlmService(String scenario) {
        SettingsService settings = mock(SettingsService.class);
        boolean enabled = !"disabled".equals(scenario);
        String provider = enabled ? scenario : "nvidia";
        when(settings.getLlmRuntimeConfig()).thenReturn(new LlmRuntimeConfig(
                enabled, provider, "http://127.0.0.1:1", "test-model", 1));

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {
            context.setEnvironment(new MockEnvironment());
            context.registerBean(SettingsService.class, () -> settings);
            context.registerBean(RetryPolicy.class, RetryPolicy::new);
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.register(RoutingLLMService.class);
            context.refresh();

            assertThat(context.getBeansOfType(LLMService.class))
                    .hasSize(1)
                    .allSatisfy((name, bean) ->
                            assertThat(bean).isInstanceOf(RoutingLLMService.class));
        }
    }
}
