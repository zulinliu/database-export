package com.dbexport.service;

import com.dbexport.model.ExportProgress;
import com.dbexport.model.Template;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.main.banner-mode=off",
    "logging.level.root=WARN",
    "spring.thymeleaf.cache=false",
    "app.template.storage.path=./templates_test"
})
class ServiceTests {
    
    @Autowired
    private TemplateService templateService;
    
    @Test
    void testTemplateService_Init() {
        templateService.init();
        assertNotNull(templateService);
    }
    
    @Test
    void testTemplateService_SaveAndGet() {
        Template t = new Template();
        t.setName("测试模板_" + System.currentTimeMillis());
        t.setDescription("测试描述");
        com.dbexport.model.ExportConfig config = new com.dbexport.model.ExportConfig();
        config.setExportType("customTables");
        config.setTables("T1,T2");
        t.setExportConfig(config);
        
        Template saved = templateService.save(t);
        assertNotNull(saved.getId());
        assertNotNull(saved.getCreateTime());
        
        Template loaded = templateService.getById(saved.getId());
        assertNotNull(loaded);
        assertEquals(t.getName(), loaded.getName());
        
        templateService.deleteById(saved.getId());
    }
    
    @Test
    void testTemplateService_ListAll() {
        List<Template> list = templateService.listAll();
        assertNotNull(list);
    }
    
    @Test
    void testTemplateService_DeleteNonExistent() {
        boolean deleted = templateService.deleteById(999999L);
        assertFalse(deleted);
    }
    
    @Test
    void testTemplateService_Count() {
        int count = templateService.count();
        assertTrue(count >= 0);
    }
    
    @Test
    void testTemplateService_Search() {
        List<Template> results = templateService.search("测试");
        assertNotNull(results);
    }
    
    @Test
    void testTemplateService_BatchDelete() {
        Template t1 = new Template();
        t1.setName("批量测试1_" + System.currentTimeMillis());
        Template t2 = new Template();
        t2.setName("批量测试2_" + System.currentTimeMillis());
        Template s1 = templateService.save(t1);
        Template s2 = templateService.save(t2);
        int deleted = templateService.deleteByIds(java.util.Arrays.asList(s1.getId(), s2.getId()));
        assertEquals(2, deleted);
    }
    
    @Test
    void testExportProgressMap_Operations() {
        ExportProgress progress = new ExportProgress();
        progress.setTaskId("test-task-1");
        progress.setStatus("PENDING");
        progress.setTotalTables(10);
        progress.setCompletedTables(0);
        progress.setTotalRows(1000L);
        progress.setExportedRows(0L);
        progress.setStartTime(System.currentTimeMillis());
        
        assertEquals("test-task-1", progress.getTaskId());
        assertEquals("PENDING", progress.getStatus());
        assertEquals(10, progress.getTotalTables());
        assertEquals(0.0, progress.calculateProgress(), 0.1);
        
        progress.setStatus("RUNNING");
        progress.setExportedRows(500L);
        assertEquals(50.0, progress.calculateProgress(), 0.1);
        
        progress.setStatus("COMPLETED");
        progress.setExportedRows(1000L);
        assertEquals(100.0, progress.calculateProgress(), 0.1);
    }
    
    @Test
    void testExportProgress_Errors() {
        ExportProgress progress = new ExportProgress();
        assertNotNull(progress.getErrors());
        progress.getErrors().add("Error 1");
        assertEquals(1, progress.getErrors().size());
    }
}
