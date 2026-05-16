package com.dbexport.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class SqlValidator {

    private static final Set<String> ALLOWED_KEYWORDS = new HashSet<>(Arrays.asList(
            "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "IN", "IS", "NULL", "LIKE",
            "BETWEEN", "AND", "ORDER", "BY", "ASC", "DESC", "LIMIT", "OFFSET",
            "GROUP", "HAVING", "JOIN", "INNER", "LEFT", "RIGHT", "OUTER", "ON",
            "AS", "DISTINCT", "ALL", "ANY", "SOME", "EXISTS", "CASE", "WHEN",
            "THEN", "ELSE", "END", "COALESCE", "IF", "IFNULL", "NULLIF",
            "COUNT", "SUM", "AVG", "MIN", "MAX", "ROUND", "TRUNC", "ABS",
            "CONCAT", "SUBSTR", "SUBSTRING", "LENGTH", "CHAR_LENGTH",
            "LOWER", "UPPER", "TRIM", "LTRIM", "RTRIM", "REPLACE",
            "CAST", "CONVERT", "TO_CHAR", "TO_DATE", "TO_NUMBER",
            "NOW", "CURRENT_DATE", "CURRENT_TIME", "CURRENT_TIMESTAMP",
            "DATE", "TIME", "DATETIME", "TIMESTAMP", "INTERVAL",
            "YEAR", "MONTH", "DAY", "HOUR", "MINUTE", "SECOND",
            "WITH", "UNION", "EXCEPT", "INTERSECT"
    ));

    private static final Set<String> BLOCKED_KEYWORDS = new HashSet<>(Arrays.asList(
            "INSERT", "UPDATE", "DELETE", "DROP", "TRUNCATE", "ALTER", "CREATE",
            "REPLACE", "MERGE", "GRANT", "REVOKE", "COMMIT", "ROLLBACK",
            "SAVEPOINT", "LOCK", "UNLOCK", "CALL", "EXECUTE", "EXEC",
            "INTO", "VALUES", "SET", "USE", "SHOW", "DESCRIBE", "DESC",
            "EXPLAIN", "ANALYZE", "OPTIMIZE", "REPAIR", "CHECK", "BACKUP",
            "RESTORE", "LOAD", "INFILE", "OUTFILE", "DUMPFILE",
            "MASTER", "SLAVE", "RESET", "START", "STOP", "PURGE",
            "FLUSH", "KILL", "SHUTDOWN", "PROCESSLIST", "PROCESS",
            "SUPER", "FILE", "ADMIN", "PRIVILEGES", "GRANT", "REVOKE"
    ));

    private static final Pattern COMMENT_PATTERN = Pattern.compile(
            "(?s)/\\*.*?\\*/|--.*?$|#.*?$",
            Pattern.MULTILINE
    );

    public static boolean validateSelectSql(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return false;
        }

        String normalizedSql = normalizeSql(sql);

        if (normalizedSql.contains(";")) {
            return false;
        }

        if (!normalizedSql.toUpperCase().startsWith("SELECT")) {
            return false;
        }

        String[] tokens = tokenize(normalizedSql);
        for (String token : tokens) {
            String upperToken = token.toUpperCase();
            if (BLOCKED_KEYWORDS.contains(upperToken)) {
                return false;
            }
        }

        return true;
    }

    private static String normalizeSql(String sql) {
        String noComments = COMMENT_PATTERN.matcher(sql).replaceAll(" ");
        return noComments.trim().replaceAll("\\s+", " ");
    }

    private static String[] tokenize(String sql) {
        return sql.split("\\s+|\\(|\\)|,|;|=|<>|!=|<=|>=|<|>|\\+|-|\\*|/|%|\\||&|\\^|~");
    }
}
