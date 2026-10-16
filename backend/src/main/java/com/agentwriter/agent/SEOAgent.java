package com.agentwriter.agent;

/**
 * SEO 优化智能体 - 对文章进行 SEO 优化
 */
public interface SEOAgent {

    /**
     * SEO 优化
     * @param content 原文
     * @return SEO 优化后的文章
     */
    String optimize(String content);
}
