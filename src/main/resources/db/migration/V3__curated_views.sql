-- V3: curated views — the ONLY surface the LLM's text-to-SQL may read.
-- Do NOT set security_invoker = true: with the Postgres default (false) the view
-- reads base tables with the view OWNER's privileges, so the read-only role (V4)
-- needs no base-table grant. That property is the security boundary.

-- Denormalized workhorse: one row per transaction, joined to its code, with derived
-- boolean/label/date columns so the LLM rarely needs joins or window functions.
CREATE VIEW v_transactions AS
SELECT
    t.transaction_id,
    t.account_number,
    t.amount,
    t.transaction_date,
    t.status,
    t.response_code,
    rc.meaning   AS response_meaning,
    rc.handling  AS response_handling,
    (t.response_code =  '00') AS is_success,
    (t.response_code <> '00') AS is_failed,
    date(t.transaction_date)                     AS transaction_day,
    extract(year  FROM t.transaction_date)::int  AS txn_year,
    extract(month FROM t.transaction_date)::int  AS txn_month,
    row_number() OVER (
        PARTITION BY t.account_number
        ORDER BY t.transaction_date DESC, t.transaction_id DESC
    ) AS recency_rank
FROM transactions t
JOIN response_codes rc ON rc.rc = t.response_code;

-- Per-account rollup: counts, totals, failure rate, first/last activity.
CREATE VIEW v_account_summary AS
SELECT
    account_number,
    count(*)                                        AS total_transactions,
    count(*) FILTER (WHERE response_code <> '00')   AS failed_count,
    count(*) FILTER (WHERE response_code =  '00')   AS success_count,
    sum(amount)                                     AS total_amount,
    sum(amount) FILTER (WHERE response_code = '00') AS total_success_amount,
    max(transaction_date)                           AS last_transaction_date,
    min(transaction_date)                           AS first_transaction_date,
    round(100.0 * count(*) FILTER (WHERE response_code <> '00')
          / nullif(count(*), 0), 2)                 AS failure_rate_pct
FROM transactions
GROUP BY account_number;

-- Per-code frequency. LEFT JOIN so zero-occurrence codes still appear.
CREATE VIEW v_response_code_stats AS
SELECT
    rc.rc,
    rc.meaning,
    rc.handling,
    count(t.transaction_id)          AS occurrence_count,
    count(DISTINCT t.account_number) AS affected_accounts,
    coalesce(sum(t.amount), 0)       AS total_amount
FROM response_codes rc
LEFT JOIN transactions t ON t.response_code = rc.rc
GROUP BY rc.rc, rc.meaning, rc.handling;

-- Per-day rollup for trend questions.
CREATE VIEW v_daily_stats AS
SELECT
    date(transaction_date)                          AS transaction_day,
    count(*)                                        AS total_transactions,
    count(*) FILTER (WHERE response_code <> '00')   AS failed_count,
    count(*) FILTER (WHERE response_code =  '00')   AS success_count,
    sum(amount)                                     AS total_amount
FROM transactions
GROUP BY date(transaction_date)
ORDER BY 1;
