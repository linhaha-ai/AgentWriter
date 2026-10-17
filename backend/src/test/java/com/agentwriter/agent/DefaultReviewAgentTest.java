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
        assertThat(result).doesNotContain("\"success\"");
        assertThat(result).isEqualTo("# Improved\n\nFixed grammar.");
    }

    @Test
    void shouldPreferFixedContentField() {
        when(promptService.loadPrompt("review")).thenReturn("You are a reviewer");
        when(llmService.generate(anyString(), anyString()))
                .thenReturn("""
                        {"success": true, "data": {"issues": [], "content": "old content", "fixedContent": "# Fixed\\n\\nNew revised content."}}
                        """);

        var result = agent.review("Test", "Bad content");

        assertThat(result).isEqualTo("# Fixed\n\nNew revised content.");
        assertThat(result).doesNotContain("old content");
    }

    @Test
    void shouldReturnSuccessForEmptyContent() {
        when(promptService.loadPrompt("review")).thenReturn("You are a reviewer");
        when(llmService.generate(anyString(), anyString()))
                .thenReturn("""
                        {"success": true, "data": {"title": "", "content": "", "changes": []}}
                        """);

        var result = agent.review("", "");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldHandleLongContent() {
        var longContent = "x".repeat(10000);
        when(promptService.loadPrompt("review")).thenReturn("You are a reviewer");
        when(llmService.generate(anyString(), anyString()))
                .thenReturn("""
                        {"success": true, "data": {"title": "Long Review", "fixedContent": "# Reviewed", "changes": []}}
                        """);

        var result = agent.review("Long", longContent);

        assertThat(result).isEqualTo("# Reviewed");
    }

    @Test
    void shouldFallbackToRawResponseWhenNotJson() {
        when(promptService.loadPrompt("review")).thenReturn("You are a reviewer");
        when(llmService.generate(anyString(), anyString()))
                .thenReturn("# Plain Markdown\n\nReviewed.");

        var result = agent.review("Plain", "content");

        assertThat(result).isEqualTo("# Plain Markdown\n\nReviewed.");
    }

    @Test
    void shouldFallbackToInputWhenEnvelopeIsBroken() {
        when(promptService.loadPrompt("review")).thenReturn("You are a reviewer");
        when(llmService.generate(anyString(), anyString()))
                .thenReturn("```json\n{\"success\": true, \"data\": {\"fixedContent\": \"# Broken \\\" quote\"}");

        var result = agent.review("Plain", "# Original\n\nKeep me.");

        assertThat(result).isEqualTo("# Original\n\nKeep me.");
    }
}
