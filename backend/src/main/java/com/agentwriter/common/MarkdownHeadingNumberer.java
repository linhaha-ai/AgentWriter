package com.agentwriter.common;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 标题编号规范化工具。
 *
 * <p>为生成的文章正文做确定性兜底：把全部一级（##）与二级（###）标题统一编号为
 * {@code 1.} / {@code 1.1} 形式。先剥离标题中已存在的各种编号前缀
 * （如 {@code 1.}、{@code 1.1}、{@code 一、}、{@code （一）}、{@code 第1章}），再按出现顺序连续重编号。
 * 仅处理 ## 与 ### 两级标题；代码块围栏内的内容、更深层标题（#### 及以上）与其它行保持不变。</p>
 */
public final class MarkdownHeadingNumberer {

    private MarkdownHeadingNumberer() {
    }

    /** 行首：缩进 + 两个井号 + 空白，一级标题 */
    private static final Pattern H1 = Pattern.compile("^(\\s*)(#{2})(\\s+)(.*)$");

    /** 行首：缩进 + 三个井号 + 空白，二级标题 */
    private static final Pattern H2 = Pattern.compile("^(\\s*)(#{3})(\\s+)(.*)$");

    /** 行首已有的编号前缀：1. / 1.1 / 1.1.1 / 一、 / （一） / 第1章 等，用于剥离 */
    private static final Pattern NUMBER_PREFIX = Pattern.compile(
            "^(?:\\d+\\.\\d+(?:\\.\\d+)*[\\.、．]?\\s+|"
                    + "\\d+[\\.、．]\\s+|"
                    + "[一二三四五六七八九十百千]+[、．]\\s*|"
                    + "[（(][一二三四五六七八九十百千]+[）)]\\s*|"
                    + "第[一二三四五六七八九十百千\\d]+[章节篇]\\s+)");

    /** Markdown 代码块围栏行 */
    private static final Pattern FENCE = Pattern.compile("^\\s*```");

    /**
     * 规范化给定 Markdown 中所有一级/二级标题的章节编号。
     *
     * @param markdown 原始 Markdown 正文
     * @return 标题已按 1. / 1.1 形式连续编号的 Markdown
     */
    public static String numberize(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return markdown;
        }

        StringBuilder result = new StringBuilder(markdown.length() + 64);
        int h1 = 0;
        int h2 = 0;
        boolean inFence = false;

        for (String line : markdown.split("\n", -1)) {
            if (FENCE.matcher(line).find()) {
                inFence = !inFence;
                result.append(line).append('\n');
                continue;
            }
            if (!inFence) {
                Matcher m1 = H1.matcher(line);
                if (m1.matches()) {
                    h1++;
                    h2 = 0;
                    result.append(m1.group(1)).append("## ").append(h1).append(". ")
                            .append(stripNumberPrefix(m1.group(4))).append('\n');
                    continue;
                }
                Matcher m2 = H2.matcher(line);
                if (m2.matches()) {
                    if (h1 == 0) {
                        h1 = 1; // 无一级标题时，二级标题归入第 1 章
                    }
                    h2++;
                    result.append(m2.group(1)).append("### ").append(h1).append('.').append(h2).append(' ')
                            .append(stripNumberPrefix(m2.group(4))).append('\n');
                    continue;
                }
            }
            result.append(line).append('\n');
        }

        int length = result.length();
        if (length > 0 && result.charAt(length - 1) == '\n') {
            result.setLength(length - 1);
        }
        return result.toString();
    }

    private static String stripNumberPrefix(String headingText) {
        String text = headingText == null ? "" : headingText.trim();
        Matcher matcher = NUMBER_PREFIX.matcher(text);
        return matcher.find() ? text.substring(matcher.end()).trim() : text;
    }
}
