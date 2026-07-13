package com.example.smartfinassistant.chat;

import java.util.List;
import org.springframework.ai.document.Document;

/** One vector-store hit, exposed for debugging: which RC chunk and its similarity score. */
public record RetrievedChunk(String id, String rc, Double score, String text) {

    static RetrievedChunk of(Document document) {
        Object rc = document.getMetadata().get("rc");
        return new RetrievedChunk(
                document.getId(), rc == null ? null : rc.toString(),
                document.getScore(), document.getText());
    }

    static List<RetrievedChunk> from(List<Document> documents) {
        return documents.stream().map(RetrievedChunk::of).toList();
    }
}
