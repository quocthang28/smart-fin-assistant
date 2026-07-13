-- V4: read-only DB role — the REAL boundary for LLM-authored SQL.
-- Runs as the app/owner role. Password is injected via a Flyway placeholder
-- (${readonly_password}) so the cloud path is not hardcoded. Phase 3 binds a
-- second DataSource authenticating as this role; Phase 1 only ships role + grants.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'finassist_readonly') THEN
        CREATE ROLE finassist_readonly LOGIN PASSWORD '${readonly_password}';
    END IF;
END $$;

GRANT USAGE ON SCHEMA public TO finassist_readonly;

-- SELECT on the curated views ONLY — never on base tables.
GRANT SELECT ON v_transactions, v_account_summary,
                v_response_code_stats, v_daily_stats
    TO finassist_readonly;

-- Belt-and-suspenders: ensure no accidental base-table access.
REVOKE ALL ON transactions, response_codes FROM finassist_readonly;
