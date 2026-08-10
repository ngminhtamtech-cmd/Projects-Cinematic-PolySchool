package com.mycompany.website.ban.ve.xem.phim.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Regression gate for the explicit account/refund appeal migration contract. */
@Tag("it")
@DisplayName("fix36 - explicit UserAppeal contract")
class Fix36UserAppealMigrationIT {

    private static final String PREFIX = "FIX36-IT-";
    private static final Path SCRIPT = Path.of("database/fix36_user_appeal_contract.sql");

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
                    "fix36 regression must run only on the runner-owned ephemeral database: " + name);
        }
    }

    @AfterAll
    static void shutdown() {
        DBConnection.shutdown();
    }

    @Test
    @DisplayName("legacy rows are backfilled and a second run is idempotent")
    void backfillsLegacyRowsAndRerunsSafely() throws Exception {
        try (Connection connection = DBConnection.getConnection()) {
            removeFixture(connection);
            try {
                int userId = integer(connection, "SELECT TOP 1 Id FROM Users ORDER BY Id");
                execute(connection, """
                        INSERT INTO Orders
                            (UserId,ShowtimeId,SeatSubtotal,ComboSubtotal,DiscountAmount,
                             TotalAmount,TicketCode,PaymentMethod,PaymentStatus,OrderStatus)
                        VALUES (%d,1,100000,0,0,100000,'%sORDER','card','paid','confirmed')
                        """.formatted(userId, PREFIX));
                int orderId = integer(connection,
                        "SELECT Id FROM Orders WHERE TicketCode='" + PREFIX + "ORDER'");
                String ticket = PREFIX + "ORDER";

                prepareLegacyShape(connection);
                execute(connection, """
                        INSERT INTO UserAppeals
                            (UserId,Email,Reason,TicketCode,BankAccountInfo,Status,AppealType,OrderId)
                        SELECT Id,Email,'%sACCOUNT',NULL,NULL,'approved',NULL,NULL
                        FROM Users WHERE Id=%d
                        """.formatted(PREFIX, userId));
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO UserAppeals
                            (UserId,Email,Reason,TicketCode,BankAccountInfo,Status,AppealType,OrderId)
                        SELECT Id,Email,?,?,?,'approved',NULL,NULL FROM Users WHERE Id=?
                        """)) {
                    statement.setString(1, PREFIX + "REFUND");
                    statement.setString(2, ticket);
                    statement.setString(3, PREFIX + "BANK");
                    statement.setInt(4, userId);
                    assertEquals(1, statement.executeUpdate());
                }

                runScript(connection);
                assertEquals("account", string(connection,
                        "SELECT AppealType FROM UserAppeals WHERE Reason='" + PREFIX + "ACCOUNT'"));
                assertEquals("refund", string(connection,
                        "SELECT AppealType FROM UserAppeals WHERE Reason='" + PREFIX + "REFUND'"));
                assertEquals(orderId, integer(connection,
                        "SELECT OrderId FROM UserAppeals WHERE Reason='" + PREFIX + "REFUND'"));
                assertContractObjects(connection);

                int before = integer(connection,
                        "SELECT COUNT(*) FROM UserAppeals WHERE Reason LIKE '" + PREFIX + "%'");
                runScript(connection);
                assertEquals(before, integer(connection,
                        "SELECT COUNT(*) FROM UserAppeals WHERE Reason LIKE '" + PREFIX + "%'"));
                assertContractObjects(connection);
            } finally {
                removeFixture(connection);
                runScript(connection);
            }
        }
    }

    @Test
    @DisplayName("invalid legacy ticket rolls back without changing metadata or indexes")
    void invalidMappingFailsClosedAndRollsBack() throws Exception {
        try (Connection connection = DBConnection.getConnection()) {
            removeFixture(connection);
            execute(connection, """
                    IF EXISTS (SELECT 1 FROM sys.check_constraints
                               WHERE parent_object_id=OBJECT_ID('UserAppeals')
                                 AND name='CK_UserAppeals_TypeMetadata')
                        ALTER TABLE UserAppeals DROP CONSTRAINT CK_UserAppeals_TypeMetadata
                    """);
            int userId = integer(connection, "SELECT TOP 1 Id FROM Users ORDER BY Id");
            execute(connection, """
                    INSERT INTO UserAppeals
                        (UserId,Email,Reason,TicketCode,BankAccountInfo,Status,AppealType,OrderId)
                    SELECT Id,Email,'%sINVALID','%sMISSING','%sBANK','approved','refund',NULL
                    FROM Users WHERE Id=%d
                    """.formatted(PREFIX, PREFIX, PREFIX, userId));
            int indexesBefore = contractIndexCount(connection);
            try {
                SQLException error = assertThrows(SQLException.class,
                        () -> runScript(connection));
                assertEquals(51603, error.getErrorCode());
                assertEquals("refund", string(connection,
                        "SELECT AppealType FROM UserAppeals WHERE Reason='" + PREFIX + "INVALID'"));
                assertEquals(indexesBefore, contractIndexCount(connection));
            } finally {
                removeFixture(connection);
                runScript(connection);
            }
            assertContractObjects(connection);
        }
    }

    private static void prepareLegacyShape(Connection connection) throws SQLException {
        execute(connection, """
                IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name='CK_UserAppeals_TypeMetadata')
                    ALTER TABLE UserAppeals DROP CONSTRAINT CK_UserAppeals_TypeMetadata;
                IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('UserAppeals')
                           AND name='UX_UserAppeals_Pending_Order')
                    DROP INDEX UX_UserAppeals_Pending_Order ON UserAppeals;
                IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('UserAppeals')
                           AND name='UX_UserAppeals_Pending_Account')
                    DROP INDEX UX_UserAppeals_Pending_Account ON UserAppeals;
                IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('UserAppeals')
                           AND name='IX_UserAppeals_Type_Status_Order')
                    DROP INDEX IX_UserAppeals_Type_Status_Order ON UserAppeals;
                IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('UserAppeals')
                           AND name='IX_UserAppeals_Type_Status_CreatedAt')
                    DROP INDEX IX_UserAppeals_Type_Status_CreatedAt ON UserAppeals;
                IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('UserAppeals')
                           AND name='IX_UserAppeals_Cinema_Type_Status')
                    DROP INDEX IX_UserAppeals_Cinema_Type_Status ON UserAppeals;
                ALTER TABLE UserAppeals ALTER COLUMN AppealType NVARCHAR(20) NULL;
                """);
    }

    private static void assertContractObjects(Connection connection) throws SQLException {
        assertEquals(0, integer(connection, """
                SELECT is_nullable FROM sys.columns
                WHERE object_id=OBJECT_ID('UserAppeals') AND name='AppealType'
                """));
        assertEquals(1, integer(connection, """
                SELECT COUNT(*) FROM sys.check_constraints
                WHERE parent_object_id=OBJECT_ID('UserAppeals')
                  AND name='CK_UserAppeals_TypeMetadata' AND is_disabled=0 AND is_not_trusted=0
                """));
        assertEquals(4, contractIndexCount(connection));
    }

    private static int contractIndexCount(Connection connection) throws SQLException {
        return integer(connection, """
                SELECT COUNT(*) FROM sys.indexes WHERE object_id=OBJECT_ID('UserAppeals')
                  AND name IN ('UX_UserAppeals_Pending_Order','UX_UserAppeals_Pending_Account',
                               'IX_UserAppeals_Type_Status_Order',
                               'IX_UserAppeals_Type_Status_CreatedAt')
                """);
    }

    private static void removeFixture(Connection connection) throws SQLException {
        execute(connection, "DELETE FROM UserAppeals WHERE Reason LIKE '" + PREFIX + "%'");
        execute(connection, "DELETE FROM Orders WHERE TicketCode LIKE '" + PREFIX + "%'");
    }

    private static void runScript(Connection connection) throws IOException, SQLException {
        for (String batch : splitOnGo(Files.readString(SCRIPT, StandardCharsets.UTF_8))) {
            execute(connection, batch);
        }
    }

    private static List<String> splitOnGo(String sql) {
        List<String> batches = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : sql.split("\\R")) {
            if (line.trim().equalsIgnoreCase("GO")) {
                if (!current.toString().isBlank()) {
                    batches.add(current.toString());
                }
                current.setLength(0);
            } else {
                current.append(line).append(System.lineSeparator());
            }
        }
        if (!current.toString().isBlank()) {
            batches.add(current.toString());
        }
        return batches;
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
                // Drain all SQL Server results before the next batch.
            }
        }
    }
}
