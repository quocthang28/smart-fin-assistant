package com.example.smartfinassistant.rag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class ResponseCodeCatalog {

    public static final String SOURCE = "docs/response-codes.md";
    private static final String RESOURCE_PATH = "rag/response-codes.md";

    private final List<ResponseCodeChunk> chunks;
    private final Map<String, ResponseCodeChunk> chunksByCode;

    public ResponseCodeCatalog() {
        chunks = new ResponseCodeDocumentParser().parse(new ClassPathResource(RESOURCE_PATH));
        chunksByCode = chunks.stream().collect(Collectors.toUnmodifiableMap(
                ResponseCodeChunk::rc, Function.identity()));
    }

    public List<ResponseCodeChunk> chunks() {
        return chunks;
    }

    public Optional<ResponseCodeChunk> find(String rc) {
        return Optional.ofNullable(chunksByCode.get(rc));
    }

    public boolean contains(String rc) {
        return chunksByCode.containsKey(rc);
    }

    public List<Document> documents() {
        return chunks.stream().map(this::toDocument).toList();
    }

    private Document toDocument(ResponseCodeChunk chunk) {
        String content = chunk.content();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", SOURCE);
        metadata.put("document_type", "response_code");
        metadata.put("rc", chunk.rc());
        metadata.put("content_hash", sha256(content));
        return new Document(stableId(chunk.rc()), content, metadata);
    }

    private String stableId(String rc) {
        return UUID.nameUUIDFromBytes((SOURCE + "#" + rc).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
