package com.example.smartfinassistant.transaction.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves the REAL anti-injection boundary: LLM-authored SQL runs as the dedicated
 * {@code finassist_readonly} PostgreSQL role, so the curated views are readable but base
 * tables and writes are denied by the database itself — not by string matching.
 *
 * <p>Flyway V1–V4 (run here as the container owner) create the tables, seed the response
 * codes, build the curated views, and provision the read-only role + grants. The executor
 * is then driven over a datasource that authenticates as that read-only role.
 */
@Testcontainers
class ReadonlySqlExecutorIntegrationTest {

    private static final String READONLY_PASSWORD = "readonly_secret";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("pgvector/pgvector:pg17");

    private static ReadonlySqlExecutor executor;

    @BeforeAll
    static void migrateAndWire() {
        // Owner connection runs the migrations, including V4 which creates the read-only role.
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .placeholders(Map.of("readonly_password", READONLY_PASSWORD))
                .load()
                .migrate();

        // Transactions are seeded at runtime by the loader, not by Flyway, so insert one row
        // as the owner to give the curated view something to read.
        DriverManagerDataSource owner = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        owner.setDriverClassName("org.postgresql.Driver");
        new JdbcTemplate(owner).update(
                "INSERT INTO transactions(transaction_id, account_number, amount,"
                        + " transaction_date, response_code, status)"
                        + " VALUES ('TXN00005', '123456', 999000,"
                        + " TIMESTAMP '2026-07-10 19:20:00', '51', 'FAILED')");

        DriverManagerDataSource readonly = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), "finassist_readonly", READONLY_PASSWORD);
        readonly.setDriverClassName("org.postgresql.Driver");

        executor = new ReadonlySqlExecutor(
                new JdbcTemplate(readonly),
                new SqlProperties(100, Duration.ofSeconds(4), 2));
    }

    @Test
    void executesAsTheReadonlyRole() {
        SqlQueryResult result = executor.execute(
                new ValidatedSql("SELECT current_user AS role", Set.of()));

        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().get(0).get("role")).isEqualTo("finassist_readonly");
    }

    @Test
    void allowsReadingCuratedViews() {
        SqlQueryResult result = executor.execute(new ValidatedSql(
                "SELECT transaction_id FROM v_transactions WHERE account_number = '123456'"
                        + " AND is_failed ORDER BY transaction_date DESC LIMIT 1",
                Set.of("v_transactions")));

        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().get(0).get("transaction_id")).isEqualTo("TXN00005");
    }

    @Test
    void deniesReadingBaseTables() {
        assertThatThrownBy(() -> executor.execute(
                        new ValidatedSql("SELECT * FROM transactions", Set.of())))
                .isInstanceOf(SqlExecutionException.class)
                .hasMessageContaining("PostgreSQL")
                .rootCause()
                .hasMessageContaining("permission denied");
    }

    @Test
    void deniesWritesToBaseTables() {
        assertThatThrownBy(() -> executor.execute(new ValidatedSql(
                        "INSERT INTO transactions(transaction_id, account_number, amount,"
                                + " transaction_date, response_code, status)"
                                + " VALUES ('X', '1', 1, now(), '00', 'SUCCESS')",
                        Set.of())))
                .isInstanceOf(SqlExecutionException.class)
                .rootCause()
                .hasMessageContaining("permission denied");
    }
}
