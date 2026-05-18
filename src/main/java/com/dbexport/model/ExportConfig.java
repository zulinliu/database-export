package com.dbexport.model;

import lombok.Data;

@Data
public class ExportConfig {
    private String exportType;
    private String tables;
    private String customSql;

    private Boolean enableTimeFilter;
    private String timeFieldNames;
    private String startDate;
    private String endDate;

    private Boolean enableFieldFilter;
    private FieldFilterConfig fieldFilter;

    private String exportFormats;
    private Integer maxConnections;
    private String sqlFileMode;
}
