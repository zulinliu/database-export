package com.dbexport.util;

import java.util.Locale;

public enum DatabaseDialect {
    DM("dm", "达梦数据库", 5236, "dm.jdbc.driver.DmDriver", "jdbc:dm://%s:%d/%s?characterEncoding=utf-8", "\"", "\"", "SELECT 1"),
    KINGBASE("kingbase", "金仓 KingbaseES", 54321, "com.kingbase8.Driver", "jdbc:kingbase8://%s:%d/%s", "\"", "\"", "SELECT 1"),
    MYSQL("mysql", "MySQL", 3306, "com.mysql.cj.jdbc.Driver", "jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai", "`", "`", "SELECT 1"),
    POSTGRESQL("postgresql", "PostgreSQL", 5432, "org.postgresql.Driver", "jdbc:postgresql://%s:%d/%s", "\"", "\"", "SELECT 1"),
    ORACLE("oracle", "Oracle", 1521, "oracle.jdbc.OracleDriver", "jdbc:oracle:thin:@%s:%d:%s", "\"", "\"", "SELECT 1 FROM DUAL"),
    SQLSERVER("sqlserver", "SQL Server", 1433, "com.microsoft.sqlserver.jdbc.SQLServerDriver", "jdbc:sqlserver://%s:%d;databaseName=%s;encrypt=false", "[", "]", "SELECT 1"),
    GENERIC("custom", "自定义数据库", null, null, null, "\"", "\"", "SELECT 1");

    private final String type;
    private final String displayName;
    private final Integer defaultPort;
    private final String defaultDriverClass;
    private final String jdbcUrlPattern;
    private final String quotePrefix;
    private final String quoteSuffix;
    private final String connectionTestQuery;

    DatabaseDialect(String type, String displayName, Integer defaultPort, String defaultDriverClass,
                    String jdbcUrlPattern, String quotePrefix, String quoteSuffix, String connectionTestQuery) {
        this.type = type;
        this.displayName = displayName;
        this.defaultPort = defaultPort;
        this.defaultDriverClass = defaultDriverClass;
        this.jdbcUrlPattern = jdbcUrlPattern;
        this.quotePrefix = quotePrefix;
        this.quoteSuffix = quoteSuffix;
        this.connectionTestQuery = connectionTestQuery;
    }

    public String getType() {
        return type;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Integer getDefaultPort() {
        return defaultPort;
    }

    public String getDefaultDriverClass() {
        return defaultDriverClass;
    }

    public String getConnectionTestQuery() {
        return connectionTestQuery;
    }

    public boolean canBuildUrl() {
        return jdbcUrlPattern != null;
    }

    public boolean isDm() {
        return this == DM;
    }

    public String buildJdbcUrl(String host, Integer port, String databaseName) {
        if (!canBuildUrl() || host == null || host.trim().isEmpty()) {
            return null;
        }
        Integer actualPort = port != null ? port : defaultPort;
        if (actualPort == null) {
            return null;
        }
        String dbName = databaseName == null ? "" : databaseName.trim();
        return String.format(jdbcUrlPattern, host.trim(), actualPort, dbName);
    }

    public String quoteIdentifier(String identifier) {
        if (identifier == null) {
            return "";
        }
        String escaped = identifier;
        if ("[".equals(quotePrefix)) {
            escaped = escaped.replace("]", "]]");
        } else {
            escaped = escaped.replace(quoteSuffix, quoteSuffix + quoteSuffix);
        }
        return quotePrefix + escaped + quoteSuffix;
    }

    public String toDateLiteral(String dateStr) {
        String escaped = escapeSql(dateStr);
        if (this == DM || this == ORACLE) {
            if (dateStr != null && dateStr.length() <= 10) {
                return "TO_DATE('" + escaped + "', 'YYYY-MM-DD')";
            }
            return "TO_DATE('" + escaped + "', 'YYYY-MM-DD HH24:MI:SS')";
        }
        if (this == SQLSERVER) {
            return "CONVERT(datetime, '" + escaped + "', 120)";
        }
        return "'" + escaped + "'";
    }

    public String toBooleanLiteral(boolean value) {
        if (this == DM || this == ORACLE || this == SQLSERVER) {
            return value ? "1" : "0";
        }
        return value ? "TRUE" : "FALSE";
    }

    public String toBinaryLiteral(byte[] value) {
        String hex = toHex(value);
        if (this == POSTGRESQL || this == KINGBASE) {
            return "E'\\\\x" + hex + "'";
        }
        return "X'" + hex + "'";
    }

    public static String escapeSql(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    public static DatabaseDialect fromType(String type) {
        if (type == null || type.trim().isEmpty()) {
            return GENERIC;
        }
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        for (DatabaseDialect dialect : values()) {
            if (dialect.type.equals(normalized)) {
                return dialect;
            }
        }
        if ("dm6".equals(normalized) || "dm7".equals(normalized) || "dm8".equals(normalized) || "dameng".equals(normalized)) {
            return DM;
        }
        if ("kingbasees".equals(normalized) || "kingbase8".equals(normalized) || "kingbase".equals(normalized)) {
            return KINGBASE;
        }
        return GENERIC;
    }

    public static DatabaseDialect fromDriverClass(String driverClassName) {
        if (driverClassName == null) {
            return GENERIC;
        }
        String name = driverClassName.toLowerCase(Locale.ROOT);
        if (name.contains("dm.jdbc") || name.contains("dmdriver")) {
            return DM;
        }
        if (name.contains("kingbase")) {
            return KINGBASE;
        }
        if (name.contains("mysql")) {
            return MYSQL;
        }
        if (name.contains("postgresql")) {
            return POSTGRESQL;
        }
        if (name.contains("oracle")) {
            return ORACLE;
        }
        if (name.contains("sqlserver")) {
            return SQLSERVER;
        }
        return GENERIC;
    }

    public static DatabaseDialect fromMetadata(String productName, String productVersion,
                                               String driverName, String fallbackType) {
        String combined = ((productName == null ? "" : productName) + " " +
                (productVersion == null ? "" : productVersion) + " " +
                (driverName == null ? "" : driverName)).toLowerCase(Locale.ROOT);
        if (combined.contains("dm dbms") || combined.contains("dameng") || combined.contains("达梦")) {
            return DM;
        }
        if (combined.contains("kingbase") || combined.contains("人大金仓") || combined.contains("金仓")) {
            return KINGBASE;
        }
        if (combined.contains("mysql")) {
            return MYSQL;
        }
        if (combined.contains("postgresql")) {
            return POSTGRESQL;
        }
        if (combined.contains("oracle")) {
            return ORACLE;
        }
        if (combined.contains("sql server") || combined.contains("microsoft")) {
            return SQLSERVER;
        }
        return fromType(fallbackType);
    }

    private static String toHex(byte[] value) {
        StringBuilder sb = new StringBuilder();
        for (byte b : value) {
            String hex = Integer.toHexString(b & 0xff).toUpperCase(Locale.ROOT);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }
}
