package com.example.smartfinassistant.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatResponse(String answer, List<String> citations, ChatDebug debug) {

    public ChatResponse {
        citations = List.copyOf(citations);
    }

    public ChatResponse(String answer, List<String> citations) {
        this(answer, citations, null);
    }

    /** Drop the debug trace so it is never serialized when {@code ?debug=true} was not requested. */
    public ChatResponse withoutDebug() {
        return debug == null ? this : new ChatResponse(answer, citations, null);
    }
}
