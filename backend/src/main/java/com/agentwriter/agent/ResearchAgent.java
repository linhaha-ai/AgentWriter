package com.agentwriter.agent;

/**
 * 深度调研智能体 - 对选定话题进行深度资料调研
 */
public interface ResearchAgent {

    /**
     * 对话题进行深度调研
     * @param topic 话题标题
     * @return 调研结果（Markdown 格式）
     */
    String research(String topic);
}
