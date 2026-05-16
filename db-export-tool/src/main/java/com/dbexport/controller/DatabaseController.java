package com.dbexport.controller;

import com.dbexport.model.ApiResponse;
import com.dbexport.model.DatabaseInfo;
import com.dbexport.service.ExportServiceImpl;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/database")
public class DatabaseController {

    private final ExportServiceImpl exportService;

    public DatabaseController(ExportServiceImpl exportService) {
        this.exportService = exportService;
    }

    @PostMapping("/connect")
    public ApiResponse<?> connectDatabase(@RequestBody DatabaseInfo dbInfo, HttpSession session) {
        try {
            HikariDataSource ds = exportService.connect(dbInfo);
            session.setAttribute("dataSource", ds);
            session.setAttribute("dbInfo", dbInfo);

            Map<String, Object> data = new HashMap<>();
            data.put("connected", true);
            data.put("dbType", dbInfo.getDbType());
            data.put("dbName", dbInfo.getDbName());
            return ApiResponse.success("连接成功", data);
        } catch (Exception e) {
            return ApiResponse.error("连接失败: " + e.getMessage());
        }
    }

    @PostMapping("/test")
    public ApiResponse<?> testConnection(@RequestBody DatabaseInfo dbInfo) {
        try {
            boolean success = exportService.testConnection(dbInfo);
            if (success) {
                return ApiResponse.success("连接测试成功");
            } else {
                return ApiResponse.error("连接测试失败");
            }
        } catch (Exception e) {
            return ApiResponse.error("连接测试失败: " + e.getMessage());
        }
    }

    @GetMapping("/tables")
    public ApiResponse<?> getTableList(HttpSession session) {
        try {
            HikariDataSource ds = (HikariDataSource) session.getAttribute("dataSource");
            if (ds == null) {
                return ApiResponse.error(401, "请先连接数据库");
            }

            List<String> tables = exportService.getTableList(ds);
            return ApiResponse.success(tables);
        } catch (Exception e) {
            return ApiResponse.error("获取表列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/tableInfo")
    public ApiResponse<?> getTableInfo(@RequestParam String tableName, HttpSession session) {
        try {
            HikariDataSource ds = (HikariDataSource) session.getAttribute("dataSource");
            if (ds == null) {
                return ApiResponse.error(401, "请先连接数据库");
            }

            Map<String, Object> tableInfo = exportService.getTableInfo(ds, tableName);
            return ApiResponse.success(tableInfo);
        } catch (Exception e) {
            return ApiResponse.error("获取表信息失败: " + e.getMessage());
        }
    }

    @GetMapping("/columns")
    public ApiResponse<?> getTableColumns(@RequestParam String tableName, HttpSession session) {
        try {
            HikariDataSource ds = (HikariDataSource) session.getAttribute("dataSource");
            if (ds == null) {
                return ApiResponse.error(401, "请先连接数据库");
            }

            List<String> columns = exportService.getTableColumns(ds, tableName);
            return ApiResponse.success(columns);
        } catch (Exception e) {
            return ApiResponse.error("获取列信息失败: " + e.getMessage());
        }
    }

    @GetMapping("/poolStatus")
    public ApiResponse<?> getPoolStatus(HttpSession session) {
        try {
            HikariDataSource ds = (HikariDataSource) session.getAttribute("dataSource");
            if (ds == null) {
                return ApiResponse.error(401, "请先连接数据库");
            }

            Map<String, Integer> status = exportService.getPoolStatus(ds);
            return ApiResponse.success(status);
        } catch (Exception e) {
            return ApiResponse.error("获取连接池状态失败: " + e.getMessage());
        }
    }

    @PostMapping("/disconnect")
    public ApiResponse<?> disconnect(HttpSession session) {
        try {
            HikariDataSource ds = (HikariDataSource) session.getAttribute("dataSource");
            exportService.disconnect(ds);
            session.removeAttribute("dataSource");
            session.removeAttribute("dbInfo");
            return ApiResponse.success("断开连接成功");
        } catch (Exception e) {
            return ApiResponse.error("断开连接失败: " + e.getMessage());
        }
    }
}
