package com.dbexport.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
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

    public ExportConfig() {
    }

    public String getExportType() {
        return exportType;
    }

    public void setExportType(String exportType) {
        this.exportType = exportType;
    }

    public String getTables() {
        return tables;
    }

    public void setTables(String tables) {
        this.tables = tables;
    }

    public String getCustomSql() {
        return customSql;
    }

    public void setCustomSql(String customSql) {
        this.customSql = customSql;
    }

    public Boolean getEnableTimeFilter() {
        return enableTimeFilter;
    }

    public void setEnableTimeFilter(Boolean enableTimeFilter) {
        this.enableTimeFilter = enableTimeFilter;
    }

    public String getTimeFieldNames() {
        return timeFieldNames;
    }

    public void setTimeFieldNames(String timeFieldNames) {
        this.timeFieldNames = timeFieldNames;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public Boolean getEnableFieldFilter() {
        return enableFieldFilter;
    }

    public void setEnableFieldFilter(Boolean enableFieldFilter) {
        this.enableFieldFilter = enableFieldFilter;
    }

    public FieldFilterConfig getFieldFilter() {
        return fieldFilter;
    }

    public void setFieldFilter(FieldFilterConfig fieldFilter) {
        this.fieldFilter = fieldFilter;
    }

    public String getExportFormats() {
        return exportFormats;
    }

    public void setExportFormats(String exportFormats) {
        this.exportFormats = exportFormats;
    }

    public Integer getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(Integer maxConnections) {
        this.maxConnections = maxConnections;
    }

    public String buildWhereClause(String tableName, List<String> availableColumns) {
        StringBuilder whereClause = new StringBuilder();
        boolean hasTimeFilter = false;
        boolean hasFieldFilter = false;

        if (enableTimeFilter != null && enableTimeFilter && timeFieldNames != null
                && !timeFieldNames.isEmpty() && startDate != null && !startDate.isEmpty()
                && endDate != null && !endDate.isEmpty()) {

            String[] timeFields = timeFieldNames.split(",");
            StringBuilder timeCondition = new StringBuilder();
            boolean matched = false;

            for (String timeField : timeFields) {
                String trimmed = timeField.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (availableColumns != null && !availableColumns.contains(trimmed)) {
                    continue;
                }
                if (matched) {
                    timeCondition.append(" OR ");
                }
                timeCondition.append(trimmed)
                        .append(" >= '").append(startDate).append("'")
                        .append(" AND ")
                        .append(trimmed)
                        .append(" <= '").append(endDate).append("'");
                matched = true;
            }

            if (matched) {
                whereClause.append("(").append(timeCondition).append(")");
                hasTimeFilter = true;
            }
        }

        if (enableFieldFilter != null && enableFieldFilter && fieldFilter != null) {
            String fieldCondition = fieldFilter.buildCondition();
            if (!fieldCondition.isEmpty()) {
                if (hasTimeFilter) {
                    whereClause.append(" AND ");
                }
                whereClause.append("(").append(fieldCondition).append(")");
                hasFieldFilter = true;
            }
        }

        if (hasTimeFilter || hasFieldFilter) {
            return " WHERE " + whereClause.toString();
        }

        return "";
    }
}