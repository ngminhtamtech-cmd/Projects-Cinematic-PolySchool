package com.mycompany.website.ban.ve.xem.phim.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.Promotion;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import com.mycompany.website.ban.ve.xem.phim.service.LoyaltyService;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * P1 regression: kho qua va POST doi diem phai cung mot policy, dung gio SQL Server.
 */
@Tag("it")
@DisplayName("Loyalty redemption policy")
public class LoyaltyRedemptionPolicyIT {
    private static final String EMAIL_PREFIX = "loyalty-policy-";
    private static final String PROMO_PREFIX = "LOYPA";
    private static final String TICKET_PREFIX = "LOYPA";

    static {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    private final LoyaltyService service = new LoyaltyService();

    @BeforeAll
    static void configure() {
        DBConnection.shutdown();
    }

    @BeforeEach
    @AfterEach
    void cleanup() throws SQLException {
        assertTestDatabase();
        execute("""
                DELETE pu FROM PromotionUsage pu
                JOIN Promotions p ON p.Id = pu.PromotionId
                WHERE p.Code LIKE 'LOYPA%';
                DELETE FROM Orders WHERE TicketCode LIKE 'LOYPA%';
                DELETE uv FROM UserVouchers uv
                JOIN Promotions p ON p.Id = uv.PromotionId
                WHERE p.Code LIKE 'LOYPA%';
                DELETE pt FROM PointTransactions pt
                JOIN Users u ON u.Id = pt.UserId
                WHERE u.Email LIKE 'loyalty-policy-%';
                DELETE FROM Promotions WHERE Code LIKE 'LOYPA%';
                DELETE FROM Users WHERE Email LIKE 'loyalty-policy-%';
                """);
    }

    @AfterAll
    static void shutdown() {
        DBConnection.shutdown();
    }

    @Test
    @DisplayName("forged POST rejects public, inactive, future, expired, exhausted and zero-cost IDs")
    void forgedIdsOutsidePolicyAreRejectedWithoutAnySideEffect() throws SQLException {
        int userId = createUser("invalid", 700, "EMERALD");
        List<Integer> invalidPromotionIds = List.of(
                createPromotion("PUBLIC", "PUBLIC", "active", -1, 7, null, 0, "ALL", 100, 1),
                createPromotion("INACTIVE", "REDEEMABLE", "inactive", -1, 7, null, 0, "ALL", 100, 1),
                createPromotion("FUTURE", "REDEEMABLE", "active", 1, 7, null, 0, "ALL", 100, 1),
                createPromotion("EXPIRED", "REDEEMABLE", "active", -7, -1, null, 0, "ALL", 100, 1),
                createPromotion("EXHAUST", "REDEEMABLE", "active", -1, 7, 1, 1, "ALL", 100, 1),
                createPromotion("ZEROCOST", "REDEEMABLE", "active", -1, 7, null, 0, "ALL", 0, 1));

        for (int promotionId : invalidPromotionIds) {
            assertRejectedWithoutSideEffects(userId, promotionId);
        }
    }

    @Test
    @DisplayName("target tier and per-user voucher/usage limits are enforced")
    void targetTierAndPerUserLimitsApplyToListAndPost() throws Exception {
        int bronzeUser = createUser("bronze", 500, "BRONZE");
        int tierPromotion = createPromotion(
                "TIER", "REDEEMABLE", "active", -1, 7, null, 0, "DIAMOND", 100, 1);
        assertRejectedWithoutSideEffects(bronzeUser, tierPromotion);

        int diamondUser = createUser("diamond", 500, "DIAMOND");
        int voucherLimited = createPromotion(
                "VOUCHERLIM", "REDEEMABLE", "active", -1, 7, null, 0, "ALL", 100, 1);
        insertVoucher(diamondUser, voucherLimited, "LOYPA-EXISTING-VOUCHER");
        assertRejectedWithoutSideEffects(diamondUser, voucherLimited);

        int usageLimited = createPromotion(
                "USAGELIM", "REDEEMABLE", "active", -1, 7, null, 0, "ALL", 100, 1);
        insertPromotionUsage(diamondUser, usageLimited);
        assertRejectedWithoutSideEffects(diamondUser, usageLimited);

        Set<Integer> listed = listForUser(diamondUser).stream()
                .map(Promotion::getId)
                .collect(Collectors.toSet());
        assertTrue(!listed.contains(voucherLimited), "Voucher da cap phai tinh vao PerUserLimit");
        assertTrue(!listed.contains(usageLimited), "PromotionUsage da tieu phai tinh vao PerUserLimit");
    }

    @Test
    @DisplayName("GET includes only the exact DB-clock policy and accepts today's boundary")
    void getAndPostUseTheSameDatabaseClockPolicy() throws Exception {
        int userId = createUser("listing", 500, "SILVER");
        int eligible = createPromotion(
                "TODAY", "REDEEMABLE", "active", 0, 0, 3, 1, "SILVER", 100, 1);
        int publicId = createPromotion(
                "LISTPUBLIC", "PUBLIC", "active", -1, 7, null, 0, "ALL", 100, 1);
        int futureId = createPromotion(
                "LISTFUTURE", "REDEEMABLE", "active", 1, 7, null, 0, "ALL", 100, 1);

        Set<Integer> listed = listForUser(userId).stream()
                .map(Promotion::getId)
                .collect(Collectors.toSet());

        assertEquals(Set.of(eligible), listed,
                "GET must expose exactly the promotions that POST can redeem");
        redeemWithKey(userId, eligible, "policy-today-boundary");
        assertRejectedWithoutSideEffects(userId, publicId);
        assertRejectedWithoutSideEffects(userId, futureId);
    }

    @Test
    @DisplayName("last global quota is reserved by exactly one concurrent redemption")
    void concurrentRedemptionsCannotOverbookTheLastGlobalQuota() throws Exception {
        int userA = createUser("quota-a", 200, "BRONZE");
        int userB = createUser("quota-b", 200, "BRONZE");
        int promotionId = createPromotion(
                "QUOTARACE", "REDEEMABLE", "active", -1, 7, 1, 0, "ALL", 100, 1);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger rejections = new AtomicInteger();
        AtomicReference<Throwable> unexpected = new AtomicReference<>();

        runRedemption(ready, start, done, successes, rejections, unexpected,
                userA, promotionId, "quota-race-a");
        runRedemption(ready, start, done, successes, rejections, unexpected,
                userB, promotionId, "quota-race-b");

        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS));
        assertEquals(null, unexpected.get(), () -> "Unexpected race failure: " + unexpected.get());
        assertEquals(1, successes.get());
        assertEquals(1, rejections.get());
        assertEquals(1, scalar("SELECT COUNT(*) FROM UserVouchers WHERE PromotionId=?", promotionId));
        assertEquals(1, scalar("SELECT COUNT(*) FROM PointTransactions pt "
                + "JOIN UserVouchers uv ON uv.Id=pt.VoucherId WHERE uv.PromotionId=?", promotionId));
        assertEquals(300, userPoints(userA) + userPoints(userB));
    }

    @Test
    @DisplayName("same idempotency key is a no-op replay and cannot be rebound to another promotion")
    void replayIsIdempotentAndKeyCannotBeRebound() throws Exception {
        int userId = createUser("replay", 400, "BRONZE");
        int firstPromotion = createPromotion(
                "REPLAYA", "REDEEMABLE", "active", -1, 7, null, 0, "ALL", 100, 0);
        int secondPromotion = createPromotion(
                "REPLAYB", "REDEEMABLE", "active", -1, 7, null, 0, "ALL", 100, 0);
        String key = "loyalty-replay-fixed-key";

        redeemWithKey(userId, firstPromotion, key);
        redeemWithKey(userId, firstPromotion, key);

        assertEquals(300, userPoints(userId));
        assertEquals(1, voucherCount(userId, firstPromotion));
        assertEquals(1, redemptionLedgerCount(userId));

        BookingException conflict = assertThrows(BookingException.class,
                () -> redeemWithKey(userId, secondPromotion, key));
        assertEquals(409, conflict.getStatusCode());
        assertEquals(300, userPoints(userId));
        assertEquals(0, voucherCount(userId, secondPromotion));
        assertEquals(1, redemptionLedgerCount(userId));
    }

    @Test
    @DisplayName("concurrent copies of one request return one atomic redemption")
    void concurrentReplayHasExactlyOneSetOfSideEffects() throws Exception {
        int userId = createUser("concurrent-replay", 300, "BRONZE");
        int promotionId = createPromotion(
                "CONREPLAY", "REDEEMABLE", "active", -1, 7, null, 0, "ALL", 100, 0);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger rejections = new AtomicInteger();
        AtomicReference<Throwable> unexpected = new AtomicReference<>();

        runRedemption(ready, start, done, successes, rejections, unexpected,
                userId, promotionId, "same-concurrent-key");
        runRedemption(ready, start, done, successes, rejections, unexpected,
                userId, promotionId, "same-concurrent-key");

        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS));
        assertEquals(null, unexpected.get(), () -> "Unexpected replay failure: " + unexpected.get());
        assertEquals(2, successes.get(), "Both copies should receive the idempotent success result");
        assertEquals(0, rejections.get());
        assertEquals(200, userPoints(userId));
        assertEquals(1, voucherCount(userId, promotionId));
        assertEquals(1, redemptionLedgerCount(userId));
    }

    @Test
    @DisplayName("eligible redemption commits one debit, one voucher and one ledger row")
    void eligibleRedemptionCommitsExactlyOneAtomicTriple() throws Exception {
        int userId = createUser("eligible", 250, "SILVER");
        int promotionId = createPromotion(
                "ELIGIBLE", "REDEEMABLE", "active", -1, 7, 5, 1, "SILVER", 100, 1);

        redeemWithKey(userId, promotionId, "eligible-one");

        assertEquals(150, userPoints(userId));
        assertEquals(1, voucherCount(userId, promotionId));
        assertEquals(1, redemptionLedgerCount(userId));
        assertEquals(-100, scalar("SELECT COALESCE(SUM(Points),0) FROM PointTransactions "
                + "WHERE UserId=? AND Type='REDEEM_VOUCHER'", userId));
    }

    private void assertRejectedWithoutSideEffects(int userId, int promotionId) throws SQLException {
        int pointsBefore = userPoints(userId);
        int vouchersBefore = scalar("SELECT COUNT(*) FROM UserVouchers WHERE UserId=?", userId);
        int ledgerBefore = redemptionLedgerCount(userId);

        BookingException rejected = assertThrows(BookingException.class,
                () -> service.redeemVoucherWithPoints(userId, promotionId));
        assertTrue(rejected.getStatusCode() >= 400 && rejected.getStatusCode() < 500,
                () -> "Policy rejection must be 4xx, got " + rejected.getStatusCode());
        assertEquals(pointsBefore, userPoints(userId));
        assertEquals(vouchersBefore, scalar("SELECT COUNT(*) FROM UserVouchers WHERE UserId=?", userId));
        assertEquals(ledgerBefore, redemptionLedgerCount(userId));
    }

    @SuppressWarnings("unchecked")
    private List<Promotion> listForUser(int userId) throws Exception {
        Method method = LoyaltyService.class.getMethod("listRedeemablePromotionsForUser", int.class);
        return (List<Promotion>) method.invoke(service, userId);
    }

    private void redeemWithKey(int userId, int promotionId, String redemptionKey) throws Exception {
        Method method = LoyaltyService.class.getMethod(
                "redeemVoucherWithPoints", int.class, int.class, String.class);
        try {
            method.invoke(service, userId, promotionId, redemptionKey);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof BookingException bookingException) {
                throw bookingException;
            }
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw ex;
        }
    }

    private void runRedemption(CountDownLatch ready, CountDownLatch start, CountDownLatch done,
            AtomicInteger successes, AtomicInteger rejections, AtomicReference<Throwable> unexpected,
            int userId, int promotionId, String redemptionKey) {
        new Thread(() -> {
            ready.countDown();
            try {
                start.await();
                redeemWithKey(userId, promotionId, redemptionKey);
                successes.incrementAndGet();
            } catch (BookingException ex) {
                rejections.incrementAndGet();
            } catch (Throwable ex) {
                unexpected.compareAndSet(null, ex);
            } finally {
                done.countDown();
            }
        }, redemptionKey).start();
    }

    private int createUser(String suffix, int points, String tier) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     INSERT INTO Users
                         (FullName, Email, PasswordHash, Role, LoyaltyPoints,
                          LifetimeEarnedPoints, TotalSpent, MembershipTier)
                     VALUES (?, ?, ?, 'member', ?, ?, 0, ?)
                     """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, "Loyalty Policy " + suffix);
            ps.setString(2, EMAIL_PREFIX + suffix + "@test.local");
            ps.setString(3, "$2a$12$Cja7B.jV5kPjNnjZfPWAR.5lZPcgJ9Z/mTRrIUTaSBxpO6iTyfoBm");
            ps.setInt(4, points);
            ps.setInt(5, points);
            ps.setString(6, tier);
            assertEquals(1, ps.executeUpdate());
            try (ResultSet keys = ps.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getInt(1);
            }
        }
    }

    private int createPromotion(String suffix, String voucherType, String status,
            int startOffsetDays, int endOffsetDays, Integer usageLimit, int usedCount,
            String targetTier, int pointsRequired, int perUserLimit) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     INSERT INTO Promotions
                         (Code, Description, DiscountPercent, MaxDiscount, StartDate, EndDate,
                          UsageLimit, UsedCount, Status, VoucherType, TargetTier,
                          PointsRequired, PerUserLimit)
                     VALUES (?, 'Loyalty policy regression', 10, 50000,
                             DATEADD(DAY, ?, CAST(GETDATE() AS DATE)),
                             DATEADD(DAY, ?, CAST(GETDATE() AS DATE)),
                             ?, ?, ?, ?, ?, ?, ?)
                     """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, PROMO_PREFIX + suffix);
            ps.setInt(2, startOffsetDays);
            ps.setInt(3, endOffsetDays);
            if (usageLimit == null) {
                ps.setNull(4, Types.INTEGER);
            } else {
                ps.setInt(4, usageLimit);
            }
            ps.setInt(5, usedCount);
            ps.setString(6, status);
            ps.setString(7, voucherType);
            ps.setString(8, targetTier);
            ps.setInt(9, pointsRequired);
            ps.setInt(10, perUserLimit);
            assertEquals(1, ps.executeUpdate());
            try (ResultSet keys = ps.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getInt(1);
            }
        }
    }

    private void insertVoucher(int userId, int promotionId, String code) throws SQLException {
        execute("INSERT INTO UserVouchers (UserId, PromotionId, Code) VALUES (?, ?, ?)",
                userId, promotionId, code);
    }

    private void insertPromotionUsage(int userId, int promotionId) throws SQLException {
        String ticketCode = TICKET_PREFIX + "USAGE" + userId;
        int orderId;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     INSERT INTO Orders
                         (UserId, ShowtimeId, PromotionId, SeatSubtotal, ComboSubtotal,
                          DiscountAmount, TotalAmount, TicketCode, PaymentMethod,
                          PaymentStatus, OrderStatus)
                     VALUES (?, 3, ?, 100000, 0, 10000, 90000, ?, 'card', 'paid', 'confirmed')
                     """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userId);
            ps.setInt(2, promotionId);
            ps.setString(3, ticketCode);
            assertEquals(1, ps.executeUpdate());
            try (ResultSet keys = ps.getGeneratedKeys()) {
                assertTrue(keys.next());
                orderId = keys.getInt(1);
            }
        }
        execute("INSERT INTO PromotionUsage (PromotionId, UserId, OrderId) VALUES (?, ?, ?)",
                promotionId, userId, orderId);
    }

    private int userPoints(int userId) throws SQLException {
        return scalar("SELECT LoyaltyPoints FROM Users WHERE Id=?", userId);
    }

    private int voucherCount(int userId, int promotionId) throws SQLException {
        return scalar("SELECT COUNT(*) FROM UserVouchers WHERE UserId=? AND PromotionId=?",
                userId, promotionId);
    }

    private int redemptionLedgerCount(int userId) throws SQLException {
        return scalar("SELECT COUNT(*) FROM PointTransactions "
                + "WHERE UserId=? AND Type='REDEEM_VOUCHER'", userId);
    }

    private static int scalar(String sql, Object... parameters) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, parameters);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private static void execute(String sql, Object... parameters) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, parameters);
            ps.executeUpdate();
        }
    }

    private static void bind(PreparedStatement ps, Object... parameters) throws SQLException {
        for (int index = 0; index < parameters.length; index++) {
            ps.setObject(index + 1, parameters[index]);
        }
    }

    private static void assertTestDatabase() throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT DB_NAME()");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(System.getProperty("cinebook.it.database", "CineBookIT_REQUIRED"), rs.getString(1));
        }
    }
}
