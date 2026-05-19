package com.dbexport.model;

import lombok.Data;

@Data
public class DatabaseDriverInfo {
    private String id;
    private String fileName;
    private String driverClassName;
    private String detectedType;
    private String detectedDialect;
    private String displayName;
    private Integer defaultPort;
    private Long uploadedAt;
    private String message;
}
