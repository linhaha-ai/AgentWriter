package com.agentwriter.dto;

import com.agentwriter.entity.Article;

public record WorkflowResponse(
        Long articleId,
        String status,
        String message
) {
    public static WorkflowResponse from(Article article) {
        return new WorkflowResponse(
                article.getId(),
                article.getStatus(),
                "工作流已启动"
        );
    }
}
