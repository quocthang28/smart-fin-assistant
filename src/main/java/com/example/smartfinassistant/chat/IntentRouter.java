package com.example.smartfinassistant.chat;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class IntentRouter {

    private static final Pattern TRANSACTION_ID = Pattern.compile("(?i)\\bTXN\\d+\\b");
    private static final Pattern ACCOUNT_NUMBER = Pattern.compile(
            "(?iu)(?:tài khoản|tai khoan|\\btk\\b)\\s*[:#-]?\\s*\\d{3,}");
    private static final Pattern EXPLICIT_RESPONSE_CODE = Pattern.compile(
            "(?iu)(?:\\bmã(?:\\s+lỗi)?|\\brc)\\s*[:#-]?\\s*\\d{2}\\b");
    private static final Set<String> DATA_OPERATIONS = Set.of(
            "gần nhất", "mới nhất", "chi tiết giao dịch", "bao nhiêu giao dịch", "tổng",
            "liệt kê", "xếp hạng", "nhiều nhất", "ít nhất", "tỉ lệ", "tỷ lệ",
            "trên ", "dưới ", "trong năm", "những tài khoản", "bị lỗi mã");
    private static final Set<String> EXPLANATION_OPERATIONS = Set.of(
            "tại sao", "vì sao", "giải thích", "lý do", "cách xử lý", "nên làm gì");
    private static final Set<String> RESPONSE_CODE_OPERATIONS = Set.of(
            "mã lỗi", "mã phản hồi", "response code", " rc ", "mã ");
    private static final Set<String> DOCUMENT_OPERATIONS = Set.of(
            "nghĩa là gì", "là gì", "ý nghĩa", "hướng xử lý", "khác mã", "mã nào");
    private static final Set<String> MISSING_SCHEMA = Set.of(
            "tên khách hàng", "kênh giao dịch", "atm hay", "mobile banking hay",
            "loại tiền tệ", "usd hay", "vnd hay", "số thẻ", "cccd", "mật khẩu",
            "số dư", "chuyển tiền");
    private static final Set<String> OBVIOUSLY_UNSAFE = Set.of(
            "drop table", "delete from", "insert into", "update transactions",
            "hack hệ thống", "prompt hệ thống", "bỏ qua mọi hướng dẫn", "thời tiết");

    private final ChatClient fallbackClassifier;

    public IntentRouter(ChatClient.Builder builder) {
        this.fallbackClassifier = builder
                .defaultSystem(new ClassPathResource("prompts/intent-router-system.txt"))
                .build();
    }

    public ChatIntent classify(String question) {
        String normalized = " " + question.toLowerCase(Locale.ROOT).trim() + " ";
        if (containsAny(normalized, MISSING_SCHEMA) || containsAny(normalized, OBVIOUSLY_UNSAFE)) {
            return ChatIntent.OUT_OF_SCOPE;
        }

        boolean transactionIdentity = TRANSACTION_ID.matcher(question).find()
                || ACCOUNT_NUMBER.matcher(question).find();
        boolean dataOperation = transactionIdentity || containsAny(normalized, DATA_OPERATIONS);
        boolean explanation = containsAny(normalized, EXPLANATION_OPERATIONS);
        if (dataOperation) {
            return explanation
                    ? ChatIntent.TRANSACTION_WITH_EXPLANATION
                    : ChatIntent.TRANSACTION_LOOKUP;
        }

        if (containsAny(normalized, RESPONSE_CODE_OPERATIONS)
                && (containsAny(normalized, DOCUMENT_OPERATIONS)
                        || EXPLICIT_RESPONSE_CODE.matcher(question).find())) {
            return ChatIntent.RESPONSE_CODE_LOOKUP;
        }
        return classifyWithModel(question);
    }

    private ChatIntent classifyWithModel(String question) {
        try {
            String content = fallbackClassifier.prompt().user(question).call().content();
            return content == null
                    ? ChatIntent.OUT_OF_SCOPE
                    : ChatIntent.valueOf(content.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            return ChatIntent.OUT_OF_SCOPE;
        }
    }

    private boolean containsAny(String value, Set<String> candidates) {
        return candidates.stream().anyMatch(value::contains);
    }
}
