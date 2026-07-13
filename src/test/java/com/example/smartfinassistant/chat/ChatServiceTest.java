package com.example.smartfinassistant.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.smartfinassistant.rag.ResponseCodeCatalog;
import com.example.smartfinassistant.rag.ResponseCodeRetriever;
import com.example.smartfinassistant.rag.RetrievalResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class ChatServiceTest {

    private ResponseCodeRetriever retriever;
    private ResponseCodeAnswerGenerator generator;
    private ResponseCodeChatService service;

    @BeforeEach
    void setUp() {
        retriever = mock(ResponseCodeRetriever.class);
        generator = mock(ResponseCodeAnswerGenerator.class);
        service = new ResponseCodeChatService(retriever, generator);
    }

    @Test
    void undefinedCodeIsRefusedWithoutCallingModel() {
        when(retriever.retrieve(anyString()))
                .thenReturn(new RetrievalResult(List.of(), List.of("77"), List.of()));

        ChatResponse response = service.answer("Mã lỗi 77 nghĩa là gì?");

        assertThat(response.answer()).contains("Mã 77 không được định nghĩa");
        assertThat(response.citations()).isEmpty();
        verify(generator, never()).generate(anyString(), anyList());
    }

    @Test
    void appendsCitationFromRetrievedMetadata() {
        Document document = new Document("00000000-0000-0000-0000-000000000051",
                "Mã 51", Map.of("rc", "51", "source", ResponseCodeCatalog.SOURCE));
        when(retriever.retrieve(anyString()))
                .thenReturn(new RetrievalResult(List.of(document), List.of(), List.of()));
        when(generator.generate(anyString(), anyList()))
                .thenReturn("Mã 51 có nghĩa là số dư không đủ.");

        ChatResponse response = service.answer("Mã 51 nghĩa là gì?");

        assertThat(response.answer()).contains("Mã 51 có nghĩa là số dư không đủ")
                .contains("Nguồn: " + ResponseCodeCatalog.SOURCE + " — RC 51.");
        assertThat(response.citations()).containsExactly("RC 51");
    }
}
