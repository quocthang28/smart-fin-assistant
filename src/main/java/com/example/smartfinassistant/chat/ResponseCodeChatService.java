package com.example.smartfinassistant.chat;

import com.example.smartfinassistant.common.AiServiceUnavailableException;
import com.example.smartfinassistant.rag.ResponseCodeCatalog;
import com.example.smartfinassistant.rag.ResponseCodeRetriever;
import com.example.smartfinassistant.rag.RetrievalResult;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ResponseCodeChatService {

    private final ResponseCodeRetriever retriever;
    private final ResponseCodeAnswerGenerator answerGenerator;

    public ChatResponse answer(String question) {
        RetrievalResult retrieval;
        try {
            retrieval = retriever.retrieve(question);
        } catch (RuntimeException e) {
            throw new AiServiceUnavailableException("Không thể truy hồi tài liệu mã phản hồi.", e);
        }

        List<String> citations = citations(retrieval.documents());
        String answer;
        if (retrieval.documents().isEmpty()) {
            answer = noContextAnswer(retrieval);
        } else {
            try {
                answer = answerGenerator.generate(question, retrieval.documents());
            } catch (RuntimeException e) {
                throw new AiServiceUnavailableException("Không thể tạo câu trả lời vào lúc này.", e);
            }
            if (answer == null || answer.isBlank()) {
                throw new AiServiceUnavailableException("Mô hình không trả về nội dung.");
            }
            answer = appendCodeNotices(answer.trim(), retrieval)
                    + "\n\nNguồn: " + ResponseCodeCatalog.SOURCE + " — "
                    + String.join(", ", citations) + ".";
        }
        return new ChatResponse(answer, citations,
                new ChatDebug("RESPONSE_CODE_LOOKUP", false, null,
                        RetrievedChunk.from(retrieval.documents())));
    }

    private String noContextAnswer(RetrievalResult retrieval) {
        String notices = codeNotices(retrieval);
        return notices.isBlank()
                ? "Không tìm thấy thông tin phù hợp trong tài liệu mã phản hồi."
                : notices;
    }

    private String appendCodeNotices(String answer, RetrievalResult retrieval) {
        String notices = codeNotices(retrieval);
        return notices.isBlank() ? answer : answer + "\n\n" + notices;
    }

    private String codeNotices(RetrievalResult retrieval) {
        List<String> unsupported = retrieval.unsupportedCodes().stream()
                .map(code -> "Mã " + code + " không được định nghĩa trong tài liệu mã phản hồi.")
                .toList();
        List<String> unavailable = retrieval.unavailableCodes().stream()
                .map(code -> "Không tìm thấy đoạn tài liệu đã lập chỉ mục cho mã " + code + ".")
                .toList();
        return java.util.stream.Stream.concat(unsupported.stream(), unavailable.stream())
                .collect(Collectors.joining("\n"));
    }

    private List<String> citations(List<Document> documents) {
        Set<String> citations = new LinkedHashSet<>();
        for (Document document : documents) {
            Object rc = document.getMetadata().get("rc");
            if (rc != null) {
                citations.add("RC " + rc);
            }
        }
        return List.copyOf(citations);
    }
}
