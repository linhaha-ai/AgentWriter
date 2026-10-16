package com.agentwriter.agent.impl;

import com.agentwriter.entity.Article;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class WeChatContentRenderer {

    private static final Pattern MARKDOWN_IMAGE = Pattern.compile("^!\\[([^]]*)]\\(([^)]+)\\)\\s*$");

    private WeChatContentRenderer() {
    }

    static String render(Article article) {
        return render(article, Function.identity());
    }

    static String render(Article article, Function<String, String> imageUrlResolver) {
        if (article.getContentHtml() != null && !article.getContentHtml().isBlank()) {
            return article.getContentHtml();
        }
        if (article.getContentMd() == null || article.getContentMd().isBlank()) {
            return "<p>无内容</p>";
        }

        StringBuilder html = new StringBuilder();
        boolean inCodeBlock = false;
        for (String line : article.getContentMd().split("\n", -1)) {
            if (line.trim().startsWith("```")) {
                html.append(inCodeBlock ? "</code></pre>" : "<pre><code>");
                inCodeBlock = !inCodeBlock;
                continue;
            }
            if (inCodeBlock) {
                html.append(escapeHtml(line)).append("\n");
                continue;
            }

            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Matcher imageMatcher = MARKDOWN_IMAGE.matcher(trimmed);
            if (imageMatcher.matches()) {
                String altText = imageMatcher.group(1);
                String source = imageMatcher.group(2);
                String resolved = imageUrlResolver.apply(source);
                if (resolved != null && !resolved.equals(source)) {
                    html.append("<p><img src=\"").append(escapeHtml(resolved))
                            .append("\" alt=\"").append(escapeHtml(altText)).append("\"></p>");
                } else {
                    html.append("<p>").append(escapeHtml(trimmed)).append("</p>");
                }
            } else if (line.startsWith("# ")) {
                html.append("<h2>").append(escapeHtml(line.substring(2).trim())).append("</h2>");
            } else if (line.startsWith("## ")) {
                html.append("<h3>").append(escapeHtml(line.substring(3).trim())).append("</h3>");
            } else if (line.startsWith("### ")) {
                html.append("<h4>").append(escapeHtml(line.substring(4).trim())).append("</h4>");
            } else if (line.startsWith("#### ")) {
                html.append("<h5>").append(escapeHtml(line.substring(5).trim())).append("</h5>");
            } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                html.append("<li>").append(escapeHtml(trimmed.substring(2))).append("</li>");
            } else if (trimmed.matches("^\\d+\\.\\s.*")) {
                String text = trimmed.replaceFirst("^\\d+\\.\\s", "");
                html.append("<li>").append(escapeHtml(text)).append("</li>");
            } else {
                html.append("<p>").append(processInline(escapeHtml(line))).append("</p>");
            }
        }
        if (inCodeBlock) {
            html.append("</code></pre>");
        }
        return html.toString();
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String processInline(String text) {
        return text.replaceAll("`([^`]+)`", "<code>$1</code>")
                .replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
    }
}
