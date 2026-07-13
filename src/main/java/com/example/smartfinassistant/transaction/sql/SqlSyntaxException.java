package com.example.smartfinassistant.transaction.sql;

public class SqlSyntaxException extends RuntimeException {

    public SqlSyntaxException(String message, Throwable cause) {
        super(message, cause);
    }
}
