package com.agentwriter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
@EnableAsync
public class AgentWriterApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentWriterApplication.class, args);
    }

    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder().build();
    }

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
