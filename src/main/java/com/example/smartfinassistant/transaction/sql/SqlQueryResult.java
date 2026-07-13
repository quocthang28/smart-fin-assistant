package com.example.smartfinassistant.transaction.sql;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

public record SqlQueryResult(ValidatedSql query, List<Map<String, Object>> rows) {

    public SqlQueryResult {
        rows = rows.stream()
                .map(row -> Collections.unmodifiableMap(new LinkedHashMap<>(row)))
                .toList();
    }
}
