package com.dbexport.controller;

import com.dbexport.config.SecurityConfigProperties;
import com.dbexport.model.*;
import com.dbexport.service.ExportService;
import com.dbexport.service.TemplateService;
import com.dbexport.util.SqlValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpSession;
import java.io.File;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ExportController {

    private static final Logger log = LoggerFactory.getLogger(ExportController.class);

    @Autowired
    private ExportService exportService;

    @Autowired
    private TemplateService templateService;

    @Autowired
    private SecurityConfigProperties securityConfig;

    // ====== 认证接口 ======

    @PostMapping("/login")
    public ApiResponse<String> login(@RequestBody Map<String, String> loginForm, HttpSession session) {
        String username = loginForm.get("username");
        String password = loginForm.get("password");

        if (securityConfig.getDefaultConfig().getUsername().equals(username) &&
                securityConfig.getDefaultConfig().getPassword().equals(password)) {
            session.setAttribute("loggedInUser", username);
            session.setMaxInactiveInterval(securityConfig.getSessionTimeout());
            return ApiResponse.ok("登录成功", username);
        }
        return ApiResponse.error("用户名或密码错误");
    }

    @PostMapping("/logout")
    public ApiResponse<String> logout(HttpSession session) {
        session.invalidate();
        return ApiResponse.ok("已退出登录", null);
    }

    @GetMapping("/checkLogin")
    public ApiResponse<String> checkLogin(HttpSession session) {
        Object user = session.getAttribute("loggedInUser");
        if (user != null) {
            return ApiResponse.ok("已登录", user.toString());
        }
        return ApiResponse.error("未登录");
    }

    // ====== 数据库连接 ======

    @PostMapping("/database/connect")
    public ApiResponse<String> connectDatabase(@RequestBody DatabaseInfo dbInfo) {
        try {
            if (exportService.testConnection(dbInfo)) {
                if (exportService.connectDatabase(dbInfo)) {
                    return ApiResponse.ok("数据库连接成功", null);
                }
                return ApiResponse.error("连接池初始化失败");
            }
            return ApiResponse.error("连接测试失败，请检查配置");
        } catch (Exception e) {
            return ApiResponse.error("连接失败: " + e.getMessage());
        }
    }

    @GetMapping("/database/tables")
    public ApiResponse<List<DatabaseTableInfo>> getTableList() {
        try {
            return ApiResponse.ok(exportService.getTableList());
        } catch (Exception e) {
            return ApiResponse.error("获取表列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/database/status")
    public ApiResponse<ConnectionPoolStatus> getConnectionPoolStatus() {
        return ApiResponse.ok(exportService.getConnectionPoolStatus());
    }

    // ====== 数据导出 ======

    @PostMapping("/export/start")
    public ApiResponse<String> startExport(@RequestBody ExportConfig config) {
        try {
            String taskId = exportService.startExport(config);
            return ApiResponse.ok("导出任务已启动", taskId);
        } catch (Exception e) {
            return ApiResponse.error("启动导出失败: " + e.getMessage());
        }
    }

    @GetMapping("/export/progress/{taskId}")
    public ApiResponse<ExportProgress> getProgress(@PathVariable String taskId) {
        ExportProgress progress = exportService.getProgress(taskId);
        if (progress == null) {
            return ApiResponse.error("任务不存在");
        }
        return ApiResponse.ok(progress);
    }

    @PostMapping("/export/cancel/{taskId}")
    public ApiResponse<String> cancelTask(@PathVariable String taskId) {
        if (exportService.cancelTask(taskId)) {
            return ApiResponse.ok("已发送取消请求", null);
        }
        return ApiResponse.error("取消失败，任务可能不存在");
    }

    @GetMapping("/export/files")
    public ApiResponse<List<ExportFileInfo>> getExportFiles() {
        return ApiResponse.ok(exportService.getExportFiles());
    }

    @GetMapping("/export/download/{fileName}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileName) {
        try {
            String filePath = exportService.getExportFilePath(fileName);
            if (filePath == null) {
                return ResponseEntity.notFound().build();
            }

            File file = new File(filePath);
            FileSystemResource resource = new FileSystemResource(file);

            String encodedName = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + encodedName + "\"; filename*=UTF-8''" + encodedName)
                    .contentLength(file.length())
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/export/files/{fileName}")
    public ApiResponse<String> deleteExportFile(@PathVariable String fileName) {
        if (exportService.deleteExportFile(fileName)) {
            return ApiResponse.ok("文件已删除", null);
        }
        return ApiResponse.error("删除文件失败");
    }

    @PostMapping("/export/files/batch-delete")
    public ApiResponse<String> batchDeleteFiles(@RequestBody Map<String, List<String>> body) {
        List<String> fileNames = body.get("fileNames");
        if (fileNames != null) {
            for (String name : fileNames) {
                exportService.deleteExportFile(name);
            }
            return ApiResponse.ok("批量删除完成", null);
        }
        return ApiResponse.error("参数错误");
    }

    // ====== 模板管理 ======

    @GetMapping("/template/list")
    public ApiResponse<List<Template>> listTemplates(@RequestParam(required = false) String keyword) {
        if (keyword != null && !keyword.trim().isEmpty()) {
            return ApiResponse.ok(templateService.search(keyword));
        }
        return ApiResponse.ok(templateService.listAll());
    }

    @PostMapping("/template/save")
    public ApiResponse<Template> saveTemplate(@RequestBody Template template) {
        try {
            Template saved = templateService.save(template);
            return ApiResponse.ok("模板保存成功", saved);
        } catch (Exception e) {
            return ApiResponse.error("保存失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/template/{id}")
    public ApiResponse<String> deleteTemplate(@PathVariable Long id) {
        if (templateService.deleteById(id)) {
            return ApiResponse.ok("模板已删除", null);
        }
        return ApiResponse.error("删除失败，模板不存在");
    }

    @PostMapping("/template/batch-delete")
    public ApiResponse<String> batchDeleteTemplates(@RequestBody Map<String, List<Long>> body) {
        List<Long> ids = body.get("ids");
        if (ids != null) {
            int count = templateService.deleteByIds(ids);
            return ApiResponse.ok("已删除 " + count + " 个模板", null);
        }
        return ApiResponse.error("参数错误");
    }

    @PostMapping("/template/export")
    public ResponseEntity<Resource> exportTemplates(@RequestBody Map<String, List<Long>> body) {
        try {
            List<Long> ids = body.get("ids");
            if (ids == null || ids.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            Path zipPath = templateService.exportTemplates(ids);
            FileSystemResource resource = new FileSystemResource(zipPath.toFile());

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"templates_export.zip\"")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/template/import")
    public ApiResponse<List<Template>> importTemplates(@RequestParam("file") MultipartFile file) {
        try {
            Path tempFile = Files.createTempFile("import_", ".zip");
            file.transferTo(tempFile.toFile());
            List<Template> imported = templateService.importTemplates(tempFile);
            Files.deleteIfExists(tempFile);
            return ApiResponse.ok("导入成功，共 " + imported.size() + " 个模板", imported);
        } catch (Exception e) {
            return ApiResponse.error("导入失败: " + e.getMessage());
        }
    }

    // ====== SQL校验 ======

    @PostMapping("/sql/validate")
    public ApiResponse<Map<String, Object>> validateSql(@RequestBody Map<String, String> body) {
        String sql = body.get("sql");
        Map<String, Object> result = new HashMap<>();

        if (sql == null || sql.trim().isEmpty()) {
            result.put("valid", false);
            result.put("message", "SQL不能为空");
            return ApiResponse.ok(result);
        }

        String[] lines = sql.split("\\n");
        List<Map<String, Object>> details = new ArrayList<>();
        boolean allValid = true;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            SqlValidator.ValidationResult vr = SqlValidator.validate(line);
            Map<String, Object> detail = new HashMap<>();
            detail.put("line", i + 1);
            detail.put("sql", line);
            detail.put("valid", vr.isValid());
            detail.put("message", vr.getMessage());
            detail.put("tableName", SqlValidator.extractTableName(line));
            if (!vr.isValid()) allValid = false;
            details.add(detail);
        }

        result.put("valid", allValid);
        result.put("details", details);
        return ApiResponse.ok(result);
    }
}
