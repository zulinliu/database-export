package com.dbexport.util;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import com.dbexport.model.ExportConfig;
import static org.junit.jupiter.api.Assertions.*;

class UtilTests {
    
    // ===== SqlValidator Tests =====
    @Test
    void testSqlValidator_SimpleSelect() {
        SqlValidator.ValidationResult r = SqlValidator.validate("SELECT * FROM TABLE1");
        assertTrue(r.isValid());
    }
    
    @Test
    void testSqlValidator_SelectWithWhere() {
        SqlValidator.ValidationResult r = SqlValidator.validate("SELECT * FROM TABLE1 WHERE ID = 1");
        assertTrue(r.isValid());
    }
    
    @Test
    void testSqlValidator_SelectWithJoin() {
        SqlValidator.ValidationResult r = SqlValidator.validate("SELECT a.*, b.name FROM TABLE1 a LEFT JOIN TABLE2 b ON a.id = b.id");
        assertTrue(r.isValid());
    }
    
    @Test
    void testSqlValidator_SelectWithFunctions() {
        SqlValidator.ValidationResult r = SqlValidator.validate("SELECT COUNT(*), SUM(AMOUNT), MAX(DATE) FROM TABLE1 GROUP BY NAME");
        assertTrue(r.isValid());
    }
    
    @Test
    void testSqlValidator_RejectsNull() {
        SqlValidator.ValidationResult r = SqlValidator.validate(null);
        assertFalse(r.isValid());
    }
    
    @Test
    void testSqlValidator_RejectsEmpty() {
        SqlValidator.ValidationResult r = SqlValidator.validate("");
        assertFalse(r.isValid());
    }
    
    @Test
    void testSqlValidator_RejectsInsert() {
        SqlValidator.ValidationResult r = SqlValidator.validate("INSERT INTO TABLE1 VALUES(1)");
        assertFalse(r.isValid());
    }
    
    @Test
    void testSqlValidator_RejectsUpdate() {
        SqlValidator.ValidationResult r = SqlValidator.validate("UPDATE TABLE1 SET NAME='X'");
        assertFalse(r.isValid());
    }
    
    @Test
    void testSqlValidator_RejectsDelete() {
        SqlValidator.ValidationResult r = SqlValidator.validate("DELETE FROM TABLE1");
        assertFalse(r.isValid());
    }
    
    @Test
    void testSqlValidator_RejectsDrop() {
        SqlValidator.ValidationResult r = SqlValidator.validate("DROP TABLE TABLE1");
        assertFalse(r.isValid());
    }
    
    @Test
    void testSqlValidator_RejectsSemicolon() {
        SqlValidator.ValidationResult r = SqlValidator.validate("SELECT * FROM T1; SELECT * FROM T2");
        assertFalse(r.isValid());
    }
    
    @Test
    void testSqlValidator_RemovesComments() {
        SqlValidator.ValidationResult r = SqlValidator.validate("SELECT * FROM T1 -- comment");
        assertTrue(r.isValid());
    }
    
    @Test
    void testSqlValidator_RemovesBlockComments() {
        SqlValidator.ValidationResult r = SqlValidator.validate("SELECT /* comment */ * FROM T1");
        assertTrue(r.isValid());
    }
    
    @Test
    void testSqlValidator_RejectsSet() {
        SqlValidator.ValidationResult r = SqlValidator.validate("SET NAME='X'");
        assertFalse(r.isValid());
    }
    
    @Test
    void testSqlValidator_ExtractTableName() {
        assertEquals("TABLE1", SqlValidator.extractTableName("SELECT * FROM TABLE1"));
        assertEquals("TABLE1", SqlValidator.extractTableName("SELECT * FROM `TABLE1`"));
        assertEquals("TABLE1", SqlValidator.extractTableName("SELECT * FROM \"TABLE1\""));
    }
    
    @Test
    void testSqlValidator_ExtractTableName_Complex() {
        assertEquals("TABLE1", SqlValidator.extractTableName("SELECT a.* FROM TABLE1 a WHERE a.id = 1"));
    }
    
    @Test
    void testSqlValidator_ValidateMultiple() {
        List<String> sqls = Arrays.asList("SELECT * FROM T1", "SELECT * FROM T2");
        List<SqlValidator.ValidationResult> results = SqlValidator.validateMultiple(sqls);
        assertEquals(2, results.size());
        assertTrue(results.get(0).isValid());
        assertTrue(results.get(1).isValid());
    }
    
    // ===== FileUtil Tests =====
    @Test
    void testFileUtil_HumanReadableByteCount() {
        assertEquals("100 B", FileUtil.humanReadableByteCount(100));
        assertEquals("1.0 KB", FileUtil.humanReadableByteCount(1024));
        assertEquals("1.0 MB", FileUtil.humanReadableByteCount(1024 * 1024));
        assertEquals("1.0 GB", FileUtil.humanReadableByteCount(1024L * 1024 * 1024));
    }
    
    @Test
    void testFileUtil_HumanReadableByteCount_Negative() {
        assertEquals("0 B", FileUtil.humanReadableByteCount(-1));
    }
    
    @Test
    void testFileUtil_SanitizeFileName() {
        assertEquals("filename", FileUtil.sanitizeFileName("filename"));
        assertEquals("file_name_test", FileUtil.sanitizeFileName("file:name/test"));
        assertEquals("untitled", FileUtil.sanitizeFileName(null));
        assertEquals("untitled", FileUtil.sanitizeFileName(""));
        assertEquals("file_name", FileUtil.sanitizeFileName("file  name"));
    }
    
    @Test
    void testFileUtil_EnsureDirectoryExists() {
        File tempDir = new File(System.getProperty("java.io.tmpdir"), "test_util_" + System.currentTimeMillis());
        FileUtil.ensureDirectoryExists(tempDir.getAbsolutePath());
        assertTrue(tempDir.exists());
        tempDir.delete();
    }
    
    @Test
    void testFileUtil_CreateZipFromFiles() throws IOException {
        File tempDir = new File(System.getProperty("java.io.tmpdir"), "test_zip_" + System.currentTimeMillis());
        tempDir.mkdirs();
        File testFile = new File(tempDir, "test.txt");
        try (FileWriter fw = new FileWriter(testFile)) { fw.write("test content"); }
        File zipFile = new File(System.getProperty("java.io.tmpdir"), "test.zip");
        FileUtil.createZipFromFiles(Arrays.asList(testFile), zipFile.getAbsolutePath());
        assertTrue(zipFile.exists());
        zipFile.delete();
        testFile.delete();
        tempDir.delete();
    }
    
    // ===== JsonUtil Tests =====
    @Test
    void testJsonUtil_ToJson() {
        Map<String, Object> obj = new HashMap<>();
        obj.put("key", "value");
        obj.put("number", 123);
        String json = JsonUtil.toJson(obj);
        assertNotNull(json);
        assertTrue(json.contains("key"));
        assertTrue(json.contains("value"));
    }
    
    @Test
    void testJsonUtil_FromJson() {
        String json = "{\"name\":\"test\",\"age\":25}";
        Map result = JsonUtil.fromJson(json, Map.class);
        assertNotNull(result);
        assertEquals("test", result.get("name"));
        assertEquals(25, ((Number)result.get("age")).intValue());
    }
    
    @Test
    void testJsonUtil_FromJson_Null() {
        assertNull(JsonUtil.fromJson(null, Map.class));
        assertNull(JsonUtil.fromJson("", Map.class));
    }
    
    @Test
    void testJsonUtil_PrettyPrint() {
        Map<String, Object> obj = new HashMap<>();
        obj.put("key", "value");
        String pretty = JsonUtil.prettyPrint(obj);
        assertNotNull(pretty);
        assertTrue(pretty.contains("\n"));
    }
    
    @Test
    void testJsonUtil_RoundTrip() {
        ExportConfig config = new ExportConfig();
        config.setExportType("customTables");
        config.setTables("T1,T2");
        String json = JsonUtil.toJson(config);
        ExportConfig loaded = JsonUtil.fromJson(json, ExportConfig.class);
        assertNotNull(loaded);
        assertEquals("customTables", loaded.getExportType());
    }
}
