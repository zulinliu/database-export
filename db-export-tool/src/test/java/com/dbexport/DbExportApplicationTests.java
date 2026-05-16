package com.dbexport;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = DbExportApplication.class)
@TestPropertySource(properties = {
    "spring.main.banner-mode=off",
    "logging.level.root=WARN",
    "spring.thymeleaf.cache=false"
})
class DbExportApplicationTests {
    @Test
    void contextLoads() {
    }
}
