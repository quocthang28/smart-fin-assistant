package com.example.smartfinassistant.loader;

import com.example.smartfinassistant.transaction.Transaction;
import com.example.smartfinassistant.transaction.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Cleans and validates raw seed rows before they reach the DB (spec: "Data Validation in
 * Java"). Every rule either normalizes a value or rejects the row with a reason. The DB
 * constraints in V1 are the second line of defense behind these checks.
 */
@Component
public class TransactionValidator {

    private static final Pattern RC_FORMAT = Pattern.compile("\\d{2}");

    /**
     * @param raw        the untrusted row
     * @param knownCodes the response codes present in the reference table
     * @return a valid, normalized {@link Transaction}
     * @throws InvalidTransactionException if any rule fails
     */
    public Transaction validate(RawTransactionRecord raw, Set<String> knownCodes) {
        String id = trimToEmpty(raw.transactionId());
        if (id.isEmpty()) {
            throw new InvalidTransactionException("empty transaction_id");
        }

        // Account number: trim, keep as text (preserve leading zeros), reject empty.
        String account = trimToEmpty(raw.accountNumber());
        if (account.isEmpty()) {
            throw new InvalidTransactionException("empty account_number");
        }

        // Amount: numeric, non-negative, and a whole number of đồng (VND has no sub-unit).
        BigDecimal amount;
        try {
            amount = new BigDecimal(trimToEmpty(raw.amount()));
        } catch (NumberFormatException e) {
            throw new InvalidTransactionException("amount not numeric: " + raw.amount());
        }
        if (amount.signum() < 0) {
            throw new InvalidTransactionException("negative amount: " + raw.amount());
        }
        if (amount.stripTrailingZeros().scale() > 0) {
            throw new InvalidTransactionException("fractional amount (đồng has no sub-unit): " + raw.amount());
        }
        amount = amount.setScale(0);

        // Date: must parse as an offset datetime and not be in the future.
        OffsetDateTime date;
        try {
            date = OffsetDateTime.parse(trimToEmpty(raw.transactionDate()));
        } catch (DateTimeParseException e) {
            throw new InvalidTransactionException("unparseable transaction_date: " + raw.transactionDate());
        }
        if (date.toInstant().isAfter(Instant.now())) {
            throw new InvalidTransactionException("future-dated transaction_date: " + raw.transactionDate());
        }

        // Response code: exactly 2 digits AND present in the reference table.
        String code = trimToEmpty(raw.responseCode());
        if (!RC_FORMAT.matcher(code).matches()) {
            throw new InvalidTransactionException("response_code not 2 digits: " + raw.responseCode());
        }
        if (!knownCodes.contains(code)) {
            throw new InvalidTransactionException("unknown response_code: " + code);
        }

        // Status: normalize to the enum.
        TransactionStatus status;
        try {
            status = TransactionStatus.fromRaw(raw.status());
        } catch (IllegalArgumentException e) {
            throw new InvalidTransactionException(e.getMessage());
        }

        // Consistency: 00 <=> SUCCESS, every other code <=> FAILED.
        TransactionStatus expected = code.equals("00") ? TransactionStatus.SUCCESS : TransactionStatus.FAILED;
        if (status != expected) {
            throw new InvalidTransactionException(
                    "status " + status + " contradicts response_code " + code);
        }

        Transaction tx = new Transaction();
        tx.setTransactionId(id);
        tx.setAccountNumber(account);
        tx.setAmount(amount);
        tx.setTransactionDate(date);
        tx.setResponseCode(code);
        tx.setStatus(status);
        return tx;
    }

    private static String trimToEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
