package com.dbexport.model;

import lombok.Data;

@Data
public class TableInfo {
    private String tableName;
    private Long rowCount;
    private Long dataSize;
}
