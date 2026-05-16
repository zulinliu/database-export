package com.dbexport.model;

import lombok.Data;

@Data
public class ExportFileInfo {
    private String fileName;
    private long fileSize;
    private long createTime;
    private String status;
}
