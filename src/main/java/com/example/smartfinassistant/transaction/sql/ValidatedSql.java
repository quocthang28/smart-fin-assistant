package com.example.smartfinassistant.transaction.sql;

import java.util.Set;

public record ValidatedSql(String sql, Set<String> views) {

    public ValidatedSql {
        views = Set.copyOf(views);
    }
}
