package com.dbexport.service;

import com.dbexport.config.HikariPoolManager;
import com.dbexport.model.DatabaseInfo;
import com.dbexport.model.ExportConfig;
import com.dbexport.model.ExportProgress;
import com.dbexport.util.FileUtil;
import com.dbexport.util.SqlValidator;
import com.zaxxer.hikari.HikariDataSource;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ExportServiceImpl {

    private static final ConcurrentHashMap<String, ExportProgress> taskProgressMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, HikariDataSource> taskDataSourceMap = new ConcurrentHashMap<>();

    private static final int BATCH_SIZE = 1000;
    private static final int FETCH_SIZE = 1000;
    private static final int EXCEL_WINDOW_SIZE = 100;
    private static final int DEFAULT_POOL_SIZE = 10;

    public boolean testConnection(DatabaseInfo dbInfo) {
        HikariPoolManager poolManager = HikariPoolManager.getInstance();
        HikariDataSource ds = null;
        try {
            ds = poolManager.createPool(dbInfo, 1);
            try (Connection conn = ds.getConnection()) {
                return conn != null && !conn.isClosed();
            }
        } catch (Exception e) {
            return false;
        } finally {
            if (ds != null) {
                poolManager.closePool(ds);
            }
        }
    }

    public List<String> getTableList(HikariDataSource ds) {
        List<String> tableList = new ArrayList<>();
        try (Connection conn = ds.getConnection()) {
            conn.setReadOnly(true);
            DatabaseMetaData metaData = conn.getMetaData();
            String catalog = conn.getCatalog();
            String schema = conn.getSchema();

            try (ResultSet rs = metaData.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    if (tableName != null && !tableName.trim().isEmpty()) {
                        tableList.add(tableName);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get table list: " + e.getMessage(), e);
        }
        return tableList;
    }

    public Map<String, Object> getTableInfo(HikariDataSource ds, String tableName) {
        Map<String, Object> tableInfo = new HashMap<>();
        tableInfo.put("tableName", tableName);
        try (Connection conn = ds.getConnection()) {
            conn.setReadOnly(true);
            try (Statement stmt = conn.createStatement();
                 ResultSet countRs = stmt.executeQuery("SELECT COUNT(*) FROM " + escapeIdentifier(tableName))) {
                if (countRs.next()) {
                    tableInfo.put("rowCount", countRs.getLong(1));
                } else {
                    tableInfo.put("rowCount", 0L);
                }
            }
        } catch (Exception e) {
            tableInfo.put("rowCount", 0L);
        }
        tableInfo.put("dataSize", "0 B");
        return tableInfo;
    }

    public List<String> getTableColumns(HikariDataSource ds, String tableName) {
        List<String> columns = new ArrayList<>();
        try (Connection conn = ds.getConnection()) {
            conn.setReadOnly(true);
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM " + escapeIdentifier(tableName) + " WHERE 1=0")) {
                ResultSetMetaData metaData = rs.getMetaData();
                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    columns.add(metaData.getColumnName(i));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get columns for table " + tableName + ": " + e.getMessage(), e);
        }
        return columns;
    }

    public HikariDataSource connect(DatabaseInfo dbInfo) {
        HikariPoolManager poolManager = HikariPoolManager.getInstance();
        HikariDataSource ds = poolManager.createPool(dbInfo, DEFAULT_POOL_SIZE);
        return ds;
    }

    public void disconnect(HikariDataSource ds) {
        if (ds != null) {
            HikariPoolManager.getInstance().closePool(ds);
        }
    }

    public Map<String, Integer> getPoolStatus(HikariDataSource ds) {
        return HikariPoolManager.getInstance().getPoolStatus(ds);
    }

    @Async("exportExecutor")
    public void startExport(String taskId, ExportConfig config, HikariDataSource ds) {
        ExportProgress progress = new ExportProgress();
        progress.setTaskId(taskId);
        progress.setStatus("RUNNING");
        progress.setStartTime(System.currentTimeMillis());
        progress.setCompletedTables(0);
        progress.setExportedRows(0L);
        progress.setTotalRows(0L);
        taskProgressMap.put(taskId, progress);

        try {
            progress.addLog("INFO", "开始导出任务");
            List<String> tables = resolveTableList(config);
            if (tables.isEmpty()) {
                progress.setStatus("FAILED");
                progress.getErrors().add("未找到需要导出的表");
                progress.addLog("ERROR", "未找到需要导出的表");
                return;
            }

            progress.setTotalTables(tables.size());
            progress.addLog("INFO", "共 " + tables.size() + " 张表待导出");

            FileUtil.ensureDirectoryExists("./temp/" + taskId);
            FileUtil.ensureDirectoryExists("./exports");

            int maxConnections = config.getMaxConnections() != null ? config.getMaxConnections() : 4;
            Semaphore semaphore = new Semaphore(maxConnections);
            ExecutorService executor = Executors.newFixedThreadPool(maxConnections);
            AtomicInteger completedCount = new AtomicInteger(0);
            List<File> tempFiles = new ArrayList<>();
            List<String> errors = new ArrayList<>();

            for (String tableName : tables) {
                executor.submit(() -> {
                    try {
                        semaphore.acquire();
                        try {
                            progress.setCurrentTable(tableName);
                            progress.addLog("INFO", "开始导出表: " + tableName);
                            List<File> exportedFiles = exportTable(taskId, tableName, config, ds, progress);
                            synchronized (tempFiles) {
                                tempFiles.addAll(exportedFiles);
                            }
                            int done = completedCount.incrementAndGet();
                            progress.setCompletedTables(done);
                            progress.addLog("INFO", "完成导出表: " + tableName + " (" + done + "/" + tables.size() + ")");
                        } finally {
                            semaphore.release();
                        }
                    } catch (Exception e) {
                        String errorMsg = "导出表 " + tableName + " 失败: " + e.getMessage();
                        progress.addLog("ERROR", errorMsg);
                        synchronized (errors) {
                            errors.add(errorMsg);
                        }
                        completedCount.incrementAndGet();
                    }
                });
            }

            executor.shutdown();
            executor.awaitTermination(24, TimeUnit.HOURS);

            if (!errors.isEmpty()) {
                progress.getErrors().addAll(errors);
            }

            String zipName = buildZipFileName(config, ds);
            String zipPath = "./exports/" + zipName;

            if (!tempFiles.isEmpty()) {
                File zipFile = FileUtil.createZipFromFiles(tempFiles, zipPath);
                progress.setExportFileName(zipName);
                progress.setFileSize(zipFile.length());
                progress.addLog("INFO", "ZIP 文件已生成: " + zipName
                        + " (" + FileUtil.humanReadableByteCount(zipFile.length()) + ")");
            }

            FileUtil.deleteDirectory(new File("./temp/" + taskId));

            progress.setStatus("COMPLETED");
            progress.setCurrentTable(null);
            progress.addLog("INFO", "导出任务完成");
        } catch (Exception e) {
            progress.setStatus("FAILED");
            progress.getErrors().add("导出任务异常: " + e.getMessage());
            progress.addLog("ERROR", "导出任务异常: " + e.getMessage());
            try {
                FileUtil.deleteDirectory(new File("./temp/" + taskId));
            } catch (Exception ignored) {
            }
        }
    }

    private List<File> exportTable(String taskId, String tableName, ExportConfig config,
                                    HikariDataSource ds, ExportProgress progress) throws Exception {
        List<File> outputFiles = new ArrayList<>();
        List<String> columns = getTableColumns(ds, tableName);
        if (columns.isEmpty()) {
            throw new RuntimeException("无法获取表 " + tableName + " 的列信息");
        }

        String whereClause = config.buildWhereClause(tableName, columns);
        String countSql = "SELECT COUNT(*) FROM " + escapeIdentifier(tableName) + whereClause;
        long tableRowCount = 0;
        try (Connection conn = ds.getConnection();
             Statement countStmt = conn.createStatement();
             ResultSet countRs = countStmt.executeQuery(countSql)) {
            conn.setReadOnly(true);
            if (countRs.next()) {
                tableRowCount = countRs.getLong(1);
            }
        }

        progress.setCurrentTableRows(tableRowCount);
        synchronized (progress) {
            progress.setTotalRows(progress.getTotalRows() + tableRowCount);
        }

        String selectSql = "SELECT * FROM " + escapeIdentifier(tableName) + whereClause;

        boolean exportExcel = shouldExportFormat(config, "excel");
        boolean exportSql = shouldExportFormat(config, "sql");

        SXSSFWorkbook workbook = null;
        BufferedWriter sqlWriter = null;
        File excelFile = null;
        File sqlFile = null;

        try {
            if (exportExcel) {
                workbook = new SXSSFWorkbook(XSSFWorkbook::new, EXCEL_WINDOW_SIZE);
                workbook.setCompressTempFiles(true);
                excelFile = new File("./temp/" + taskId + "/" + FileUtil.sanitizeFileName(tableName) + ".xlsx");
            }
            if (exportSql) {
                sqlFile = new File("./temp/" + taskId + "/" + FileUtil.sanitizeFileName(tableName) + ".sql");
                sqlWriter = new BufferedWriter(new FileWriter(sqlFile));
            }

            Connection conn = ds.getConnection();
            try {
                conn.setReadOnly(true);
                PreparedStatement pstmt = conn.prepareStatement(selectSql,
                        ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
                try {
                    pstmt.setFetchSize(FETCH_SIZE);
                    pstmt.setFetchDirection(ResultSet.FETCH_FORWARD);
                    ResultSet rs = pstmt.executeQuery();
                    try {
                        ResultSetMetaData metaData = rs.getMetaData();
                        int columnCount = metaData.getColumnCount();

                        if (exportExcel) {
                            writeExcelHeader(workbook, columns);
                        }

                        int rowIndex = 0;
                        int excelRowIndex = 1;
                        List<List<Object>> sqlBatch = exportSql ? new ArrayList<>() : null;

                        while (rs.next()) {
                            List<Object> rowData = new ArrayList<>(columnCount);
                            for (int i = 1; i <= columnCount; i++) {
                                rowData.add(rs.getObject(i));
                            }

                            if (exportExcel) {
                                writeExcelRow(workbook, rowData, excelRowIndex++, metaData);
                            }

                            if (exportSql) {
                                sqlBatch.add(rowData);
                                if (sqlBatch.size() >= BATCH_SIZE) {
                                    writeSqlBatch(sqlWriter, tableName, columns, sqlBatch, metaData);
                                    sqlBatch.clear();
                                }
                            }

                            rowIndex++;
                            if (rowIndex % BATCH_SIZE == 0) {
                                synchronized (progress) {
                                    progress.setExportedRows(progress.getExportedRows() + BATCH_SIZE);
                                }
                                progress.calculateProgress();
                                progress.calculateElapsed();
                                progress.calculateEstimatedRemaining();
                            }
                        }

                        if (exportSql && sqlBatch != null && !sqlBatch.isEmpty()) {
                            writeSqlBatch(sqlWriter, tableName, columns, sqlBatch, metaData);
                        }

                        long remainingRows = tableRowCount % BATCH_SIZE;
                        if (remainingRows > 0) {
                            synchronized (progress) {
                                progress.setExportedRows(progress.getExportedRows() + remainingRows);
                            }
                        } else {
                            long alreadyCounted = (tableRowCount / BATCH_SIZE) * BATCH_SIZE;
                            long rest = tableRowCount - alreadyCounted;
                            if (rest > 0) {
                                synchronized (progress) {
                                    progress.setExportedRows(progress.getExportedRows() + rest);
                                }
                            }
                        }

                        progress.calculateProgress();
                        progress.calculateElapsed();
                        progress.calculateEstimatedRemaining();
                    } finally {
                        rs.close();
                    }
                } finally {
                    pstmt.close();
                }
            } finally {
                conn.close();
            }

            if (exportExcel && workbook != null) {
                workbook.write(new java.io.FileOutputStream(excelFile));
                outputFiles.add(excelFile);
            }

            if (exportSql && sqlWriter != null) {
                sqlWriter.flush();
                outputFiles.add(sqlFile);
            }

        } finally {
            if (workbook != null) {
                try {
                    workbook.close();
                } catch (IOException ignored) {
                }
                try {
                    workbook.dispose();
                } catch (Exception ignored) {
                }
            }
            if (sqlWriter != null) {
                try {
                    sqlWriter.close();
                } catch (IOException ignored) {
                }
            }
        }

        return outputFiles;
    }

    public ExportProgress getProgress(String taskId) {
        return taskProgressMap.get(taskId);
    }

    public boolean cancelTask(String taskId) {
        ExportProgress progress = taskProgressMap.get(taskId);
        if (progress != null && "RUNNING".equals(progress.getStatus())) {
            progress.setStatus("CANCELLED");
            progress.addLog("WARNING", "任务已取消");
            return true;
        }
        return false;
    }

    public List<Map<String, Object>> getExportFiles() {
        List<Map<String, Object>> files = new ArrayList<>();
        File exportDir = new File("./exports");
        if (!exportDir.exists() || !exportDir.isDirectory()) {
            return files;
        }

        File[] fileList = exportDir.listFiles();
        if (fileList == null) {
            return files;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (File file : fileList) {
            if (file.isFile()) {
                Map<String, Object> fileInfo = new HashMap<>();
                fileInfo.put("fileName", file.getName());
                fileInfo.put("fileSize", FileUtil.humanReadableByteCount(file.length()));
                fileInfo.put("createTime", sdf.format(new Date(file.lastModified())));
                fileInfo.put("status", "COMPLETED");
                files.add(fileInfo);
            }
        }

        files.sort((a, b) -> {
            String timeA = (String) a.get("createTime");
            String timeB = (String) b.get("createTime");
            return timeB.compareTo(timeA);
        });

        return files;
    }

    public boolean deleteExportFiles(List<String> fileNames) {
        if (fileNames == null || fileNames.isEmpty()) {
            return false;
        }

        boolean allDeleted = true;
        for (String fileName : fileNames) {
            File file = new File("./exports/" + fileName);
            if (file.exists() && file.isFile()) {
                if (!file.delete()) {
                    allDeleted = false;
                }
            }
        }
        return allDeleted;
    }

    public int cleanOldExports(int daysOld) {
        File exportDir = new File("./exports");
        if (!exportDir.exists() || !exportDir.isDirectory()) {
            return 0;
        }

        File[] fileList = exportDir.listFiles();
        if (fileList == null) {
            return 0;
        }

        long cutoffTime = System.currentTimeMillis() - (long) daysOld * 24 * 60 * 60 * 1000;
        int deletedCount = 0;

        for (File file : fileList) {
            if (file.isFile() && file.lastModified() < cutoffTime) {
                if (file.delete()) {
                    deletedCount++;
                }
            }
        }

        return deletedCount;
    }

    private List<String> resolveTableList(ExportConfig config) {
        List<String> tables = new ArrayList<>();
        String exportType = config.getExportType();

        if ("tableSelect".equals(exportType)) {
            String tablesStr = config.getTables();
            if (tablesStr != null && !tablesStr.trim().isEmpty()) {
                tables.addAll(Arrays.asList(tablesStr.split(",")));
            }
        } else if ("customTables".equals(exportType)) {
            String tablesStr = config.getTables();
            if (tablesStr != null && !tablesStr.trim().isEmpty()) {
                String[] parts = tablesStr.split("[,;\\n\\r]+");
                for (String part : parts) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) {
                        tables.add(trimmed);
                    }
                }
            }
        } else if ("customSql".equals(exportType)) {
            String customSql = config.getCustomSql();
            if (customSql != null && !customSql.trim().isEmpty()) {
                String[] sqlStatements = customSql.split(";");
                for (String sql : sqlStatements) {
                    String trimmed = sql.trim();
                    if (!trimmed.isEmpty()) {
                        SqlValidator.ValidationResult result = SqlValidator.validate(trimmed);
                        if (result.isValid()) {
                            String tableName = SqlValidator.extractTableName(trimmed);
                            if (tableName != null && !tables.contains(tableName)) {
                                tables.add(tableName);
                            }
                        }
                    }
                }
            }
        }

        return tables;
    }

    private boolean shouldExportFormat(ExportConfig config, String format) {
        String formats = config.getExportFormats();
        if (formats == null || formats.trim().isEmpty()) {
            return true;
        }
        String[] parts = formats.split(",");
        for (String part : parts) {
            if (part.trim().equalsIgnoreCase(format)) {
                return true;
            }
        }
        return false;
    }

    private String buildZipFileName(ExportConfig config, HikariDataSource ds) {
        String dbName = "export";
        try (Connection conn = ds.getConnection()) {
            dbName = conn.getCatalog();
            if (dbName == null || dbName.isEmpty()) {
                dbName = "export";
            }
        } catch (Exception ignored) {
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        return FileUtil.sanitizeFileName(dbName) + "_" + sdf.format(new Date()) + ".zip";
    }

    private void writeExcelHeader(SXSSFWorkbook workbook, List<String> columns) {
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheet("Sheet1");
        if (sheet == null) {
            sheet = workbook.createSheet("Sheet1");
        }
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < columns.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columns.get(i));
        }
    }

    private void writeExcelRow(SXSSFWorkbook workbook, List<Object> rowData,
                                int rowIndex, ResultSetMetaData metaData) throws SQLException {
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheet("Sheet1");
        Row row = sheet.createRow(rowIndex);
        for (int i = 0; i < rowData.size(); i++) {
            Cell cell = row.createCell(i);
            Object value = rowData.get(i);
            if (value == null) {
                cell.setCellValue("");
            } else if (value instanceof Number) {
                int columnType = metaData.getColumnType(i + 1);
                if (columnType == Types.DECIMAL || columnType == Types.NUMERIC
                        || columnType == Types.DOUBLE || columnType == Types.FLOAT) {
                    cell.setCellValue(((Number) value).doubleValue());
                } else {
                    cell.setCellValue(((Number) value).longValue());
                }
            } else if (value instanceof java.sql.Timestamp || value instanceof java.sql.Date
                    || value instanceof java.sql.Time) {
                cell.setCellValue(value.toString());
            } else if (value instanceof Boolean) {
                cell.setCellValue((Boolean) value);
            } else {
                cell.setCellValue(value.toString());
            }
        }
    }

    private void writeSqlBatch(BufferedWriter writer, String tableName, List<String> columns,
                                List<List<Object>> batch, ResultSetMetaData metaData)
            throws SQLException, IOException {
        if (batch.isEmpty()) {
            return;
        }

        StringBuilder columnList = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                columnList.append(", ");
            }
            columnList.append(escapeIdentifier(columns.get(i)));
        }

        StringBuilder sb = new StringBuilder();
        sb.append("INSERT ALL\n");

        for (List<Object> row : batch) {
            sb.append("    INTO ").append(escapeIdentifier(tableName))
                    .append(" (").append(columnList).append(") VALUES (");
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(formatSqlValue(row.get(i), metaData, i + 1));
            }
            sb.append(")\n");
        }

        sb.append("SELECT 1 FROM DUAL");
        writer.write(sb.toString());
        writer.write(";\n\n");
    }

    private String formatSqlValue(Object value, ResultSetMetaData metaData, int columnIndex)
            throws SQLException {
        if (value == null) {
            return "NULL";
        }

        if (value instanceof Number) {
            return value.toString();
        }

        if (value instanceof java.sql.Timestamp) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return "TIMESTAMP '" + sdf.format((java.sql.Timestamp) value) + "'";
        }

        if (value instanceof java.sql.Date) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            return "DATE '" + sdf.format((java.sql.Date) value) + "'";
        }

        if (value instanceof java.sql.Time) {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
            return "TIME '" + sdf.format((java.sql.Time) value) + "'";
        }

        if (value instanceof Boolean) {
            return (Boolean) value ? "1" : "0";
        }

        int columnType = metaData.getColumnType(columnIndex);
        if (columnType == Types.BLOB || columnType == Types.BINARY
                || columnType == Types.VARBINARY || columnType == Types.LONGVARBINARY) {
            return "NULL";
        }

        if (columnType == Types.CLOB || columnType == Types.NCLOB) {
            String escaped = escapeSqlString(value.toString());
            return "TO_CLOB('" + escaped + "')";
        }

        String escaped = escapeSqlString(value.toString());
        return "'" + escaped + "'";
    }

    private String escapeSqlString(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("'", "''");
    }

    private String escapeIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}