package com.example.smartfinassistant.rag;

import com.example.smartfinassistant.responsecode.ResponseCode;
import com.example.smartfinassistant.responsecode.ResponseCodeRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ResponseCodeIngestionRunner implements ApplicationRunner {

    private static final String EXISTING_DOCUMENTS_SQL = """
            SELECT id::text, metadata->>'rc', metadata->>'content_hash'
            FROM vector_store
            WHERE metadata->>'source' = ?
            """;

    private final ResponseCodeCatalog catalog;
    private final ResponseCodeRepository responseCodeRepository;
    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        validateAgainstDatabase();

        List<Document> desiredDocuments = catalog.documents();
        Map<String, Document> desiredById = desiredDocuments.stream()
                .collect(Collectors.toMap(Document::getId, Function.identity()));
        Map<String, StoredDocument> storedById = loadStoredDocuments();

        List<String> staleOrChangedIds = new ArrayList<>();
        List<Document> documentsToAdd = new ArrayList<>();
        int updated = 0;

        for (StoredDocument stored : storedById.values()) {
            Document desired = desiredById.get(stored.id());
            if (desired == null) {
                staleOrChangedIds.add(stored.id());
                continue;
            }
            String desiredHash = desired.getMetadata().get("content_hash").toString();
            if (!Objects.equals(stored.contentHash(), desiredHash)) {
                staleOrChangedIds.add(stored.id());
                documentsToAdd.add(desired);
                updated++;
            }
        }

        for (Document desired : desiredDocuments) {
            if (!storedById.containsKey(desired.getId())) {
                documentsToAdd.add(desired);
            }
        }

        if (!staleOrChangedIds.isEmpty()) {
            vectorStore.delete(staleOrChangedIds);
        }
        if (!documentsToAdd.isEmpty()) {
            vectorStore.add(documentsToAdd);
        }

        int removed = staleOrChangedIds.size() - updated;
        int added = documentsToAdd.size() - updated;
        log.info("Response-code RAG sync complete: {} current, {} added, {} updated, {} removed.",
                desiredDocuments.size(), added, updated, removed);
    }

    private void validateAgainstDatabase() {
        Map<String, ResponseCode> databaseCodes = responseCodeRepository.findAll().stream()
                .collect(Collectors.toMap(ResponseCode::getRc, Function.identity()));
        List<String> mismatches = new ArrayList<>();
        Set<String> documentCodes = new HashSet<>();

        for (ResponseCodeChunk chunk : catalog.chunks()) {
            documentCodes.add(chunk.rc());
            ResponseCode databaseCode = databaseCodes.get(chunk.rc());
            if (databaseCode == null) {
                mismatches.add("RC " + chunk.rc() + " is missing from response_codes");
            } else if (!chunk.meaning().equals(databaseCode.getMeaning())
                    || !chunk.handling().equals(databaseCode.getHandling())) {
                mismatches.add("RC " + chunk.rc() + " content differs from response_codes");
            }
        }

        Set<String> databaseOnlyCodes = new HashSet<>(databaseCodes.keySet());
        databaseOnlyCodes.removeAll(documentCodes);
        if (!databaseOnlyCodes.isEmpty()) {
            mismatches.add("response_codes contains codes absent from the document: " + databaseOnlyCodes);
        }
        if (!mismatches.isEmpty()) {
            throw new IllegalStateException(
                    "Response-code document/database consistency check failed: " + String.join("; ", mismatches));
        }
    }

    private Map<String, StoredDocument> loadStoredDocuments() {
        Map<String, StoredDocument> result = new HashMap<>();
        jdbcTemplate.query(EXISTING_DOCUMENTS_SQL, rs -> {
            StoredDocument document = new StoredDocument(
                    rs.getString(1), rs.getString(2), rs.getString(3));
            result.put(document.id(), document);
        }, ResponseCodeCatalog.SOURCE);
        return result;
    }

    private record StoredDocument(String id, String rc, String contentHash) {
    }
}
