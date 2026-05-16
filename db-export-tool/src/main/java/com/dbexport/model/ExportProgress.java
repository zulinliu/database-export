package com.dbexport.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ExportProgress {
    private String taskId;
    private Integer totalTables;
    private Integer completedTables;
    private Integer failedTables;
    private Long totalRows;
    private String currentTable;
    private Long currentTableRows;
    private String status;
    private Long startTime;
    private Long endTime;
    private List<LogEntry> logs = new ArrayList<>();
    private String fileName;
    private Long fileSize;
    private Boolean canceled = false;

    @Data
    public static class LogEntry {
        private Long timestamp;
        private String level;
        private String message;

        public LogEntry(String level, String message) {
            this.timestamp = System.currentTimeMillis();
            this.level = level;
            this.message = message;
        }
    }

    public void addLog(String level, String message) {
        this.logs.add(new LogEntry(level, message));
        if (this.logs.size() > 500) {
            this.logs.remove(0);
        }
    }

    public double getProgressPercent() {
        if (totalTables == null || totalTables == 0) return 0;
        return (double) (completedTables + failedTables) / totalTables * 100;
    }

    public long getElapsedTime() {
        if (startTime == null) return 0;
        if (endTime != null) return endTime - startTime;
        return System.currentTimeMillis() - startTime;
    }

    public long getEstimatedRemainingTime() {
        if (completedTables == null || completedTables == 0) return 0;
        long elapsed = getElapsedTime();
        double avgTimePerTable = (double) elapsed / completedTables;
        int remaining = totalTables - completedTables - failedTables;
        return (long) (avgTimePerTable * remaining);
    }
}
