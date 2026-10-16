package com.agentwriter.agent;

/**
 * 写作智能体 - 根据大纲撰写完整文章
 */
public interface WriterAgent {

    /**
     * 撰写文章（使用默认风格）
     * @param title 标题
     * @param outline 大纲
     * @return 完整文章（Markdown 格式）
     */
    String write(String title, String outline);

    /**
     * 撰写文章（指定写作风格）
     * @param title 标题
     * @param outline 大纲
     * @param styleId 写作风格 ID，为 null 则使用默认风格
     * @return 完整文章（Markdown 格式）
     */
    default String write(String title, String outline, Long styleId) {
        return write(title, outline);
    }
}
