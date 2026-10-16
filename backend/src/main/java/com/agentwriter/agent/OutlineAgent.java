package com.agentwriter.agent;

/**
 * 大纲智能体 - 根据调研结果生成文章大纲
 */
public interface OutlineAgent {

    /**
     * 生成文章大纲
     * @param title 文章标题
     * @param research 调研结果
     * @return 大纲（Markdown 格式）
     */
    String generateOutline(String title, String research);
}
