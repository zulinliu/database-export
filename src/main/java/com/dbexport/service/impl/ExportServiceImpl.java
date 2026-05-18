package com.dbexport.service.impl;

import com.dbexport.config.ExportConfigProperties;
import com.dbexport.model.*;
import com.dbexport.service.ExportService;
import com.dbexport.util.SqlSafeUtils;
import com.dbexport.util.SqlValidator;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ExportServiceImpl implements ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportServiceImpl.class);
    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Autowired
    private ExportConfigProperties configProps;

    @Autowired
    private ApplicationContext applicationContext;

    private HikariDataSource dataSource;
    private DatabaseInfo currentDbInfo;
    private final Map<String, ExportProgress> progressMap = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();
    private final ExecutorService exportWorkerPool = Executors.newCachedThreadPool();

    @Override
    public boolean testConnection(DatabaseInfo dbInfo) {
        Connection conn = null;
        try {
            Class.forName(dbInfo.resolveDriverClass());
            conn = DriverManager.getConnection(dbInfo.buildUrl(), dbInfo.getUsername(), dbInfo.getPassword());
            conn.setReadOnly(true);
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 1")) {
                return rs.next();
            }
        } catch (Exception e) {
            log.error("连接测试失败: {}", e.getMessage());
            return false;
        } finally {
            closeQuietly(conn);
        }
    }

    @Override
    public synchronized boolean connectDatabase(DatabaseInfo dbInfo) {
        if (dataSource != null) {
            closeDataSource();
        }

        try {
            HikariConfig hc = new HikariConfig();
            hc.setDriverClassName(dbInfo.resolveDriverClass());
            hc.setJdbcUrl(dbInfo.buildUrl());
            hc.setUsername(dbInfo.getUsername());
            hc.setPassword(dbInfo.getPassword());
            hc.setMaximumPoolSize(dbInfo.getMaxConnections() != null ? dbInfo.getMaxConnections() : 10);
            hc.setMinimumIdle(2);
            hc.setConnectionTimeout(30000);
            hc.setIdleTimeout(1800000);
            hc.setMaxLifetime(7200000);
            hc.setLeakDetectionThreshold(60000);
            hc.setReadOnly(true);
            hc.setConnectionTestQuery("SELECT 1");

            dataSource = new HikariDataSource(hc);
            currentDbInfo = dbInfo;
            columnCache.clear();

            try (Connection conn = dataSource.getConnection()) {
                log.info("数据库连接成功: {}", dbInfo.buildUrl());
            }
            return true;
        } catch (Exception e) {
            log.error("数据库连接失败: {}", e.getMessage());
            if (dataSource != null) {
                closeDataSource();
            }
            return false;
        }
    }

    @Override
    public List<DatabaseTableInfo> getTableList() {
        if (dataSource == null) {
            throw new RuntimeException("未连接数据库");
        }

        List<DatabaseTableInfo> tables = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            String dbType = currentDbInfo != null ? currentDbInfo.getType() : "mysql";
            if ("dm".equalsIgnoreCase(dbType)) {
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT TABLE_NAME FROM ALL_TABLES WHERE OWNER = ? ORDER BY TABLE_NAME")) {
                    stmt.setString(1, currentDbInfo.getUsername().toUpperCase());
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            DatabaseTableInfo info = new DatabaseTableInfo();
                            info.setTableName(rs.getString("TABLE_NAME"));
                            tables.add(info);
                        }
                    }
                }
            } else {
                DatabaseMetaData meta = conn.getMetaData();
                try (ResultSet rs = meta.getTables(conn.getCatalog(), conn.getSchema(), "%", new String[]{"TABLE"})) {
                    while (rs.next()) {
                        DatabaseTableInfo info = new DatabaseTableInfo();
                        info.setTableName(rs.getString("TABLE_NAME"));
                        tables.add(info);
                    }
                }
            }

            for (DatabaseTableInfo table : tables) {
                String tName = table.getTableName();
                if (!SqlSafeUtils.isValidIdentifier(tName)) {
                    table.setRowCount(-1);
                    continue;
                }
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM \"" + tName + "\"")) {
                    if (rs.next()) {
                        table.setRowCount(rs.getLong(1));
                    }
                } catch (Exception e) {
                    table.setRowCount(-1);
                }
            }
        } catch (Exception e) {
            log.error("获取表列表失败: {}", e.getMessage());
            throw new RuntimeException("获取表列表失败");
        }
        return tables;
    }

    @Override
    public ConnectionPoolStatus getConnectionPoolStatus() {
        ConnectionPoolStatus status = new ConnectionPoolStatus();
        if (dataSource == null) {
            status.setStatus("未连接");
            return status;
        }
        try {
            status.setActiveConnections(dataSource.getHikariPoolMXBean().getActiveConnections());
            status.setIdleConnections(dataSource.getHikariPoolMXBean().getIdleConnections());
            status.setTotalConnections(dataSource.getHikariPoolMXBean().getTotalConnections());
            status.setThreadsAwaiting(dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection());
            status.setMaxConnections(dataSource.getMaximumPoolSize());
            status.setLastUpdated(System.currentTimeMillis());
            status.setStatus("正常");
        } catch (Exception e) {
            status.setStatus("异常");
        }
        return status;
    }

    @Override
    public String startExport(ExportConfig config) {
        if (dataSource == null) {
            throw new RuntimeException("未连接数据库，请先连接数据库");
        }

        String taskId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        ExportProgress progress = new ExportProgress();
        progress.setTaskId(taskId);
        progress.setStatus("RUNNING");
        progress.setStartTime(System.currentTimeMillis());
        progressMap.put(taskId, progress);
        cancelFlags.put(taskId, new AtomicBoolean(false));

        ExportServiceImpl proxy = applicationContext.getBean(ExportServiceImpl.class);
        proxy.executeExportAsync(taskId, config);
        return taskId;
    }

    @Async("exportExecutor")
    public void executeExportAsync(String taskId, ExportConfig config) {
        ExportProgress progress = progressMap.get(taskId);
        try {
            progress.addLog("INFO", "开始导出任务...");

            List<String> tableNames = resolveTableNames(config);
            progress.setTotalTables(tableNames.size());
            progress.addLog("INFO", "共 " + tableNames.size() + " 个表需要导出");

            int maxConn = config.getMaxConnections() != null ? config.getMaxConnections() : 5;
            Semaphore semaphore = new Semaphore(maxConn);
            CountDownLatch latch = new CountDownLatch(tableNames.size());

            String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
            String dbName = currentDbInfo != null ? currentDbInfo.getDatabaseName() : "EXPORT";
            String safeDbName = dbName.replaceAll("[^A-Za-z0-9_]", "");
            String baseFileName = safeDbName + "_" + timestamp;
            Path exportDir = Paths.get(configProps.getStorage().getPath()).toAbsolutePath().normalize();
            Files.createDirectories(exportDir);

            Map<String, Path> csvFiles = new ConcurrentHashMap<>();
            Map<String, Path> sqlFiles = new ConcurrentHashMap<>();

            String formats = config.getExportFormats();
            boolean exportCsv = formats != null && (formats.contains("csv") || formats.contains("excel"));
            boolean exportSql = formats != null && formats.contains("sql");
            boolean sqlSingle = !"perTable".equals(config.getSqlFileMode());

            for (String tableName : tableNames) {
                if (cancelFlags.get(taskId).get()) {
                    progress.addLog("WARNING", "导出任务已取消");
                    latch.countDown();
                    continue;
                }

                try {
                    semaphore.acquire();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                String finalTableName = tableName;
                CompletableFuture.runAsync(() -> {
                    try {
                        if (cancelFlags.get(taskId).get()) return;

                        progress.setCurrentTable(finalTableName);
                        progress.setCurrentTableRows(0);
                        progress.addLog("INFO", "开始导出表: " + finalTableName);

                        String querySql = buildQuerySql(finalTableName, config);
                        long rowCount = exportTable(taskId, finalTableName, querySql, exportCsv, exportSql, csvFiles, sqlFiles);

                        progress.getTotalRows().addAndGet(rowCount);
                        progress.getCompletedTables().incrementAndGet();
                        progress.addLog("SUCCESS", finalTableName + " 导出成功 (" + String.format("%,d", rowCount) + " 条)");
                        progress.getSuccessCount().incrementAndGet();

                    } catch (Exception e) {
                        progress.getCompletedTables().incrementAndGet();
                        progress.addLog("ERROR", finalTableName + " 导出失败: " + e.getMessage());
                        progress.getFailCount().incrementAndGet();
                        log.error("导出表 {} 失败", finalTableName, e);
                    } finally {
                        latch.countDown();
                        semaphore.release();
                    }
                }, exportWorkerPool);
            }

            boolean completed = latch.await(configProps.getTimeout(), TimeUnit.SECONDS);

            if (cancelFlags.get(taskId).get()) {
                progress.setStatus("CANCELLED");
                progress.setEndTime(System.currentTimeMillis());
                progress.addLog("WARNING", "导出任务已取消");
                cleanupProgress(taskId);
                return;
            }

            if (!completed) {
                progress.setStatus("TIMEOUT");
                progress.setEndTime(System.currentTimeMillis());
                progress.addLog("ERROR", "导出超时");
                cleanupProgress(taskId);
                return;
            }

            progress.addLog("INFO", "正在打包文件...");

            Path zipPath = exportDir.resolve(baseFileName + ".zip");
            try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(zipPath)))) {
                for (Map.Entry<String, Path> entry : csvFiles.entrySet()) {
                    addToZip(zos, "csv/" + entry.getKey() + ".csv", entry.getValue());
                }
                if (!sqlFiles.isEmpty()) {
                    if (sqlSingle) {
                        Path mergedSql = mergeSqlFiles(sqlFiles);
                        addToZip(zos, "export.sql", mergedSql);
                        Files.deleteIfExists(mergedSql);
                    } else {
                        for (Map.Entry<String, Path> entry : sqlFiles.entrySet()) {
                            addToZip(zos, "sql/" + entry.getKey() + ".sql", entry.getValue());
                        }
                    }
                }
                csvFiles.values().forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
                sqlFiles.values().forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
            }

            progress.setFileName(baseFileName + ".zip");
            progress.setFileSize(Files.size(zipPath));
            progress.setStatus("COMPLETED");
            progress.setEndTime(System.currentTimeMillis());
            progress.addLog("SUCCESS", "导出完成！文件: " + baseFileName + ".zip (" +
                    String.format("%.1f", progress.getFileSize() / 1024.0 / 1024.0) + " MB)");

            cleanupProgress(taskId);

        } catch (Exception e) {
            progress.setStatus("FAILED");
            progress.setEndTime(System.currentTimeMillis());
            progress.setErrorMessage(e.getMessage());
            progress.addLog("ERROR", "导出失败: " + e.getMessage());
            log.error("导出任务失败", e);
            cleanupProgress(taskId);
        }
    }

    private long exportTable(String taskId, String tableName, String querySql,
                             boolean exportCsv, boolean exportSql,
                             Map<String, Path> csvFiles, Map<String, Path> sqlFiles) throws Exception {
        ExportProgress progress = progressMap.get(taskId);
        Path tempDir = Paths.get(configProps.getStorage().getTemp());
        Files.createDirectories(tempDir);
        long rowCount = 0;
        if (exportCsv) {
            rowCount = exportTableToCsv(taskId, tableName, querySql, progress, tempDir, csvFiles);
        }
        if (exportSql) {
            long sqlRowCount = exportTableToSql(taskId, tableName, querySql, progress, tempDir, sqlFiles);
            if (!exportCsv) rowCount = sqlRowCount;
        }
        return rowCount;
    }

    private long exportTableToCsv(String taskId, String tableName, String querySql,
                                   ExportProgress progress, Path tempDir,
                                   Map<String, Path> csvFiles) throws Exception {
        long rowCount = 0;
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
             ResultSet rs = stmt.executeQuery(querySql)) {
            stmt.setFetchSize(configProps.getFetchSize());
            conn.setReadOnly(true);
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            Path csvPath = tempDir.resolve(tableName + ".csv");
            try (BufferedWriter writer = Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8)) {
                writer.write('﻿');
                StringBuilder header = new StringBuilder();
                for (int i = 1; i <= columnCount; i++) {
                    if (i > 1) header.append(',');
                    header.append(escapeCsv(meta.getColumnLabel(i)));
                }
                writer.write(header.toString());
                writer.write('\n');
                while (rs.next()) {
                    if (cancelFlags.get(taskId).get()) break;
                    StringBuilder row = new StringBuilder();
                    for (int i = 1; i <= columnCount; i++) {
                        if (i > 1) row.append(',');
                        row.append(escapeCsv(rs.getString(i)));
                    }
                    writer.write(row.toString());
                    writer.write('\n');
                    rowCount++;
                    if (rowCount % 1000 == 0) progress.setCurrentTableRows(rowCount);
                }
            }
            csvFiles.put(tableName, csvPath);
        }
        return rowCount;
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private long exportTableToSql(String taskId, String tableName, String querySql,
                                  ExportProgress progress, Path tempDir,
                                  Map<String, Path> sqlFiles) throws Exception {
        long rowCount = 0;
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
             ResultSet rs = stmt.executeQuery(querySql)) {
            stmt.setFetchSize(configProps.getFetchSize());
            conn.setReadOnly(true);
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            Path sqlPath = tempDir.resolve(tableName + ".sql");
            try (BufferedWriter writer = Files.newBufferedWriter(sqlPath, StandardCharsets.UTF_8)) {
                writer.write("-- Table: ");
                writer.write(tableName);
                writer.write("\n\n");
                StringBuilder cols = new StringBuilder();
                for (int i = 1; i <= columnCount; i++) {
                    if (i > 1) cols.append(", ");
                    cols.append("\"").append(meta.getColumnLabel(i)).append("\"");
                }
                String colList = cols.toString();
                while (rs.next()) {
                    if (cancelFlags.get(taskId).get()) break;
                    writer.write("INSERT INTO \"");
                    writer.write(tableName);
                    writer.write("\" (");
                    writer.write(colList);
                    writer.write(") VALUES (");
                    for (int i = 1; i <= columnCount; i++) {
                        if (i > 1) writer.write(", ");
                        String value = rs.getString(i);
                        if (value == null) {
                            writer.write("NULL");
                        } else {
                            writer.write('\'');
                            writer.write(value.replace("'", "''"));
                            writer.write('\'');
                        }
                    }
                    writer.write(");\n");
                    rowCount++;
                    if (rowCount % 1000 == 0) progress.setCurrentTableRows(rowCount);
                }
            }
            sqlFiles.put(tableName, sqlPath);
        }
        return rowCount;
    }

    private Path mergeSqlFiles(Map<String, Path> sqlFiles) throws IOException {
        Path mergedPath = Files.createTempFile("merged_", ".sql");
        try (BufferedWriter writer = Files.newBufferedWriter(mergedPath, StandardCharsets.UTF_8)) {
            writer.write("-- Database Export Tool - Merged SQL\n");
            writer.write("-- Generated: ");
            writer.write(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            writer.write("\n\n");
            List<String> sortedTables = new ArrayList<>(sqlFiles.keySet());
            Collections.sort(sortedTables);
            for (String tableName : sortedTables) {
                writer.write("-- ========================================\n");
                writer.write("-- Table: ");
                writer.write(tableName);
                writer.write("\n-- ========================================\n");
                try (BufferedReader reader = Files.newBufferedReader(sqlFiles.get(tableName), StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        writer.write(line);
                        writer.write('\n');
                    }
                }
                writer.write('\n');
            }
        }
        return mergedPath;
    }

    private void cleanupProgress(String taskId) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.schedule(() -> {
            progressMap.remove(taskId);
            cancelFlags.remove(taskId);
            scheduler.shutdown();
        }, 5, TimeUnit.MINUTES);
    }

    private String buildQuerySql(String tableName, ExportConfig config) {
        if (!SqlSafeUtils.isValidIdentifier(tableName)) {
            throw new RuntimeException("非法表名: " + tableName);
        }

        StringBuilder sql = new StringBuilder("SELECT * FROM \"").append(tableName).append("\"");
        List<String> conditions = new ArrayList<>();
        boolean isDm = "dm".equalsIgnoreCase(currentDbInfo.getType());

        if (Boolean.TRUE.equals(config.getEnableTimeFilter()) && config.getTimeFieldNames() != null) {
            String[] fieldNames = config.getTimeFieldNames().split("[、,，]");
            for (String field : fieldNames) {
                field = field.trim();
                if (!field.isEmpty() && SqlSafeUtils.isValidIdentifier(field) && hasColumn(tableName, field)) {
                    if (config.getStartDate() != null && !config.getStartDate().isEmpty()) {
                        conditions.add("\"" + field + "\" >= " + toDateLiteral(config.getStartDate(), isDm));
                    }
                    if (config.getEndDate() != null && !config.getEndDate().isEmpty()) {
                        conditions.add("\"" + field + "\" <= " + toDateLiteral(config.getEndDate() + " 23:59:59", isDm));
                    }
                    if (!conditions.isEmpty()) break;
                }
            }
        }

        if (Boolean.TRUE.equals(config.getEnableFieldFilter()) && config.getFieldFilter() != null) {
            FieldFilterConfig fc = config.getFieldFilter();
            if (fc.getFieldName() != null && SqlSafeUtils.isValidIdentifier(fc.getFieldName())
                    && hasColumn(tableName, fc.getFieldName())) {
                String condition = fc.buildCondition();
                if (condition != null) {
                    conditions.add(condition);
                }
            }
        }

        if (!conditions.isEmpty()) {
            sql.append(" WHERE ");
            for (int i = 0; i < conditions.size(); i++) {
                if (i > 0) sql.append(" AND ");
                sql.append(conditions.get(i));
            }
        }

        return sql.toString();
    }

    private String toDateLiteral(String dateStr, boolean isDm) {
        if (isDm) {
            if (dateStr.length() <= 10) {
                return "TO_DATE('" + dateStr + "', 'YYYY-MM-DD')";
            }
            return "TO_DATE('" + dateStr + "', 'YYYY-MM-DD HH24:MI:SS')";
        }
        return "'" + dateStr + "'";
    }

    private final Map<String, Boolean> columnCache = new ConcurrentHashMap<>();

    private boolean hasColumn(String tableName, String columnName) {
        String cacheKey = tableName.toUpperCase() + "." + columnName.toUpperCase();
        Boolean cached = columnCache.get(cacheKey);
        if (cached != null) return cached;

        try (Connection conn = dataSource.getConnection()) {
            boolean result;
            if ("dm".equalsIgnoreCase(currentDbInfo.getType())) {
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT COUNT(*) FROM ALL_TAB_COLUMNS WHERE OWNER = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?")) {
                    stmt.setString(1, currentDbInfo.getUsername().toUpperCase());
                    stmt.setString(2, tableName.toUpperCase());
                    stmt.setString(3, columnName.toUpperCase());
                    try (ResultSet rs = stmt.executeQuery()) {
                        result = rs.next() && rs.getInt(1) > 0;
                    }
                }
            } else {
                DatabaseMetaData meta = conn.getMetaData();
                try (ResultSet rs = meta.getColumns(conn.getCatalog(), conn.getSchema(), tableName, columnName)) {
                    result = rs.next();
                }
            }
            columnCache.put(cacheKey, result);
            return result;
        } catch (Exception e) {
            log.warn("检查列是否存在失败: {}.{}: {}", tableName, columnName, e.getMessage());
            return false;
        }
    }

    private List<String> resolveTableNames(ExportConfig config) {
        List<String> tables = new ArrayList<>();
        String type = config.getExportType();

        if ("customTables".equals(type) && config.getTables() != null) {
            String[] parts = config.getTables().split("[、,，\\n\\r]+");
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty() && SqlSafeUtils.isValidIdentifier(trimmed)) {
                    tables.add(trimmed.toUpperCase());
                }
            }
        } else if ("customSql".equals(type) && config.getCustomSql() != null) {
            String[] lines = config.getCustomSql().split("\\n");
            for (String line : lines) {
                line = line.trim();
                if (!line.isEmpty()) {
                    SqlValidator.ValidationResult vr = SqlValidator.validate(line);
                    if (vr.isValid()) {
                        tables.add(SqlValidator.extractTableName(line));
                    }
                }
            }
        }

        return tables;
    }

    private void addToZip(ZipOutputStream zos, String entryName, Path filePath) throws IOException {
        zos.putNextEntry(new ZipEntry(entryName));
        Files.copy(filePath, zos);
        zos.closeEntry();
    }

    @Override
    public ExportProgress getProgress(String taskId) {
        return progressMap.get(taskId);
    }

    @Override
    public boolean cancelTask(String taskId) {
        AtomicBoolean flag = cancelFlags.get(taskId);
        if (flag != null) {
            flag.set(true);
            ExportProgress progress = progressMap.get(taskId);
            if (progress != null) {
                progress.addLog("WARNING", "正在取消导出任务...");
            }
            return true;
        }
        return false;
    }

    @Override
    public List<ExportFileInfo> getExportFiles() {
        List<ExportFileInfo> files = new ArrayList<>();
        Path exportDir = Paths.get(configProps.getStorage().getPath()).toAbsolutePath().normalize();
        if (!Files.exists(exportDir)) {
            return files;
        }

        try {
            Files.list(exportDir)
                    .filter(p -> p.toString().endsWith(".zip"))
                    .forEach(p -> {
                        ExportFileInfo info = new ExportFileInfo();
                        info.setFileName(p.getFileName().toString());
                        try {
                            info.setFileSize(Files.size(p));
                        } catch (IOException e) {
                            info.setFileSize(0);
                        }
                        try {
                            info.setCreateTime(Files.getLastModifiedTime(p).toMillis());
                        } catch (IOException e) {
                            info.setCreateTime(0);
                        }
                        info.setStatus("成功");
                        files.add(info);
                    });
        } catch (IOException e) {
            log.error("读取导出文件列表失败", e);
        }

        files.sort((a, b) -> Long.compare(b.getCreateTime(), a.getCreateTime()));
        return files;
    }

    @Override
    public String getExportFilePath(String fileName) {
        String safeName = SqlSafeUtils.sanitizeFileName(fileName);
        if (safeName == null) return null;
        Path exportDir = Paths.get(configProps.getStorage().getPath()).toAbsolutePath().normalize();
        Path path = exportDir.resolve(safeName).normalize();
        if (!path.startsWith(exportDir)) return null;
        if (Files.exists(path)) {
            return path.toString();
        }
        return null;
    }

    @Override
    public boolean deleteExportFile(String fileName) {
        String safeName = SqlSafeUtils.sanitizeFileName(fileName);
        if (safeName == null) return false;
        try {
            Path exportDir = Paths.get(configProps.getStorage().getPath()).toAbsolutePath().normalize();
            Path path = exportDir.resolve(safeName).normalize();
            if (!path.startsWith(exportDir)) return false;
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            log.error("删除导出文件失败: {}", fileName, e);
            return false;
        }
    }

    @Override
    @PreDestroy
    public void shutdown() {
        exportWorkerPool.shutdown();
        closeDataSource();
    }

    private void closeDataSource() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            dataSource = null;
        }
    }

    private void closeQuietly(Connection conn) {
        if (conn != null) {
            try { conn.close(); } catch (Exception ignored) {}
        }
    }
}
