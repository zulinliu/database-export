package com.dbexport.util;

import java.util.regex.Pattern;

public class SqlSafeUtils {

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    public static boolean isValidIdentifier(String name) {
        if (name == null || name.isEmpty()) return false;
        return IDENTIFIER_PATTERN.matcher(name).matches();
    }

    public static String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) return null;
        String clean = fileName.replaceAll("[^A-Za-z0-9_.\\-]", "");
        if (clean.contains("..")) return null;
        return clean;
    }
}
