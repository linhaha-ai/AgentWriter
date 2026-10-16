package com.agentwriter.service;

import com.agentwriter.dto.ArticleImageGenerateRequest;
import com.agentwriter.entity.Article;
import com.agentwriter.entity.ArticleImage;
import com.agentwriter.entity.ArticleImageRole;
import com.agentwriter.entity.ArticleImageStatus;
import com.agentwriter.repository.ArticleImageRepository;
import com.agentwriter.repository.ArticleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleImageServiceTest {

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private ArticleImageRepository articleImageRepository;

    @Mock
    private ImageSuggestionService suggestionService;

    @Mock
    private ComfyUiClient comfyUiClient;

    @Mock
    private ArticleImageJobService jobService;

    @Test
    void queuesValidatedImageTaskWithoutChangingArticleStatus() {
        Article article = article();
        when(articleRepository.findById(1L)).thenReturn(Optional.of(article));
        when(articleImageRepository.save(any(ArticleImage.class))).thenAnswer(invocation -> {
            ArticleImage image = invocation.getArgument(0);
            image.setId(9L);
            return image;
        });
        doNothing().when(comfyUiClient).validateConfiguration();
        doNothing().when(jobService).generateAsync(9L);
        var service = service();

        service.queue(1L, new ArticleImageGenerateRequest(
                ArticleImageRole.INLINE, "架构设计", "technical illustration", "架构图"
        ));

        ArgumentCaptor<ArticleImage> captor = ArgumentCaptor.forClass(ArticleImage.class);
        verify(articleImageRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ArticleImageStatus.QUEUED);
        assertThat(article.getStatus()).isEqualTo("generated");
        verify(jobService).generateAsync(9L);
    }

    @Test
    void insertsReadyInlineImageAfterMatchedHeadingAndClearsHtmlCache() {
        Article article = article();
        article.setContentHtml("<p>stale</p>");
        ArticleImage image = new ArticleImage();
        image.setId(7L);
        image.setArticle(article);
        image.setRole(ArticleImageRole.INLINE);
        image.setStatus(ArticleImageStatus.READY);
        image.setHeadingAnchor("架构设计");
        image.setAltText("架构图");
        when(articleRepository.findById(1L)).thenReturn(Optional.of(article));
        when(articleImageRepository.findByIdAndArticleId(7L, 1L)).thenReturn(Optional.of(image));
        when(articleImageRepository.save(image)).thenReturn(image);
        when(articleRepository.save(article)).thenReturn(article);

        service().insert(1L, 7L);

        assertThat(article.getContentMd())
                .contains("## 架构设计\n\n![架构图](/api/article-images/7/content)");
        assertThat(article.getContentHtml()).isNull();
        assertThat(image.isInserted()).isTrue();
    }

    private ArticleImageService service() {
        return new ArticleImageService(
                articleRepository, articleImageRepository, suggestionService, comfyUiClient, jobService
        );
    }

    private static Article article() {
        Article article = new Article();
        article.setId(1L);
        article.setTitle("文章标题");
        article.setStatus("generated");
        article.setContentMd("# 标题\n\n## 架构设计\n正文\n\n## 实践步骤\n正文");
        return article;
    }
}
