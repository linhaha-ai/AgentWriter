package com.agentwriter.workflow;

import com.agentwriter.common.RetryPolicy;
import com.agentwriter.dto.ArticleResponse;
import com.agentwriter.dto.WorkflowRequest;
import com.agentwriter.entity.Article;
import com.agentwriter.repository.ExecutionLogRepository;
import com.agentwriter.service.ArticleService;
import com.agentwriter.service.RAGService;
import com.agentwriter.service.TopicService;
import com.agentwriter.workflow.step.OutlineStep;
import com.agentwriter.workflow.step.ResearchStep;
import com.agentwriter.workflow.step.ReviewStep;
import com.agentwriter.workflow.step.SEOStep;
import com.agentwriter.workflow.step.WriterStep;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowEngineBaselineCharacterizationTest {

    @Test
    void currentlyExecutesInjectedStepsInSuppliedOrder() throws Exception {
        var articleService = mock(ArticleService.class);
        var topicService = mock(TopicService.class);
        var logRepository = mock(ExecutionLogRepository.class);
        var writer = mock(WriterStep.class);
        var research = mock(ResearchStep.class);
        var outline = mock(OutlineStep.class);
        var review = mock(ReviewStep.class);
        var seo = mock(SEOStep.class);
        var observed = new ArrayList<String>();

        when(writer.name()).thenReturn("writer");
        when(research.name()).thenReturn("research");
        doAnswer(invocation -> {
            observed.add("writer");
            return null;
        }).when(writer).execute(any());
        doAnswer(invocation -> {
            observed.add("research");
            return null;
        }).when(research).execute(any());

        var saved = new Article();
        saved.setId(7L);
        saved.setTitle("Baseline");
        when(articleService.saveEntity(any())).thenReturn(saved);
        when(articleService.completeGeneration(any(), any())).thenAnswer(invocation -> {
            saved.setStatus("generated");
            saved.setContentMd(invocation.getArgument(1));
            return ArticleResponse.from(saved);
        });
        when(logRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var engine = new WorkflowEngine(
                articleService,
                mock(com.agentwriter.repository.ArticleRepository.class),
                topicService,
                logRepository,
                new RetryPolicy(),
                mock(RAGService.class),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                research,
                outline,
                writer,
                review,
                seo);

        engine.start(new WorkflowRequest(1L, "Baseline", null));

        assertThat(observed).containsExactly("research", "writer");
    }
}
