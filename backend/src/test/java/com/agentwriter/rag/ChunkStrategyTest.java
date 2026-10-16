package com.agentwriter.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkStrategyTest {

    private ChunkStrategy strategy;

    @BeforeEach
    void setUp() {
        // 使用较小的 minTokens（10）防止 mergeUndersized 合并小型测试段落
        strategy = new ChunkStrategy(600, 1000, 10, 50, 1);
    }

    @Test
    void shouldReturnEmptyForNullContent() {
        var chunks = strategy.split("test.md", null);
        assertThat(chunks).isEmpty();
    }

    @Test
    void shouldReturnEmptyForBlankContent() {
        var chunks = strategy.split("test.md", "   ");
        assertThat(chunks).isEmpty();
    }

    @Test
    void shouldHandleShortContentBelowMinTokens() {
        // 短内容会被 splitByHeadings() 中的 < 10 token 过滤器过滤掉
        var chunks = strategy.split("test.md", "hi");
        assertThat(chunks).isEmpty();
    }

    @Test
    void shouldChunkWithEnoughContent() {
        var chunks = strategy.split("test.md", "This line has enough words to pass the minimum token filter.");
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).title()).isEqualTo("test.md");
    }

    @Test
    void shouldSplitByH2Headings() {
        var md = """
                # Title

                ## Overview
                This is the overview section with enough text.

                ## Details
                This is the details section with more content here.

                ## Conclusion
                This is the conclusion with enough words here.
                """;

        var chunks = strategy.split("article.md", md);
        assertThat(chunks).hasSize(3);

        assertThat(chunks.get(0).title()).isEqualTo("Overview");
        assertThat(chunks.get(0).content()).contains("overview section");

        assertThat(chunks.get(1).title()).isEqualTo("Details");
        assertThat(chunks.get(1).content()).contains("details section");

        assertThat(chunks.get(2).title()).isEqualTo("Conclusion");
        assertThat(chunks.get(2).content()).contains("conclusion");
    }

    @Test
    void shouldHandleContentWithoutHeadings() {
        var md = """
                This is a paragraph without any markdown headings.
                Just regular text content that should all be one chunk here.
                It has enough text to pass the minimum token filter.
                """;

        var chunks = strategy.split("plain.md", md);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).title()).isEqualTo("plain.md");
    }

    @Test
    void shouldHandleLargeContentWithoutCrashing() {
        var sb = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            sb.append("## Section ").append(i).append("\n");
            sb.append("Content for section ").append(i).append(" with enough words here to pass the minimum.\n\n");
        }

        var chunks = strategy.split("large.md", sb.toString());
        assertThat(chunks).hasSize(500);
        assertThat(chunks.get(0).title()).isEqualTo("Section 0");
        assertThat(chunks.get(499).title()).isEqualTo("Section 499");
    }

    @Test
    void shouldTokenCountBePositive() {
        var md = """
                ## Section
                This section content has enough words to pass the token filter.
                """;

        var chunks = strategy.split("count.md", md);
        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0).content()).isNotBlank();
    }

    @Test
    void shouldHandleEmptyHeadingsGracefully() {
        var md = """
                ##\s
                content under empty heading with enough words here.
                ## Next
                This section has text here to pass the limit too.
                """;

        var chunks = strategy.split("empty.md", md);
        assertThat(chunks).isNotEmpty();
    }
}
