package com.example.smartfinassistant.loader;

/** Raised when a raw seed row fails validation; the loader logs it and skips the row. */
public class InvalidTransactionException extends RuntimeException {

    public InvalidTransactionException(String message) {
        super(message);
    }
}
