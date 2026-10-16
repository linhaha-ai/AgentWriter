package com.agentwriter.service;

import com.agentwriter.agent.TopicAgent;
import com.agentwriter.dto.TopicCreateRequest;
import com.agentwriter.dto.TopicResponse;
import com.agentwriter.entity.Topic;
import com.agentwriter.exception.TopicNotFoundException;
import com.agentwriter.repository.TopicRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TopicService {

    private static final Logger log = LoggerFactory.getLogger(TopicService.class);

    private final TopicRepository topicRepository;
    private final TopicAgent topicAgent;
    private final ObjectMapper objectMapper;

    public TopicService(TopicRepository topicRepository, TopicAgent topicAgent, ObjectMapper objectMapper) {
        this.topicRepository = topicRepository;
        this.topicAgent = topicAgent;
        this.objectMapper = objectMapper;
    }

    /**
     * 手动触发话题采集：调用 TopicAgent 从各来源抓取，解析后全部保存到数据库。
     *
     * @return 新保存的话题列表
     */
    public List<TopicResponse> collectTopics() {
        log.info("手动触发话题采集...");
        String json = topicAgent.collectTrendingTopics();
        if (json == null || json.isBlank()) {
            log.warn("话题采集返回空，无数据保存");
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(json);
            List<JsonNode> items = extractTopicItems(root);

            if (items.isEmpty()) {
                log.warn("话题采集返回的数据中无有效话题项");
                return List.of();
            }

            List<TopicResponse> saved = new ArrayList<>();
            int skipped = 0;
            for (JsonNode item : items) {
                String title = item.path("title").asText("");
                if (title.isBlank()) continue;

                // 去重：标题相同则跳过
                if (topicRepository.existsByTitle(title)) {
                    skipped++;
                    continue;
                }

                String source = item.path("source").asText("manual");
                // 兼容 hotLevel / score 两个字段名
                int score = item.has("hotLevel") ? item.path("hotLevel").asInt(0) : item.path("score").asInt(0);
                String link = item.path("link").asText("");
                String reason = item.path("reason").asText("");

                var request = new TopicCreateRequest(title, source, score, link, reason);
                saved.add(create(request));
            }

            log.info("话题采集完成，保存 {} 条，跳过重复 {} 条", saved.size(), skipped);
            return saved;

        } catch (Exception e) {
            log.error("解析话题采集结果失败: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 从 JSON 根节点中提取话题数组，兼容两种格式：
     * LLM 格式：{"data": {"topics": [...]}}
     * 原始格式：{"success": true, "data": [...]}
     */
    private List<JsonNode> extractTopicItems(JsonNode root) {
        // 尝试 LLM 格式: data.topics
        JsonNode data = root.path("data");
        if (data.isObject()) {
            JsonNode topics = data.path("topics");
            if (topics.isArray() && !topics.isEmpty()) {
                List<JsonNode> result = new ArrayList<>();
                topics.forEach(result::add);
                return result;
            }
        }
        // 尝试原始格式: data = [...]
        if (data.isArray()) {
            List<JsonNode> result = new ArrayList<>();
            data.forEach(result::add);
            return result;
        }
        return List.of();
    }

    public TopicResponse create(TopicCreateRequest request) {
        var topic = new Topic();
        topic.setTitle(request.title());
        topic.setSource(request.source());
        topic.setScore(request.score() != null ? request.score() : 0);
        topic.setLink(request.link());
        topic.setReason(request.reason());
        topic.setStatus("pending");
        var saved = topicRepository.save(topic);
        return TopicResponse.from(saved);
    }

    public TopicResponse findById(Long id) {
        var topic = topicRepository.findById(id)
                .orElseThrow(() -> new TopicNotFoundException(id));
        return TopicResponse.from(topic);
    }

    public List<TopicResponse> findAll() {
        return topicRepository.findAll().stream()
                .map(TopicResponse::from)
                .toList();
    }

    public List<TopicResponse> findByStatus(String status) {
        return topicRepository.findByStatus(status).stream()
                .map(TopicResponse::from)
                .toList();
    }

    public TopicResponse selectTopic(Long id) {
        var topic = topicRepository.findById(id)
                .orElseThrow(() -> new TopicNotFoundException(id));
        topic.setStatus("selected");
        var saved = topicRepository.save(topic);
        return TopicResponse.from(saved);
    }

    Topic findEntityById(Long id) {
        return topicRepository.findById(id)
                .orElseThrow(() -> new TopicNotFoundException(id));
    }

    public Topic saveEntity(Topic topic) {
        return topicRepository.save(topic);
    }
}
