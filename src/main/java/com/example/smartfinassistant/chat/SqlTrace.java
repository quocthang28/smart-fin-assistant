package com.example.smartfinassistant.chat;

import java.util.List;
import java.util.Map;

/** The validated text-to-SQL the read-only role actually ran, plus the rows it returned. */
public record SqlTrace(String sql, List<String> views, int rowCount, List<Map<String, Object>> rows) {
}
