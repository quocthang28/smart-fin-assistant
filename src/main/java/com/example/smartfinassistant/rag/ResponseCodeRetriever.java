package com.example.smartfinassistant.rag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ResponseCodeRetriever {

    private static final Pattern EXPLICIT_CODE = Pattern.compile(
            "(?iu)(?:\\bmã(?:\\s+lỗi)?|\\brc)\\s*[:#-]?\\s*(\\d{2})\\b");

    private final ResponseCodeCatalog catalog;
    private final VectorStore vectorStore;
    private final RagProperties properties;

    public RetrievalResult retrieve(String question) {
        Set<String> explicitCodes = extractExplicitCodes(question);
        if (explicitCodes.isEmpty()) {
            return semanticSearch(question);
        }

        Map<String, Document> retrievedById = new LinkedHashMap<>();
        List<String> unsupportedCodes = new ArrayList<>();
        List<String> unavailableCodes = new ArrayList<>();

        for (String rc : explicitCodes) {
            if (!catalog.contains(rc)) {
                unsupportedCodes.add(rc);
                continue;
            }

            SearchRequest request = SearchRequest.builder()
                    .query(question)
                    .topK(1)
                    .similarityThresholdAll()
                    .filterExpression("source == '" + ResponseCodeCatalog.SOURCE + "' && rc == '" + rc + "'")
                    .build();
            List<Document> matches = vectorStore.similaritySearch(request);
            if (matches.isEmpty()) {
                unavailableCodes.add(rc);
            } else {
                Document document = matches.getFirst();
                retrievedById.put(document.getId(), document);
            }
        }

        return new RetrievalResult(
                new ArrayList<>(retrievedById.values()), unsupportedCodes, unavailableCodes);
    }

    Set<String> extractExplicitCodes(String question) {
        Set<String> codes = new LinkedHashSet<>();
        Matcher matcher = EXPLICIT_CODE.matcher(question);
        while (matcher.find()) {
            codes.add(matcher.group(1));
        }
        return codes;
    }

    private RetrievalResult semanticSearch(String question) {
        SearchRequest request = SearchRequest.builder()
                .query(question)
                .topK(properties.topK())
                .similarityThreshold(properties.similarityThreshold())
                .filterExpression("source == '" + ResponseCodeCatalog.SOURCE + "'")
                .build();
        List<Document> documents = vectorStore.similaritySearch(request);
        return new RetrievalResult(documents, List.of(), List.of());
    }
}
