package com.agentwriter.service;

import com.agentwriter.dto.ImageSuggestion;
import com.agentwriter.entity.Article;
import com.agentwriter.entity.ArticleImageRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 为文章生成可编辑的封面和正文插图提示词。
 */
@Service
public class ImageSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(ImageSuggestionService.class);
    private static final Pattern H2_PATTERN = Pattern.compile("(?m)^##\\s+(.+?)\\s*$");

    private final LLMService llmService;
    private final PromptService promptService;
    private final ObjectMapper objectMapper;

    public ImageSuggestionService(LLMService llmService, PromptService promptService, ObjectMapper objectMapper) {
        this.llmService = llmService;
        this.promptService = promptService;
        this.objectMapper = objectMapper;
    }

    public List<ImageSuggestion> suggest(Article article) {
        List<String> headings = extractHeadings(article.getContentMd());
        if (headings.size() < 2) {
            throw new IllegalArgumentException("正文至少需要两个二级标题才能生成插图建议");
        }
        try {
            String response = llmService.generate(promptService.loadPrompt("image"), buildRequest(article, headings));
            List<ImageSuggestion> suggestions = parse(response, headings);
            if (suggestions.size() == 3) {
                return suggestions;
            }
        } catch (RuntimeException exception) {
            log.warn("图片提示词建议生成失败，使用默认建议: type={}", exception.getClass().getSimpleName());
        }
        return fallback(article, headings);
    }

    private String buildRequest(Article article, List<String> headings) {
        try {
            return objectMapper.writeValueAsString(new ImageSuggestionInput(
                    article.getTitle(), article.getContentMd(), headings.subList(0, 2)
            ));
        } catch (Exception exception) {
            throw new IllegalStateException("无法序列化图片建议输入", exception);
        }
    }

    private List<ImageSuggestion> parse(String response, List<String> headings) {
        if (response == null || response.isBlank()) {
            return List.of();
        }
        String json = stripCodeFence(response);
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray() || root.size() != 3) {
                return List.of();
            }
            List<ImageSuggestion> result = new ArrayList<>();
            boolean coverSeen = false;
            for (JsonNode node : root) {
                ArticleImageRole role = ArticleImageRole.valueOf(node.path("role").asText());
                String heading = node.path("headingAnchor").isNull() ? null : node.path("headingAnchor").asText(null);
                String prompt = node.path("prompt").asText();
                String altText = node.path("altText").asText();
                if (prompt.isBlank() || altText.isBlank() || prompt.length() > 4000 || altText.length() > 500) {
                    return List.of();
                }
                if (role == ArticleImageRole.COVER) {
                    if (coverSeen || heading != null) {
                        return List.of();
                    }
                    coverSeen = true;
                } else if (!headings.subList(0, 2).contains(heading)) {
                    return List.of();
                }
                result.add(new ImageSuggestion(role, heading, prompt, altText));
            }
            return coverSeen && result.stream().filter(item -> item.role() == ArticleImageRole.INLINE).count() == 2
                    ? result
                    : List.of();
        } catch (Exception exception) {
            return List.of();
        }
    }

    private static List<ImageSuggestion> fallback(Article article, List<String> headings) {
        String title = article.getTitle();
        return List.of(
                new ImageSuggestion(
                        ArticleImageRole.COVER,
                        null,
                        "Editorial technical illustration for " + title + ", clean modern composition, wide landscape, no text, no logo, no watermark",
                        title + "文章封面插画"
                ),
                inlineSuggestion(title, headings.get(0)),
                inlineSuggestion(title, headings.get(1))
        );
    }

    private static ImageSuggestion inlineSuggestion(String title, String heading) {
        return new ImageSuggestion(
                ArticleImageRole.INLINE,
                heading,
                "Technical editorial illustration explaining " + heading + " in the context of " + title
                        + ", clean diagram-like composition, wide landscape, no text, no logo, no watermark",
                heading + "示意图"
        );
    }

    private static List<String> extractHeadings(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("文章正文为空，无法生成图片建议");
        }
        List<String> headings = new ArrayList<>();
        Matcher matcher = H2_PATTERN.matcher(content);
        while (matcher.find() && headings.size() < 2) {
            headings.add(matcher.group(1).trim());
        }
        return headings;
    }

    private static String stripCodeFence(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("```")) {
            int firstLineEnd = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLineEnd >= 0 && lastFence > firstLineEnd) {
                return trimmed.substring(firstLineEnd + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    private record ImageSuggestionInput(String title, String contentMd, List<String> headings) {
    }
}
