package com.example;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Lazily-created connection pool for Azure SQL.
 *
 * Configuration comes from environment variables (App Service "Application settings"):
 *   DB_SERVER    e.g. myserver.database.windows.net
 *   DB_NAME      e.g. mydatabase
 *   DB_USER / DB_PASSWORD   optional - if both are set, SQL authentication is used,
 *                           otherwise Microsoft Entra (managed identity / az login).
 */
public final class Db {

    private static volatile HikariDataSource dataSource;

    private Db() { }

    public static Connection getConnection() throws SQLException {
        return pool().getConnection();
    }

    public static void close() {
        HikariDataSource ds = dataSource;
        if (ds != null) {
            ds.close();
            dataSource = null;
        }
    }

    private static HikariDataSource pool() {
        HikariDataSource local = dataSource;
        if (local == null) {
            synchronized (Db.class) {
                local = dataSource;
                if (local == null) {
                    local = create();
                    dataSource = local;
                }
            }
        }
        return local;
    }

    private static HikariDataSource create() {
        String server = env("DB_SERVER");
        String name = env("DB_NAME");
        if (server == null || name == null) {
            throw new IllegalStateException("DB_SERVER and DB_NAME application settings are required");
        }

        StringBuilder url = new StringBuilder("jdbc:sqlserver://")
                .append(server).append(":1433;")
                .append("databaseName=").append(name).append(';')
                .append("encrypt=true;trustServerCertificate=false;")
                .append("hostNameInCertificate=*.database.windows.net;")
                .append("loginTimeout=60;");

        HikariConfig cfg = new HikariConfig();
        cfg.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        cfg.setPoolName("d5-pool");

        String user = env("DB_USER");
        String password = env("DB_PASSWORD");
        if (user != null && password != null) {
            cfg.setUsername(user);
            cfg.setPassword(password);
        } else {
            url.append("authentication=ActiveDirectoryDefault;");
        }

        cfg.setJdbcUrl(url.toString());
        cfg.setMaximumPoolSize(5);
        cfg.setMinimumIdle(0);
        cfg.setConnectionTimeout(60_000);        // serverless/free tier may need time to resume
        cfg.setMaxLifetime(25 * 60 * 1000L);     // recycle before Entra tokens / Azure idle limits
        cfg.setInitializationFailTimeout(-1);    // don't fail app startup if the DB is paused
        return new HikariDataSource(cfg);
    }

    private static String env(String key) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? null : v.trim();
    }
}
