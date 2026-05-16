package com.dbexport.util;

import java.text.SimpleDateFormat;
import java.util.Date;

public class DateUtil {

    public static String formatDate(long timestamp, String pattern) {
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        return sdf.format(new Date(timestamp));
    }

    public static String formatDateTime(long timestamp) {
        return formatDate(timestamp, "yyyy-MM-dd HH:mm:ss");
    }

    public static String formatFileSuffix() {
        return formatDate(System.currentTimeMillis(), "yyyyMMddHHmmss");
    }
}
