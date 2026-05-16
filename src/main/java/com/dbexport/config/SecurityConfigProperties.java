package com.dbexport.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "security")
public class SecurityConfigProperties {
    private DefaultConfig defaultConfig = new DefaultConfig();
    private int sessionTimeout = 1800;

    @Data
    public static class DefaultConfig {
        private String username = "admin";
        private String password = "123456";
    }
}
