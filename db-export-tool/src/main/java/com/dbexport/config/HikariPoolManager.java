package com.dbexport.config;

import com.dbexport.model.DatabaseInfo;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

import java.util.HashMap;
import java.util.Map;

public class HikariPoolManager {

    private static volatile HikariPoolManager instance;

    private HikariPoolManager() {
    }

    public static HikariPoolManager getInstance() {
        if (instance == null) {
            synchronized (HikariPoolManager.class) {
                if (instance == null) {
                    instance = new HikariPoolManager();
                }
            }
        }
        return instance;
    }

    public HikariDataSource createPool(DatabaseInfo dbInfo, int maxPoolSize) {
        HikariConfig config = new HikariConfig();

        String driverClass = dbInfo.getDriverClass();
        if (driverClass != null && !driverClass.isEmpty()) {
            config.setDriverClassName(driverClass);
        }

        config.setJdbcUrl(dbInfo.toJdbcUrl());
        config.setUsername(dbInfo.getUsername());
        config.setPassword(dbInfo.getPassword());
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("HikariPool-" + dbInfo.getType() + "-" + dbInfo.getDatabaseName());

        return new HikariDataSource(config);
    }

    public void closePool(HikariDataSource ds) {
        if (ds != null && !ds.isClosed()) {
            ds.close();
        }
    }

    public Map<String, Integer> getPoolStatus(HikariDataSource ds) {
        Map<String, Integer> status = new HashMap<>();
        if (ds == null || ds.isClosed()) {
            status.put("active", 0);
            status.put("total", 0);
            status.put("idle", 0);
            status.put("waiting", 0);
            return status;
        }

        HikariPoolMXBean poolMXBean = ds.getHikariPoolMXBean();
        if (poolMXBean != null) {
            status.put("active", poolMXBean.getActiveConnections());
            status.put("total", poolMXBean.getTotalConnections());
            status.put("idle", poolMXBean.getIdleConnections());
            status.put("waiting", poolMXBean.getThreadsAwaitingConnection());
        } else {
            status.put("active", 0);
            status.put("total", 0);
            status.put("idle", 0);
            status.put("waiting", 0);
        }

        return status;
    }
}