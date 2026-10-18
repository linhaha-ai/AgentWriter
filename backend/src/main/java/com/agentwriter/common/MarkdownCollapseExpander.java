package com.agentwriter.common;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 折叠块展开工具。
 * <p>
 * 文章生成 / 编辑完成后，若内容中带有 &lt;details&gt; / &lt;summary&gt; 等折叠标记
 * （部分大模型会用它包裹代码块实现"点击展开"），统一展开为完整可见内容，
 * 保证代码块默认全部展开、不被收起。
 */
public final class MarkdownCollapseExpander {

    /** &lt;details&gt; 折叠块（含 open 属性、任意属性、可能嵌套） */
    private static final Pattern DETAILS_BLOCK = Pattern.compile(
            "<details\\b[^>]*>([\\s\\S]*?)</details>",
            Pattern.CASE_INSENSITIVE);

    /** &lt;summary&gt; 标题行 */
    private static final Pattern SUMMARY_TAG = Pattern.compile(
            "<summary\\b[^>]*>([\\s\\S]*?)</summary>",
            Pattern.CASE_INSENSITIVE);

    /** 行首的 &lt;summary&gt; / &lt;details&gt; 单标签残留 */
    private static final Pattern LEFTOVER_OPEN_TAGS = Pattern.compile(
            "</?(?:details|summary)\\b[^>]*>",
            Pattern.CASE_INSENSITIVE);

    private MarkdownCollapseExpander() {
    }

    /**
     * 展开给定 Markdown 中所有的折叠块。
     *
     * @param markdown 原始 Markdown
     * @return 折叠块已展开、代码块完整可见的 Markdown
     */
    public static String expand(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return markdown;
        }
        String result = DETAILS_BLOCK.matcher(markdown).replaceAll(m -> expandBlock(m.group(1)));
        // 清理残留的孤立标签（如未闭合的 details）
        result = LEFTOVER_OPEN_TAGS.matcher(result).replaceAll("");
        return result;
    }

    /**
     * 展开单个折叠块内部内容：移除 summary 标签但保留其文字，其余内容原样保留。
     */
    private static String expandBlock(String inner) {
        String content = inner;
        // 处理 summary：去掉标签，保留标题文字并另起一行（使折叠标题成为普通段落）
        Matcher summaryMatcher = SUMMARY_TAG.matcher(content);
        StringBuilder sb = new StringBuilder();
        while (summaryMatcher.find()) {
            String summaryText = summaryMatcher.group(1).trim();
            summaryMatcher.appendReplacement(sb, Matcher.quoteReplacement(summaryText));
            if (!summaryText.endsWith("\n")) {
                sb.append("\n");
            }
        }
        summaryMatcher.appendTail(sb);
        content = sb.toString();

        // 清理残留的 summary/details 标签
        content = LEFTOVER_OPEN_TAGS.matcher(content).replaceAll("");
        return content;
    }
}
