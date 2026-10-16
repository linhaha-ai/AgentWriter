package com.agentwriter.service;

import com.agentwriter.entity.KnowledgeChunk;
import com.agentwriter.exception.EmbeddingUnavailableException;
import com.agentwriter.rag.ChunkStrategy;
import com.agentwriter.repository.KnowledgeChunkRepository;
import com.agentwriter.service.impl.DefaultRAGService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultRAGServiceTest {

    @Mock
    private KnowledgeChunkRepository chunkRepository;
    @Mock
    private ChunkStrategy chunkStrategy;
    @Mock
    private EmbeddingService embeddingService;

    private DefaultRAGService ragService;

    @BeforeEach
    void setUp() {
        ragService = new DefaultRAGService(chunkRepository, chunkStrategy, embeddingService);
    }

    @Test
    void retrieve_shouldReturnEmptyForBlankQuery() {
        var result = ragService.retrieve("", 5);

        assertThat(result).contains("查询内容为空");
    }

    @Test
    void retrieve_shouldReturnNullForNullQuery() {
        var result = ragService.retrieve(null, 5);

        assertThat(result).contains("查询内容为空");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 101})
    void retrieve_shouldRejectInvalidTopK(int topK) {
        assertThatThrownBy(() -> ragService.retrieve("query", topK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 到 100");

        verifyNoInteractions(chunkRepository, embeddingService);
    }

    @Test
    void storeArticle_shouldReturnZeroForNullContent() {
        var result = ragService.storeArticle(1L, null);

        assertThat(result).isZero();
    }

    @Test
    void storeArticle_shouldReturnZeroForBlankContent() {
        var result = ragService.storeArticle(1L, "   ");

        assertThat(result).isZero();
    }

    @Test
    void storeArticle_shouldDeleteExistingBeforeStore() {
        when(chunkStrategy.split(anyString(), anyString())).thenReturn(List.of());
        when(chunkRepository.findByArticleId(1L)).thenReturn(List.of());

        ragService.storeArticle(1L, "Some content");

        verify(chunkRepository).findByArticleId(1L);
    }

    @Test
    void storeArticle_shouldSkipVectorForFailedEmbedding() {
        var chunk = new ChunkStrategy.Chunk("title", 1, "content");
        when(chunkStrategy.split(anyString(), anyString())).thenReturn(List.of(chunk));
        when(chunkRepository.findByArticleId(anyLong())).thenReturn(List.of());
        when(embeddingService.embed(anyString()))
                .thenThrow(new EmbeddingUnavailableException("disabled"));

        ragService.storeArticle(1L, "article content");

        ArgumentCaptor<KnowledgeChunk> captor = ArgumentCaptor.forClass(KnowledgeChunk.class);
        verify(chunkRepository).save(captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("content");
        assertThat(captor.getValue().getEmbedding()).isNull();
    }

    @Test
    void deleteByArticleId_shouldRemoveExistingChunks() {
        var existing = new KnowledgeChunk();
        existing.setId(1L);
        when(chunkRepository.findByArticleId(1L)).thenReturn(List.of(existing));

        ragService.deleteByArticleId(1L);

        verify(chunkRepository).deleteAll(List.of(existing));
    }

    @Test
    void deleteByArticleId_shouldDoNothingForNoChunks() {
        when(chunkRepository.findByArticleId(1L)).thenReturn(List.of());

        ragService.deleteByArticleId(1L);

        verify(chunkRepository, never()).deleteAll(any());
    }

    @Test
    void findByArticleId_shouldReturnChunks() {
        var chunk = new KnowledgeChunk();
        chunk.setId(1L);
        when(chunkRepository.findByArticleId(1L)).thenReturn(List.of(chunk));

        var result = ragService.findByArticleId(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
    }

    @Test
    void storeArticle_shouldCountSavedChunks() {
        var chunk1 = new ChunkStrategy.Chunk("title1", 1, "content1");
        var chunk2 = new ChunkStrategy.Chunk("title2", 2, "content2");
        when(chunkStrategy.split(anyString(), anyString())).thenReturn(List.of(chunk1, chunk2));
        when(chunkRepository.findByArticleId(anyLong())).thenReturn(List.of());
        when(embeddingService.embed(anyString())).thenReturn(new float[1536]);

        var result = ragService.storeArticle(1L, "article content");

        assertThat(result).isEqualTo(2);
        ArgumentCaptor<KnowledgeChunk> captor = ArgumentCaptor.forClass(KnowledgeChunk.class);
        verify(chunkRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .allSatisfy(saved -> assertThat(saved.getEmbedding()).hasSize(1536));
    }

    @ParameterizedTest
    @ValueSource(ints = {1535, 2048})
    void storeArticle_shouldRejectNonCanonicalVectorBeforeRepositorySave(int dimensions) {
        var chunk = new ChunkStrategy.Chunk("title", 1, "content");
        when(chunkStrategy.split(anyString(), anyString())).thenReturn(List.of(chunk));
        when(chunkRepository.findByArticleId(anyLong())).thenReturn(List.of());
        when(embeddingService.embed(anyString())).thenReturn(new float[dimensions]);

        assertThatThrownBy(() -> ragService.storeArticle(1L, "article content"))
                .hasMessageContaining("1536")
                .hasMessageContaining(String.valueOf(dimensions));

        verify(chunkRepository, never()).save(any(KnowledgeChunk.class));
    }

    @Test
    void retrieve_shouldFallbackToKeywordSearch() {
        when(embeddingService.embed(anyString())).thenThrow(new RuntimeException("Vector error"));
        when(chunkRepository.findByContentContainingIgnoreCase(anyString(), any(Pageable.class)))
                .thenReturn(List.of());

        var result = ragService.retrieve("test query", 5);

        assertThat(result).contains("未找到");
        verify(chunkRepository).findByContentContainingIgnoreCase(
                org.mockito.ArgumentMatchers.eq("test query"), any(Pageable.class));
        verify(chunkRepository, never()).findAll();
    }

    @Test
    void retrieve_shouldUseStableLimitedKeywordQuery() {
        KnowledgeChunk first = keywordChunk(1L, "first");
        KnowledgeChunk second = keywordChunk(2L, "second");
        KnowledgeChunk third = keywordChunk(3L, "third");
        when(embeddingService.embed("keyword")).thenThrow(new RuntimeException("unavailable"));
        when(chunkRepository.findByContentContainingIgnoreCase(
                org.mockito.ArgumentMatchers.eq("keyword"), any(Pageable.class)))
                .thenReturn(List.of(first, second, third));

        String result = ragService.retrieve("keyword", 3);

        assertThat(result.indexOf("first")).isLessThan(result.indexOf("second"));
        assertThat(result.indexOf("second")).isLessThan(result.indexOf("third"));
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(chunkRepository).findByContentContainingIgnoreCase(
                org.mockito.ArgumentMatchers.eq("keyword"), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(3);
        assertThat(pageable.getValue().getSort().isSorted()).isTrue();
        verify(chunkRepository, never()).searchByVector(any(), anyInt(), anyDouble());
    }

    private static KnowledgeChunk keywordChunk(Long id, String title) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setId(id);
        chunk.setTitle(title);
        chunk.setContent("seeded keyword content");
        return chunk;
    }
}
