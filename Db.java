package com.shambapulse;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Minimal JDBC access. Reads settings from environment variables. */
public final class Db {
    private static final String URL = env("SP_DB_URL",
            "jdbc:mysql://127.0.0.1:3306/shambapulse?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC");
    private static final String USER = env("SP_DB_USER", "root");
    private static final String PASS = env("SP_DB_PASS", "");

    private Db() {}

    public static Connection open() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }

    public static String env(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
