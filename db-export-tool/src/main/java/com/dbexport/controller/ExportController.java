package com.dbexport.controller;

import com.dbexport.config.SecurityConfig;
import com.dbexport.model.*;
import com.dbexport.service.ExportService;
import com.dbexport.service.TemplateService;
import com.dbexport.util.DateUtil;
import com.dbexport.util.FileUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
public class ExportController {

    @Autowired
    private ExportService exportService;

    @Autowired
    private TemplateService templateService;

    @Autowired
    private SecurityConfig securityConfig;

    @Autowired
    private ObjectMapper objectMapper;

    @GetMapping("/")
    public String index(HttpSession session) {
        if (session.getAttribute("user") != null) {
            return "redirect:/index.html";
        }
        return "redirect:/login.html";
    }

    @GetMapping("/login.html")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/index.html")
    public String mainPage(HttpSession session) {
        if (session.getAttribute("user") == null) {
            return "redirect:/login.html";
        }
        return "index";
    }

    @PostMapping("/api/login")
    @ResponseBody
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> params, HttpSession session) {
        String username = params.get("username");
        String password = params.get("password");
        
        if (securityConfig.getDefaultUsername().equals(username) 
                && securityConfig.getDefaultPassword().equals(password)) {
            session.setAttribute("user", username);
            Map<String, Object> data = new HashMap<>();
            data.put("username", username);
            return Result.success(data);
        }
        
        return Result.error("用户名或密码错误");
    }

    @PostMapping("/api/logout")
    @ResponseBody
    public Result<Void> logout(HttpSession session) {
        session.invalidate();
        return Result.success();
    }

    @GetMapping("/api/check-login")
    @ResponseBody
    public Result<Map<String, Object>> checkLogin(HttpSession session) {
        String user = (String) session.getAttribute("user");
        if (user != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("username", user);
            return Result.success(data);
        }
        return Result.error(401, "未登录");
    }

    @PostMapping("/api/database/test")
    @ResponseBody
    public Result<Map<String, Object>> testConnection(@RequestBody DatabaseInfo dbInfo) {
        boolean success = exportService.testConnection(dbInfo);
        Map<String, Object> data = new HashMap<>();
        data.put("success", success);
        if (success) {
            return Result.success(data);
        } else {
            return Result.error("连接失败，请检查配置");
        }
    }

    @PostMapping("/api/database/connect")
    @ResponseBody
    public Result<Map<String, Object>> connectDatabase(@RequestBody DatabaseInfo dbInfo) {
        boolean success = exportService.connectDatabase(dbInfo);
        Map<String, Object> data = new HashMap<>();
        data.put("success", success);
        if (success) {
            List<String> tables = exportService.getTableList();
            data.put("tables", tables);
            return Result.success(data);
        } else {
            return Result.error("连接失败，请检查配置");
        }
    }

    @GetMapping("/api/database/tables")
    @ResponseBody
    public Result<List<String>> getTables() {
        List<String> tables = exportService.getTableList();
        return Result.success(tables);
    }

    @GetMapping("/api/database/status")
    @ResponseBody
    public Result<Map<String, Object>> getDatabaseStatus() {
        Map<String, Object> status = exportService.getDatabaseStatus();
        return Result.success(status);
    }

    @PostMapping("/api/export/start")
    @ResponseBody
    public Result<Map<String, Object>> startExport(@RequestBody Map<String, Object> params) {
        try {
            DatabaseInfo dbInfo = objectMapper.convertValue(params.get("database"), DatabaseInfo.class);
            ExportConfig config = objectMapper.convertValue(params.get("config"), ExportConfig.class);
            
            String taskId = exportService.startExport(dbInfo, config);
            
            Map<String, Object> data = new HashMap<>();
            data.put("taskId", taskId);
            return Result.success(data);
        } catch (Exception e) {
            log.error("启动导出任务失败", e);
            return Result.error("启动导出任务失败: " + e.getMessage());
        }
    }

    @GetMapping("/api/export/progress/{taskId}")
    @ResponseBody
    public Result<ExportProgress> getProgress(@PathVariable String taskId) {
        ExportProgress progress = exportService.getProgress(taskId);
        if (progress != null) {
            return Result.success(progress);
        }
        return Result.error("任务不存在");
    }

    @PostMapping("/api/export/cancel/{taskId}")
    @ResponseBody
    public Result<Void> cancelTask(@PathVariable String taskId) {
        exportService.cancelTask(taskId);
        return Result.success();
    }

    @GetMapping("/api/export/files")
    @ResponseBody
    public Result<List<ExportFile>> getExportFiles() {
        List<ExportFile> files = exportService.getExportFiles();
        return Result.success(files);
    }

    @GetMapping("/api/export/download/{fileName:.+}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileName) {
        Path filePath = exportService.getExportFile(fileName);
        if (filePath == null || !Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }
        
        Resource resource = new FileSystemResource(filePath);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(resource);
    }

    @DeleteMapping("/api/export/files/{fileName:.+}")
    @ResponseBody
    public Result<Void> deleteFile(@PathVariable String fileName) {
        boolean success = exportService.deleteExportFile(fileName);
        if (success) {
            return Result.success();
        }
        return Result.error("删除失败");
    }

    @GetMapping("/api/templates")
    @ResponseBody
    public Result<List<Template>> getTemplates() {
        List<Template> templates = templateService.listAll();
        return Result.success(templates);
    }

    @PostMapping("/api/templates")
    @ResponseBody
    public Result<Template> saveTemplate(@RequestBody Template template) {
        template.setCreateTime(System.currentTimeMillis());
        template.setUpdateTime(System.currentTimeMillis());
        Template saved = templateService.save(template);
        return Result.success(saved);
    }

    @DeleteMapping("/api/templates/{id}")
    @ResponseBody
    public Result<Void> deleteTemplate(@PathVariable Long id) {
        boolean success = templateService.deleteById(id);
        if (success) {
            return Result.success();
        }
        return Result.error("删除失败");
    }

    @GetMapping("/api/templates/{id}")
    @ResponseBody
    public Result<Template> getTemplate(@PathVariable Long id) {
        Template template = templateService.getById(id);
        if (template != null) {
            return Result.success(template);
        }
        return Result.error("模板不存在");
    }

    @PostMapping("/api/templates/export")
    public ResponseEntity<Resource> exportTemplates(@RequestBody(required = false) List<Long> ids) throws IOException {
        Path zipPath = templateService.exportTemplates(ids);
        Resource resource = new FileSystemResource(zipPath);
        String fileName = "templates_" + DateUtil.formatFileSuffix() + ".zip";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(resource);
    }

    @PostMapping("/api/templates/import")
    @ResponseBody
    public Result<List<Template>> importTemplates(@RequestParam("file") MultipartFile file) throws IOException {
        Path tempPath = Files.createTempFile("template_import_", ".zip");
        file.transferTo(tempPath.toFile());
        List<Template> imported = templateService.importTemplates(tempPath);
        Files.deleteIfExists(tempPath);
        return Result.success(imported);
    }

    @PostMapping("/api/sql/validate")
    @ResponseBody
    public Result<Map<String, Object>> validateSql(@RequestBody Map<String, String> params) {
        String sql = params.get("sql");
        boolean valid = com.dbexport.util.SqlValidator.validateSelectSql(sql);
        Map<String, Object> data = new HashMap<>();
        data.put("valid", valid);
        return Result.success(data);
    }
}
