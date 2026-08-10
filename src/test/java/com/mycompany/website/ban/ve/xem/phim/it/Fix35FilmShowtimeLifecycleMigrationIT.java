package com.mycompany.website.ban.ve.xem.phim.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Regression gate for applying fix35 to the real pre-lifecycle legacy shape. */
@Tag("it")
@DisplayName("fix35 - legacy film/showtime lifecycle migration")
class Fix35FilmShowtimeLifecycleMigrationIT {

    private static final Path SCRIPT = Path.of("database/fix35_film_showtime_lifecycle.sql");

    static {
        System.setProperty("cinebook.db.config",
                System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    @BeforeAll
    static void requireEphemeralDatabase() throws SQLException {
        DBConnection.shutdown();
        try (Connection connection = DBConnection.getConnection()) {
            String name = string(connection, "SELECT DB_NAME()");
            assertTrue(name.matches("CineBookIT_[0-9]{14}_[0-9a-fA-F]{8}"),
                    "fix35 regression must run only on a runner-owned ephemeral database: " + name);
        }
    }

    @AfterAll
    static void shutdown() {
        DBConnection.shutdown();
    }

    @Test
    @DisplayName("missing legacy columns compile, migrate, and rerun idempotently")
    void migratesTrueLegacyShapeAndRerunsSafely() throws Exception {
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                execute(connection, """
                        IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('Films') AND name='IX_Films_AdminLifecycle')
                            DROP INDEX IX_Films_AdminLifecycle ON Films;
                        IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('Showtimes') AND name='IX_Showtimes_DeletionQueue')
                            DROP INDEX IX_Showtimes_DeletionQueue ON Showtimes;
                        IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name='CK_Films_DeletionMetadata')
                            ALTER TABLE Films DROP CONSTRAINT CK_Films_DeletionMetadata;
                        IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name='CK_Showtimes_DeleteMetadata')
                            ALTER TABLE Showtimes DROP CONSTRAINT CK_Showtimes_DeleteMetadata;
                        IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name='CK_Showtimes_SaleStatus')
                            ALTER TABLE Showtimes DROP CONSTRAINT CK_Showtimes_SaleStatus;
                        DECLARE @dropDependencies NVARCHAR(MAX)=N'';
                        SELECT @dropDependencies += N'ALTER TABLE '+QUOTENAME(OBJECT_SCHEMA_NAME(fk.parent_object_id))+N'.'
                               +QUOTENAME(OBJECT_NAME(fk.parent_object_id))+N' DROP CONSTRAINT '+QUOTENAME(fk.name)+N';'
                        FROM sys.foreign_keys fk
                        JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id=fk.object_id
                        JOIN sys.columns c ON c.object_id=fkc.parent_object_id AND c.column_id=fkc.parent_column_id
                        WHERE (fk.parent_object_id=OBJECT_ID('Films') AND c.name='DeletedByUserId')
                           OR (fk.parent_object_id=OBJECT_ID('Showtimes') AND c.name='DeleteRequestedByUserId');
                        SELECT @dropDependencies += N'ALTER TABLE '+QUOTENAME(OBJECT_SCHEMA_NAME(dc.parent_object_id))+N'.'
                               +QUOTENAME(OBJECT_NAME(dc.parent_object_id))+N' DROP CONSTRAINT '+QUOTENAME(dc.name)+N';'
                        FROM sys.default_constraints dc
                        JOIN sys.columns c ON c.object_id=dc.parent_object_id AND c.column_id=dc.parent_column_id
                        WHERE dc.parent_object_id=OBJECT_ID('Showtimes') AND c.name='SaleStatus';
                        EXEC sys.sp_executesql @dropDependencies;
                        ALTER TABLE Films DROP COLUMN DeletedAt, DeletedByUserId, DeletionMode;
                        ALTER TABLE Showtimes DROP COLUMN SaleStatus, DeleteRequestedAt, DeleteNotBefore, DeleteRequestedByUserId;
                        """);

                runScript(connection);
                assertContract(connection);
                runScript(connection);
                assertContract(connection);
            } finally {
                connection.rollback();
            }
        }
    }

    private static void assertContract(Connection connection) throws SQLException {
        assertEquals(7, integer(connection, """
                SELECT COUNT(*) FROM sys.columns
                WHERE (object_id=OBJECT_ID('Films') AND name IN ('DeletedAt','DeletedByUserId','DeletionMode'))
                   OR (object_id=OBJECT_ID('Showtimes') AND name IN ('SaleStatus','DeleteRequestedAt','DeleteNotBefore','DeleteRequestedByUserId'))
                """));
        assertEquals(3, integer(connection, """
                SELECT COUNT(*) FROM sys.check_constraints
                WHERE name IN ('CK_Films_DeletionMetadata','CK_Showtimes_SaleStatus','CK_Showtimes_DeleteMetadata')
                  AND is_disabled=0 AND is_not_trusted=0
                """));
        assertEquals(2, integer(connection, """
                SELECT COUNT(*) FROM sys.indexes
                WHERE name IN ('IX_Films_AdminLifecycle','IX_Showtimes_DeletionQueue') AND is_disabled=0
                """));
    }

    private static void runScript(Connection connection) throws Exception {
        execute(connection, Files.readString(SCRIPT, StandardCharsets.UTF_8));
    }

    private static int integer(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private static String string(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
            while (statement.getMoreResults() || statement.getUpdateCount() != -1) {
                // Drain every SQL Server result before the next statement.
            }
        }
    }
}
