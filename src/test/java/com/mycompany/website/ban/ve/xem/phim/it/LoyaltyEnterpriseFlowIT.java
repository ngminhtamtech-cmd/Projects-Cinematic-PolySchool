package com.mycompany.website.ban.ve.xem.phim.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import com.mycompany.website.ban.ve.xem.phim.service.LoyaltyService;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Luong loyalty that tren SQL Server: earn -> tier -> redeem -> ledger.
 */
@Tag("it")
@DisplayName("Loyalty enterprise flow")
public class LoyaltyEnterpriseFlowIT {
    private static final String EMAIL_PREFIX = "flow-loyalty-";
    private static final String PROMO_PREFIX = "FLOWLOY";

    static {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    private final LoyaltyService loyaltyService = new LoyaltyService();

    @BeforeAll
    static void configure() {
        DBConnection.shutdown();
    }

    @BeforeEach
    @AfterEach
    void cleanup() throws SQLException {
        assertTestDatabase();
        execute("""
                DELETE uv FROM UserVouchers uv
                JOIN Users u ON u.Id=uv.UserId
                WHERE u.Email LIKE 'flow-loyalty-%';
                DELETE pt FROM PointTransactions pt
                JOIN Users u ON u.Id=pt.UserId
                WHERE u.Email LIKE 'flow-loyalty-%';
                DELETE FROM Promotions WHERE Code LIKE 'FLOWLOY%';
                DELETE FROM Users WHERE Email LIKE 'flow-loyalty-%';
                """);
    }

    @AfterAll
    static void shutdown() {
        DBConnection.shutdown();
    }

    @Test
    @DisplayName("FLOW-LOY-004: 10,000 VND earns exactly 10 loyalty points")
    void tenThousandVndEarnsTenPoints() throws SQLException {
        int userId = createUser("rate", 0, BigDecimal.ZERO, "BRONZE");

        loyaltyService.addPointsOnPayment(userId, new BigDecimal("10000.00"));

        assertEquals(10, userInt(userId, "LoyaltyPoints"));
        assertEquals(0, new BigDecimal("10000.00").compareTo(userDecimal(userId, "TotalSpent")));
        assertEquals(10, pointSum(userId, "EARN_PURCHASE"));
        assertEquals(1, pointTransactionCount(userId, "EARN_PURCHASE"),
                "FLOW-LOY-004 one payment event must create one ledger row");
    }

    @Test
    @DisplayName("FLOW-LOY-005: redeem deducts balance but keeps the earned membership tier")
    void redeemDeductsBalanceWithoutDowngradingTier() throws SQLException {
        int userId = createUser("tier", 600, new BigDecimal("500000.00"), "SILVER");
        int promotionId = createRedeemablePromotion(PROMO_PREFIX + "TIER", 500);

        loyaltyService.redeemVoucherWithPoints(userId, promotionId);

        assertEquals(100, userInt(userId, "LoyaltyPoints"),
                "redeeming a 500-point voucher from 600 points must leave 100");
        assertEquals("SILVER", userString(userId, "MembershipTier"),
                "spending points must not downgrade the tier earned before redemption");
        assertEquals(-500, pointSum(userId, "REDEEM_VOUCHER"));
        assertEquals(1, voucherCount(userId, promotionId));
    }

    @Test
    @DisplayName("FLOW-LOY-006: concurrent redemption cannot overspend one balance")
    void concurrentRedemptionIssuesOnlyOneVoucherAndNeverMakesBalanceNegative() throws Exception {
        int userId = createUser("race", 100, new BigDecimal("100000.00"), "BRONZE");
        int promotionId = createRedeemablePromotion(PROMO_PREFIX + "RACE", 100);
        int workers = 8;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(workers);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        for (int index = 0; index < workers; index++) {
            Thread thread = new Thread(() -> {
                ready.countDown();
                try {
                    start.await();
                    loyaltyService.redeemVoucherWithPoints(userId, promotionId);
                    succeeded.incrementAndGet();
                } catch (BookingException ex) {
                    rejected.incrementAndGet();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }, "loyalty-redeem-" + index);
            thread.start();
        }

        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS));

        int balance = userInt(userId, "LoyaltyPoints");
        int vouchers = voucherCount(userId, promotionId);
        assertEquals(1, succeeded.get(),
                "FLOW-LOY-006 exactly one concurrent request may spend the only 100 points; "
                + "succeeded=" + succeeded + ", rejected=" + rejected);
        assertEquals(1, vouchers,
                "FLOW-LOY-006 exactly one personal voucher may be issued; actual=" + vouchers);
        assertEquals(0, balance,
                "FLOW-LOY-006 balance must be zero, never negative; actual=" + balance);
    }

    private int createUser(String suffix, int points, BigDecimal totalSpent, String tier) throws SQLException {
        String sql = """
                INSERT INTO Users
                    (FullName, Email, PasswordHash, Role, LoyaltyPoints, TotalSpent, MembershipTier)
                VALUES (?, ?, ?, 'member', ?, ?, ?)
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, "Enterprise Loyalty " + suffix);
            ps.setString(2, EMAIL_PREFIX + suffix + "@test.local");
            ps.setString(3, "$2a$12$Cja7B.jV5kPjNnjZfPWAR.5lZPcgJ9Z/mTRrIUTaSBxpO6iTyfoBm");
            ps.setInt(4, points);
            ps.setBigDecimal(5, totalSpent);
            ps.setString(6, tier);
            assertEquals(1, ps.executeUpdate());
            try (ResultSet keys = ps.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getInt(1);
            }
        }
    }

    private int createRedeemablePromotion(String code, int pointsRequired) throws SQLException {
        String sql = """
                INSERT INTO Promotions
                    (Code, Description, DiscountPercent, MaxDiscount, StartDate, EndDate,
                     Status, VoucherType, PointsRequired, PerUserLimit)
                VALUES (?, 'Enterprise loyalty flow', 10, 50000,
                        DATEADD(DAY,-1,CAST(GETDATE() AS DATE)),
                        DATEADD(DAY,30,CAST(GETDATE() AS DATE)),
                        'active', 'REDEEMABLE', ?, 1)
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, code);
            ps.setInt(2, pointsRequired);
            assertEquals(1, ps.executeUpdate());
            try (ResultSet keys = ps.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getInt(1);
            }
        }
    }

    private int userInt(int userId, String column) throws SQLException {
        return queryInt("SELECT " + column + " FROM Users WHERE Id=?", userId);
    }

    private BigDecimal userDecimal(int userId, String column) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT " + column + " FROM Users WHERE Id=?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getBigDecimal(1);
            }
        }
    }

    private String userString(int userId, String column) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT " + column + " FROM Users WHERE Id=?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getString(1);
            }
        }
    }

    private int pointSum(int userId, String type) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT COALESCE(SUM(Points),0) FROM PointTransactions WHERE UserId=? AND Type=?")) {
            ps.setInt(1, userId);
            ps.setString(2, type);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private int pointTransactionCount(int userId, String type) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT COUNT(*) FROM PointTransactions WHERE UserId=? AND Type=?")) {
            ps.setInt(1, userId);
            ps.setString(2, type);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private int voucherCount(int userId, int promotionId) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT COUNT(*) FROM UserVouchers WHERE UserId=? AND PromotionId=?")) {
            ps.setInt(1, userId);
            ps.setInt(2, promotionId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private int queryInt(String sql, int id) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.executeUpdate();
        }
    }

    private void assertTestDatabase() throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT DB_NAME()");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(System.getProperty("cinebook.it.database", "CineBookIT_REQUIRED"), rs.getString(1));
        }
    }
}
