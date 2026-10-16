package com.agentwriter.agent;

/**
 * 审校智能体 - 审校文章质量、修正错误
 */
public interface ReviewAgent {

    /**
     * 审校文章
     * @param title 标题
     * @param content 文章正文
     * @return 审校后的文章
     */
    String review(String title, String content);
}
