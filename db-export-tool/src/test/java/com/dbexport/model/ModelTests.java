package com.dbexport.model;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ModelTests {
    
    // Test DatabaseInfo
    @Test
    void testDatabaseInfoGettersSetters() {
        DatabaseInfo info = new DatabaseInfo();
        info.setType("dm");
        info.setHost("localhost");
        info.setPort(5236);
        info.setDatabaseName("SYSDBA");
        info.setUsername("SYSDBA");
        info.setPassword("123456");
        assertEquals("dm", info.getType());
        assertEquals("localhost", info.getHost());
        assertEquals(5236, info.getPort());
        assertEquals("SYSDBA", info.getDatabaseName());
        assertEquals("SYSDBA", info.getUsername());
        assertEquals("123456", info.getPassword());
    }
    
    @Test
    void testDatabaseInfoJdbcUrl() {
        DatabaseInfo info = new DatabaseInfo("dm", "localhost", 5236, "SYSDBA", "SYSDBA", "123456");
        String url = info.toJdbcUrl();
        assertTrue(url.contains("jdbc:dm://"));
        assertTrue(url.contains("localhost:5236"));
        
        info.setType("mysql");
        String mysqlUrl = info.toJdbcUrl();
        assertTrue(mysqlUrl.contains("jdbc:mysql://"));
    }
    
    @Test
    void testDatabaseInfoDriverClass() {
        DatabaseInfo info = new DatabaseInfo();
        info.setType("dm");
        assertEquals("dm.jdbc.driver.DmDriver", info.getDriverClass());
        info.setType("mysql");
        assertEquals("com.mysql.cj.jdbc.Driver", info.getDriverClass());
    }
    
    // Test FieldFilterConfig
    @Test
    void testFieldFilterBuildCondition_EQUAL() {
        FieldFilterConfig config = new FieldFilterConfig();
        config.setFieldName("CASE_ID");
        config.setFilterType("EQUAL");
        config.setFilterValue("111");
        config.setEnabled(true);
        String cond = config.buildCondition();
        assertTrue(cond.contains("CASE_ID"));
        assertTrue(cond.contains("="));
    }
    
    @Test
    void testFieldFilterBuildCondition_IN() {
        FieldFilterConfig config = new FieldFilterConfig();
        config.setFieldName("CASE_ID");
        config.setFilterType("IN");
        config.setFilterValue("111,222,333");
        config.setEnabled(true);
        String cond = config.buildCondition();
        assertTrue(cond.contains("CASE_ID") && cond.contains("IN"));
    }
    
    @Test
    void testFieldFilterBuildCondition_RANGE() {
        FieldFilterConfig config = new FieldFilterConfig();
        config.setFieldName("CASE_ID");
        config.setFilterType("RANGE");
        config.setFilterValue("100-200");
        config.setEnabled(true);
        String cond = config.buildCondition();
        assertTrue(cond.contains("CASE_ID") && cond.contains(">=") && cond.contains("<="));
    }
    
    @Test
    void testFieldFilterBuildCondition_NotEnabled() {
        FieldFilterConfig config = new FieldFilterConfig();
        config.setFieldName("CASE_ID");
        config.setFilterType("EQUAL");
        config.setFilterValue("111");
        config.setEnabled(false);
        assertEquals("", config.buildCondition());
    }
    
    @Test
    void testFieldFilterBuildCondition_EmptyFieldName() {
        FieldFilterConfig config = new FieldFilterConfig();
        config.setFieldName("");
        config.setFilterType("EQUAL");
        config.setFilterValue("111");
        config.setEnabled(true);
        assertEquals("", config.buildCondition());
    }
    
    // Test ExportConfig
    @Test
    void testExportConfigBuildWhereClause_TimeFilter() {
        ExportConfig config = new ExportConfig();
        config.setEnableTimeFilter(true);
        config.setTimeFieldNames("DATE,DATA_TIME");
        config.setStartDate("2025-01-01");
        config.setEndDate("2025-01-31");
        List<String> columns = Arrays.asList("DATE", "NAME");
        String where = config.buildWhereClause("TABLE1", columns);
        assertTrue(where.contains("WHERE"));
        assertTrue(where.contains("DATE"));
    }
    
    @Test
    void testExportConfigBuildWhereClause_FullFilter() {
        ExportConfig config = new ExportConfig();
        config.setEnableTimeFilter(true);
        config.setTimeFieldNames("DATE");
        config.setStartDate("2025-01-01");
        config.setEndDate("2025-01-31");
        config.setEnableFieldFilter(true);
        FieldFilterConfig fc = new FieldFilterConfig();
        fc.setFieldName("STATUS");
        fc.setFilterType("EQUAL");
        fc.setFilterValue("ACTIVE");
        fc.setEnabled(true);
        config.setFieldFilter(fc);
        List<String> columns = Arrays.asList("DATE", "STATUS", "NAME");
        String where = config.buildWhereClause("TABLE1", columns);
        assertTrue(where.contains("DATE"));
        assertTrue(where.contains("STATUS"));
        assertTrue(where.contains("WHERE"));
    }
    
    @Test
    void testExportConfigBuildWhereClause_NoFilter() {
        ExportConfig config = new ExportConfig();
        config.setEnableTimeFilter(false);
        config.setEnableFieldFilter(false);
        assertEquals("", config.buildWhereClause("TABLE1", null));
    }
    
    // Test ExportProgress
    @Test
    void testExportProgressLogEntry() {
        ExportProgress.LogEntry entry = new ExportProgress.LogEntry(1000L, "INFO", "test message");
        assertEquals(1000L, entry.getTimestamp());
        assertEquals("INFO", entry.getLevel());
        assertEquals("test message", entry.getMessage());
    }
    
    @Test
    void testExportProgressAddLog() {
        ExportProgress progress = new ExportProgress();
        progress.addLog("SUCCESS", "Table exported");
        assertEquals(1, progress.getLogs().size());
        assertEquals("SUCCESS", progress.getLogs().get(0).getLevel());
    }
    
    @Test
    void testExportProgressCalculateProgress() {
        ExportProgress progress = new ExportProgress();
        progress.setTotalRows(100L);
        progress.setExportedRows(50L);
        assertEquals(50.0, progress.calculateProgress(), 0.1);
        
        progress.setExportedRows(100L);
        assertEquals(100.0, progress.calculateProgress(), 0.1);
    }
    
    @Test
    void testExportProgressCalculateProgress_ZeroTotal() {
        ExportProgress progress = new ExportProgress();
        progress.setTotalRows(0L);
        assertEquals(0.0, progress.calculateProgress(), 0.1);
    }
    
    @Test
    void testExportProgressElapsed() {
        ExportProgress progress = new ExportProgress();
        progress.setStartTime(System.currentTimeMillis() - 5000);
        long elapsed = progress.calculateElapsed();
        assertTrue(elapsed >= 5000);
    }
    
    // Test Template
    @Test
    void testTemplateToExportConfig() {
        Template template = new Template();
        ExportConfig config = new ExportConfig();
        config.setExportType("customTables");
        config.setTables("TABLE1,TABLE2");
        template.setExportConfig(config);
        String json = template.getConfigJson();
        assertNotNull(json);
        assertTrue(json.contains("customTables"));
        
        ExportConfig loaded = template.toExportConfig();
        assertNotNull(loaded);
        assertEquals("customTables", loaded.getExportType());
    }
    
    @Test
    void testTemplateToExportConfig_Null() {
        Template template = new Template();
        template.setConfigJson(null);
        assertNull(template.toExportConfig());
    }
    
    @Test
    void testTemplateSetters() {
        Template t = new Template();
        t.setId(1L);
        t.setName("test");
        t.setDescription("desc");
        t.setCreateTime(1000L);
        t.setUpdateTime(2000L);
        assertEquals(1L, t.getId());
        assertEquals("test", t.getName());
        assertEquals("desc", t.getDescription());
        assertEquals(1000L, t.getCreateTime());
        assertEquals(2000L, t.getUpdateTime());
    }
    
    // Test ApiResponse
    @Test
    void testApiResponseSuccess() {
        ApiResponse<String> resp = ApiResponse.success("data");
        assertEquals(200, resp.getCode());
        assertEquals("data", resp.getData());
    }
    
    @Test
    void testApiResponseSuccessWithMessage() {
        ApiResponse<String> resp = ApiResponse.success("msg", "data");
        assertEquals(200, resp.getCode());
        assertEquals("msg", resp.getMessage());
        assertEquals("data", resp.getData());
    }
    
    @Test
    void testApiResponseError() {
        ApiResponse<String> resp = ApiResponse.error(500, "error");
        assertEquals(500, resp.getCode());
        assertEquals("error", resp.getMessage());
    }
    
    @Test
    void testApiResponseErrorDefault() {
        ApiResponse<String> resp = ApiResponse.error("default error");
        assertEquals(500, resp.getCode());
    }
}
