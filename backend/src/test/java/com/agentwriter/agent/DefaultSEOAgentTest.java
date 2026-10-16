package com.agentwriter.agent;

import com.agentwriter.agent.impl.DefaultSEOAgent;
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
class DefaultSEOAgentTest {

    @Mock
    private LLMService llmService;
    @Mock
    private PromptService promptService;

    private DefaultSEOAgent agent;

    @BeforeEach
    void setUp() {
        agent = new DefaultSEOAgent(llmService, promptService);
    }

    @Test
    void shouldReturnOptimizedContent() {
        when(promptService.loadPrompt("seo")).thenReturn("You are an SEO expert");
        when(llmService.generate(anyString(), anyString()))
                .thenReturn("""
                        {"success": true, "data": {"title": "Optimized Title", "description": "SEO meta", "keywords": ["java", "tutorial"], "content": "# Optimized\\n\\nSEO content."}}
                        """);

        var result = agent.optimize("# Original\\n\\nOriginal content.");

        assertThat(result).isNotNull();
        assertThat(result).contains("\"success\": true");
    }

    @Test
    void shouldIncludeKeywords() {
        when(promptService.loadPrompt("seo")).thenReturn("You are an SEO expert");
        when(llmService.generate(anyString(), anyString()))
                .thenReturn("""
                        {"success": true, "data": {"title": "SEO Title", "description": "Desc", "keywords": ["spring", "boot", "java"], "content": "# SEO\\n\\nContent"}}
                        """);

        var result = agent.optimize("# Content");

        assertThat(result).contains("spring");
        assertThat(result).contains("boot");
        assertThat(result).contains("java");
    }

    @Test
    void shouldHandleEmptyContent() {
        when(promptService.loadPrompt("seo")).thenReturn("You are an SEO expert");
        when(llmService.generate(anyString(), anyString()))
                .thenReturn("""
                        {"success": true, "data": {"title": "", "description": "", "keywords": [], "content": ""}}
                        """);

        var result = agent.optimize("");

        assertThat(result).contains("\"success\": true");
    }

    @Test
    void shouldReturnMetaDescription() {
        when(promptService.loadPrompt("seo")).thenReturn("You are an SEO expert");
        when(llmService.generate(anyString(), anyString()))
                .thenReturn("""
                        {"success": true, "data": {"title": "Guide", "description": "A comprehensive guide to Java 21", "keywords": ["java"], "content": "# Guide"}}
                        """);

        var result = agent.optimize("# Guide");

        assertThat(result).contains("comprehensive guide");
    }
}
