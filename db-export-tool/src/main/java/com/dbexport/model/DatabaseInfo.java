package com.dbexport.model;

import lombok.Data;

@Data
public class DatabaseInfo {
    private String dbType;
    private String host;
    private Integer port;
    private String databaseName;
    private String username;
    private String password;
}
