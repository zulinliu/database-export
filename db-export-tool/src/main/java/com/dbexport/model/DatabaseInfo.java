package com.dbexport.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DatabaseInfo {

    private String type;
    private String host;
    private Integer port;
    private String databaseName;
    private String username;
    private String password;

    public DatabaseInfo() {
    }

    public DatabaseInfo(String type, String host, Integer port, String databaseName,
                        String username, String password) {
        this.type = type;
        this.host = host;
        this.port = port;
        this.databaseName = databaseName;
        this.username = username;
        this.password = password;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String toJdbcUrl() {
        if (type == null || type.isEmpty()) {
            throw new IllegalStateException("Database type is not set");
        }
        if (host == null || host.isEmpty()) {
            throw new IllegalStateException("Database host is not set");
        }
        if (port == null) {
            throw new IllegalStateException("Database port is not set");
        }

        switch (type.toLowerCase()) {
            case "dm":
                return "jdbc:dm://" + host + ":" + port
                        + (databaseName != null ? "/" + databaseName : "");
            case "mysql":
                return "jdbc:mysql://" + host + ":" + port
                        + (databaseName != null ? "/" + databaseName
                            + "?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai"
                            : "?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai");
            case "custom":
                return "jdbc:" + type + "://" + host + ":" + port
                        + (databaseName != null ? "/" + databaseName : "");
            default:
                throw new IllegalArgumentException("Unsupported database type: " + type);
        }
    }

    public String getDriverClass() {
        if (type == null || type.isEmpty()) {
            throw new IllegalStateException("Database type is not set");
        }

        switch (type.toLowerCase()) {
            case "dm":
                return "dm.jdbc.driver.DmDriver";
            case "mysql":
                return "com.mysql.cj.jdbc.Driver";
            case "custom":
                return "";
            default:
                throw new IllegalArgumentException("Unsupported database type: " + type);
        }
    }
}