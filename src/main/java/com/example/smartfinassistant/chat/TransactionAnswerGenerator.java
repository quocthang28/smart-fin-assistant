package com.example.smartfinassistant.chat;

import com.example.smartfinassistant.transaction.sql.SqlQueryResult;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class TransactionAnswerGenerator {

    private final ChatClient chatClient;

    public TransactionAnswerGenerator(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem(new ClassPathResource("prompts/transaction-answer-system.txt"))
                .build();
    }

    public String generate(
            String question,
            SqlQueryResult result,
            List<Document> responseCodeDocuments) {
        String documentContext = responseCodeDocuments.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));
        String prompt = """
                CÂU HỎI:
                %s

                SQL ĐÃ XÁC THỰC:
                %s

                KẾT QUẢ CÓ CẤU TRÚC:
                %s

                TÀI LIỆU MÃ PHẢN HỒI ĐƯỢC TRUY HỒI:
                %s
                """.formatted(question, result.query().sql(), result.rows(), documentContext);
        return chatClient.prompt().user(prompt).call().content();
    }
}
