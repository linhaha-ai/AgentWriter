package com.agentwriter.agent;

import com.agentwriter.agent.impl.RoutingPublisher;
import com.agentwriter.service.SettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SpringJUnitConfig(RoutingPublisherBeanTest.Config.class)
class RoutingPublisherBeanTest {

    @org.springframework.beans.factory.annotation.Autowired
    private List<Publisher> publishers;

    @Test
    void applicationRegistersOneRuntimePublisherBean() {
        assertThat(publishers).singleElement().isInstanceOf(RoutingPublisher.class);
    }

    @Configuration(proxyBeanMethods = false)
    @Import(RoutingPublisher.class)
    static class Config {

        @Bean
        SettingsService settingsService() {
            return mock(SettingsService.class);
        }

        @Bean
        Environment environment() {
            return new StandardEnvironment();
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        WebClient.Builder webClientBuilder() {
            return WebClient.builder();
        }
    }
}
