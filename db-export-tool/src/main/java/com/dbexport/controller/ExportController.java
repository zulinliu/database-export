package com.dbexport.controller;

import com.dbexport.model.ApiResponse;
import com.dbexport.model.DatabaseInfo;
import com.dbexport.model.ExportConfig;
import com.dbexport.model.ExportProgress;
import com.dbexport.model.Template;
import com.dbexport.service.ExportServiceImpl;
import com.dbexport.service.TemplateService;
import com.dbexport.util.SqlValidator;
import com.zaxxer.hikari.HikariDataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class ExportController {

    private static final String EXPORT_DIR = "./exports";
    private static final String TEMP_DIR = "./temp";

    @Value("${app.login.username:admin}")
    private String configUsername;

    @Value("${app.login.password:123456}")
    private String configPassword;

    @Autowired
    private ExportServiceImpl exportService;

    @Autowired
    private TemplateService templateService;

    private final Map<String, HikariDataSource> sessionDataSourceMap = new ConcurrentHashMap<>();

    @PostMapping("/login")
    public ApiResponse<?> login(@RequestBody Map<String, String> body, HttpSession session) {
        try {
            String username = body.get("username");
            String password = body.get("password");

            if (username == null || username.trim().isEmpty()) {
                return ApiResponse.error(400, "用户名不能为空");
            }
            if (password == null || password.trim().isEmpty()) {
                return ApiResponse.error(400, "密码不能为空");
            }

            if (configUsername.equals(username) && configPassword.equals(password)) {
                session.setAttribute("user", username);
                session.setMaxInactiveInterval(1800);
                Map<String, Object> data = new HashMap<>();
                data.put("username", username);
                return ApiResponse.success("登录成功", data);
            }

            return ApiResponse.error(401, "用户名或密码错误");
        } catch (Exception e) {
            return ApiResponse.error("登录失败: " + e.getMessage());
        }
    }

    @PostMapping("/logout")
    public ApiResponse<?> logout(HttpSession session) {
        try {
            String sessionId = session.getId();
            HikariDataSource ds = sessionDataSourceMap.remove(sessionId);
            if (ds != null) {
                exportService.disconnect(ds);
            }
            session.invalidate();
            return ApiResponse.success("已登出");
        } catch (Exception e) {
            return ApiResponse.error("登出失败: " + e.getMessage());
        }
    }

    @GetMapping("/checkLogin")
    public ApiResponse<?> checkLogin(HttpSession session) {
        try {
            Object user = session.getAttribute("user");
            if (user != null) {
                return ApiResponse.success(user);
            }
            return ApiResponse.error(401, "未登录");
        } catch (Exception e) {
            return ApiResponse.error(401, "未登录");
        }
    }

    @PostMapping("/database/test")
    public ApiResponse<?> testConnection(@RequestBody DatabaseInfo dbInfo) {
        try {
            if (dbInfo.getType() == null || dbInfo.getType().trim().isEmpty()) {
                return ApiResponse.error(400, "数据库类型不能为空");
            }
            if (dbInfo.getHost() == null || dbInfo.getHost().trim().isEmpty()) {
                return ApiResponse.error(400, "数据库主机不能为空");
            }
            if (dbInfo.getPort() == null) {
                return ApiResponse.error(400, "数据库端口不能为空");
            }
            if (dbInfo.getUsername() == null || dbInfo.getUsername().trim().isEmpty()) {
                return ApiResponse.error(400, "用户名不能为空");
            }

            long startTime = System.currentTimeMillis();
            boolean result = exportService.testConnection(dbInfo);
            long elapsed = System.currentTimeMillis() - startTime;

            Map<String, Object> data = new HashMap<>();
            data.put("success", result);
            data.put("elapsed", elapsed);
            data.put("message", result ? "连接成功" : "连接失败");

            return ApiResponse.success(data);
        } catch (Exception e) {
            return ApiResponse.error("连接测试失败: " + e.getMessage());
        }
    }

    @PostMapping("/database/connect")
    public ApiResponse<?> connect(@RequestBody DatabaseInfo dbInfo, HttpSession session) {
        try {
            if (dbInfo.getType() == null || dbInfo.getType().trim().isEmpty()) {
                return ApiResponse.error(400, "数据库类型不能为空");
            }
            if (dbInfo.getHost() == null || dbInfo.getHost().trim().isEmpty()) {
                return ApiResponse.error(400, "数据库主机不能为空");
            }
            if (dbInfo.getPort() == null) {
                return ApiResponse.error(400, "数据库端口不能为空");
            }
            if (dbInfo.getDatabaseName() == null || dbInfo.getDatabaseName().trim().isEmpty()) {
                return ApiResponse.error(400, "数据库名称不能为空");
            }
            if (dbInfo.getUsername() == null || dbInfo.getUsername().trim().isEmpty()) {
                return ApiResponse.error(400, "用户名不能为空");
            }

            String sessionId = session.getId();
            HikariDataSource existingDs = sessionDataSourceMap.get(sessionId);
            if (existingDs != null) {
                exportService.disconnect(existingDs);
            }

            HikariDataSource ds = exportService.connect(dbInfo);
            sessionDataSourceMap.put(sessionId, ds);

            List<String> tableList = exportService.getTableList(ds);

            Map<String, Object> data = new HashMap<>();
            data.put("type", dbInfo.getType());
            data.put("host", dbInfo.getHost());
            data.put("port", dbInfo.getPort());
            data.put("databaseName", dbInfo.getDatabaseName());
            data.put("username", dbInfo.getUsername());
            data.put("tableCount", tableList != null ? tableList.size() : 0);

            return ApiResponse.success("连接成功", data);
        } catch (Exception e) {
            return ApiResponse.error("连接数据库失败: " + e.getMessage());
        }
    }

    @PostMapping("/database/disconnect")
    public ApiResponse<?> disconnect(HttpSession session) {
        try {
            String sessionId = session.getId();
            HikariDataSource ds = sessionDataSourceMap.remove(sessionId);
            if (ds == null) {
                return ApiResponse.error(400, "未找到数据库连接");
            }
            exportService.disconnect(ds);
            return ApiResponse.success("已断开连接");
        } catch (Exception e) {
            return ApiResponse.error("断开连接失败: " + e.getMessage());
        }
    }

    @GetMapping("/database/tables")
    public ApiResponse<?> getTables(HttpSession session,
                                     @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int pageSize,
                                     @RequestParam(required = false) String search) {
        try {
            HikariDataSource ds = getCurrentDataSource(session);
            if (ds == null) {
                return ApiResponse.error(401, "请先连接数据库");
            }

            List<String> allTables = exportService.getTableList(ds);

            if (search != null && !search.trim().isEmpty()) {
                String lowerSearch = search.toLowerCase();
                allTables = allTables.stream()
                        .filter(t -> t.toLowerCase().contains(lowerSearch))
                        .collect(Collectors.toList());
            }

            int total = allTables.size();
            int fromIndex = (page - 1) * pageSize;
            int toIndex = Math.min(fromIndex + pageSize, total);

            List<String> pageTables;
            if (fromIndex >= total) {
                pageTables = Collections.emptyList();
            } else {
                pageTables = allTables.subList(fromIndex, toIndex);
            }

            List<Map<String, Object>> tableInfoList = new ArrayList<>();
            for (String tableName : pageTables) {
                Map<String, Object> info = exportService.getTableInfo(ds, tableName);
                tableInfoList.add(info);
            }

            Map<String, Object> data = new HashMap<>();
            data.put("total", total);
            data.put("page", page);
            data.put("pageSize", pageSize);
            data.put("tables", tableInfoList);

            return ApiResponse.success(data);
        } catch (Exception e) {
            return ApiResponse.error("获取表列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/database/status")
    public ApiResponse<?> getDatabaseStatus(HttpSession session) {
        try {
            HikariDataSource ds = getCurrentDataSource(session);
            if (ds == null) {
                return ApiResponse.error(401, "请先连接数据库");
            }

            Map<String, Object> status = exportService.getPoolStatus(ds);
            return ApiResponse.success(status);
        } catch (Exception e) {
            return ApiResponse.error("获取数据库状态失败: " + e.getMessage());
        }
    }

    @PostMapping("/export/start")
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

            if ("customTables".equals(exportType)) {
                if (config.getTables() == null || config.getTables().trim().isEmpty()) {
                    return ApiResponse.error(400, "请输入要导出的表名");
                }
            } else if ("tableSelect".equals(exportType)) {
                // tableSelect uses config.tables from JS (comma-separated selected table names)
                if (config.getTables() == null || config.getTables().trim().isEmpty()) {
                    return ApiResponse.error(400, "请选择要导出的表");
                }
            } else if ("customSql".equals(exportType)) {
                if (config.getCustomSql() == null || config.getCustomSql().trim().isEmpty()) {
                    return ApiResponse.error(400, "请输入自定义SQL语句");
                }
                String[] sqlLines = config.getCustomSql().split("\n");
                for (String sql : sqlLines) {
                    String trimmed = sql.trim();
                    if (!trimmed.isEmpty()) {
                        SqlValidator.ValidationResult validationResult = SqlValidator.validate(trimmed);
                        if (!validationResult.isValid()) {
                            return ApiResponse.error(400, "SQL验证失败: " + validationResult.getMessage());
                        }
                    }
                }
            } else {
                return ApiResponse.error(400, "不支持的导出类型: " + exportType);
            }

            String taskId = UUID.randomUUID().toString().replace("-", "");
            exportService.startExport(taskId, config, ds);

            Map<String, Object> data = new HashMap<>();
            data.put("taskId", taskId);

            return ApiResponse.success("导出任务已启动", data);
        } catch (Exception e) {
            return ApiResponse.error("启动导出任务失败: " + e.getMessage());
        }
    }

    @GetMapping("/export/progress/{taskId}")
    public ApiResponse<?> getProgress(@PathVariable String taskId) {
        try {
            if (taskId == null || taskId.trim().isEmpty()) {
                return ApiResponse.error(400, "任务ID不能为空");
            }

            ExportProgress progress = exportService.getProgress(taskId);
            if (progress == null) {
                return ApiResponse.error(404, "任务不存在");
            }

            progress.calculateElapsed();
            progress.calculateEstimatedRemaining();

            return ApiResponse.success(progress);
        } catch (Exception e) {
            return ApiResponse.error("获取进度失败: " + e.getMessage());
        }
    }

    @PostMapping("/export/cancel/{taskId}")
    public ApiResponse<?> cancelExport(@PathVariable String taskId) {
        try {
            if (taskId == null || taskId.trim().isEmpty()) {
                return ApiResponse.error(400, "任务ID不能为空");
            }

            boolean cancelled = exportService.cancelTask(taskId);
            if (cancelled) {
                return ApiResponse.success("任务已取消");
            }
            return ApiResponse.error(400, "取消失败，任务可能不存在或已完成");
        } catch (Exception e) {
            return ApiResponse.error("取消任务失败: " + e.getMessage());
        }
    }

    @GetMapping("/export/files")
    public ApiResponse<?> getExportFiles() {
        try {
            List<Map<String, Object>> files = exportService.getExportFiles();
            return ApiResponse.success(files);
        } catch (Exception e) {
            return ApiResponse.error("获取导出文件列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/export/download/{fileName:.+}")
    public void downloadFile(@PathVariable String fileName, HttpServletResponse response) {
        File file = new File(EXPORT_DIR, fileName);
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

    @PostMapping("/export/delete")
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

    @GetMapping("/template/list")
    public ApiResponse<?> listTemplates(@RequestParam(required = false) String keyword) {
        try {
            List<Template> templates;
            if (keyword != null && !keyword.trim().isEmpty()) {
                templates = templateService.search(keyword);
            } else {
                templates = templateService.listAll();
            }
            return ApiResponse.success(templates);
        } catch (Exception e) {
            return ApiResponse.error("获取模板列表失败: " + e.getMessage());
        }
    }

    @PostMapping("/template/save")
    public ApiResponse<?> saveTemplate(@RequestBody Template template) {
        try {
            if (template.getName() == null || template.getName().trim().isEmpty()) {
                return ApiResponse.error(400, "模板名称不能为空");
            }

            Template saved = templateService.save(template);
            return ApiResponse.success("保存成功", saved);
        } catch (Exception e) {
            return ApiResponse.error("保存模板失败: " + e.getMessage());
        }
    }

    @GetMapping("/template/{id}")
    public ApiResponse<?> getTemplate(@PathVariable Long id) {
        try {
            if (id == null) {
                return ApiResponse.error(400, "模板ID不能为空");
            }

            Template template = templateService.getById(id);
            if (template == null) {
                return ApiResponse.error(404, "模板不存在");
            }
            return ApiResponse.success(template);
        } catch (Exception e) {
            return ApiResponse.error("获取模板失败: " + e.getMessage());
        }
    }

    @PostMapping("/template/delete")
    public ApiResponse<?> deleteTemplate(@RequestBody Map<String, Long> body) {
        try {
            Long id = body.get("id");
            if (id == null) {
                return ApiResponse.error(400, "模板ID不能为空");
            }

            boolean deleted = templateService.deleteById(id);
            if (deleted) {
                return ApiResponse.success("删除成功");
            }
            return ApiResponse.error(404, "模板不存在");
        } catch (Exception e) {
            return ApiResponse.error("删除模板失败: " + e.getMessage());
        }
    }

    @PostMapping("/template/batchDelete")
    public ApiResponse<?> batchDeleteTemplates(@RequestBody Map<String, List<Long>> body) {
        try {
            List<Long> ids = body.get("ids");
            if (ids == null || ids.isEmpty()) {
                return ApiResponse.error(400, "请选择要删除的模板");
            }

            int count = templateService.deleteByIds(ids);

            Map<String, Object> data = new HashMap<>();
            data.put("deletedCount", count);
            return ApiResponse.success("批量删除成功", data);
        } catch (Exception e) {
            return ApiResponse.error("批量删除模板失败: " + e.getMessage());
        }
    }

    @PostMapping("/template/export")
    public void exportTemplates(@RequestBody Map<String, List<Long>> body, HttpServletResponse response) throws IOException {
        try {
            List<Long> ids = body.get("ids");
            if (ids == null || ids.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":400,\"message\":\"请选择要导出的模板\"}");
                return;
            }

            String zipPath = templateService.exportTemplates(ids);
            File zipFile = new File(zipPath);

            if (!zipFile.exists()) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":500,\"message\":\"导出文件生成失败\"}");
                return;
            }

            response.setContentType("application/zip");
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + URLEncoder.encode(zipFile.getName(), "UTF-8") + "\"");
            response.setContentLengthLong(zipFile.length());

            try (FileInputStream fis = new FileInputStream(zipFile);
                 OutputStream os = response.getOutputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
                os.flush();
            }

            zipFile.delete();
        } catch (IOException e) {
            try {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":500,\"message\":\"导出模板失败: " + e.getMessage() + "\"}");
            } catch (IOException ignored) {
            }
        }
    }

    @PostMapping("/template/import")
    public ApiResponse<?> importTemplates(@RequestParam("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                return ApiResponse.error(400, "请选择要导入的文件");
            }

            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".zip")) {
                return ApiResponse.error(400, "仅支持导入ZIP格式的文件");
            }

            Path tempDir = Paths.get(TEMP_DIR);
            if (!Files.exists(tempDir)) {
                Files.createDirectories(tempDir);
            }

            String tempFileName = "import_" + UUID.randomUUID().toString().replace("-", "") + ".zip";
            Path tempFilePath = tempDir.resolve(tempFileName);
            file.transferTo(tempFilePath.toFile());

            List<Template> imported = templateService.importTemplates(tempFilePath.toString());

            try {
                Files.deleteIfExists(tempFilePath);
            } catch (IOException ignored) {
            }

            Map<String, Object> data = new HashMap<>();
            data.put("importedCount", imported.size());
            data.put("templates", imported);

            return ApiResponse.success("导入成功", data);
        } catch (Exception e) {
            return ApiResponse.error("导入模板失败: " + e.getMessage());
        }
    }

    @PostMapping("/sql/validate")
    public ApiResponse<?> validateSql(@RequestBody Map<String, Object> body) {
        try {
            Object sqlObj = body.get("sql");
            Object sqlsObj = body.get("sqls");

            if (sqlObj != null) {
                String sql = sqlObj.toString();
                SqlValidator.ValidationResult result = SqlValidator.validate(sql);
                Map<String, Object> data = new HashMap<>();
                data.put("valid", result.isValid());
                data.put("message", result.getMessage());
                if (result.isValid()) {
                    String tableName = SqlValidator.extractTableName(sql);
                    data.put("tableName", tableName);
                }
                return ApiResponse.success(data);
            }

            if (sqlsObj != null) {
                @SuppressWarnings("unchecked")
                List<String> sqls = (List<String>) sqlsObj;
                List<SqlValidator.ValidationResult> results = SqlValidator.validateMultiple(sqls);
                List<Map<String, Object>> resultList = results.stream().map(r -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("valid", r.isValid());
                    map.put("message", r.getMessage());
                    return map;
                }).collect(Collectors.toList());
                return ApiResponse.success(resultList);
            }

            return ApiResponse.error(400, "请提供要验证的SQL语句");
        } catch (Exception e) {
            return ApiResponse.error("SQL验证失败: " + e.getMessage());
        }
    }

    private HikariDataSource getCurrentDataSource(HttpSession session) {
        return sessionDataSourceMap.get(session.getId());
    }
}