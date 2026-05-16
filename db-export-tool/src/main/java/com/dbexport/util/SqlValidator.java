package com.dbexport.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SqlValidator {

    private static final Set<String> WHITELIST_KEYWORDS = new HashSet<>(Arrays.asList(
            "SELECT", "FROM", "WHERE", "JOIN", "INNER", "LEFT", "RIGHT", "OUTER",
            "ON", "AND", "OR", "NOT", "IN", "LIKE", "BETWEEN", "IS", "NULL", "AS",
            "ORDER", "BY", "GROUP", "HAVING", "LIMIT", "OFFSET", "DISTINCT",
            "UNION", "ALL", "CASE", "WHEN", "THEN", "ELSE", "END", "EXISTS",
            "COUNT", "SUM", "AVG", "MAX", "MIN", "COALESCE", "CAST", "CONCAT", "SUBSTR"
    ));

    private static final Set<String> BLACKLIST_KEYWORDS = new HashSet<>(Arrays.asList(
            "INSERT", "UPDATE", "DELETE", "DROP", "CREATE", "ALTER", "TRUNCATE",
            "EXEC", "EXECUTE", "GRANT", "REVOKE", "MERGE", "REPLACE", "LOAD",
            "CALL", "DO", "HANDLER", "IMPORT", "RENAME", "SET", "LOCK", "UNLOCK",
            "COMMIT", "ROLLBACK"
    ));

    private static final Pattern KEYWORD_PATTERN = Pattern.compile("\\b([A-Za-z_]+)\\b");
    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile(
            "SELECT\\s+.*?\\s+FROM\\s+([A-Za-z0-9_`\".]+)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private SqlValidator() {
    }

    public static ValidationResult validate(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return new ValidationResult(false, "SQL statement is null or empty");
        }

        String cleaned = removeComments(sql);
        cleaned = cleaned.trim();

        if (cleaned.isEmpty()) {
            return new ValidationResult(false, "SQL statement contains only comments");
        }

        if (cleaned.contains(";")) {
            return new ValidationResult(false, "SQL statement contains semicolon (multi-statement not allowed)");
        }

        String normalized = cleaned.toUpperCase().trim();
        if (!normalized.startsWith("SELECT")) {
            return new ValidationResult(false, "Only SELECT statements are allowed");
        }

        Matcher matcher = KEYWORD_PATTERN.matcher(cleaned);
        while (matcher.find()) {
            String word = matcher.group(1).toUpperCase();
            if (BLACKLIST_KEYWORDS.contains(word)) {
                return new ValidationResult(false, "Forbidden keyword detected: " + word);
            }
        }

        return new ValidationResult(true, "OK");
    }

    public static List<ValidationResult> validateMultiple(List<String> sqls) {
        List<ValidationResult> results = new ArrayList<>();
        if (sqls != null) {
            for (int i = 0; i < sqls.size(); i++) {
                ValidationResult result = validate(sqls.get(i));
                results.add(result);
            }
        }
        return results;
    }

    public static String extractTableName(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return null;
        }

        String cleaned = removeComments(sql);
        Matcher matcher = TABLE_NAME_PATTERN.matcher(cleaned);
        if (matcher.find()) {
            String tableName = matcher.group(1);
            tableName = tableName.replaceAll("[`\"]", "").trim();
            return tableName;
        }
        return null;
    }

    private static String removeComments(String sql) {
        if (sql == null) {
            return null;
        }

        String cleaned = sql.replaceAll("/\\*.*?\\*/", " ");

        cleaned = cleaned.replaceAll("--[^\n]*", " ");

        return cleaned;
    }

    public static class ValidationResult {
        private final boolean valid;
        private final String message;

        public ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return "ValidationResult{valid=" + valid + ", message='" + message + "'}";
        }
    }
}