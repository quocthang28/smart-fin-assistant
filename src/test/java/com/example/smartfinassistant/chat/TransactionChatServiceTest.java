package com.example.smartfinassistant.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.smartfinassistant.rag.ResponseCodeRetriever;
import com.example.smartfinassistant.rag.RetrievalResult;
import com.example.smartfinassistant.transaction.sql.SqlQueryResult;
import com.example.smartfinassistant.transaction.sql.TransactionQueryService;
import com.example.smartfinassistant.transaction.sql.ValidatedSql;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class TransactionChatServiceTest {

    private TransactionQueryService queryService;
    private ResponseCodeRetriever retriever;
    private TransactionAnswerGenerator generator;
    private TransactionChatService service;

    @BeforeEach
    void setUp() {
        queryService = mock(TransactionQueryService.class);
        retriever = mock(ResponseCodeRetriever.class);
        generator = mock(TransactionAnswerGenerator.class);
        service = new TransactionChatService(queryService, retriever, generator);
    }

    @Test
    void emptyRowsReturnDeterministicNoDataWithoutModel() {
        SqlQueryResult result = result(List.of());
        when(queryService.query(anyString())).thenReturn(result);

        ChatResponse response = service.answer("account 999999", false);

        assertThat(response.answer()).contains("Không tìm thấy dữ liệu");
        assertThat(response.citations()).containsExactly("v_transactions");
        verify(generator, never()).generate(anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void derivesTransactionViewAndResponseCodeCitationsFromGrounding() {
        SqlQueryResult result = result(List.of(Map.of(
                "transaction_id", "TXN00005",
                "response_code", "51")));
        Document document = new Document(
                "00000000-0000-0000-0000-000000000051",
                "Mã 51: Số dư không đủ",
                Map.of("rc", "51"));
        when(queryService.query(anyString())).thenReturn(result);
        when(retriever.retrieve(anyString()))
                .thenReturn(new RetrievalResult(List.of(document), List.of(), List.of()));
        when(generator.generate(anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn("TXN00005 thất bại do số dư không đủ.");

        ChatResponse response = service.answer("why", true);

        assertThat(response.citations())
                .containsExactly("v_transactions", "TXN00005", "RC 51");
        assertThat(response.answer()).contains("Nguồn: v_transactions, TXN00005, RC 51.");
        verify(retriever).retrieve("Mã lỗi 51 nghĩa là gì và hướng xử lý ra sao?");
    }

    @Test
    void extractsAggregateEvidenceIdsWithoutLettingModelCreateCitations() {
        SqlQueryResult result = result(List.of(Map.of(
                "failed_count", 3,
                "evidence_transaction_ids", "TXN00002,TXN00004,TXN00005")));
        when(queryService.query(anyString())).thenReturn(result);
        when(generator.generate(anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn("Có 3 giao dịch lỗi.");

        ChatResponse response = service.answer("count", false);

        assertThat(response.citations()).containsExactly(
                "v_transactions", "TXN00002", "TXN00004", "TXN00005");
    }

    private SqlQueryResult result(List<Map<String, Object>> rows) {
        return new SqlQueryResult(
                new ValidatedSql("SELECT ...", Set.of("v_transactions")), rows);
    }
}
