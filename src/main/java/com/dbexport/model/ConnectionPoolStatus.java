package com.dbexport.model;

import lombok.Data;

@Data
public class ConnectionPoolStatus {
    private int activeConnections;
    private int idleConnections;
    private int totalConnections;
    private int maxConnections;
    private int threadsAwaiting;
    private String status;
    private long lastUpdated;
}
