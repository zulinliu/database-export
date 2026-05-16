package com.dbexport.model;

import lombok.Data;

@Data
public class ExportFile {
    private String fileName;
    private Long fileSize;
    private Long createTime;
    private String status;
}
