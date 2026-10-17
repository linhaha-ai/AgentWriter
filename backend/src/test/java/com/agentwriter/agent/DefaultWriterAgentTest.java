package com.agentwriter.agent;

import com.agentwriter.agent.impl.DefaultWriterAgent;
import com.agentwriter.service.LLMService;
import com.agentwriter.service.PromptService;
import com.agentwriter.service.WriterStyleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultWriterAgentTest {

    @Mock
    private LLMService llmService;
    @Mock
    private PromptService promptService;
    @Mock
    private WriterStyleService writerStyleService;

    private DefaultWriterAgent agent;

    @BeforeEach
    void setUp() {
        agent = new DefaultWriterAgent(llmService, promptService, writerStyleService);
    }

    @Test
    void shouldReturnWrittenArticle() {
        when(promptService.loadPrompt("writer")).thenReturn("You are a writer");
        when(llmService.generate(anyString(), anyString()))
                .thenReturn("""
                        {"success": true, "data": {"title": "Java 21 Guide", "content": "# Java 21\\n\\nVirtual threads are..."}}
                        """);

        var result = agent.write("Java 21 Guide", "# Introduction\n## Body");

        assertThat(result).isNotNull();
        assertThat(result).doesNotContain("\"success\"");
        assertThat(result).isEqualTo("# Java 21\n\nVirtual threads are...");
    }

    @Test
    void shouldExtractArticleContent() {
        when(promptService.loadPrompt("writer")).thenReturn("You are a writer");
        when(llmService.generate(anyString(), anyString()))
                .thenReturn("""
                        {"success": true, "data": {"title": "Rust vs Go", "content": "# Rust vs Go\\n\\nBoth are great..."}}
                        """);

        var result = agent.write("Rust vs Go", "# Intro\n## Performance");

        assertThat(result).contains("Rust vs Go");
        assertThat(result).contains("Both are great");
    }

    @Test
    void shouldHandleEmptyOutline() {
        when(promptService.loadPrompt("writer")).thenReturn("You are a writer");
        when(llmService.generate(anyString(), anyString()))
                .thenReturn("""
                        {"success": true, "data": {"title": "Empty Outline", "content": "# Generated\\n\\nNo outline provided."}}
                        """);

        var result = agent.write("Empty Outline", "");

        assertThat(result).isNotNull();
        assertThat(result).doesNotContain("\"success\"");
        assertThat(result).contains("No outline provided");
    }

    @Test
    void shouldProduceMarkdownContent() {
        when(promptService.loadPrompt("writer")).thenReturn("You are a writer");
        when(llmService.generate(anyString(), anyString()))
                .thenReturn("""
                        {"success": true, "data": {"title": "Markdown Test", "content": "# Heading\\n\\nParagraph with **bold** and `code`."}}
                        """);

        var result = agent.write("Markdown Test", "# Heading");

        assertThat(result).contains("**bold**");
        assertThat(result).contains("`code`");
    }

    @Test
    void shouldFallbackToRawResponseWhenNotJson() {
        when(promptService.loadPrompt("writer")).thenReturn("You are a writer");
        when(llmService.generate(anyString(), anyString()))
                .thenReturn("# Plain Markdown\n\nDirect content.");

        var result = agent.write("Plain", "# Heading");

        assertThat(result).isEqualTo("# Plain Markdown\n\nDirect content.");
    }
}
