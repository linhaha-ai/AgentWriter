package com.agentwriter.dto;

import com.agentwriter.entity.Topic;
import java.time.LocalDateTime;

public record TopicResponse(
        Long id,
        String title,
        String source,
        Integer score,
        String status,
        String link,
        String reason,
        LocalDateTime createdAt
) {
    public static TopicResponse from(Topic topic) {
        return new TopicResponse(
                topic.getId(),
                topic.getTitle(),
                topic.getSource(),
                topic.getScore(),
                topic.getStatus(),
                topic.getLink(),
                topic.getReason(),
                topic.getCreatedAt()
        );
    }
}
