package com.agentwriter.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownHeadingNumbererTest {

    @Test
    void numbersPlainHeadingsConsecutively() {
        String md = """
                ## 引言
                ### 背景
                ### 文章结构
                ## 核心概念
                ### 定义
                """;

        assertThat(MarkdownHeadingNumberer.numberize(md)).isEqualTo("""
                ## 1. 引言
                ### 1.1 背景
                ### 1.2 文章结构
                ## 2. 核心概念
                ### 2.1 定义
                """);
    }

    @Test
    void stripsExistingNumberPrefixesAndRenumbers() {
        String md = """
                ## 一、引言
                ### （一）背景
                ### 1.2 文章结构
                ## 第2章 核心概念
                ### 2.1 定义
                """;

        assertThat(MarkdownHeadingNumberer.numberize(md)).isEqualTo("""
                ## 1. 引言
                ### 1.1 背景
                ### 1.2 文章结构
                ## 2. 核心概念
                ### 2.1 定义
                """);
    }

    @Test
    void doesNotStripNumbersThatArePartOfHeadingText() {
        String md = """
                ## Java 21 新特性速览
                ### 21 个实用技巧
                """;

        assertThat(MarkdownHeadingNumberer.numberize(md)).isEqualTo("""
                ## 1. Java 21 新特性速览
                ### 1.1 21 个实用技巧
                """);
    }

    @Test
    void leavesDeeperHeadingsAndCodeFencesUntouched() {
        String md = """
                ## 1. 示例
                #### 不处理的四级标题
                ```java
                ## 这是代码里的注释行
                ### 也不是标题
                ```
                """;

        assertThat(MarkdownHeadingNumberer.numberize(md)).isEqualTo("""
                ## 1. 示例
                #### 不处理的四级标题
                ```java
                ## 这是代码里的注释行
                ### 也不是标题
                ```
                """);
    }

    @Test
    void numbersSecondLevelUnderFirstChapterWhenNoH1Exists() {
        String md = """
                ### 背景
                ### 结构
                """;

        assertThat(MarkdownHeadingNumberer.numberize(md)).isEqualTo("""
                ### 1.1 背景
                ### 1.2 结构
                """);
    }

    @Test
    void handlesBlankAndNull() {
        assertThat(MarkdownHeadingNumberer.numberize(null)).isNull();
        assertThat(MarkdownHeadingNumberer.numberize("   ")).isEqualTo("   ");
        assertThat(MarkdownHeadingNumberer.numberize("")).isEmpty();
    }

    @Test
    void preservesInlineCodeAndFormattingInsideHeading() {
        String md = "## 使用 `Optional` 处理空值\n";

        assertThat(MarkdownHeadingNumberer.numberize(md))
                .isEqualTo("## 1. 使用 `Optional` 处理空值\n");
    }
}
