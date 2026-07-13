package com.example.smartfinassistant.chat;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class ResponseCodeAnswerGenerator {

    private final ChatClient chatClient;

    public ResponseCodeAnswerGenerator(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
                .defaultSystem(new ClassPathResource("prompts/response-code-system.txt"))
                .build();
    }

    public String generate(String question, List<Document> documents) {
        String context = documents.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));
        String userPrompt = """
                CÂU HỎI:
                %s

                NGỮ CẢNH ĐƯỢC TRUY HỒI:
                %s
                """.formatted(question, context);

        return chatClient.prompt()
                .user(userPrompt)
                .call()
                .content();
    }
}
