package com.dbexport.model;

import com.dbexport.util.SqlSafeUtils;
import lombok.Data;

@Data
public class FieldFilterConfig {
    private String fieldName;
    private String filterType;
    private String filterValue;

    public String buildCondition() {
        if (fieldName == null || fieldName.isEmpty() || filterValue == null || filterValue.isEmpty()) {
            return null;
        }
        if (!SqlSafeUtils.isValidIdentifier(fieldName)) {
            return null;
        }
        String quotedField = "\"" + fieldName + "\"";

        switch (filterType) {
            case "EQUAL":
                return quotedField + " = '" + filterValue.replace("'", "''") + "'";
            case "IN":
                StringBuilder inValues = new StringBuilder();
                String[] values = filterValue.split(",");
                for (int i = 0; i < values.length; i++) {
                    if (i > 0) inValues.append(",");
                    String v = values[i].trim().replace("'", "''");
                    inValues.append("'").append(v).append("'");
                }
                return quotedField + " IN (" + inValues + ")";
            case "RANGE":
                String[] range = filterValue.split("-");
                if (range.length == 2) {
                    String lo = range[0].trim().replaceAll("[^0-9.]", "");
                    String hi = range[1].trim().replaceAll("[^0-9.]", "");
                    if (lo.isEmpty() || hi.isEmpty()) return null;
                    return quotedField + " >= " + lo + " AND " + quotedField + " <= " + hi;
                }
                return null;
            default:
                return null;
        }
    }
}
