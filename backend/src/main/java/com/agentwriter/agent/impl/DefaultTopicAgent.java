package com.agentwriter.agent.impl;

import com.agentwriter.agent.TopicAgent;
import com.agentwriter.service.LLMService;
import com.agentwriter.service.PromptService;
import org.jsoup.Jsoup;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
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

@Component
public class DefaultTopicAgent implements TopicAgent {

    private static final Logger log = LoggerFactory.getLogger(DefaultTopicAgent.class);
    private static final String DEFAULT_GITHUB_TRENDING_URL = "https://github.com/trending?since=weekly";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

    private final LLMService llmService;
    private final PromptService promptService;

    @Value("${agentwriter.topic.rss-feeds:}")
    private String[] rssFeeds = new String[0];

    @Value("${agentwriter.topic.use-llm:true}")
    private boolean useLlm = true;

    @Value("${agentwriter.topic.github-trending-url:" + DEFAULT_GITHUB_TRENDING_URL + "}")
    private String githubTrendingUrl = DEFAULT_GITHUB_TRENDING_URL;

    @Value("${agentwriter.topic.proxy-host:}")
    private String proxyHost = "";

    @Value("${agentwriter.topic.proxy-port:0}")
    private int proxyPort;

    public DefaultTopicAgent(LLMService llmService, PromptService promptService) {
        this.llmService = llmService;
        this.promptService = promptService;
    }

    @Override
    public String collectTrendingTopics() {
        log.info("开始收集热度话题");

        // 步骤1：从网络来源抓取热门话题
        List<Map<String, Object>> topics = scrapeAllSources();

        // 步骤2：若启用 use-llm 且有数据，用 LLM 精炼
        if (useLlm && !topics.isEmpty()) {
            try {
                String systemPrompt = promptService.loadPrompt("topic");
                String scrapedJson = buildTopicsJson(topics);
                log.info("使用 LLM 精炼 {} 个话题", topics.size());
                return llmService.generate(systemPrompt, scrapedJson);
            } catch (Exception e) {
                log.warn("LLM 话题精炼失败，返回原始抓取数据: {}", e.getMessage());
            }
        }

        // 步骤3：直接返回抓取的数据
        if (!topics.isEmpty()) {
            return buildTopicsJson(topics);
        }

        // 步骤4：回退到 LLM 兜底
        try {
            String systemPrompt = promptService.loadPrompt("topic");
            String userMessage = "{\"sources\": [\"github\", \"openhot\", \"rss\", \"dailyhot\"]}";
            log.warn("未抓取到可用话题，降级为 LLM 生成");
            return llmService.generate(systemPrompt, userMessage);
        } catch (Exception e) {
            log.error("所有话题来源（含 LLM）均失败: {}", e.getMessage());
            return "";
        }
    }

    private List<Map<String, Object>> scrapeAllSources() {
        List<Map<String, Object>> topics = new ArrayList<>();

        // GitHub 趋势
        try {
            topics.addAll(scrapeGitHubTrending());
            log.info("GitHub Trending 返回 {} 个话题", topics.size());
        } catch (Exception e) {
            log.warn("GitHub Trending 抓取失败: {}", e.getMessage());
        }

        // RSS 订阅源
        if (rssFeeds.length > 0) {
            try {
                topics.addAll(scrapeRssFeeds());
                log.info("RSS 订阅源返回 {} 个话题", topics.size() - countGithubTopics(topics));
            } catch (Exception e) {
                log.warn("RSS 抓取失败: {}", e.getMessage());
            }
        }

        return topics;
    }

    private List<Map<String, Object>> scrapeGitHubTrending() {
        List<Map<String, Object>> topics = new ArrayList<>();

        try {
            Connection connection = Jsoup.connect(githubTrendingUrl)
                    .userAgent(USER_AGENT)
                    .timeout(15000);
            if (hasProxy()) {
                connection.proxy(proxyHost, proxyPort);
            }
            Document doc = connection.get();

            Elements articles = doc.select("article.Box-row");
            for (Element article : articles) {
                Element h2 = article.selectFirst("h2 a");
                if (h2 == null) continue;
                String fullName = h2.text().replaceAll("\\s+", "").trim();
                if (fullName.isEmpty()) continue;

                Element descEl = article.selectFirst("p");
                String description = descEl != null ? descEl.text().trim() : "";

                Element starsEl = article.selectFirst(".d-flex > a.Link--muted");
                String starsText = starsEl != null ? starsEl.text().trim() : "0";
                int stars = parseStars(starsText);

                Element langEl = article.selectFirst("[itemprop='programmingLanguage']");
                String language = langEl != null ? langEl.text().trim() : "Unknown";

                int hotLevel = Math.min(100, Math.max(1,
                        (int) (Math.log(Math.max(stars, 1)) / Math.log(50000) * 100)));

                Map<String, Object> topic = new LinkedHashMap<>();
                topic.put("title", fullName);
                topic.put("source", "github");
                topic.put("link", "https://github.com/" + fullName);
                topic.put("hotLevel", hotLevel);
                topic.put("reason", description.isEmpty() ? "Trending on GitHub" : description);
                topic.put("language", language);
                topics.add(topic);
            }
        } catch (Exception e) {
            log.warn("GitHub Trending 抓取失败: {}", e.getMessage());
        }

        return topics;
    }

    private List<Map<String, Object>> scrapeRssFeeds() {
        List<Map<String, Object>> topics = new ArrayList<>();
        HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL);
        if (hasProxy()) {
            clientBuilder.proxy(ProxySelector.of(new InetSocketAddress(proxyHost, proxyPort)));
        }
        HttpClient client = clientBuilder.build();

        for (String feedUrl : rssFeeds) {
            if (feedUrl.isBlank()) continue;
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(feedUrl.trim()))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request,
                        HttpResponse.BodyHandlers.ofString());

                Document xml = Jsoup.parse(response.body(), "", org.jsoup.parser.Parser.xmlParser());
                Elements items = xml.select("item, entry");

                for (Element item : items) {
                    String title = item.select("title").text().trim();
                    if (title.isEmpty()) continue;

                    String link = item.select("link").text().trim();
                    if (link.isEmpty()) {
                        Element linkEl = item.selectFirst("link[href]");
                        link = linkEl != null ? linkEl.attr("href") : "";
                    }

                    String desc = item.select("description").text().trim();
                    if (desc.isEmpty()) {
                        desc = item.select("summary").text().trim();
                    }

                    int hotLevel = Math.max(1, 100 - topics.size() * 5);

                    Map<String, Object> topic = new LinkedHashMap<>();
                    topic.put("title", title);
                    topic.put("source", "rss");
                    topic.put("link", link.isEmpty() ? feedUrl.trim() : link);
                    topic.put("hotLevel", hotLevel);
                    topic.put("reason", desc.length() > 200 ? desc.substring(0, 200) + "..." : desc);
                    topics.add(topic);
                }
                log.info("RSS 订阅源 {} 返回 {} 条内容", feedUrl.trim(), items.size());
            } catch (Exception e) {
                log.warn("RSS 订阅源 {} 抓取失败: {}", feedUrl.trim(), e.getMessage());
            }
        }

        return topics;
    }

    private String buildTopicsJson(List<Map<String, Object>> topics) {
        StringBuilder json = new StringBuilder();
        json.append("{\"success\":true,\"data\":[");
        for (int i = 0; i < topics.size(); i++) {
            if (i > 0) json.append(",");
            Map<String, Object> t = topics.get(i);
            json.append("{");
            appendJsonField(json, "title", String.valueOf(t.get("title")));
            json.append(",");
            appendJsonField(json, "source", String.valueOf(t.get("source")));
            json.append(",");
            appendJsonField(json, "link", String.valueOf(t.get("link")));
            json.append(",");
            json.append("\"hotLevel\":").append(t.get("hotLevel")).append(",");
            appendJsonField(json, "reason", String.valueOf(t.get("reason")));
            if (t.containsKey("language")) {
                json.append(",");
                appendJsonField(json, "language", String.valueOf(t.get("language")));
            }
            json.append("}");
        }
        json.append("]}");
        return json.toString();
    }

    private void appendJsonField(StringBuilder sb, String key, String value) {
        sb.append("\"").append(key).append("\":\"")
                .append(value.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                        .replace("\t", "\\t"))
                .append("\"");
    }

    private int parseStars(String text) {
        try {
            String cleaned = text.replaceAll("[^0-9kK.]", "").toLowerCase();
            if (cleaned.endsWith("k")) {
                return (int) (Double.parseDouble(cleaned.replace("k", "")) * 1000);
            }
            if (cleaned.isEmpty()) return 0;
            return Integer.parseInt(cleaned);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int countGithubTopics(List<Map<String, Object>> topics) {
        return (int) topics.stream().filter(t -> "github".equals(t.get("source"))).count();
    }

    private boolean hasProxy() {
        return !proxyHost.isBlank() && proxyPort > 0;
    }
}
