package com.example.smartfinassistant.loader;

import com.example.smartfinassistant.responsecode.ResponseCode;
import com.example.smartfinassistant.responsecode.ResponseCodeRepository;
import com.example.smartfinassistant.transaction.Transaction;
import com.example.smartfinassistant.transaction.TransactionRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the transactions table on startup from {@code seed/transactions.csv}, running every
 * row through {@link TransactionValidator} first. Idempotent: skips if data already exists.
 * Response codes are Flyway-seeded reference data and are not touched here.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionSeedLoader implements ApplicationRunner {

    private static final String SEED_PATH = "seed/transactions.csv";
    private static final int FIELD_COUNT = 6;

    private final TransactionRepository transactionRepository;
    private final ResponseCodeRepository responseCodeRepository;
    private final TransactionValidator validator;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        long existing = transactionRepository.count();
        if (existing > 0) {
            log.info("Transactions already present ({}); skipping seed.", existing);
            return;
        }

        Set<String> knownCodes = responseCodeRepository.findAll().stream()
                .map(ResponseCode::getRc)
                .collect(Collectors.toSet());
        if (knownCodes.isEmpty()) {
            log.warn("No response_codes found; aborting transaction seed (run Flyway first).");
            return;
        }

        List<RawTransactionRecord> rows = readSeed();
        List<Transaction> valid = new ArrayList<>();
        int rejected = 0;
        for (RawTransactionRecord row : rows) {
            try {
                valid.add(validator.validate(row, knownCodes));
            } catch (InvalidTransactionException e) {
                rejected++;
                log.warn("Rejected seed row '{}': {}", row.transactionId(), e.getMessage());
            }
        }

        transactionRepository.saveAll(valid);
        log.info("Transaction seed complete: {} inserted, {} rejected, {} total rows.",
                valid.size(), rejected, rows.size());
    }

    private List<RawTransactionRecord> readSeed() {
        List<RawTransactionRecord> rows = new ArrayList<>();
        ClassPathResource resource = new ClassPathResource(SEED_PATH);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (header) {          // skip the column header row
                    header = false;
                    continue;
                }
                if (line.isBlank()) {
                    continue;
                }
                // Seed fields never contain commas, so a plain split is safe. limit=-1
                // keeps trailing empty fields (e.g. an intentionally empty account).
                String[] f = line.split(",", -1);
                if (f.length != FIELD_COUNT) {
                    log.warn("Skipping malformed seed line (expected {} fields): {}", FIELD_COUNT, line);
                    continue;
                }
                rows.add(new RawTransactionRecord(f[0], f[1], f[2], f[3], f[4], f[5]));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read seed file " + SEED_PATH, e);
        }
        return rows;
    }
}
