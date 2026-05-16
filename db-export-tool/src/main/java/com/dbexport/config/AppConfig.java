package com.dbexport.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppConfig {
    private String exportPath = "./exports";
    private String tempPath = "./temp";
    private String templatePath = "./templates";
    private Integer batchSize = 1000;
    private Integer fetchSize = 1000;
    private Integer exportTimeout = 3600;
}
