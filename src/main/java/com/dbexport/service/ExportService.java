package com.dbexport.service;

import com.dbexport.model.*;

import java.sql.Connection;
import java.util.List;
import java.util.Map;

public interface ExportService {

    boolean testConnection(DatabaseInfo dbInfo);

    boolean connectDatabase(DatabaseInfo dbInfo);

    List<DatabaseTableInfo> getTableList();

    ConnectionPoolStatus getConnectionPoolStatus();

    String startExport(ExportConfig config);

    ExportProgress getProgress(String taskId);

    boolean cancelTask(String taskId);

    List<ExportFileInfo> getExportFiles();

    String getExportFilePath(String fileName);

    boolean deleteExportFile(String fileName);

    void shutdown();
}
