package com.example.smartfinassistant.loader;

/**
 * Untrusted transaction row exactly as read from the seed CSV, before validation.
 * All fields are raw strings so the validator can decide what is/isn't acceptable.
 */
public record RawTransactionRecord(
        String transactionId,
        String accountNumber,
        String amount,
        String transactionDate,
        String responseCode,
        String status) {
}
