package com.dbexport.model;

import lombok.Data;

@Data
public class DatabaseTableInfo {
    private String tableName;
    private long rowCount;
    private String tableSize;
}
