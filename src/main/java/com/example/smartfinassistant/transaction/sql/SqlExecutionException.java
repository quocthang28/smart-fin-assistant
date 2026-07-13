package com.example.smartfinassistant.transaction.sql;

public class SqlExecutionException extends RuntimeException {

    private final boolean retryable;
    private final String correctionHint;

    public SqlExecutionException(
            String message, boolean retryable, String correctionHint, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
        this.correctionHint = correctionHint;
    }

    public boolean retryable() {
        return retryable;
    }

    public String correctionHint() {
        return correctionHint;
    }
}
