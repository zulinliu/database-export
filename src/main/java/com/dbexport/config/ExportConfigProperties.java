package com.dbexport.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "export")
public class ExportConfigProperties {
    private ThreadConfig thread = new ThreadConfig();
    private int batchSize = 1000;
    private int fetchSize = 1000;
    private StorageConfig storage = new StorageConfig();
    private long timeout = 3600;

    @Data
    public static class ThreadConfig {
        private int core = 4;
        private int max = 8;
    }

    @Data
    public static class StorageConfig {
        private String path = "./exports";
        private String temp = "./temp";
    }
}
