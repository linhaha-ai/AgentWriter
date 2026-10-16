package com.agentwriter.agent.impl;

import com.agentwriter.agent.Publisher;
import com.agentwriter.exception.PublisherUnavailableException;
import com.agentwriter.dto.PublishResult;
import com.agentwriter.entity.Article;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认发布器（空实现）- 在微信发布器未启用时作为兜底。
 *
 * <p>当 agentwriter.publisher.wechat.enabled=false 或未配置时激活。</p>
 */
public class DefaultPublisher implements Publisher {

    private static final Logger log = LoggerFactory.getLogger(DefaultPublisher.class);

    @Override
    public PublishResult publish(Article article) {
        log.warn("发布器不可用: articleId={}, title={}", article.getId(), article.getTitle());
        throw new PublisherUnavailableException("未配置可用的文章发布器");
    }
}
