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
        assertThat(result).doesNotContain("\"success\"");
        assertThat(result).isEqualTo("# Optimized\n\nSEO content.");
    }

    @Test
    void shouldExtractOptimizedContentWithoutEnvelope() {
        when(promptService.loadPrompt("seo")).thenReturn("You are an SEO expert");
        when(llmService.generate(anyString(), anyString()))
                .thenReturn("""
                        ```json
                        {"success": true, "data": {"title": "SEO Title", "description": "Desc", "keywords": ["spring", "boot", "java"], "content": "# SEO\\n\\nContent"}}
                        ```
                        """);

        var result = agent.optimize("# Content");

        assertThat(result).isEqualTo("# SEO\n\nContent");
        assertThat(result).doesNotContain("spring", "boot");
    }

    @Test
    void shouldHandleEmptyContent() {
        when(promptService.loadPrompt("seo")).thenReturn("You are an SEO expert");
        when(llmService.generate(anyString(), anyString()))
                .thenReturn("""
                        {"success": true, "data": {"title": "", "description": "", "keywords": [], "content": ""}}
                        """);

        var result = agent.optimize("");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnExtractedContent() {
        when(promptService.loadPrompt("seo")).thenReturn("You are an SEO expert");
        when(llmService.generate(anyString(), anyString()))
                .thenReturn("""
                        {"success": true, "data": {"title": "Guide", "metaDescription": "A comprehensive guide to Java 21", "content": "# Guide"}}
                        """);

        var result = agent.optimize("# Guide");

        assertThat(result).isEqualTo("# Guide");
    }

    @Test
    void shouldFallbackToRawResponseWhenNotJson() {
        when(promptService.loadPrompt("seo")).thenReturn("You are an SEO expert");
        when(llmService.generate(anyString(), anyString()))
                .thenReturn("# Plain Markdown\n\nSEO content.");

        var result = agent.optimize("# Guide");

        assertThat(result).isEqualTo("# Plain Markdown\n\nSEO content.");
    }

    @Test
    void shouldFallbackToInputWhenEnvelopeIsBroken() {
        when(promptService.loadPrompt("seo")).thenReturn("You are an SEO expert");
        when(llmService.generate(anyString(), anyString()))
                .thenReturn("```json\n{\"success\": true, \"data\": {\"content\": \"# Broken \\\" quote\"}");

        var result = agent.optimize("# Original\n\nKeep me.");

        assertThat(result).isEqualTo("# Original\n\nKeep me.");
    }
}
