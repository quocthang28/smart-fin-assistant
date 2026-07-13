package com.example.smartfinassistant.rag;

public record ResponseCodeChunk(String rc, String meaning, String handling) {

    public String content() {
        return "Mã " + rc + ": " + meaning + System.lineSeparator()
                + "Hướng xử lý: " + handling;
    }
}
