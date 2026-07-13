package com.example.smartfinassistant.transaction;

import java.util.Locale;

/**
 * Normalized transaction outcome. Stored as its name (EnumType.STRING) and constrained
 * to SUCCESS / FAILED by a DB CHECK (V1), matching the values here.
 */
public enum TransactionStatus {
    SUCCESS,
    FAILED;

    /**
     * Normalize a raw status string (case/whitespace-insensitive, common synonyms).
     *
     * @throws IllegalArgumentException if the value maps to no known status
     */
    public static TransactionStatus fromRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("missing status");
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "SUCCESS", "SUCCEEDED", "SUCCESSFUL", "OK" -> SUCCESS;
            case "FAILED", "FAIL", "FAILURE", "ERROR" -> FAILED;
            default -> throw new IllegalArgumentException("unknown status: " + raw);
        };
    }
}
