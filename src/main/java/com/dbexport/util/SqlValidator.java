package com.dbexport.util;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class SqlValidator {

    private static final List<String> BLACKLIST = Arrays.asList(
            "INSERT", "UPDATE", "DELETE", "DROP", "CREATE", "ALTER", "TRUNCATE",
            "EXEC", "EXECUTE", "GRANT", "REVOKE", "MERGE", "CALL"
    );

    private static final Pattern COMMENT_PATTERN = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT_PATTERN = Pattern.compile("--.*$", Pattern.MULTILINE);
    private static final Pattern SEMICOLON_PATTERN = Pattern.compile(";");

    public static ValidationResult validate(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return ValidationResult.error("SQL语句不能为空");
        }

        String cleaned = sql.trim().toUpperCase();

        cleaned = COMMENT_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = LINE_COMMENT_PATTERN.matcher(cleaned).replaceAll("");

        if (SEMICOLON_PATTERN.matcher(cleaned).find()) {
            return ValidationResult.error("SQL语句中不允许包含分号");
        }

        if (!cleaned.startsWith("SELECT")) {
            return ValidationResult.error("仅允许SELECT查询语句");
        }

        for (String keyword : BLACKLIST) {
            String regex = "\\b" + keyword + "\\b";
            if (Pattern.compile(regex).matcher(cleaned).find()) {
                return ValidationResult.error("SQL语句中包含不允许的关键字: " + keyword);
            }
        }

        return ValidationResult.ok();
    }

    public static String extractTableName(String sql) {
        String upper = sql.toUpperCase().trim();
        int fromIndex = upper.indexOf("FROM");
        if (fromIndex == -1) {
            return "UNKNOWN";
        }
        String afterFrom = sql.substring(fromIndex + 4).trim();
        String[] parts = afterFrom.split("\\s+");
        if (parts.length > 0) {
            return parts[0].replaceAll("[;\"'`]", "");
        }
        return "UNKNOWN";
    }

    public static class ValidationResult {
        private final boolean valid;
        private final String message;

        private ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public static ValidationResult ok() {
            return new ValidationResult(true, "SQL校验通过");
        }

        public static ValidationResult error(String message) {
            return new ValidationResult(false, message);
        }

        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
    }
}
