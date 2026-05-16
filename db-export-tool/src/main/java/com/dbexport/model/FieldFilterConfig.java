package com.dbexport.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FieldFilterConfig {

    private String fieldName;
    private String filterType;
    private String filterValue;
    private Boolean enabled;

    public FieldFilterConfig() {
    }

    public FieldFilterConfig(String fieldName, String filterType, String filterValue, Boolean enabled) {
        this.fieldName = fieldName;
        this.filterType = filterType;
        this.filterValue = filterValue;
        this.enabled = enabled;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getFilterType() {
        return filterType;
    }

    public void setFilterType(String filterType) {
        this.filterType = filterType;
    }

    public String getFilterValue() {
        return filterValue;
    }

    public void setFilterValue(String filterValue) {
        this.filterValue = filterValue;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String buildCondition() {
        if (enabled == null || !enabled) {
            return "";
        }
        if (fieldName == null || fieldName.isEmpty()) {
            return "";
        }
        if (filterType == null || filterType.isEmpty()) {
            return "";
        }
        if (filterValue == null || filterValue.isEmpty()) {
            return "";
        }

        switch (filterType.toUpperCase()) {
            case "EQUAL":
                return fieldName + " = '" + escapeSql(filterValue) + "'";
            case "IN":
                return buildInCondition();
            case "RANGE":
                return buildRangeCondition();
            default:
                return "";
        }
    }

    private String buildInCondition() {
        String[] values = filterValue.split(",");
        StringBuilder sb = new StringBuilder(fieldName);
        sb.append(" IN (");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("'").append(escapeSql(values[i].trim())).append("'");
        }
        sb.append(")");
        return sb.toString();
    }

    private String buildRangeCondition() {
        if (!filterValue.contains("-")) {
            return "";
        }
        String[] range = filterValue.split("-", 2);
        if (range.length < 2) {
            return "";
        }
        String start = range[0].trim();
        String end = range[1].trim();
        if (start.isEmpty() || end.isEmpty()) {
            return "";
        }
        return fieldName + " >= " + start + " AND " + fieldName + " <= " + end;
    }

    private String escapeSql(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("'", "''").replace("\\", "\\\\");
    }
}