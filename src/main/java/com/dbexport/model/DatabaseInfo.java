package com.dbexport.model;

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
    private Integer maxConnections;

    public String buildUrl() {
        if (url != null && !url.isEmpty()) {
            return url;
        }
        if ("dm".equalsIgnoreCase(type)) {
            return "jdbc:dm://" + host + ":" + port + "/" + databaseName;
        } else if ("mysql".equalsIgnoreCase(type)) {
            return "jdbc:mysql://" + host + ":" + port + "/" + databaseName + "?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai";
        } else {
            return url;
        }
    }

    public String resolveDriverClass() {
        if (driverClass != null && !driverClass.isEmpty()) {
            return driverClass;
        }
        if ("dm".equalsIgnoreCase(type)) {
            return "dm.jdbc.driver.DmDriver";
        } else if ("mysql".equalsIgnoreCase(type)) {
            return "com.mysql.cj.jdbc.Driver";
        }
        return driverClass;
    }
}
