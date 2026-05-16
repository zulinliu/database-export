package com.dbexport.service.impl;

import com.dbexport.config.ExportConfigProperties;
import com.dbexport.model.*;
import com.dbexport.service.ExportService;
import com.dbexport.util.SqlSafeUtils;
import com.dbexport.util.SqlValidator;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.io.*;
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

            Map<String, Path> excelFiles = new ConcurrentHashMap<>();
            Map<String, Path> sqlFiles = new ConcurrentHashMap<>();

            boolean exportExcel = config.getExportFormats() != null && config.getExportFormats().contains("excel");
            boolean exportSql = config.getExportFormats() != null && config.getExportFormats().contains("sql");

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
                        long rowCount = exportTable(taskId, finalTableName, querySql, exportExcel, exportSql, excelFiles, sqlFiles);

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
                if (exportExcel && !excelFiles.isEmpty()) {
                    SXSSFWorkbook mergedWorkbook = new SXSSFWorkbook(100);
                    for (Map.Entry<String, Path> entry : excelFiles.entrySet()) {
                        try (InputStream is = Files.newInputStream(entry.getValue());
                             Workbook wb = new SXSSFWorkbook(new org.apache.poi.xssf.usermodel.XSSFWorkbook(is))) {
                            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                                Sheet srcSheet = wb.getSheetAt(i);
                                Sheet destSheet = mergedWorkbook.createSheet(srcSheet.getSheetName());
                                copySheet(srcSheet, destSheet);
                            }
                        } catch (Exception e) {
                            log.warn("合并Excel表 {} 失败，单独打包", entry.getKey(), e);
                            addToZip(zos, entry.getKey() + ".xlsx", entry.getValue());
                        }
                    }
                    if (mergedWorkbook.getNumberOfSheets() > 0) {
                        Path mergedPath = exportDir.resolve(baseFileName + "_merged.xlsx");
                        try (OutputStream os = Files.newOutputStream(mergedPath)) {
                            mergedWorkbook.write(os);
                        }
                        addToZip(zos, baseFileName + ".xlsx", mergedPath);
                        Files.deleteIfExists(mergedPath);
                    }
                    mergedWorkbook.dispose();
                    mergedWorkbook.close();
                }

                for (Map.Entry<String, Path> entry : sqlFiles.entrySet()) {
                    addToZip(zos, entry.getKey() + ".sql", entry.getValue());
                }

                for (Map.Entry<String, Path> entry : excelFiles.entrySet()) {
                    Files.deleteIfExists(entry.getValue());
                }
                for (Map.Entry<String, Path> entry : sqlFiles.entrySet()) {
                    Files.deleteIfExists(entry.getValue());
                }
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

    private void copySheet(Sheet src, Sheet dest) {
        for (int r = 0; r <= src.getLastRowNum(); r++) {
            Row srcRow = src.getRow(r);
            if (srcRow == null) continue;
            Row destRow = dest.createRow(r);
            for (int c = 0; c < srcRow.getLastCellNum(); c++) {
                Cell srcCell = srcRow.getCell(c);
                if (srcCell == null) continue;
                Cell destCell = destRow.createCell(c);
                destCell.setCellValue(srcCell.getStringCellValue());
            }
        }
    }

    private void cleanupProgress(String taskId) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.schedule(() -> {
            progressMap.remove(taskId);
            cancelFlags.remove(taskId);
            scheduler.shutdown();
        }, 5, TimeUnit.MINUTES);
    }

    private long exportTable(String taskId, String tableName, String querySql,
                             boolean exportExcel, boolean exportSql,
                             Map<String, Path> excelFiles, Map<String, Path> sqlFiles) throws Exception {
        ExportProgress progress = progressMap.get(taskId);
        Path tempDir = Paths.get(configProps.getStorage().getTemp());
        Files.createDirectories(tempDir);

        long rowCount = 0;

        if (exportExcel) {
            rowCount = exportTableToExcel(taskId, tableName, querySql, progress, tempDir, excelFiles);
        }

        if (exportSql) {
            long sqlRowCount = exportTableToSql(taskId, tableName, querySql, progress, tempDir, sqlFiles);
            if (!exportExcel) rowCount = sqlRowCount;
        }

        return rowCount;
    }

    private long exportTableToExcel(String taskId, String tableName, String querySql,
                                    ExportProgress progress, Path tempDir,
                                    Map<String, Path> excelFiles) throws Exception {
        long rowCount = 0;
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
             ResultSet rs = stmt.executeQuery(querySql)) {
            stmt.setFetchSize(configProps.getFetchSize());
            conn.setReadOnly(true);

            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();

            SXSSFWorkbook workbook = new SXSSFWorkbook(100);
            try {
                String sheetName = tableName.substring(0, Math.min(tableName.length(), 31));
                Sheet sheet = workbook.createSheet(sheetName);

                Row headerRow = sheet.createRow(0);
                CellStyle headerStyle = workbook.createCellStyle();
                Font font = workbook.createFont();
                font.setBold(true);
                headerStyle.setFont(font);
                headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
                headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

                for (int i = 1; i <= columnCount; i++) {
                    Cell cell = headerRow.createCell(i - 1);
                    cell.setCellValue(meta.getColumnLabel(i));
                    cell.setCellStyle(headerStyle);
                }

                int rowNum = 1;
                while (rs.next()) {
                    if (cancelFlags.get(taskId).get()) break;
                    Row row = sheet.createRow(rowNum++);
                    for (int i = 1; i <= columnCount; i++) {
                        Cell cell = row.createCell(i - 1);
                        String value = rs.getString(i);
                        cell.setCellValue(value != null ? value : "");
                    }
                    rowCount++;
                    if (rowCount % 1000 == 0) progress.setCurrentTableRows(rowCount);
                }

                for (int i = 1; i <= columnCount; i++) sheet.setColumnWidth(i - 1, 5000);

                Path excelPath = tempDir.resolve(tableName + ".xlsx");
                try (OutputStream os = Files.newOutputStream(excelPath)) {
                    workbook.write(os);
                }
                excelFiles.put(tableName, excelPath);
            } finally {
                workbook.dispose();
                workbook.close();
            }
        }
        return rowCount;
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
            try (Writer sqlWriter = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(sqlPath), "UTF-8"))) {
                int batchSize = 0;
                StringBuilder batchBuilder = new StringBuilder();

                while (rs.next()) {
                    if (cancelFlags.get(taskId).get()) break;

                    if (batchSize == 0) {
                        batchBuilder.append("-- ").append(tableName).append(" 表数据\n");
                        batchBuilder.append("INSERT ALL\n");
                    }

                    StringBuilder cols = new StringBuilder();
                    for (int i = 1; i <= columnCount; i++) {
                        if (i > 1) cols.append(", ");
                        cols.append(meta.getColumnLabel(i));
                    }

                    StringBuilder values = new StringBuilder("INTO ");
                    values.append(tableName).append(" (").append(cols).append(") VALUES (");

                    for (int i = 1; i <= columnCount; i++) {
                        if (i > 1) values.append(", ");
                        String value = rs.getString(i);
                        if (value == null) {
                            values.append("NULL");
                        } else {
                            values.append("'").append(value.replace("'", "''")).append("'");
                        }
                    }
                    values.append(")");
                    batchBuilder.append(values).append("\n");

                    batchSize++;
                    rowCount++;
                    if (rowCount % 1000 == 0) progress.setCurrentTableRows(rowCount);

                    if (batchSize >= configProps.getBatchSize()) {
                        batchBuilder.append("SELECT 1 FROM DUAL;\n\n");
                        sqlWriter.write(batchBuilder.toString());
                        batchBuilder = new StringBuilder();
                        batchSize = 0;
                    }
                }

                if (batchSize > 0) {
                    batchBuilder.append("SELECT 1 FROM DUAL;\n");
                    sqlWriter.write(batchBuilder.toString());
                }
            }
            sqlFiles.put(tableName, sqlPath);
        }
        return rowCount;
    }

    private String buildQuerySql(String tableName, ExportConfig config) {
        if (!SqlSafeUtils.isValidIdentifier(tableName)) {
            throw new RuntimeException("非法表名: " + tableName);
        }

        StringBuilder sql = new StringBuilder("SELECT * FROM \"").append(tableName).append("\"");
        List<String> conditions = new ArrayList<>();

        if (Boolean.TRUE.equals(config.getEnableTimeFilter()) && config.getTimeFieldNames() != null) {
            String[] fieldNames = config.getTimeFieldNames().split("[、,，]");
            for (String field : fieldNames) {
                field = field.trim();
                if (!field.isEmpty() && SqlSafeUtils.isValidIdentifier(field) && hasColumn(tableName, field)) {
                    if (config.getStartDate() != null && !config.getStartDate().isEmpty()) {
                        conditions.add("\"" + field + "\" >= '" + config.getStartDate() + "'");
                    }
                    if (config.getEndDate() != null && !config.getEndDate().isEmpty()) {
                        conditions.add("\"" + field + "\" <= '" + config.getEndDate() + "'");
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

    private boolean hasColumn(String tableName, String columnName) {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();
            String schema = conn.getSchema();

            if ("dm".equalsIgnoreCase(currentDbInfo.getType())) {
                schema = currentDbInfo.getUsername().toUpperCase();
            }

            try (ResultSet rs = meta.getColumns(catalog, schema, tableName, columnName)) {
                return rs.next();
            }
        } catch (Exception e) {
            log.warn("检查列是否存在失败: {}.{}", tableName, columnName);
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
