package com.dbexport.service;

import com.dbexport.config.AppConfig;
import com.dbexport.model.*;
import com.dbexport.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
public class ExportService {

    @Autowired
    private AppConfig appConfig;

    @Autowired
    private ObjectMapper objectMapper;

    private final Map<String, ExportProgress> progressMap = new ConcurrentHashMap<>();
    private HikariDataSource dataSource;
    private DatabaseInfo currentDbInfo;

    public boolean connectDatabase(DatabaseInfo dbInfo) {
        if (dataSource != null) {
            dataSource.close();
        }
        try {
            dataSource = DatabaseUtil.createDataSource(dbInfo);
            currentDbInfo = dbInfo;
            try (Connection conn = dataSource.getConnection()) {
                return conn.isValid(5);
            }
        } catch (Exception e) {
            log.error("连接数据库失败", e);
            return false;
        }
    }

    public boolean testConnection(DatabaseInfo dbInfo) {
        return DatabaseUtil.testConnection(dbInfo);
    }

    public List<String> getTableList() {
        if (dataSource == null) {
            return Collections.emptyList();
        }
        try (Connection conn = dataSource.getConnection()) {
            return DatabaseUtil.getTableNames(conn);
        } catch (Exception e) {
            log.error("获取表列表失败", e);
            return Collections.emptyList();
        }
    }

    public Map<String, Object> getDatabaseStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("connected", dataSource != null && !dataSource.isClosed());
        if (dataSource != null) {
            status.put("dbType", currentDbInfo != null ? currentDbInfo.getDbType() : "unknown");
            status.put("host", currentDbInfo != null ? currentDbInfo.getHost() : "unknown");
            status.put("database", currentDbInfo != null ? currentDbInfo.getDatabaseName() : "unknown");
        }
        return status;
    }

    public String startExport(DatabaseInfo dbInfo, ExportConfig config) {
        String taskId = UUID.randomUUID().toString();
        
        ExportProgress progress = new ExportProgress();
        progress.setTaskId(taskId);
        progress.setStatus("starting");
        progress.setStartTime(System.currentTimeMillis());
        progressMap.put(taskId, progress);

        boolean needReconnect = false;
        if (dataSource == null || currentDbInfo == null || !isSameDatabase(currentDbInfo, dbInfo)) {
            needReconnect = true;
        }

        doExport(taskId, dbInfo, config, needReconnect);

        return taskId;
    }

    private boolean isSameDatabase(DatabaseInfo db1, DatabaseInfo db2) {
        if (db1 == null || db2 == null) return false;
        return Objects.equals(db1.getDbType(), db2.getDbType())
                && Objects.equals(db1.getHost(), db2.getHost())
                && Objects.equals(db1.getPort(), db2.getPort())
                && Objects.equals(db1.getDatabaseName(), db2.getDatabaseName())
                && Objects.equals(db1.getUsername(), db2.getUsername());
    }

    @Async("exportExecutor")
    public void doExport(String taskId, DatabaseInfo dbInfo, ExportConfig config, boolean needReconnect) {
        ExportProgress progress = progressMap.get(taskId);
        if (progress == null) return;

        String tempPath = appConfig.getTempPath();
        String exportPath = appConfig.getExportPath();
        FileUtil.ensureDirectoryExists(tempPath);
        FileUtil.ensureDirectoryExists(exportPath);

        HikariDataSource exportDataSource = null;
        try {
            if (needReconnect) {
                exportDataSource = DatabaseUtil.createDataSource(dbInfo);
            } else {
                exportDataSource = dataSource;
            }

            progress.setStatus("preparing");
            progress.addLog("INFO", "开始导出任务...");

            List<String> tables = parseTables(config);
            if (tables.isEmpty() && !"customSql".equals(config.getExportType())) {
                progress.addLog("ERROR", "没有指定要导出的表");
                progress.setStatus("failed");
                progress.setEndTime(System.currentTimeMillis());
                return;
            }

            progress.setTotalTables(tables.size());
            progress.setCompletedTables(0);
            progress.setFailedTables(0);
            progress.setTotalRows(0L);

            List<Path> generatedFiles = new ArrayList<>();
            List<String> exportFormats = parseExportFormats(config);

            if (exportFormats.contains("excel")) {
                Path excelFile = exportToExcel(taskId, exportDataSource, config, tables, progress);
                if (excelFile != null) {
                    generatedFiles.add(excelFile);
                }
            }

            if (exportFormats.contains("sql")) {
                Path sqlFile = exportToSql(taskId, exportDataSource, config, tables, progress);
                if (sqlFile != null) {
                    generatedFiles.add(sqlFile);
                }
            }

            String zipFileName = dbInfo.getDatabaseName() + "_" + DateUtil.formatFileSuffix() + ".zip";
            Path zipFilePath = Paths.get(exportPath, zipFileName);
            
            zipFiles(generatedFiles, zipFilePath);
            
            for (Path file : generatedFiles) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException e) {
                    log.warn("删除临时文件失败: {}", file);
                }
            }

            progress.setFileName(zipFileName);
            progress.setFileSize(Files.size(zipFilePath));
            progress.setStatus("completed");
            progress.addLog("SUCCESS", "导出完成！");

        } catch (Exception e) {
            log.error("导出任务失败", e);
            progress.setStatus("failed");
            progress.addLog("ERROR", "导出失败: " + e.getMessage());
        } finally {
            if (needReconnect && exportDataSource != null) {
                exportDataSource.close();
            }
            progress.setEndTime(System.currentTimeMillis());
        }
    }

    private List<String> parseTables(ExportConfig config) {
        List<String> tables = new ArrayList<>();
        String exportType = config.getExportType();
        
        if ("customTables".equals(exportType) || "tableSelect".equals(exportType)) {
            String tablesStr = config.getTables();
            if (tablesStr != null && !tablesStr.trim().isEmpty()) {
                String[] parts = tablesStr.split("[,，\\n\\r;]+");
                for (String part : parts) {
                    String table = part.trim();
                    if (!table.isEmpty()) {
                        tables.add(table);
                    }
                }
            }
        }
        
        return tables;
    }

    private List<String> parseExportFormats(ExportConfig config) {
        String formats = config.getExportFormats();
        if (formats == null || formats.trim().isEmpty()) {
            return Arrays.asList("excel");
        }
        return Arrays.asList(formats.split(","));
    }

    private Path exportToExcel(String taskId, HikariDataSource ds, ExportConfig config, 
                               List<String> tables, ExportProgress progress) throws Exception {
        String tempPath = appConfig.getTempPath();
        String fileName = "export_" + taskId + ".xlsx";
        Path filePath = Paths.get(tempPath, fileName);
        
        int batchSize = appConfig.getBatchSize();
        int fetchSize = appConfig.getFetchSize();

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100);
             FileOutputStream fos = new FileOutputStream(filePath.toFile())) {

            if ("customSql".equals(config.getExportType())) {
                exportCustomSqlToExcel(workbook, ds, config, progress, batchSize, fetchSize);
            } else {
                exportTablesToExcel(workbook, ds, config, tables, progress, batchSize, fetchSize);
            }

            workbook.write(fos);
            workbook.dispose();
        }

        progress.addLog("INFO", "Excel文件生成成功");
        return filePath;
    }

    private void exportTablesToExcel(SXSSFWorkbook workbook, HikariDataSource ds, ExportConfig config,
                                     List<String> tables, ExportProgress progress,
                                     int batchSize, int fetchSize) throws Exception {
        int completed = 0;
        
        for (String table : tables) {
            if (progress.getCanceled()) {
                break;
            }

            progress.setCurrentTable(table);
            progress.addLog("INFO", "正在导出表: " + table);
            
            try {
                long rowCount = exportTableToExcel(workbook, ds, config, table, batchSize, fetchSize);
                progress.setTotalRows(progress.getTotalRows() + rowCount);
                progress.setCurrentTableRows(rowCount);
                completed++;
                progress.setCompletedTables(completed);
                progress.addLog("SUCCESS", "表 " + table + " 导出完成，共 " + rowCount + " 行");
            } catch (Exception e) {
                log.error("导出表失败: {}", table, e);
                progress.setFailedTables(progress.getFailedTables() + 1);
                progress.addLog("ERROR", "表 " + table + " 导出失败: " + e.getMessage());
            }
        }
    }

    private long exportTableToExcel(SXSSFWorkbook workbook, HikariDataSource ds, ExportConfig config,
                                    String table, int batchSize, int fetchSize) throws Exception {
        String sheetName = sanitizeSheetName(table);
        Sheet sheet = workbook.createSheet(sheetName);
        
        String querySql = buildQuerySql(table, config, ds);
        
        long rowCount = 0;
        
        try (Connection conn = ds.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(querySql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            
            pstmt.setFetchSize(fetchSize);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                int columnCount = md.getColumnCount();
                
                Row headerRow = sheet.createRow(0);
                for (int i = 1; i <= columnCount; i++) {
                    Cell cell = headerRow.createCell(i - 1);
                    cell.setCellValue(md.getColumnName(i));
                }
                
                int rowNum = 1;
                while (rs.next()) {
                    Row row = sheet.createRow(rowNum++);
                    for (int i = 1; i <= columnCount; i++) {
                        Cell cell = row.createCell(i - 1);
                        setCellValue(cell, rs, i, md.getColumnType(i));
                    }
                    rowCount++;
                }
            }
        }
        
        return rowCount;
    }

    private void exportCustomSqlToExcel(SXSSFWorkbook workbook, HikariDataSource ds, ExportConfig config,
                                        ExportProgress progress, int batchSize, int fetchSize) throws Exception {
        String customSql = config.getCustomSql();
        if (customSql == null || customSql.trim().isEmpty()) {
            return;
        }
        
        String[] sqls = customSql.split("\\n");
        int completed = 0;
        
        for (int i = 0; i < sqls.length; i++) {
            String sql = sqls[i].trim();
            if (sql.isEmpty() || !SqlValidator.validateSelectSql(sql)) {
                continue;
            }
            
            if (progress.getCanceled()) {
                break;
            }
            
            String sheetName = "Query_" + (i + 1);
            progress.setCurrentTable(sheetName);
            progress.addLog("INFO", "正在执行SQL查询: " + sheetName);
            
            try {
                long rowCount = exportSqlToExcel(workbook, ds, sql, sheetName, batchSize, fetchSize);
                progress.setTotalRows(progress.getTotalRows() + rowCount);
                progress.setCurrentTableRows(rowCount);
                completed++;
                progress.setCompletedTables(completed);
                progress.addLog("SUCCESS", "SQL查询 " + sheetName + " 执行完成，共 " + rowCount + " 行");
            } catch (Exception e) {
                log.error("执行SQL失败: {}", sql, e);
                progress.setFailedTables(progress.getFailedTables() + 1);
                progress.addLog("ERROR", "SQL查询执行失败: " + e.getMessage());
            }
        }
    }

    private long exportSqlToExcel(SXSSFWorkbook workbook, HikariDataSource ds, String sql,
                                  String sheetName, int batchSize, int fetchSize) throws Exception {
        Sheet sheet = workbook.createSheet(sheetName);
        long rowCount = 0;
        
        try (Connection conn = ds.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            
            pstmt.setFetchSize(fetchSize);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                int columnCount = md.getColumnCount();
                
                Row headerRow = sheet.createRow(0);
                for (int i = 1; i <= columnCount; i++) {
                    Cell cell = headerRow.createCell(i - 1);
                    cell.setCellValue(md.getColumnName(i));
                }
                
                int rowNum = 1;
                while (rs.next()) {
                    Row row = sheet.createRow(rowNum++);
                    for (int i = 1; i <= columnCount; i++) {
                        Cell cell = row.createCell(i - 1);
                        setCellValue(cell, rs, i, md.getColumnType(i));
                    }
                    rowCount++;
                }
            }
        }
        
        return rowCount;
    }

    private void setCellValue(Cell cell, ResultSet rs, int columnIndex, int sqlType) throws Exception {
        Object value = rs.getObject(columnIndex);
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
        } else if (value instanceof java.util.Date) {
            cell.setCellValue((java.util.Date) value);
        } else {
            cell.setCellValue(value.toString());
        }
    }

    private Path exportToSql(String taskId, HikariDataSource ds, ExportConfig config,
                             List<String> tables, ExportProgress progress) throws Exception {
        String tempPath = appConfig.getTempPath();
        String fileName = "export_" + taskId + ".sql";
        Path filePath = Paths.get(tempPath, fileName);
        
        int batchSize = appConfig.getBatchSize();
        int fetchSize = appConfig.getFetchSize();

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                Files.newOutputStream(filePath), "UTF-8"))) {

            if ("customSql".equals(config.getExportType())) {
                writer.write("-- 自定义SQL导出\n");
            } else {
                for (String table : tables) {
                    if (progress.getCanceled()) {
                        break;
                    }
                    
                    exportTableToSql(writer, ds, config, table, batchSize, fetchSize);
                }
            }
        }

        progress.addLog("INFO", "SQL文件生成成功");
        return filePath;
    }

    private void exportTableToSql(BufferedWriter writer, HikariDataSource ds, ExportConfig config,
                                   String table, int batchSize, int fetchSize) throws Exception {
        String querySql = buildQuerySql(table, config, ds);
        
        writer.write("\n-- " + table + " 表数据\n");
        
        try (Connection conn = ds.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(querySql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            
            pstmt.setFetchSize(fetchSize);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                int columnCount = md.getColumnCount();
                
                List<String> columnNames = new ArrayList<>();
                for (int i = 1; i <= columnCount; i++) {
                    columnNames.add(md.getColumnName(i));
                }
                
                int rowCount = 0;
                while (rs.next()) {
                    if (rowCount % batchSize == 0) {
                        if (rowCount > 0) {
                            writer.write(";\n");
                        }
                        writer.write("INSERT INTO " + escapeIdentifier(table) + " (");
                        for (int i = 0; i < columnNames.size(); i++) {
                            if (i > 0) writer.write(",");
                            writer.write(escapeIdentifier(columnNames.get(i)));
                        }
                        writer.write(") VALUES ");
                    } else {
                        writer.write(",");
                    }
                    
                    writer.write("\n(");
                    for (int i = 1; i <= columnCount; i++) {
                        if (i > 1) writer.write(",");
                        writeSqlValue(writer, rs, i, md.getColumnType(i));
                    }
                    writer.write(")");
                    rowCount++;
                }
                
                if (rowCount > 0) {
                    writer.write(";\n");
                }
                
                writer.write("-- 共 " + rowCount + " 行\n");
            }
        }
    }

    private void writeSqlValue(BufferedWriter writer, ResultSet rs, int columnIndex, int sqlType) throws Exception {
        Object value = rs.getObject(columnIndex);
        if (value == null) {
            writer.write("NULL");
        } else if (value instanceof Number) {
            writer.write(value.toString());
        } else if (value instanceof Boolean) {
            writer.write(((Boolean) value) ? "1" : "0");
        } else {
            String str = value.toString();
            str = str.replace("'", "''");
            writer.write("'" + str + "'");
        }
    }

    private String buildQuerySql(String table, ExportConfig config, HikariDataSource ds) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM ");
        sql.append(escapeIdentifier(table));
        
        List<String> conditions = new ArrayList<>();
        
        if (Boolean.TRUE.equals(config.getEnableTimeFilter())) {
            String timeCondition = buildTimeCondition(table, config, ds);
            if (timeCondition != null) {
                conditions.add(timeCondition);
            }
        }
        
        if (Boolean.TRUE.equals(config.getEnableFieldFilter()) && config.getFieldFilter() != null) {
            String fieldCondition = config.getFieldFilter().buildCondition();
            if (fieldCondition != null) {
                conditions.add(fieldCondition);
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

    private String buildTimeCondition(String table, ExportConfig config, HikariDataSource ds) throws SQLException {
        String timeFields = config.getTimeFieldNames();
        if (timeFields == null || timeFields.trim().isEmpty()) {
            return null;
        }
        
        List<String> fieldNames = new ArrayList<>();
        String[] parts = timeFields.split("[,，]");
        for (String part : parts) {
            String field = part.trim();
            if (!field.isEmpty()) {
                fieldNames.add(field);
            }
        }
        
        if (fieldNames.isEmpty()) return null;
        
        try (Connection conn = ds.getConnection()) {
            List<String> tableColumns = DatabaseUtil.getColumnNames(conn, table);
            
            for (String fieldName : fieldNames) {
                if (tableColumns.contains(fieldName)) {
                    String startDate = config.getStartDate();
                    String endDate = config.getEndDate();
                    
                    if (startDate != null && !startDate.isEmpty() && endDate != null && !endDate.isEmpty()) {
                        return escapeIdentifier(fieldName) + " >= '" + startDate + "' AND " 
                               + escapeIdentifier(fieldName) + " <= '" + endDate + " 23:59:59'";
                    } else if (startDate != null && !startDate.isEmpty()) {
                        return escapeIdentifier(fieldName) + " >= '" + startDate + "'";
                    } else if (endDate != null && !endDate.isEmpty()) {
                        return escapeIdentifier(fieldName) + " <= '" + endDate + " 23:59:59'";
                    }
                }
            }
        }
        
        return null;
    }

    private String escapeIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    private String sanitizeSheetName(String name) {
        String sanitized = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (sanitized.length() > 31) {
            sanitized = sanitized.substring(0, 31);
        }
        return sanitized;
    }

    private void zipFiles(List<Path> files, Path zipFilePath) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFilePath))) {
            for (Path file : files) {
                ZipEntry entry = new ZipEntry(file.getFileName().toString());
                zos.putNextEntry(entry);
                Files.copy(file, zos);
                zos.closeEntry();
            }
        }
    }

    public ExportProgress getProgress(String taskId) {
        return progressMap.get(taskId);
    }

    public void cancelTask(String taskId) {
        ExportProgress progress = progressMap.get(taskId);
        if (progress != null) {
            progress.setCanceled(true);
            progress.addLog("INFO", "任务已取消");
        }
    }

    public List<ExportFile> getExportFiles() {
        List<ExportFile> files = new ArrayList<>();
        String exportPath = appConfig.getExportPath();
        File dir = new File(exportPath);
        
        if (!dir.exists() || !dir.isDirectory()) {
            return files;
        }
        
        File[] fileList = dir.listFiles((d, name) -> name.endsWith(".zip"));
        if (fileList == null) return files;
        
        Arrays.sort(fileList, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        
        for (File file : fileList) {
            ExportFile exportFile = new ExportFile();
            exportFile.setFileName(file.getName());
            exportFile.setFileSize(file.length());
            exportFile.setCreateTime(file.lastModified());
            exportFile.setStatus("ready");
            files.add(exportFile);
        }
        
        return files;
    }

    public Path getExportFile(String fileName) {
        String exportPath = appConfig.getExportPath();
        Path filePath = Paths.get(exportPath, fileName);
        if (Files.exists(filePath)) {
            return filePath;
        }
        return null;
    }

    public boolean deleteExportFile(String fileName) {
        String exportPath = appConfig.getExportPath();
        Path filePath = Paths.get(exportPath, fileName);
        try {
            return Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.error("删除文件失败: {}", fileName, e);
            return false;
        }
    }
}
