package com.example.smartfinassistant.rag;

import java.util.List;
import org.springframework.ai.document.Document;

public record RetrievalResult(
        List<Document> documents,
        List<String> unsupportedCodes,
        List<String> unavailableCodes) {

    public RetrievalResult {
        documents = List.copyOf(documents);
        unsupportedCodes = List.copyOf(unsupportedCodes);
        unavailableCodes = List.copyOf(unavailableCodes);
    }
}
