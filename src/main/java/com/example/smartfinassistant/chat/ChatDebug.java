package com.example.smartfinassistant.chat;

import java.util.List;

/**
 * Trace of how the assistant produced an answer, returned only when {@code ?debug=true}.
 *
 * @param route            the {@link ChatIntent} the router picked
 * @param chained          whether an RC explanation was chained onto a transaction lookup
 * @param sql              the validated read-only SQL + rows (null for the RAG-only route)
 * @param retrievedChunks  the vector-store hits with similarity scores (empty when none)
 */
public record ChatDebug(
        String route,
        boolean chained,
        SqlTrace sql,
        List<RetrievedChunk> retrievedChunks) {
}
