package com.agentwriter.agent;

import com.agentwriter.agent.impl.DefaultOutlineAgent;
import com.agentwriter.service.LLMService;
import com.agentwriter.service.PromptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultOutlineAgentTest {

    @Mock
    private LLMService llmService;
    @Mock
    private PromptService promptService;

    private DefaultOutlineAgent agent;

    @BeforeEach
    void setUp() {
        agent = new DefaultOutlineAgent(llmService, promptService);
    }

    @Test
    void shouldReturnOutline() {
        when(promptService.loadPrompt("outline")).thenReturn("You are an outline planner");
        when(llmService.generate(anyString(), anyString()))
                .thenReturn("""
                        {"success": true, "data": {"sections": [{"title": "Introduction", "content": "Overview of topic"}]}}
                        """);

        var result = agent.generateOutline("Java 21", "Research content");

        assertThat(result).isNotNull();
        assertThat(result).contains("\"success\": true");
    }

    @Test
    void shouldIncludeTitleInLlmCall() {
        when(promptService.loadPrompt("outline")).thenReturn("You are an outline planner");
        when(llmService.generate(anyString(), anyString()))
                .thenReturn("""
                        {"success": true, "data": {"sections": [{"title": "Spring Boot 4", "content": "Intro"}]}}
                        """);

        var result = agent.generateOutline("Spring Boot 4", "Research data");

        assertThat(result).contains("Spring Boot 4");
        assertThat(result).contains("\"title\": \"Spring Boot 4\"");
    }

    @Test
    void shouldHandleEmptyResearch() {
        when(promptService.loadPrompt("outline")).thenReturn("You are an outline planner");
        when(llmService.generate(anyString(), anyString()))
                .thenReturn("""
                        {"success": true, "data": {"sections": []}}
                        """);

        var result = agent.generateOutline("Title", "");

        assertThat(result).isNotNull();
        assertThat(result).contains("\"success\": true");
    }

    @Test
    void shouldIncludeOutlineSections() {
        when(promptService.loadPrompt("outline")).thenReturn("You are an outline planner");
        when(llmService.generate(anyString(), anyString()))
                .thenReturn("""
                        {"success": true, "data": {"sections": [{"title": "Intro", "content": "Start"}, {"title": "Body", "content": "Main"}, {"title": "Conclusion", "content": "End"}]}}
                        """);

        var result = agent.generateOutline("Deep Learning", "AI research");

        assertThat(result).contains("Intro");
        assertThat(result).contains("Body");
        assertThat(result).contains("Conclusion");
    }
}
