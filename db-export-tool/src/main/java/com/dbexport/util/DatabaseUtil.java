package com.dbexport.util;

import com.dbexport.model.DatabaseInfo;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class DatabaseUtil {

    public static String buildJdbcUrl(DatabaseInfo dbInfo) {
        String dbType = dbInfo.getDbType() == null ? "mysql" : dbInfo.getDbType().toLowerCase();
        String host = dbInfo.getHost();
        Integer port = dbInfo.getPort();
        String databaseName = dbInfo.getDatabaseName();

        switch (dbType) {
            case "dm":
            case "dameng":
                return "jdbc:dm://" + host + ":" + port + "/" + databaseName;
            case "mysql":
            default:
                return "jdbc:mysql://" + host + ":" + port + "/" + databaseName 
                        + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
                        + "&useSSL=false&allowPublicKeyRetrieval=true";
        }
    }

    public static String getDriverClassName(DatabaseInfo dbInfo) {
        String dbType = dbInfo.getDbType() == null ? "mysql" : dbInfo.getDbType().toLowerCase();
        switch (dbType) {
            case "dm":
            case "dameng":
                return "dm.jdbc.driver.DmDriver";
            case "mysql":
            default:
                return "com.mysql.cj.jdbc.Driver";
        }
    }

    public static HikariDataSource createDataSource(DatabaseInfo dbInfo) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(buildJdbcUrl(dbInfo));
        config.setUsername(dbInfo.getUsername());
        config.setPassword(dbInfo.getPassword());
        config.setDriverClassName(getDriverClassName(dbInfo));
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(1800000);
        config.setMaxLifetime(7200000);
        config.setLeakDetectionThreshold(60000);
        config.setReadOnly(true);
        return new HikariDataSource(config);
    }

    public static List<String> getTableNames(Connection conn) throws SQLException {
        List<String> tables = new ArrayList<>();
        DatabaseMetaData metaData = conn.getMetaData();
        try (ResultSet rs = metaData.getTables(conn.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME"));
            }
        }
        return tables;
    }

    public static List<String> getColumnNames(Connection conn, String tableName) throws SQLException {
        List<String> columns = new ArrayList<>();
        DatabaseMetaData metaData = conn.getMetaData();
        try (ResultSet rs = metaData.getColumns(conn.getCatalog(), null, tableName, "%")) {
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME"));
            }
        }
        return columns;
    }

    public static boolean testConnection(DatabaseInfo dbInfo) {
        try (HikariDataSource ds = createDataSource(dbInfo);
             Connection conn = ds.getConnection()) {
            return conn.isValid(5);
        } catch (Exception e) {
            log.error("数据库连接测试失败", e);
            return false;
        }
    }
}
