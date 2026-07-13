package com.example.smartfinassistant.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final IntentRouter intentRouter;
    private final ResponseCodeChatService responseCodeChatService;
    private final TransactionChatService transactionChatService;

    public ChatResponse chat(String question) {
        return chat(question, false);
    }

    public ChatResponse chat(String question, boolean debug) {
        ChatResponse response = switch (intentRouter.classify(question)) {
            case RESPONSE_CODE_LOOKUP -> responseCodeChatService.answer(question);
            case TRANSACTION_LOOKUP -> transactionChatService.answer(question, false);
            case TRANSACTION_WITH_EXPLANATION -> transactionChatService.answer(question, true);
            case OUT_OF_SCOPE -> new ChatResponse(
                    "Yêu cầu này nằm ngoài dữ liệu hoặc phạm vi tra cứu chỉ đọc của trợ lý.",
                    java.util.List.of(),
                    new ChatDebug("OUT_OF_SCOPE", false, null, java.util.List.of()));
        };
        return debug ? response : response.withoutDebug();
    }
}
