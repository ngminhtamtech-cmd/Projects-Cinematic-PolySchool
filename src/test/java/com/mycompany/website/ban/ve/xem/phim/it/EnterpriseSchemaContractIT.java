package com.mycompany.website.ban.ve.xem.phim.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Hop dong schema doanh nghiep duoc phe duyet trong design 2026-07-30.
 *
 * <p>Cac assertion trong lop nay co chu dich chay tren schema that cua
 * {@code CineBookDB_Test}. Chung khong duoc thay bang grep migration hoac mock metadata.</p>
 */
@Tag("it")
@DisplayName("Enterprise schema contract - token, loyalty, notification")
public class EnterpriseSchemaContractIT {

    static {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    @BeforeAll
    static void connectOnlyToTestDatabase() throws SQLException {
        DBConnection.shutdown();
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT DB_NAME()");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(System.getProperty("cinebook.it.database", "CineBookIT_REQUIRED"), rs.getString(1),
                    "FLOW-DB-000: enterprise tests are forbidden from writing to the production database");
        }
    }

    @AfterAll
    static void shutdown() {
        DBConnection.shutdown();
    }

    @Test
    @DisplayName("FLOW-AUTH-REF-001: refresh-token rotation has a durable server-side table")
    void refreshTokenRotationTableExists() throws SQLException {
        assertTrue(tableExists("RefreshTokens"),
                """
                FLOW-AUTH-REF-001 expected a RefreshTokens table in CineBookDB_Test.
                The current application only has JSESSIONID and cannot rotate, revoke, or detect reuse
                of an opaque refresh token without durable server-side state.
                """);
    }

    @Test
    @DisplayName("FLOW-AUTH-REF-002: refresh token table can represent family rotation and replay")
    void refreshTokenTableContainsRotationAndReplayColumns() throws SQLException {
        Set<String> required = Set.of(
                "UserId", "FamilyId", "TokenHash", "IssuedAt", "ExpiresAt",
                "ConsumedAt", "ReplacedById", "RevokedAt", "RevocationReason");
        Set<String> actual = columns("RefreshTokens");
        assertTrue(actual.containsAll(required),
                "FLOW-AUTH-REF-002 missing RefreshTokens columns. required=" + required + ", actual=" + actual);
    }

    @Test
    @DisplayName("FLOW-AUTH-REF-003: refresh token hash is unique")
    void refreshTokenHashHasUniqueIndex() throws SQLException {
        assertTrue(hasUniqueIndexCovering("RefreshTokens", "TokenHash"),
                "FLOW-AUTH-REF-003 requires a UNIQUE index on RefreshTokens.TokenHash");
    }

    @Test
    @DisplayName("FLOW-LOY-001: spendable balance is separate from lifetime earned points")
    void usersStoreLifetimeEarnedPointsSeparately() throws SQLException {
        Set<String> userColumns = columns("Users");
        assertTrue(userColumns.contains("LifetimeEarnedPoints"),
                """
                FLOW-LOY-001 expected Users.LifetimeEarnedPoints.
                LoyaltyPoints is a spendable balance and cannot also be the immutable basis for membership tier.
                """);
    }

    @Test
    @DisplayName("FLOW-LOY-002: loyalty ledger references the originating business event")
    void pointLedgerHasBusinessReferencesAndIdempotency() throws SQLException {
        Set<String> required = Set.of("OrderId", "VoucherId", "IdempotencyKey");
        Set<String> actual = columns("PointTransactions");
        assertTrue(actual.containsAll(required),
                "FLOW-LOY-002 PointTransactions cannot prove or deduplicate earn/redeem events. "
                + "required=" + required + ", actual=" + actual);
    }

    @Test
    @DisplayName("FLOW-LOY-003: loyalty balance cannot become negative at database boundary")
    void loyaltyBalanceHasNonNegativeCheckConstraint() throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM sys.check_constraints cc
                WHERE cc.parent_object_id = OBJECT_ID('Users')
                  AND cc.definition LIKE '%LoyaltyPoints%'
                  AND (cc.definition LIKE '%>=(0)%' OR cc.definition LIKE '%>= 0%')
                """;
        assertTrue(queryInt(sql) > 0,
                "FLOW-LOY-003 requires a DB CHECK constraint that prevents Users.LoyaltyPoints < 0");
    }

    @Test
    @DisplayName("FLOW-NOTIFY-USER-001: user notification delivery and per-user read state are durable")
    void userNotificationsHaveDeliveryAndReceiptTables() throws SQLException {
        assertTrue(tableExists("UserNotifications"),
                "FLOW-NOTIFY-USER-001 expected UserNotifications for manager/admin-authored user messages");
        assertTrue(tableExists("NotificationRecipients"),
                "FLOW-NOTIFY-USER-001 expected NotificationRecipients for per-user delivery/read state");
        Set<String> notificationColumns = columns("UserNotifications");
        assertTrue(notificationColumns.containsAll(Set.of(
                        "Title", "Message", "TargetType", "TargetId",
                        "VisibleFrom", "VisibleUntil", "Status", "CreatedByUserId")),
                "FLOW-NOTIFY-USER-001 missing scheduling/targeting columns: " + notificationColumns);
        Set<String> receiptColumns = columns("NotificationRecipients");
        assertTrue(receiptColumns.containsAll(Set.of("NotificationId", "UserId", "ReadAt")),
                "FLOW-NOTIFY-USER-001 missing per-user receipt/read columns: " + receiptColumns);
    }

    private boolean tableExists(String tableName) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet rs = metadata.getTables(connection.getCatalog(), "dbo", tableName, new String[]{"TABLE"})) {
                return rs.next();
            }
        }
    }

    private Set<String> columns(String tableName) throws SQLException {
        Set<String> columns = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        try (Connection connection = DBConnection.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet rs = metadata.getColumns(connection.getCatalog(), "dbo", tableName, null)) {
                while (rs.next()) {
                    columns.add(rs.getString("COLUMN_NAME"));
                }
            }
        }
        return columns;
    }

    private boolean hasUniqueIndexCovering(String tableName, String columnName) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet rs = metadata.getIndexInfo(
                    connection.getCatalog(), "dbo", tableName, true, false)) {
                while (rs.next()) {
                    if (columnName.equalsIgnoreCase(rs.getString("COLUMN_NAME"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private int queryInt(String sql) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            return rs.getInt(1);
        }
    }
}
