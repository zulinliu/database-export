package com.dbexport.model;

import com.dbexport.util.DatabaseDialect;
import lombok.Data;

@Data
public class DatabaseInfo {
    private String type;
    private String host;
    private Integer port;
    private String databaseName;
    private String username;
    private String password;
    private String driverClass;
    private String url;
    private String customDriverId;
    private String detectedType;
    private String dialect;
    private String databaseProductName;
    private String databaseProductVersion;
    private String jdbcDriverName;
    private String jdbcDriverVersion;
    private Integer maxConnections;

    public String buildUrl() {
        if (url != null && !url.isEmpty()) {
            if (resolveDialect().isDm() && !url.contains("characterEncoding")) {
                return url + (url.contains("?") ? "&" : "?") + "characterEncoding=utf-8";
            }
            return url;
        }
        return resolveDialect().buildJdbcUrl(host, port, databaseName);
    }

    public String resolveDriverClass() {
        if (driverClass != null && !driverClass.isEmpty()) {
            return driverClass;
        }
        return resolveDialect().getDefaultDriverClass();
    }

    public DatabaseDialect resolveDialect() {
        if (dialect != null && !dialect.trim().isEmpty()) {
            return DatabaseDialect.fromType(dialect);
        }
        if (detectedType != null && !detectedType.trim().isEmpty()) {
            return DatabaseDialect.fromType(detectedType);
        }
        return DatabaseDialect.fromType(type);
    }

    public boolean hasCustomDriver() {
        return customDriverId != null && !customDriverId.trim().isEmpty();
    }
}
