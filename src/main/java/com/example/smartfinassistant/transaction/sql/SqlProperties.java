package com.example.smartfinassistant.transaction.sql;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.sql")
public record SqlProperties(
        int maximumRows,
        Duration statementTimeout,
        int maximumCorrectionAttempts) {

    public SqlProperties {
        if (maximumRows < 1) {
            throw new IllegalArgumentException("app.sql.maximum-rows must be at least 1");
        }
        if (statementTimeout == null || statementTimeout.isZero() || statementTimeout.isNegative()) {
            throw new IllegalArgumentException("app.sql.statement-timeout must be positive");
        }
        if (maximumCorrectionAttempts < 0 || maximumCorrectionAttempts > 2) {
            throw new IllegalArgumentException(
                    "app.sql.maximum-correction-attempts must be between 0 and 2");
        }
    }
}
