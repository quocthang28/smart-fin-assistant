package com.example.smartfinassistant.chat;

import com.example.smartfinassistant.common.AiServiceUnavailableException;
import com.example.smartfinassistant.rag.ResponseCodeRetriever;
import com.example.smartfinassistant.rag.RetrievalResult;
import com.example.smartfinassistant.transaction.sql.SqlQueryResult;
import com.example.smartfinassistant.transaction.sql.TransactionQueryService;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TransactionChatService {

    private final TransactionQueryService queryService;
    private final ResponseCodeRetriever responseCodeRetriever;
    private final TransactionAnswerGenerator answerGenerator;

    public ChatResponse answer(String question, boolean includeExplanation) {
        SqlQueryResult result = queryService.query(question);
        if (result.rows().isEmpty()) {
            return new ChatResponse(
                    "Không tìm thấy dữ liệu giao dịch phù hợp với yêu cầu.",
                    sortedViews(result),
                    new ChatDebug(route(includeExplanation), includeExplanation,
                            sqlTrace(result), List.of()));
        }

        List<Document> documents = includeExplanation
                ? retrieveResponseCodes(result.rows())
                : List.of();
        String answer;
        try {
            answer = answerGenerator.generate(question, result, documents);
        } catch (RuntimeException e) {
            throw new AiServiceUnavailableException("Không thể tạo câu trả lời giao dịch.", e);
        }
        if (answer == null || answer.isBlank()) {
            throw new AiServiceUnavailableException("Mô hình không trả về nội dung.");
        }

        List<String> citations = citations(result, documents);
        return new ChatResponse(
                answer.trim() + "\n\nNguồn: " + String.join(", ", citations) + ".",
                citations,
                new ChatDebug(route(includeExplanation), includeExplanation,
                        sqlTrace(result), RetrievedChunk.from(documents)));
    }

    private String route(boolean includeExplanation) {
        return includeExplanation ? "TRANSACTION_WITH_EXPLANATION" : "TRANSACTION_LOOKUP";
    }

    private SqlTrace sqlTrace(SqlQueryResult result) {
        return new SqlTrace(
                result.query().sql(), sortedViews(result), result.rows().size(), result.rows());
    }

    private List<Document> retrieveResponseCodes(List<Map<String, Object>> rows) {
        Map<String, Document> documents = new LinkedHashMap<>();
        for (String code : valuesFor(rows, "response_code")) {
            RetrievalResult retrieval = responseCodeRetriever.retrieve(
                    "Mã lỗi " + code + " nghĩa là gì và hướng xử lý ra sao?");
            for (Document document : retrieval.documents()) {
                documents.put(document.getId(), document);
            }
        }
        return List.copyOf(documents.values());
    }

    private List<String> citations(SqlQueryResult result, List<Document> documents) {
        Set<String> citations = new LinkedHashSet<>(sortedViews(result));
        citations.addAll(valuesFor(result.rows(), "transaction_id"));
        citations.addAll(valuesFor(result.rows(), "evidence_transaction_ids"));
        for (Document document : documents) {
            Object rc = document.getMetadata().get("rc");
            if (rc != null) {
                citations.add("RC " + rc);
            }
        }
        return List.copyOf(citations);
    }

    private List<String> sortedViews(SqlQueryResult result) {
        return result.query().views().stream().sorted().toList();
    }

    private Set<String> valuesFor(List<Map<String, Object>> rows, String key) {
        Set<String> values = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            addValues(values, row.get(key));
        }
        return values;
    }

    private void addValues(Set<String> values, Object raw) {
        if (raw == null) {
            return;
        }
        if (raw instanceof Collection<?> collection) {
            collection.forEach(value -> addValues(values, value));
            return;
        }
        if (raw.getClass().isArray()) {
            for (int index = 0; index < Array.getLength(raw); index++) {
                addValues(values, Array.get(raw, index));
            }
            return;
        }
        for (String value : raw.toString().split(",")) {
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
    }
}
