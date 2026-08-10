package com.mycompany.website.ban.ve.xem.phim.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

public final class DBConnection {
    private static volatile HikariDataSource dataSource;

    private DBConnection() {
    }

    public static Connection getConnection() throws SQLException {
        return getDataSource().getConnection();
    }

    public static HikariDataSource getDataSource() {
        HikariDataSource current = dataSource;
        if (current == null) {
            synchronized (DBConnection.class) {
                current = dataSource;
                if (current == null) {
                    HikariDataSource ds = createDataSource();
                    try {
                        ensureSchemaInitialized(ds);
                    } catch (RuntimeException ex) {
                        ds.close();
                        throw ex;
                    }
                    dataSource = current = ds;
                }
            }
        }
        return current;
    }

    public static void shutdown() {
        HikariDataSource current = dataSource;
        if (current != null) {
            current.close();
            dataSource = null;
        }
    }

    private static HikariDataSource createDataSource() {
        Properties props = DatabaseConfig.load();
        HikariConfig config = new HikariConfig();
        config.setDriverClassName(props.getProperty("db.driver", "com.microsoft.sqlserver.jdbc.SQLServerDriver"));
        config.setJdbcUrl(required(props, "db.url"));
        config.setUsername(props.getProperty("db.username", ""));
        config.setPassword(props.getProperty("db.password", ""));
        config.setMaximumPoolSize(intValue(props, "db.pool.maxSize", 20));
        config.setMinimumIdle(intValue(props, "db.pool.minIdle", 2));
        config.setConnectionTimeout(longValue(props, "db.pool.connectionTimeoutMs", 30000));
        config.setPoolName(props.getProperty("db.pool.name", "CineBookPool"));
        config.setLeakDetectionThreshold(longValue(props, "db.pool.leakDetectionThresholdMs", 20000));
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        return new HikariDataSource(config);
    }

    public static java.time.LocalDateTime dbNow() {
        try (Connection conn = getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement("SELECT GETDATE()");
             java.sql.ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                java.sql.Timestamp ts = rs.getTimestamp(1);
                if (ts != null) {
                    return ts.toLocalDateTime();
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Cannot read current database time", ex);
        }
        throw new IllegalStateException("Database did not return its current time");
    }

    private static String required(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required database property: " + key);
        }
        return value;
    }

    private static int intValue(Properties props, String key, int fallback) {
        String value = props.getProperty(key);
        return value == null || value.isBlank() ? fallback : Integer.parseInt(value.trim());
    }

    private static long longValue(Properties props, String key, long fallback) {
        String value = props.getProperty(key);
        return value == null || value.isBlank() ? fallback : Long.parseLong(value.trim());
    }

    private static void ensureSchemaInitialized(HikariDataSource ds) {
        try (Connection conn = ds.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {

            String alterUsers = """
                IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'Users') AND name = 'IsLocked')
                BEGIN
                    ALTER TABLE Users ADD IsLocked BIT NOT NULL DEFAULT 0;
                END;
                IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'Users') AND name = 'LockReason')
                BEGIN
                    ALTER TABLE Users ADD LockReason NVARCHAR(500) NULL;
                END;
                IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'Users') AND name = 'WarningCount')
                BEGIN
                    ALTER TABLE Users ADD WarningCount INT NOT NULL DEFAULT 0;
                END;
                IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'Users') AND name = 'LockedAt')
                BEGIN
                    ALTER TABLE Users ADD LockedAt DATETIME NULL;
                END;
                """;
            stmt.execute(alterUsers);

            String createCommentReports = """
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'CommentReports')
                BEGIN
                    CREATE TABLE CommentReports (
                        Id INT IDENTITY(1,1) PRIMARY KEY,
                        CommentId INT NOT NULL,
                        ReporterUserId INT NULL,
                        Reason NVARCHAR(500) NOT NULL,
                        CreatedAt DATETIME NOT NULL DEFAULT GETDATE(),
                        FOREIGN KEY (CommentId) REFERENCES Comments(Id) ON DELETE CASCADE,
                        FOREIGN KEY (ReporterUserId) REFERENCES Users(Id)
                    );
                END;
                """;
            stmt.execute(createCommentReports);

            String createUserAppeals = """
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'UserAppeals')
                BEGIN
                    CREATE TABLE UserAppeals (
                        Id INT IDENTITY(1,1) PRIMARY KEY,
                        UserId INT NOT NULL,
                        Email NVARCHAR(255) NOT NULL,
                        Reason NVARCHAR(1000) NOT NULL,
                        TicketCode NVARCHAR(50) NULL,
                        BankAccountInfo NVARCHAR(255) NULL,
                        Status NVARCHAR(50) NOT NULL DEFAULT 'pending',
                        AdminResponse NVARCHAR(1000) NULL,
                        ResolvedByUserId INT NULL,
                        CreatedAt DATETIME NOT NULL DEFAULT GETDATE(),
                        ResolvedAt DATETIME NULL,
                        FOREIGN KEY (UserId) REFERENCES Users(Id),
                        FOREIGN KEY (ResolvedByUserId) REFERENCES Users(Id)
                    );
                END;
                IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('UserAppeals') AND name = 'TicketCode')
                BEGIN
                    ALTER TABLE UserAppeals ADD TicketCode NVARCHAR(50) NULL;
                END;
                IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('UserAppeals') AND name = 'BankAccountInfo')
                BEGIN
                    ALTER TABLE UserAppeals ADD BankAccountInfo NVARCHAR(255) NULL;
                END;
                """;
            stmt.execute(createUserAppeals);
        } catch (SQLException ex) {
            throw new IllegalStateException("Cannot initialize required database schema", ex);
        }
    }
}
