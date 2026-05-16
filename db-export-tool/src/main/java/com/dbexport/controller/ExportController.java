package com.dbexport.controller;

import com.dbexport.model.ApiResponse;
import com.dbexport.model.DatabaseInfo;
import com.dbexport.model.ExportConfig;
import com.dbexport.model.ExportProgress;
import com.dbexport.service.ExportServiceImpl;
import com.dbexport.util.SqlValidator;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/export")
public class ExportController {

    private static final String EXPORT_DIR = "./exports";
    private static final String TEMP_DIR = "./temp";

    @Autowired
    private ExportServiceImpl exportService;

    private final Map<String, HikariDataSource> sessionDataSourceMap = new ConcurrentHashMap<>();

    private HikariDataSource getCurrentDataSource(HttpSession session) {
        String sessionId = session.getId();
        return sessionDataSourceMap.get(sessionId);
    }

    @PostMapping("/start")
    public ApiResponse<?> startExport(@RequestBody ExportConfig config, HttpSession session) {
        try {
            HikariDataSource ds = getCurrentDataSource(session);
            if (ds == null) {
                return ApiResponse.error(401, "请先连接数据库");
            }

            String exportType = config.getExportType();
            if (exportType == null || exportType.trim().isEmpty()) {
                return ApiResponse.error(400, "导出类型不能为空");
            }

            if (!Arrays.asList("customTables", "tableSelect", "customSql").contains(exportType)) {
                return ApiResponse.error(400, "无效的导出类型");
            }

            if ("customSql".equals(exportType)) {
                String customSql = config.getCustomSql();
                if (customSql == null || customSql.trim().isEmpty()) {
                    return ApiResponse.error(400, "自定义SQL不能为空");
                }
                String[] sqlStatements = customSql.split(";");
                for (String sql : sqlStatements) {
                    String trimmed = sql.trim();
                    if (!trimmed.isEmpty()) {
                        SqlValidator.ValidationResult result = SqlValidator.validate(trimmed);
                        if (!result.isValid()) {
                            return ApiResponse.error(400, "SQL验证失败: " + result.getMessage());
                        }
                    }
                }
            }

            String taskId = UUID.randomUUID().toString();
            exportService.startExport(taskId, config, ds);

            Map<String, Object> data = new HashMap<>();
            data.put("taskId", taskId);
            return ApiResponse.success("导出任务已启动", data);
        } catch (Exception e) {
            return ApiResponse.error("启动导出任务失败: " + e.getMessage());
        }
    }

    @GetMapping("/progress/{taskId}")
    public ApiResponse<?> getExportProgress(@PathVariable String taskId) {
        try {
            ExportProgress progress = exportService.getProgress(taskId);
            if (progress == null) {
                return ApiResponse.error(404, "任务不存在或已过期");
            }
            return ApiResponse.success(progress);
        } catch (Exception e) {
            return ApiResponse.error("获取进度失败: " + e.getMessage());
        }
    }

    @PostMapping("/cancel/{taskId}")
    public ApiResponse<?> cancelExport(@PathVariable String taskId) {
        try {
            boolean canceled = exportService.cancelTask(taskId);
            if (canceled) {
                return ApiResponse.success("已取消导出任务");
            } else {
                return ApiResponse.error("取消失败，任务可能已完成或不存在");
            }
        } catch (Exception e) {
            return ApiResponse.error("取消任务失败: " + e.getMessage());
        }
    }

    @GetMapping("/download/{fileName:.+}")
    public void downloadFile(@PathVariable String fileName, HttpServletResponse response) {
        if (fileName == null || fileName.isEmpty()) {
            try {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":400,\"message\":\"文件名不能为空\"}");
            } catch (IOException ignored) {
            }
            return;
        }

        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            try {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":403,\"message\":\"非法文件名\"}");
            } catch (IOException ignored) {
            }
            return;
        }

        File file = new File(EXPORT_DIR, fileName);
        try {
            file = file.getCanonicalFile();
        } catch (IOException e) {
            try {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":500,\"message\":\"文件路径解析失败\"}");
            } catch (IOException ignored) {
            }
            return;
        }

        File exportDir = new File(EXPORT_DIR);
        try {
            exportDir = exportDir.getCanonicalFile();
        } catch (IOException e) {
            try {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":500,\"message\":\"导出目录解析失败\"}");
            } catch (IOException ignored) {
            }
            return;
        }

        if (!file.getPath().startsWith(exportDir.getPath())) {
            try {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":403,\"message\":\"访问被拒绝\"}");
            } catch (IOException ignored) {
            }
            return;
        }

        if (!file.exists()) {
            try {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":404,\"message\":\"文件不存在\"}");
            } catch (IOException ignored) {
            }
            return;
        }

        try (FileInputStream fis = new FileInputStream(file);
             OutputStream os = response.getOutputStream()) {

            String mimeType = Files.probeContentType(file.toPath());
            if (mimeType == null) {
                mimeType = "application/octet-stream";
            }
            response.setContentType(mimeType);
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + URLEncoder.encode(fileName, "UTF-8") + "\"");
            response.setContentLengthLong(file.length());

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            os.flush();
        } catch (IOException e) {
            try {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":500,\"message\":\"文件下载失败: " + e.getMessage() + "\"}");
            } catch (IOException ignored) {
            }
        }
    }

    @PostMapping("/delete")
    public ApiResponse<?> deleteExportFiles(@RequestBody Map<String, List<String>> body) {
        try {
            List<String> fileNames = body.get("fileNames");
            if (fileNames == null || fileNames.isEmpty()) {
                return ApiResponse.error(400, "请选择要删除的文件");
            }

            exportService.deleteExportFiles(fileNames);

            Map<String, Object> data = new HashMap<>();
            data.put("deletedCount", fileNames.size());
            return ApiResponse.success("删除成功", data);
        } catch (Exception e) {
            return ApiResponse.error("删除文件失败: " + e.getMessage());
        }
    }

    @GetMapping("/list")
    public ApiResponse<?> listExportFiles() {
        try {
            List<Map<String, Object>> fileList = exportService.listExportFiles();
            return ApiResponse.success(fileList);
        } catch (Exception e) {
            return ApiResponse.error("获取导出文件列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/validateSql")
    public ApiResponse<?> validateSql(@RequestParam String sql) {
        try {
            SqlValidator.ValidationResult result = SqlValidator.validate(sql);
            Map<String, Object> data = new HashMap<>();
            data.put("valid", result.isValid());
            data.put("message", result.getMessage());
            if (result.isValid()) {
                String tableName = SqlValidator.extractTableName(sql);
                data.put("tableName", tableName);
            }
            return ApiResponse.success(data);
        } catch (Exception e) {
            return ApiResponse.error("SQL验证失败: " + e.getMessage());
        }
    }

    @PostMapping("/cleanOld")
    public ApiResponse<?> cleanOldFiles(@RequestBody Map<String, Integer> body) {
        try {
            Integer daysOld = body.get("days");
            if (daysOld == null || daysOld < 1) {
                return ApiResponse.error(400, "请指定有效的天数");
            }

            int deletedCount = exportService.cleanOldExportFiles(daysOld);
            Map<String, Object> data = new HashMap<>();
            data.put("deletedCount", deletedCount);
            return ApiResponse.success("清理完成，共删除 " + deletedCount + " 个文件", data);
        } catch (Exception e) {
            return ApiResponse.error("清理失败: " + e.getMessage());
        }
    }

    @PostMapping("/saveSessionDs")
    public ApiResponse<?> saveSessionDataSource(@RequestBody DatabaseInfo dbInfo, HttpSession session) {
        try {
            String sessionId = session.getId();
            HikariDataSource existingDs = sessionDataSourceMap.get(sessionId);
            if (existingDs != null) {
                exportService.disconnect(existingDs);
            }

            HikariDataSource ds = exportService.connect(dbInfo);
            sessionDataSourceMap.put(sessionId, ds);

            return ApiResponse.success("数据源已保存");
        } catch (Exception e) {
            return ApiResponse.error("保存数据源失败: " + e.getMessage());
        }
    }

    @PostMapping("/clearSessionDs")
    public ApiResponse<?> clearSessionDataSource(HttpSession session) {
        try {
            String sessionId = session.getId();
            HikariDataSource ds = sessionDataSourceMap.remove(sessionId);
            if (ds != null) {
                exportService.disconnect(ds);
            }
            return ApiResponse.success("数据源已清除");
        } catch (Exception e) {
            return ApiResponse.error("清除数据源失败: " + e.getMessage());
        }
    }
}
