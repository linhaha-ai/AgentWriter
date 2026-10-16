package com.agentwriter.agent;

/**
 * 选题智能体 - 收集和分析热度话题
 */
public interface TopicAgent {

    /**
     * 从各渠道收集热度话题
     * @return 话题列表 JSON
     */
    String collectTrendingTopics();
}
