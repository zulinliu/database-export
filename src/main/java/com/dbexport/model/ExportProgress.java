package com.dbexport.model;

import lombok.Data;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Data
public class ExportProgress {
    private String taskId;
    private volatile String status;
    private volatile int totalTables;
    private AtomicInteger completedTables = new AtomicInteger(0);
    private AtomicLong totalRows = new AtomicLong(0);
    private volatile String currentTable;
    private volatile long currentTableRows;
    private volatile long currentTableTotal;
    private volatile long startTime;
    private volatile long endTime;
    private AtomicInteger successCount = new AtomicInteger(0);
    private AtomicInteger failCount = new AtomicInteger(0);
    private volatile long fileSize;
    private volatile String fileName;
    private final ConcurrentLinkedDeque<LogEntry> logs = new ConcurrentLinkedDeque<>();
    private volatile String errorMessage;

    @Data
    public static class LogEntry {
        private long timestamp;
        private String level;
        private String message;

        public LogEntry(String level, String message) {
            this.timestamp = System.currentTimeMillis();
            this.level = level;
            this.message = message;
        }
    }

    public void addLog(String level, String message) {
        logs.add(new LogEntry(level, message));
        if (logs.size() > 500) {
            logs.removeFirst();
        }
    }

    public int getProgressPercent() {
        if (totalTables == 0) return 0;
        return (int) ((completedTables.get() * 100.0) / totalTables);
    }

    public long getElapsedTime() {
        long end = endTime > 0 ? endTime : System.currentTimeMillis();
        return (end - startTime) / 1000;
    }

    public long getEstimatedRemaining() {
        if (completedTables.get() == 0) return 0;
        long elapsed = getElapsedTime();
        long remaining = (elapsed / completedTables.get()) * (totalTables - completedTables.get());
        return remaining;
    }
}
