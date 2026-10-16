package com.agentwriter.agent;

import com.agentwriter.agent.impl.DefaultReviewAgent;
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
class DefaultReviewAgentTest {

    @Mock
    private LLMService llmService;
    @Mock
    private PromptService promptService;

    private DefaultReviewAgent agent;

    @BeforeEach
    void setUp() {
        agent = new DefaultReviewAgent(llmService, promptService);
    }

    @Test
    void shouldReturnReviewedArticle() {
        when(promptService.loadPrompt("review")).thenReturn("You are a reviewer");
        when(llmService.generate(anyString(), anyString()))
                .thenReturn("""
                        {"success": true, "data": {"title": "Reviewed Article", "content": "# Improved\\n\\nFixed grammar.", "changes": ["Fixed typo"]}}
                        """);

        var result = agent.review("Original Title", "Original content with typo");

        assertThat(result).isNotNull();
        assertThat(result).contains("\"success\": true");
    }

    @Test
    void shouldIdentifyIssues() {
        when(promptService.loadPrompt("review")).thenReturn("You are a reviewer");
        when(llmService.generate(anyString(), anyString()))
                .thenReturn("""
                        {"success": true, "data": {"title": "Reviewed", "content": "# Fixed", "changes": ["Fixed grammar", "Improved structure"]}}
                        """);

        var result = agent.review("Test", "Bad content");

        assertThat(result).contains("Fixed grammar");
        assertThat(result).contains("Improved structure");
    }

    @Test
    void shouldReturnSuccessForEmptyContent() {
        when(promptService.loadPrompt("review")).thenReturn("You are a reviewer");
        when(llmService.generate(anyString(), anyString()))
                .thenReturn("""
                        {"success": true, "data": {"title": "", "content": "", "changes": []}}
                        """);

        var result = agent.review("", "");

        assertThat(result).contains("\"success\": true");
    }

    @Test
    void shouldHandleLongContent() {
        var longContent = "x".repeat(10000);
        when(promptService.loadPrompt("review")).thenReturn("You are a reviewer");
        when(llmService.generate(anyString(), anyString()))
                .thenReturn("""
                        {"success": true, "data": {"title": "Long Review", "content": "# Reviewed", "changes": []}}
                        """);

        var result = agent.review("Long", longContent);

        assertThat(result).contains("\"success\": true");
    }
}
