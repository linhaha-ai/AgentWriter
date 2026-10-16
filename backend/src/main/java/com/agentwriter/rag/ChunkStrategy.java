package com.agentwriter.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 知识块切分策略 - 将 Markdown 文章按规则切分为适合 RAG 检索的知识块。
 *
 * <p>设计参考: doc/RAG_STRATEGY.md §2</p>
 *
 * <p>切分流程:<br>
 * 1. 按 ## 标题分块<br>
 * 2. 过长块（&gt; maxTokens）按自然段落切分<br>
 * 3. 过短块（&lt; minTokens）向前合并<br>
 * 4. 为每个块添加上下文窗口元信息</p>
 */
@Component
public class ChunkStrategy {

    private static final Logger log = LoggerFactory.getLogger(ChunkStrategy.class);

    /** 正则：匹配 ## 或 ### 标题行 */
    private static final Pattern HEADING_PATTERN = Pattern.compile(
            "^(#{2,3})\\s+(.+)$",
            Pattern.MULTILINE
    );

    /** 正则：匹配代码块 ```...``` */
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile(
            "```(\\w*)\\n.*?\\n```",
            Pattern.DOTALL
    );

    /** 正则：匹配空行（段落分隔符） */
    private static final Pattern PARAGRAPH_PATTERN = Pattern.compile(
            "\\n\\s*\\n"
    );

    private final int targetTokens;
    private final int maxTokens;
    private final int minTokens;
    private final int overlapTokens;
    private final int windowSize;

    public ChunkStrategy() {
        this(600, 1000, 100, 50, 1);
    }

    /**
     * @param targetTokens 目标块大小
     * @param maxTokens    最大块大小，超过则强制切分
     * @param minTokens    最小块大小，低于则合并到前一块
     * @param overlapTokens 相邻块重叠 token 数
     * @param windowSize   上下文窗口：附加前后 N 个块的标题
     */
    public ChunkStrategy(int targetTokens, int maxTokens, int minTokens,
                         int overlapTokens, int windowSize) {
        this.targetTokens = targetTokens;
        this.maxTokens = maxTokens;
        this.minTokens = minTokens;
        this.overlapTokens = overlapTokens;
        this.windowSize = windowSize;
    }

    /**
     * 将 Markdown 全文切分为有序的知识块。
     *
     * @param articleTitle 文章标题
     * @param markdown     文章 Markdown 全文
     * @return 切分后的知识块列表
     */
    public List<Chunk> split(String articleTitle, String markdown) {
        if (markdown == null || markdown.isBlank()) {
            log.debug("跳过切分: 内容为空, title={}", articleTitle);
            return List.of();
        }
        log.debug("开始切分文章: title={}, length={}", articleTitle, markdown.length());

        // 步骤 1：按 ##/### 标题分块
        List<Chunk> chunks = splitByHeadings(articleTitle, markdown);
        log.debug("步骤 1 - 按标题分块: {} 块", chunks.size());

        // 步骤 2：过长块按段落切分
        chunks = splitOversized(chunks);
        log.debug("步骤 2 - 过长块切分: {} 块", chunks.size());

        // 步骤 3：过短块向前合并
        chunks = mergeUndersized(chunks);
        log.debug("步骤 3 - 过短块合并: {} 块", chunks.size());

        // 步骤 4：添加上下文窗口
        chunks = addContextWindow(chunks);
        log.debug("步骤 4 - 已添加上下文窗口");

        return chunks;
    }

    // ========== 步骤 1：按标题分块 ==========

    private List<Chunk> splitByHeadings(String articleTitle, String markdown) {
        List<Chunk> chunks = new ArrayList<>();
        Matcher matcher = HEADING_PATTERN.matcher(markdown);

        int lastEnd = 0;
        String lastTitle = articleTitle;
        int lastLevel = 1;

        while (matcher.find()) {
            // 前一段内容（上一个标题到当前标题之间）
            if (matcher.start() > lastEnd) {
                String sectionContent = markdown.substring(lastEnd, matcher.start()).trim();
                if (!sectionContent.isEmpty()) {
                    chunks.add(new Chunk(lastTitle, lastLevel, sectionContent));
                }
            }

            lastTitle = matcher.group(2).trim();
            lastLevel = matcher.group(1).length(); // ## → 2, ### → 3
            lastEnd = matcher.end();
        }

        // 最后一个标题后的剩余内容
        if (lastEnd < markdown.length()) {
            String remaining = markdown.substring(lastEnd).trim();
            if (!remaining.isEmpty()) {
                chunks.add(new Chunk(lastTitle, lastLevel, remaining));
            }
        }

        // 如果一个块都没切出来（没有 ## 标题），将全文作为一个块
        if (chunks.isEmpty() && !markdown.isBlank()) {
            chunks.add(new Chunk(articleTitle, 1, markdown.trim()));
        }

        // 过滤掉太短的无效块（仅标题、分隔线等）
        chunks.removeIf(c -> estimateTokens(c.content()) < 10);

        return chunks;
    }

    // ========== 步骤 2：过长块切分 ==========

    private List<Chunk> splitOversized(List<Chunk> chunks) {
        List<Chunk> result = new ArrayList<>();
        for (Chunk chunk : chunks) {
            if (estimateTokens(chunk.content()) > maxTokens) {
                result.addAll(splitByParagraphs(chunk));
            } else {
                result.add(chunk);
            }
        }
        return result;
    }

    private List<Chunk> splitByParagraphs(Chunk chunk) {
        List<Chunk> result = new ArrayList<>();
        String[] paragraphs = PARAGRAPH_PATTERN.split(chunk.content());
        StringBuilder current = new StringBuilder();
        int currentTokens = 0;

        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;

            int paraTokens = estimateTokens(trimmed);

            if (currentTokens + paraTokens > targetTokens && currentTokens > 0) {
                // 当前积累的段落达到目标大小，输出一个块
                result.add(new Chunk(chunk.title(), chunk.level(), current.toString().trim()));
                // 重叠：保留最后 overlapTokens 的内容
                String overlapText = extractOverlap(current.toString(), overlapTokens);
                current = new StringBuilder(overlapText);
                currentTokens = estimateTokens(overlapText);
            }

            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(trimmed);
            currentTokens += paraTokens;
        }

        // 最后一段
        if (currentTokens > 0) {
            result.add(new Chunk(chunk.title(), chunk.level(), current.toString().trim()));
        }

        return result;
    }

    // ========== 步骤 3：过短块合并 ==========

    private List<Chunk> mergeUndersized(List<Chunk> chunks) {
        if (chunks.isEmpty()) return chunks;

        List<Chunk> result = new ArrayList<>();
        Chunk previous = chunks.get(0);

        for (int i = 1; i < chunks.size(); i++) {
            Chunk current = chunks.get(i);
            if (estimateTokens(previous.content()) < minTokens) {
                // 合并到前一块
                previous = new Chunk(
                        previous.title(),
                        previous.level(),
                        previous.content() + "\n\n" + current.content()
                );
            } else {
                result.add(previous);
                previous = current;
            }
        }
        result.add(previous);

        return result;
    }

    // ========== 步骤 4：上下文窗口 ==========

    private List<Chunk> addContextWindow(List<Chunk> chunks) {
        List<Chunk> result = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Chunk current = chunks.get(i);
            StringBuilder contextInfo = new StringBuilder();

            // 前 N 块标题
            for (int j = Math.max(0, i - windowSize); j < i; j++) {
                contextInfo.append("上一节: ").append(chunks.get(j).title()).append("\n");
            }
            // 当前块标题
            contextInfo.append("当前: ").append(current.title()).append("\n");
            // 后 N 块标题
            for (int j = i + 1; j < Math.min(chunks.size(), i + windowSize + 1); j++) {
                contextInfo.append("下一节: ").append(chunks.get(j).title()).append("\n");
            }

            String enriched = "【上下文】\n" + contextInfo.toString().trim() + "\n\n"
                    + current.content();

            result.add(new Chunk(current.title(), current.level(), enriched));
        }
        return result;
    }

    // ========== 辅助方法 ==========

    /**
     * 估算文本的 token 数量。
     * 中文: 1 汉字 ≈ 2 tokens<br>
     * 英文: 1 单词 ≈ 1.3 tokens<br>
     * 代码行: 1 行 ≈ 3 tokens
     */
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;

        int chineseChars = 0;
        int englishWords = 0;
        int codeLines = 0;

        boolean inCodeBlock = false;
        String[] lines = text.split("\n");

        for (String line : lines) {
            if (line.trim().startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                if (inCodeBlock) codeLines++; // ``` 行本身
                continue;
            }
            if (inCodeBlock) {
                codeLines++;
                continue;
            }

            // 非代码行
            for (char c : line.toCharArray()) {
                if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                    chineseChars++;
                }
            }

            // 简单英文单词计数
            String[] words = line.split("\\s+");
            for (String word : words) {
                if (word.matches("[a-zA-Z0-9.,;:!?()\\[\\]{}]+")) {
                    englishWords++;
                }
            }
        }

        return chineseChars * 2 + (int) (englishWords * 1.3) + codeLines * 3;
    }

    /** 提取文本末尾指定 token 量的内容用于重叠 */
    private String extractOverlap(String text, int overlapTokens) {
        if (text == null || text.isEmpty()) return "";
        int estimated = estimateTokens(text);
        if (estimated <= overlapTokens) return text;

        // 近似：取末尾大约 overlapTokens 比例的内容
        double ratio = (double) overlapTokens / estimated;
        int overlapChars = (int) (text.length() * ratio);
        return text.substring(Math.max(0, text.length() - overlapChars));
    }

    // ========== 内部记录类型 ==========

    /**
     * 知识块（切分结果，未持久化）
     */
    public record Chunk(
            String title,
            int level,
            String content
    ) {
        public Chunk {
            assert title != null;
            assert content != null;
        }
    }
}
