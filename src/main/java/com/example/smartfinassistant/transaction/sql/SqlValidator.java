package com.example.smartfinassistant.transaction.sql;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.TableFunction;
import net.sf.jsqlparser.statement.select.WithItem;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.stereotype.Component;

@Component
public class SqlValidator {

    static final Set<String> ALLOWED_VIEWS = Set.of(
            "v_transactions",
            "v_account_summary",
            "v_response_code_stats",
            "v_daily_stats");

    private static final Set<String> UNSAFE_FUNCTIONS = Set.of(
            "pg_sleep",
            "pg_read_file",
            "pg_read_binary_file",
            "pg_ls_dir",
            "pg_stat_file",
            "dblink",
            "dblink_exec",
            "lo_import",
            "lo_export",
            "set_config",
            "current_setting",
            "nextval",
            "setval",
            "currval",
            "query_to_xml",
            "database_to_xml",
            "table_to_xml");

    private final SqlProperties properties;

    public SqlValidator(SqlProperties properties) {
        this.properties = properties;
    }

    public ValidatedSql validate(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new SqlPolicyException("SQL must not be blank");
        }

        Statements statements;
        try {
            statements = CCJSqlParserUtil.parseStatements(sql);
        } catch (JSQLParserException e) {
            throw new SqlSyntaxException("SQL could not be parsed", e);
        }

        if (statements.size() != 1) {
            throw new SqlPolicyException("Exactly one SQL statement is required");
        }

        Statement statement = statements.getFirst();
        if (!(statement instanceof Select select)) {
            throw new SqlPolicyException("Only SELECT statements are allowed");
        }
        if (!(select instanceof PlainSelect plainSelect)) {
            throw new SqlPolicyException("Only a plain SELECT, optionally with CTEs, is allowed");
        }
        rejectWriteAndLockingForms(select, plainSelect);

        PolicyVisitor visitor = new PolicyVisitor();
        visitor.visit(select, null);
        if (visitor.tableFunction) {
            throw new SqlPolicyException("Table functions are not allowed");
        }
        if (!visitor.unsafeFunctions.isEmpty()) {
            throw new SqlPolicyException("Unsafe database functions are not allowed");
        }

        Set<String> referencedViews = validateRelations(visitor.relations, visitor.cteAliases);
        if (referencedViews.isEmpty()) {
            throw new SqlPolicyException("The query must read at least one curated view");
        }

        enforceLimit(select);
        return new ValidatedSql(select.toString(), referencedViews);
    }

    private void rejectWriteAndLockingForms(Select select, PlainSelect plainSelect) {
        if ((plainSelect.getIntoTables() != null && !plainSelect.getIntoTables().isEmpty())
                || plainSelect.getIntoTempTable() != null) {
            throw new SqlPolicyException("SELECT INTO is not allowed");
        }
        if (select.getForMode() != null
                || select.getForUpdateTable() != null
                || select.isSkipLocked()
                || select.getWait() != null) {
            throw new SqlPolicyException("Row-locking SELECT statements are not allowed");
        }
        if (plainSelect.getTop() != null) {
            throw new SqlPolicyException("TOP is not supported; use LIMIT");
        }
    }

    private Set<String> validateRelations(Set<String> relations, Set<String> cteAliases) {
        Set<String> referencedViews = new LinkedHashSet<>();
        for (String relation : relations) {
            String normalized = normalizeIdentifier(relation);
            if (cteAliases.contains(normalized)) {
                continue;
            }

            String viewName = normalized;
            int separator = normalized.indexOf('.');
            if (separator >= 0) {
                String schema = normalized.substring(0, separator);
                viewName = normalized.substring(separator + 1);
                if (!schema.equals("public") || viewName.contains(".")) {
                    throw new SqlPolicyException("Only the public curated views are allowed");
                }
            }
            if (!ALLOWED_VIEWS.contains(viewName)) {
                throw new SqlPolicyException("Relation is outside the curated-view whitelist");
            }
            referencedViews.add(viewName);
        }
        return referencedViews;
    }

    private void enforceLimit(Select select) {
        long maximumRows = properties.maximumRows();
        Limit limit = select.getLimit();
        if (limit == null
                || limit.isLimitAll()
                || !(limit.getRowCount() instanceof LongValue rowCount)
                || rowCount.getValue() > maximumRows) {
            select.setLimit(new Limit().withRowCount(new LongValue(maximumRows)));
        }
        select.setFetch(null);
    }

    private static String normalizeIdentifier(String identifier) {
        return identifier.replace("\"", "").toLowerCase(Locale.ROOT);
    }

    private static final class PolicyVisitor extends TablesNamesFinder<Void> {

        private final Set<String> relations = new LinkedHashSet<>();
        private final Set<String> cteAliases = new LinkedHashSet<>();
        private final Set<String> unsafeFunctions = new LinkedHashSet<>();
        private boolean tableFunction;

        private PolicyVisitor() {
            init(false);
        }

        @Override
        public <S> Void visit(Table table, S context) {
            relations.add(table.getFullyQualifiedName());
            return super.visit(table, context);
        }

        @Override
        public <S> Void visit(WithItem<?> withItem, S context) {
            cteAliases.add(normalizeIdentifier(withItem.getUnquotedAliasName()));
            return super.visit(withItem, context);
        }

        @Override
        public <S> Void visit(Function function, S context) {
            String name = function.getName();
            if (name != null && UNSAFE_FUNCTIONS.contains(normalizeIdentifier(name))) {
                unsafeFunctions.add(name);
            }
            return super.visit(function, context);
        }

        @Override
        public <S> Void visit(TableFunction function, S context) {
            tableFunction = true;
            return super.visit(function, context);
        }
    }
}
