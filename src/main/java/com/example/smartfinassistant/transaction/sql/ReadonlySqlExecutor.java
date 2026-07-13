package com.example.smartfinassistant.transaction.sql;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReadonlySqlExecutor {

    private final JdbcTemplate jdbcTemplate;
    private final SqlProperties properties;

    public ReadonlySqlExecutor(
            @Qualifier("readonlyJdbcTemplate") JdbcTemplate jdbcTemplate,
            SqlProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    @Transactional(transactionManager = "readonlyTransactionManager", readOnly = true)
    public SqlQueryResult execute(ValidatedSql query) {
        try {
            jdbcTemplate.execute("SET LOCAL statement_timeout = " + timeoutMillis(properties.statementTimeout()));
            List<Map<String, Object>> rows = jdbcTemplate.query(query.sql(), this::mapRow);
            return new SqlQueryResult(query, rows);
        } catch (BadSqlGrammarException e) {
            throw new SqlExecutionException(
                    "Generated SQL was rejected by PostgreSQL",
                    true,
                    "PostgreSQL rejected the syntax or a referenced column. Use only columns from the schema catalog.",
                    e);
        } catch (DataAccessException e) {
            throw new SqlExecutionException(
                    "The read-only transaction query could not be completed",
                    false,
                    null,
                    e);
        }
    }

    private Map<String, Object> mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        ResultSetMetaData metadata = resultSet.getMetaData();
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 1; index <= metadata.getColumnCount(); index++) {
            String label = metadata.getColumnLabel(index).toLowerCase(Locale.ROOT);
            row.put(label, normalize(resultSet.getObject(index)));
        }
        return row;
    }

    private Object normalize(Object value) throws SQLException {
        if (value instanceof Array array) {
            return List.of((Object[]) array.getArray());
        }
        return value;
    }

    private long timeoutMillis(Duration timeout) {
        return Math.max(1, timeout.toMillis());
    }
}
