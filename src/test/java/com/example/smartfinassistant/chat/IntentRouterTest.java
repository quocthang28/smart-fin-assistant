package com.example.smartfinassistant.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

class IntentRouterTest {

    private IntentRouter router;

    @BeforeEach
    void setUp() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.defaultSystem(any(org.springframework.core.io.Resource.class)))
                .thenReturn(builder);
        when(builder.build()).thenReturn(mock(ChatClient.class));
        router = new IntentRouter(builder);
    }

    @Test
    void routesTransactionBenchmarksBeforeGenericCodeWords() {
        assertThat(router.classify("Giao dịch lỗi gần nhất của tài khoản 123456 là gì và tại sao lỗi?"))
                .isEqualTo(ChatIntent.TRANSACTION_WITH_EXPLANATION);
        assertThat(router.classify("Mã lỗi nào xuất hiện nhiều nhất trong hệ thống?"))
                .isEqualTo(ChatIntent.TRANSACTION_LOOKUP);
        assertThat(router.classify("Có bao nhiêu giao dịch lỗi mã 51? Bao nhiêu tài khoản bị?"))
                .isEqualTo(ChatIntent.TRANSACTION_LOOKUP);
    }

    @Test
    void routesDocumentQuestionsAndMissingSchema() {
        assertThat(router.classify("Mã lỗi 51 nghĩa là gì và hướng xử lý ra sao?"))
                .isEqualTo(ChatIntent.RESPONSE_CODE_LOOKUP);
        assertThat(router.classify("Mã 51 bị phạt phí bao nhiêu tiền?"))
                .isEqualTo(ChatIntent.RESPONSE_CODE_LOOKUP);
        assertThat(router.classify("Giao dịch TXN00005 qua ATM hay Mobile Banking?"))
                .isEqualTo(ChatIntent.OUT_OF_SCOPE);
        assertThat(router.classify("Tài khoản 123456 giao dịch bằng USD hay VND?"))
                .isEqualTo(ChatIntent.OUT_OF_SCOPE);
    }
}
