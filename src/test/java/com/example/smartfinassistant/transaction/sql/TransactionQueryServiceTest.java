package com.example.smartfinassistant.transaction.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.smartfinassistant.common.AiServiceUnavailableException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransactionQueryServiceTest {

    private TransactionSqlGenerator generator;
    private SqlValidator validator;
    private ReadonlySqlExecutor executor;
    private TransactionQueryService service;

    @BeforeEach
    void setUp() {
        generator = mock(TransactionSqlGenerator.class);
        validator = mock(SqlValidator.class);
        executor = mock(ReadonlySqlExecutor.class);
        service = new TransactionQueryService(
                generator,
                validator,
                executor,
                new SqlProperties(100, Duration.ofSeconds(4), 2));
    }

    @Test
    void returnsValidQueryOnFirstAttempt() {
        ValidatedSql sql = new ValidatedSql("SELECT 1", Set.of("v_transactions"));
        SqlQueryResult expected = new SqlQueryResult(sql, List.of());
        when(generator.generate(anyString(), any(), any())).thenReturn("SELECT 1");
        when(validator.validate("SELECT 1")).thenReturn(sql);
        when(executor.execute(sql)).thenReturn(expected);

        assertThat(service.query("question")).isSameAs(expected);
        verify(generator).generate("question", null, null);
    }

    @Test
    void correctsSyntaxThenSucceeds() {
        ValidatedSql corrected = new ValidatedSql("SELECT 2", Set.of("v_transactions"));
        when(generator.generate(anyString(), any(), any())).thenReturn("bad", "SELECT 2");
        when(validator.validate("bad")).thenThrow(new SqlSyntaxException("bad", new RuntimeException()));
        when(validator.validate("SELECT 2")).thenReturn(corrected);
        when(executor.execute(corrected)).thenReturn(new SqlQueryResult(corrected, List.of()));

        service.query("question");

        verify(generator, times(2)).generate(anyString(), any(), any());
    }

    @Test
    void policyViolationIsNeverRetried() {
        when(generator.generate(anyString(), any(), any())).thenReturn("DROP TABLE transactions");
        when(validator.validate(anyString())).thenThrow(new SqlPolicyException("blocked"));

        assertThatThrownBy(() -> service.query("question"))
                .isInstanceOf(AiServiceUnavailableException.class)
                .hasMessageContaining("chỉ đọc");
        verify(generator).generate("question", null, null);
        verify(executor, never()).execute(any());
    }
}
