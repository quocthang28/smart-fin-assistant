package com.example.smartfinassistant.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChatRoutingTest {

    @Test
    void dispatchesEachIntentWithExplicitSwitch() {
        IntentRouter router = mock(IntentRouter.class);
        ResponseCodeChatService rag = mock(ResponseCodeChatService.class);
        TransactionChatService transactions = mock(TransactionChatService.class);
        ChatService service = new ChatService(router, rag, transactions);
        ChatResponse expected = new ChatResponse("ok", List.of());

        when(router.classify("rag")).thenReturn(ChatIntent.RESPONSE_CODE_LOOKUP);
        when(rag.answer("rag")).thenReturn(expected);
        assertThat(service.chat("rag")).isEqualTo(expected);
        verify(rag).answer("rag");

        when(router.classify("sql")).thenReturn(ChatIntent.TRANSACTION_LOOKUP);
        when(transactions.answer("sql", false)).thenReturn(expected);
        assertThat(service.chat("sql")).isEqualTo(expected);
        verify(transactions).answer("sql", false);

        when(router.classify("chain")).thenReturn(ChatIntent.TRANSACTION_WITH_EXPLANATION);
        when(transactions.answer("chain", true)).thenReturn(expected);
        assertThat(service.chat("chain")).isEqualTo(expected);
        verify(transactions).answer("chain", true);
    }

    @Test
    void outOfScopeDoesNotCallEitherUseCase() {
        IntentRouter router = mock(IntentRouter.class);
        ResponseCodeChatService rag = mock(ResponseCodeChatService.class);
        TransactionChatService transactions = mock(TransactionChatService.class);
        when(router.classify("weather")).thenReturn(ChatIntent.OUT_OF_SCOPE);

        ChatResponse response = new ChatService(router, rag, transactions).chat("weather");

        assertThat(response.answer()).contains("ngoài dữ liệu");
        assertThat(response.citations()).isEmpty();
        verifyNoInteractions(rag, transactions);
    }
}
