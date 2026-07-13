package com.example.smartfinassistant.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rag")
public record RagProperties(int topK, double similarityThreshold) {

    public RagProperties {
        if (topK < 1) {
            throw new IllegalArgumentException("app.rag.top-k must be at least 1");
        }
        if (similarityThreshold < 0.0 || similarityThreshold > 1.0) {
            throw new IllegalArgumentException("app.rag.similarity-threshold must be between 0 and 1");
        }
    }
}
