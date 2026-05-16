package com.dbexport.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExportProgress {

    private String taskId;
    private String status;
    private Integer totalTables;
    private Integer completedTables;
    private String currentTable;
    private Long currentTableRows;
    private Long totalRows;
    private Long exportedRows;
    private Long startTime;
    private Long elapsedTime;
    private Long estimatedRemaining;
    private List<LogEntry> logs;
    private String exportFileName;
    private Long fileSize;
    private List<String> errors;

    public ExportProgress() {
        this.logs = new ArrayList<>();
        this.errors = new ArrayList<>();
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getTotalTables() {
        return totalTables;
    }

    public void setTotalTables(Integer totalTables) {
        this.totalTables = totalTables;
    }

    public Integer getCompletedTables() {
        return completedTables;
    }

    public void setCompletedTables(Integer completedTables) {
        this.completedTables = completedTables;
    }

    public String getCurrentTable() {
        return currentTable;
    }

    public void setCurrentTable(String currentTable) {
        this.currentTable = currentTable;
    }

    public Long getCurrentTableRows() {
        return currentTableRows;
    }

    public void setCurrentTableRows(Long currentTableRows) {
        this.currentTableRows = currentTableRows;
    }

    public Long getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(Long totalRows) {
        this.totalRows = totalRows;
    }

    public Long getExportedRows() {
        return exportedRows;
    }

    public void setExportedRows(Long exportedRows) {
        this.exportedRows = exportedRows;
    }

    public Long getStartTime() {
        return startTime;
    }

    public void setStartTime(Long startTime) {
        this.startTime = startTime;
    }

    public Long getElapsedTime() {
        return elapsedTime;
    }

    public void setElapsedTime(Long elapsedTime) {
        this.elapsedTime = elapsedTime;
    }

    public Long getEstimatedRemaining() {
        return estimatedRemaining;
    }

    public void setEstimatedRemaining(Long estimatedRemaining) {
        this.estimatedRemaining = estimatedRemaining;
    }

    public List<LogEntry> getLogs() {
        return logs;
    }

    public void setLogs(List<LogEntry> logs) {
        this.logs = logs;
    }

    public String getExportFileName() {
        return exportFileName;
    }

    public void setExportFileName(String exportFileName) {
        this.exportFileName = exportFileName;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }

    public void addLog(String level, String message) {
        if (this.logs == null) {
            this.logs = new ArrayList<>();
        }
        this.logs.add(new LogEntry(System.currentTimeMillis(), level, message));
    }

    public double calculateProgress() {
        if (totalRows == null || totalRows == 0) {
            return 0.0;
        }
        if (exportedRows == null) {
            return 0.0;
        }
        double progress = (double) exportedRows / totalRows * 100.0;
        return Math.min(progress, 100.0);
    }

    public long calculateElapsed() {
        if (startTime == null) {
            return 0L;
        }
        this.elapsedTime = System.currentTimeMillis() - startTime;
        return this.elapsedTime;
    }

    public long calculateEstimatedRemaining() {
        calculateElapsed();
        if (elapsedTime == null || elapsedTime == 0) {
            return 0L;
        }
        if (exportedRows == null || exportedRows == 0) {
            return 0L;
        }
        if (totalRows == null || totalRows == 0) {
            return 0L;
        }
        long remainingRows = totalRows - exportedRows;
        if (remainingRows <= 0) {
            return 0L;
        }
        double rowsPerMs = (double) exportedRows / elapsedTime;
        if (rowsPerMs == 0) {
            return 0L;
        }
        this.estimatedRemaining = (long) (remainingRows / rowsPerMs);
        return this.estimatedRemaining;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LogEntry {

        private Long timestamp;
        private String level;
        private String message;

        public LogEntry() {
        }

        public LogEntry(Long timestamp, String level, String message) {
            this.timestamp = timestamp;
            this.level = level;
            this.message = message;
        }

        public Long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(Long timestamp) {
            this.timestamp = timestamp;
        }

        public String getLevel() {
            return level;
        }

        public void setLevel(String level) {
            this.level = level;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}