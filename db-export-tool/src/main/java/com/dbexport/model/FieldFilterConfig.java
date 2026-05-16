package com.dbexport.model;

import lombok.Data;

@Data
public class FieldFilterConfig {
    private String fieldName;
    private String filterType;
    private String filterValue;
    private Boolean enabled;

    public String buildCondition() {
        if (!Boolean.TRUE.equals(enabled) || fieldName == null || fieldName.trim().isEmpty()) {
            return null;
        }

        if (filterValue == null || filterValue.trim().isEmpty()) {
            return null;
        }

        fieldName = escapeIdentifier(fieldName.trim());
        filterValue = filterValue.trim();

        switch (filterType == null ? "EQUAL" : filterType.toUpperCase()) {
            case "EQUAL":
                return fieldName + " = '" + escapeSql(filterValue) + "'";
            case "IN":
                String[] values = filterValue.split(",");
                StringBuilder sb = new StringBuilder();
                sb.append(fieldName).append(" IN (");
                for (int i = 0; i < values.length; i++) {
                    if (i > 0) sb.append(",");
                    sb.append("'").append(escapeSql(values[i].trim())).append("'");
                }
                sb.append(")");
                return sb.toString();
            case "RANGE":
                String[] range = filterValue.split("-");
                if (range.length >= 2) {
                    return fieldName + " >= " + escapeSql(range[0].trim()) + 
                           " AND " + fieldName + " <= " + escapeSql(range[1].trim());
                } else if (range.length == 1) {
                    return fieldName + " = " + escapeSql(range[0].trim());
                }
                return null;
            default:
                return null;
        }
    }

    private String escapeSql(String value) {
        return value.replace("'", "''");
    }

    private String escapeIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }
}
