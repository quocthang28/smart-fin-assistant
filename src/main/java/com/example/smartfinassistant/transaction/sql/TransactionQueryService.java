package com.example.smartfinassistant.transaction.sql;

import com.example.smartfinassistant.common.AiServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TransactionQueryService {

    private final TransactionSqlGenerator generator;
    private final SqlValidator validator;
    private final ReadonlySqlExecutor executor;
    private final SqlProperties properties;

    public SqlQueryResult query(String question) {
        String previousSql = null;
        String correctionHint = null;

        for (int attempt = 0; attempt <= properties.maximumCorrectionAttempts(); attempt++) {
            String sql;
            try {
                sql = generator.generate(question, previousSql, correctionHint);
            } catch (RuntimeException e) {
                throw new AiServiceUnavailableException("Không thể tạo truy vấn dữ liệu.", e);
            }

            try {
                ValidatedSql validated = validator.validate(sql);
                return executor.execute(validated);
            } catch (SqlSyntaxException e) {
                previousSql = sql;
                correctionHint = "The SQL could not be parsed. Return one valid PostgreSQL SELECT only.";
            } catch (SqlPolicyException e) {
                throw new AiServiceUnavailableException(
                        "Truy vấn được tạo không đáp ứng chính sách chỉ đọc.", e);
            } catch (SqlExecutionException e) {
                if (!e.retryable()) {
                    throw new AiServiceUnavailableException("Không thể truy vấn dữ liệu vào lúc này.", e);
                }
                previousSql = sql;
                correctionHint = e.correctionHint();
            }
        }

        throw new AiServiceUnavailableException("Không thể tạo truy vấn hợp lệ sau số lần thử cho phép.");
    }
}
