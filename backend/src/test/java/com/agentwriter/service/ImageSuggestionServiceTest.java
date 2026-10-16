package com.agentwriter.service;

import com.agentwriter.entity.Article;
import com.agentwriter.entity.ArticleImageRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageSuggestionServiceTest {

    @Mock
    private LLMService llmService;

    @Mock
    private PromptService promptService;

    @Test
    void fallsBackToDeterministicCoverAndInlineSuggestionsWhenModelResponseIsInvalid() {
        when(promptService.loadPrompt("image")).thenReturn("prompt");
        when(llmService.generate(anyString(), anyString())).thenReturn("not json");
        Article article = article();

        var service = new ImageSuggestionService(llmService, promptService, new ObjectMapper());
        var result = service.suggest(article);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).role()).isEqualTo(ArticleImageRole.COVER);
        assertThat(result.get(1).headingAnchor()).isEqualTo("架构设计");
        assertThat(result.get(2).headingAnchor()).isEqualTo("实践步骤");
    }

    private static Article article() {
        Article article = new Article();
        article.setTitle("Spring Boot 图片生成");
        article.setContentMd("# 标题\n\n## 架构设计\n内容\n\n## 实践步骤\n内容");
        return article;
    }
}
