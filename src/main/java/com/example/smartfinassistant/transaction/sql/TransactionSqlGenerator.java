package com.example.smartfinassistant.transaction.sql;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class TransactionSqlGenerator {

    private final ChatClient chatClient;

    public TransactionSqlGenerator(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem(new ClassPathResource("prompts/transaction-sql-system.txt"))
                .build();
    }

    public String generate(String question, String previousSql, String correctionHint) {
        String correction = previousSql == null ? "" : """

                TRUY VẤN TRƯỚC KHÔNG HỢP LỆ:
                %s

                GỢI Ý SỬA AN TOÀN:
                %s
                """.formatted(previousSql, correctionHint);
        String response = chatClient.prompt()
                .user("CÂU HỎI:\n" + question + correction)
                .call()
                .content();
        if (response == null || response.isBlank()) {
            throw new IllegalStateException("SQL model returned no content");
        }
        return stripFence(response.trim());
    }

    private String stripFence(String value) {
        if (!value.startsWith("```")) {
            return value;
        }
        int firstNewline = value.indexOf('\n');
        int closingFence = value.lastIndexOf("```");
        if (firstNewline < 0 || closingFence <= firstNewline) {
            return value;
        }
        return value.substring(firstNewline + 1, closingFence).trim();
    }
}
