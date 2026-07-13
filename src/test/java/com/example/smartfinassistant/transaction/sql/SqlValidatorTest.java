package com.example.smartfinassistant.transaction.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SqlValidatorTest {

    private SqlValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SqlValidator(new SqlProperties(100, Duration.ofSeconds(4), 2));
    }

    @Test
    void acceptsSelectAndAddsLimit() {
        ValidatedSql result = validator.validate(
                "SELECT transaction_id FROM v_transactions WHERE account_number = '000123'");

        assertThat(result.sql()).endsWith("LIMIT 100");
        assertThat(result.views()).containsExactly("v_transactions");
    }

    @Test
    void preservesSmallerLimitAndReducesExcessiveLimit() {
        assertThat(validator.validate("SELECT * FROM v_transactions LIMIT 2").sql())
                .endsWith("LIMIT 2");
        assertThat(validator.validate("SELECT * FROM v_transactions LIMIT 1000").sql())
                .endsWith("LIMIT 100");
    }

    @Test
    void acceptsAggregateJoinNestedSelectAndCte() {
        String sql = """
                WITH failed AS (
                  SELECT account_number, count(*) AS failures
                  FROM v_transactions
                  WHERE is_failed
                  GROUP BY account_number
                )
                SELECT f.account_number, f.failures, a.failure_rate_pct
                FROM failed f
                JOIN v_account_summary a ON a.account_number = f.account_number
                WHERE f.failures = (SELECT max(failures) FROM failed)
                ORDER BY f.account_number
                """;

        ValidatedSql result = validator.validate(sql);

        assertThat(result.views()).containsExactlyInAnyOrder(
                "v_transactions", "v_account_summary");
    }

    @Test
    void rejectsBaseTableUnknownSchemaAndSystemCatalog() {
        assertRejected("SELECT * FROM transactions");
        assertRejected("SELECT * FROM private.v_transactions");
        assertRejected("SELECT * FROM pg_catalog.pg_user");
    }

    @Test
    void rejectsNonSelectMultipleStatementsAndCommentHiddenStatement() {
        assertRejected("DELETE FROM v_transactions");
        assertRejected("SELECT * FROM v_transactions; DROP TABLE transactions");
        assertRejected("SELECT * FROM v_transactions; -- hidden\nDELETE FROM transactions");
    }

    @Test
    void rejectsUnsafeFunctionsAndTableFunctions() {
        assertRejected("SELECT pg_sleep(1) FROM v_transactions");
        assertRejected("SELECT * FROM v_transactions, generate_series(1, 10)");
    }

    @Test
    void cteCannotHideBaseTableAccess() {
        assertRejected("""
                WITH v_transactions AS (SELECT * FROM transactions)
                SELECT * FROM v_transactions
                """);
    }

    private void assertRejected(String sql) {
        assertThatThrownBy(() -> validator.validate(sql))
                .isInstanceOf(SqlPolicyException.class);
    }
}
