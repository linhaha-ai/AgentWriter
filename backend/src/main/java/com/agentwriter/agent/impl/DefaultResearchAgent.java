package com.agentwriter.agent.impl;

import com.agentwriter.agent.ResearchAgent;
import com.agentwriter.service.LLMService;
import com.agentwriter.service.PromptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DefaultResearchAgent implements ResearchAgent {

    private static final Logger log = LoggerFactory.getLogger(DefaultResearchAgent.class);
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

    private final LLMService llmService;
    private final PromptService promptService;

    @Value("${agentwriter.research.search-url:https://s.jina.ai/{topic}}")
    private String searchUrlTemplate;

    @Value("${agentwriter.research.connect-timeout-seconds:15}")
    private int connectTimeoutSeconds;

    @Value("${agentwriter.research.response-timeout-seconds:45}")
    private int responseTimeoutSeconds;

    @Value("${agentwriter.research.max-sources:5}")
    private int maxSources;

    @Value("${agentwriter.research.use-llm:true}")
    private boolean useLlm;

    @Value("${agentwriter.topic.proxy-host:}")
    private String proxyHost = "";

    @Value("${agentwriter.topic.proxy-port:0}")
    private int proxyPort;

    public DefaultResearchAgent(LLMService llmService, PromptService promptService) {
        this.llmService = llmService;
        this.promptService = promptService;
    }

    @Override
    public String research(String topic) {
        log.info("开始调研话题: topic={}", topic);

        // 步骤1：通过 Jina Reader 获取真实网页内容
        List<SourceResult> sources = fetchFromWeb(topic);

        // 步骤2：可选地使用 LLM 综合生成
        if (useLlm && !sources.isEmpty()) {
            try {
                String systemPrompt = promptService.loadPrompt("research");
                String scrapedJson = buildResearchJson("Raw research data for: " + topic, sources);
                log.info("使用 LLM 综合 {} 个来源", sources.size());
                return llmService.generate(systemPrompt, scrapedJson);
            } catch (Exception e) {
                log.warn("LLM 综合失败，返回原始数据: {}", e.getMessage());
            }
        }

        // 步骤3：返回原始抓取数据
        if (!sources.isEmpty()) {
            String summary = "Research results for: " + topic + ". Found " + sources.size() + " sources.";
            return buildResearchJson(summary, sources);
        }

        // 步骤4：回退到 LLM 直接生成
        try {
            String systemPrompt = promptService.loadPrompt("research");
            String userMessage = "{\"topic\": \"" + escapeJson(topic) + "\"}";
            log.warn("未获取到网页数据，降级为 LLM 直接生成");
            return llmService.generate(systemPrompt, userMessage);
        } catch (Exception e) {
            log.error("所有调研来源（含 LLM）均失败: {}", e.getMessage());
            return "{\"success\":true,\"data\":{\"summary\":\"Research failed for: " + escapeJson(topic) + "\",\"sources\":[]}}";
        }
    }

    private List<SourceResult> fetchFromWeb(String topic) {
        List<SourceResult> sources = new ArrayList<>();

        // 使用 Jina Reader 搜索
        try {
            String encodedTopic = URLEncoder.encode(topic, StandardCharsets.UTF_8);
            String searchUrl = searchUrlTemplate.replace("{topic}", encodedTopic);
            log.info("使用 Jina Reader 搜索: {}", searchUrl);

            HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                    .followRedirects(HttpClient.Redirect.NORMAL);
            if (hasProxy()) {
                clientBuilder.proxy(ProxySelector.of(new InetSocketAddress(proxyHost, proxyPort)));
            }
            HttpClient client = clientBuilder.build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(searchUrl))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/plain, text/markdown, text/html")
                    .timeout(Duration.ofSeconds(responseTimeoutSeconds))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            String body = response.body();
            if (body != null && !body.isBlank()) {
                sources.addAll(parseSearchResults(body, topic));
                log.info("Jina Reader 为话题 '{}' 返回 {} 个来源", topic, sources.size());
            }
        } catch (java.net.http.HttpConnectTimeoutException e) {
            log.warn("Jina Reader 连接超时，已降级为 LLM 调研。请检查代理或网络连接。");
        } catch (java.net.http.HttpTimeoutException e) {
            log.warn("Jina Reader 响应超时，已降级为 LLM 调研。");
        } catch (Exception e) {
            log.warn("Jina Reader 搜索失败，已降级为 LLM 调研: {}", e.getMessage());
        }

        // 限制来源数量
        if (sources.size() > maxSources) {
            sources = sources.subList(0, maxSources);
        }

        return sources;
    }

    private List<SourceResult> parseSearchResults(String content, String topic) {
        List<SourceResult> results = new ArrayList<>();

        // Jina Reader 通常返回带链接的 Markdown：[标题](网址)
        // 按 Markdown 链接模式或换行分割
        Pattern linkPattern = Pattern.compile("\\[(.*?)\\]\\((https?://[^\\s)]+)\\)");
        Matcher matcher = linkPattern.matcher(content);

        Set<String> seenUrls = new HashSet<>();

        // 收集所有 Markdown 链接作为来源
        while (matcher.find()) {
            String title = matcher.group(1).trim();
            String url = matcher.group(2).trim();

            if (title.isEmpty() || seenUrls.contains(url)) continue;
            seenUrls.add(url);

            // 提取链接周围的上下文文本作为片段
            String snippet = extractSnippet(content, matcher.start(), 100);

            results.add(new SourceResult(title, url, snippet));
        }

        // 如果没有 Markdown 链接，则将整个内容视为一个来源
        if (results.isEmpty() && content.length() > 50) {
            results.add(new SourceResult(
                    "Search results for: " + topic,
                    "https://google.com/search?q=" + URLEncoder.encode(topic, StandardCharsets.UTF_8),
                    content.substring(0, Math.min(content.length(), 500))
            ));
        }

        return results;
    }

    private String extractSnippet(String content, int position, int maxLen) {
        int start = Math.max(0, position - maxLen / 2);
        int end = Math.min(content.length(), position + maxLen / 2);

        // 尝试在单词边界处截断
        while (start > 0 && content.charAt(start) != ' ' && content.charAt(start) != '\n') {
            start++;
        }
        while (end < content.length() && content.charAt(end) != ' ' && content.charAt(end) != '\n') {
            end--;
        }

        String snippet = content.substring(start, end).trim();
        if (snippet.length() > maxLen) {
            snippet = snippet.substring(0, maxLen) + "...";
        }
        return snippet;
    }

    private String buildResearchJson(String summary, List<SourceResult> sources) {
        StringBuilder json = new StringBuilder();
        json.append("{\"success\":true,\"data\":{");
        appendJsonField(json, "summary", summary);
        json.append(",\"sources\":[");
        for (int i = 0; i < sources.size(); i++) {
            if (i > 0) json.append(",");
            json.append("{");
            appendJsonField(json, "title", sources.get(i).title());
            json.append(",");
            appendJsonField(json, "url", sources.get(i).url());
            json.append(",");
            appendJsonField(json, "content", sources.get(i).content());
            json.append("}");
        }
        json.append("]}}");
        return json.toString();
    }

    private void appendJsonField(StringBuilder sb, String key, String value) {
        sb.append("\"").append(key).append("\":\"")
                .append(escapeJson(value))
                .append("\"");
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private boolean hasProxy() {
        return !proxyHost.isBlank() && proxyPort > 0;
    }

    private record SourceResult(String title, String url, String content) {}
}
