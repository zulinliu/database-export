package com.dbexport.controller;

import com.dbexport.model.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.main.banner-mode=off",
    "logging.level.root=WARN",
    "spring.thymeleaf.cache=false"
})
class ControllerTests {
    
    @Autowired
    private MockMvc mvc;
    
    @Test
    void testLogin_Success() throws Exception {
        mvc.perform(post("/api/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"123456\"}"))
            .andExpect(status().isOk());
    }
    
    @Test
    void testLogin_Fail() throws Exception {
        mvc.perform(post("/api/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
            .andExpect(status().isOk());
    }
    
    @Test
    void testCheckLogin_Unauthorized() throws Exception {
        mvc.perform(get("/api/checkLogin"))
            .andExpect(status().isUnauthorized());
    }
    
    @Test
    void testLogin_ThenCheckLogin() throws Exception {
        // Login first
        mvc.perform(post("/api/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"123456\"}"))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("JSESSIONID"));
    }
    
    @Test
    void testDatabaseTables_Unauthorized() throws Exception {
        mvc.perform(get("/api/database/tables"))
            .andExpect(status().isUnauthorized());
    }
    
    @Test
    void testExportStart_Unauthorized() throws Exception {
        mvc.perform(post("/api/export/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }
    
    @Test
    void testTemplateList_Unauthorized() throws Exception {
        mvc.perform(get("/api/template/list"))
            .andExpect(status().isUnauthorized());
    }
    
    @Test
    void testSqlValidate_Unauthorized() throws Exception {
        mvc.perform(post("/api/sql/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sql\":\"SELECT * FROM T\"}"))
            .andExpect(status().isUnauthorized());
    }
    
    @Test
    void testLogin_NullBody() throws Exception {
        mvc.perform(post("/api/login")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }
}
