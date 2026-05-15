package com.dbexport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class DbExportApplication {

    public static void main(String[] args) {
        SpringApplication.run(DbExportApplication.class, args);
    }
}