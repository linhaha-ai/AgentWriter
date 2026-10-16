package com.agentwriter.agent;

import com.agentwriter.dto.PublishResult;
import com.agentwriter.entity.Article;

/**
 * 发布器 - 将完成文章发布到目标平台
 */
public interface Publisher {

    /**
     * 发布文章
     * @param article 已完成的文章
     * @return 发布结果（平台、链接、时间等）
     */
    PublishResult publish(Article article);
}
