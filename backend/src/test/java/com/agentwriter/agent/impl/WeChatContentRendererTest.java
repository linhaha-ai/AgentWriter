package com.agentwriter.agent.impl;

import com.agentwriter.entity.Article;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WeChatContentRendererTest {

    @Test
    void rendersOnlyResolvedApplicationImageUrlsAsImages() {
        Article article = new Article();
        article.setContentMd("## 架构\n\n![架构图](/api/article-images/2/content)");

        String rendered = WeChatContentRenderer.render(article, source ->
                "/api/article-images/2/content".equals(source) ? "https://mmbiz.qpic.cn/image" : source
        );

        assertThat(rendered).contains("<img src=\"https://mmbiz.qpic.cn/image\" alt=\"架构图\">");
        assertThat(WeChatContentRenderer.render(article)).doesNotContain("<img");
    }
}
