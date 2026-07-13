package com.example.smartfinassistant.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

class ResponseCodeRetrieverTest {

    private ResponseCodeCatalog catalog;
    private VectorStore vectorStore;
    private ResponseCodeRetriever retriever;

    @BeforeEach
    void setUp() {
        catalog = mock(ResponseCodeCatalog.class);
        vectorStore = mock(VectorStore.class);
        retriever = new ResponseCodeRetriever(catalog, vectorStore, new RagProperties(1, 0.80));
    }

    @Test
    void undefinedExplicitCodeDoesNotCallVectorStore() {
        when(catalog.contains("77")).thenReturn(false);

        RetrievalResult result = retriever.retrieve("Mã lỗi 77 nghĩa là gì?");

        assertThat(result.documents()).isEmpty();
        assertThat(result.unsupportedCodes()).containsExactly("77");
        verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void retrievesEveryExplicitKnownCodeWithMetadataFilter() {
        when(catalog.contains("05")).thenReturn(true);
        when(catalog.contains("14")).thenReturn(true);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(document("05")))
                .thenReturn(List.of(document("14")));

        RetrievalResult result = retriever.retrieve("Mã 05 khác mã 14 như thế nào?");

        assertThat(result.documents()).extracting(document -> document.getMetadata().get("rc"))
                .containsExactly("05", "14");
        ArgumentCaptor<SearchRequest> requests = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore, org.mockito.Mockito.times(2)).similaritySearch(requests.capture());
        assertThat(requests.getAllValues()).allSatisfy(request -> {
            assertThat(request.getTopK()).isEqualTo(1);
            assertThat(request.hasFilterExpression()).isTrue();
        });
        assertThat(requests.getAllValues().get(0).getFilterExpression().toString()).contains("05");
        assertThat(requests.getAllValues().get(1).getFilterExpression().toString()).contains("14");
    }

    @Test
    void usesThresholdedSemanticSearchWhenNoCodeIsExplicit() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(document("75")));

        RetrievalResult result = retriever.retrieve("Khách bị khóa thẻ do nhập sai PIN thì xử lý sao?");

        assertThat(result.documents()).hasSize(1);
        ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(request.capture());
        assertThat(request.getValue().getTopK()).isEqualTo(1);
        assertThat(request.getValue().getSimilarityThreshold()).isEqualTo(0.80);
    }

    private Document document(String rc) {
        return new Document("00000000-0000-0000-0000-0000000000" + rc,
                "Mã " + rc, Map.of("rc", rc, "source", ResponseCodeCatalog.SOURCE));
    }
}
