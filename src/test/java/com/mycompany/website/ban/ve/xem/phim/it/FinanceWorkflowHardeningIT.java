package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.model.ShowtimeSeat;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import com.mycompany.website.ban.ve.xem.phim.service.BookingService;
import com.mycompany.website.ban.ve.xem.phim.service.StaffService;
import com.mycompany.website.ban.ve.xem.phim.util.BusinessClock;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P0/P1 regression coverage for admin cancellation and counter-payment money boundaries. */
@Tag("it")
@DisplayName("P0/P1 - paid cancel policy and atomic counter payment")
public class FinanceWorkflowHardeningIT {
    private static final String FILM_TITLE = "FIN-HARDENING-20260801";
    private static final String MEMBER_EMAIL = "fin-hardening-member@test.local";
    private static final String STAFF_EMAIL = "fin-hardening-staff@test.local";
    private static final String PROMO_CODE = "FINHARDEN";
    private static final String LOYALTY_TRIGGER = "TR_FIN_FAIL_LOYALTY";
    private static final String AUDIT_TRIGGER = "TR_FIN_FAIL_AUDIT";

    static {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    private final BookingService bookingService = new BookingService();
    private final AdminService adminService = new AdminService();
    private int cinemaId;
    private int roomId;
    private int showtimeId;
    private int memberId;
    private int staffId;

    @BeforeAll
    static void configureTestDatabase() {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
        DBConnection.shutdown();
    }

    @AfterAll
    static void shutDownPool() {
        DBConnection.shutdown();
    }

    @BeforeEach
    void createFixture() throws SQLException {
        assertTestDatabase();
        cleanupFixture();
        BusinessClock.resetForTesting();

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT TOP (1) r.CinemaId, r.Id
                     FROM Rooms r
                     WHERE r.Status='active'
                       AND (SELECT COUNT(*) FROM Seats s WHERE s.RoomId=r.Id) >= 2
                     ORDER BY r.Id
                     """);
             ResultSet result = statement.executeQuery()) {
            assertTrue(result.next(), "CineBookDB_Test needs an active room with at least two seats");
            cinemaId = result.getInt(1);
            roomId = result.getInt(2);
        }

        memberId = insertUser(MEMBER_EMAIL, "member", cinemaId);
        staffId = insertUser(STAFF_EMAIL, "staff", cinemaId);
        int filmId = insert("""
                INSERT INTO Films (Title,ReleaseDate,EndDate,DurationMinutes,Status)
                VALUES (?,DATEADD(DAY,-1,CAST(GETDATE() AS DATE)),
                        DATEADD(DAY,30,CAST(GETDATE() AS DATE)),120,'showing')
                """, FILM_TITLE);
        execute("INSERT INTO CinemaFilms (CinemaId,FilmId) VALUES (?,?)", cinemaId, filmId);
        showtimeId = insert("""
                INSERT INTO Showtimes
                    (FilmId,CinemaId,RoomId,StartTime,EndTime,BasePrice,Format,Version,Language)
                VALUES (?,?,?,DATEADD(MINUTE,90,GETDATE()),DATEADD(MINUTE,210,GETDATE()),
                        90000,'2D','Subtitle','Vietnamese')
                """, filmId, cinemaId, roomId);
        execute("""
                INSERT INTO ShowtimeSeats (ShowtimeId,SeatId,Status,ExtraFee)
                SELECT ?,Id,'available',0 FROM Seats WHERE RoomId=?
                """, showtimeId, roomId);
    }

    @AfterEach
    void cleanup() throws SQLException {
        BusinessClock.resetForTesting();
        cleanupFixture();
    }

    @Test
    @DisplayName("paid order cancellation is 409 and has no financial or inventory side effect")
    void paidCancelIsConflictWithoutMutation() throws SQLException {
        int promotionId = createPromotion();
        OrderRecord paid = payCard(PROMO_CODE);
        FinancialState before = financialState(paid.getId(), promotionId);

        BookingException conflict = assertThrows(BookingException.class,
                () -> adminService.cancelOrder(paid.getId(), admin(), "khong duoc huy paid"));
        FinancialState after = financialState(paid.getId(), promotionId);

        assertAll(
                () -> assertEquals(409, conflict.getStatusCode()),
                () -> assertEquals("paid", before.paymentStatus()),
                () -> assertEquals("confirmed", before.orderStatus()),
                () -> assertEquals("booked", before.seatStatus()),
                () -> assertEquals(before, after,
                        "Rejected paid cancellation must not mutate money, seat, promo, or loyalty"),
                () -> assertEquals(0, auditRows(paid.getId(), "CANCEL_ORDER")));
    }

    @Test
    @DisplayName("eligible draft and pending-counter cancellations release every reserved resource")
    void eligibleUnpaidCancellationReleasesResources() throws SQLException {
        OrderRecord draft = bookingService.createDraftOrder(
                memberId, showtimeId, List.of(nextSeat()), Map.of(), null, "card");
        int draftSeat = orderSeat(draft.getId());

        adminService.cancelOrder(draft.getId(), admin(), "cancel draft");

        assertAll(
                () -> assertEquals("cancelled", text(
                        "SELECT PaymentStatus FROM Orders WHERE Id=?", draft.getId())),
                () -> assertEquals("cancelled", text(
                        "SELECT OrderStatus FROM Orders WHERE Id=?", draft.getId())),
                () -> assertEquals("available", text(
                        "SELECT Status FROM ShowtimeSeats WHERE Id=?", draftSeat)));

        int promotionId = createPromotion();
        OrderRecord counter = payCounter(PROMO_CODE);
        int counterSeat = orderSeat(counter.getId());
        assertEquals(1, scalar("SELECT UsedCount FROM Promotions WHERE Id=?", promotionId));

        adminService.cancelOrder(counter.getId(), admin(), "cancel pending counter");

        assertAll(
                () -> assertEquals("cancelled", text(
                        "SELECT PaymentStatus FROM Orders WHERE Id=?", counter.getId())),
                () -> assertEquals("cancelled", text(
                        "SELECT OrderStatus FROM Orders WHERE Id=?", counter.getId())),
                () -> assertNull(timestamp(
                        "SELECT CounterExpiresAt FROM Orders WHERE Id=?", counter.getId())),
                () -> assertEquals("available", text(
                        "SELECT Status FROM ShowtimeSeats WHERE Id=?", counterSeat)),
                () -> assertEquals(0, scalar(
                        "SELECT UsedCount FROM Promotions WHERE Id=?", promotionId)),
                () -> assertEquals(0, scalar(
                        "SELECT COUNT(*) FROM PromotionUsage WHERE OrderId=?", counter.getId())),
                () -> assertEquals(0, scalar(
                        "SELECT COUNT(*) FROM PointTransactions WHERE OrderId=?", counter.getId())));
    }

    @Test
    @DisplayName("expired counter is a distinct non-collectable verdict and collection changes nothing")
    void expiredCounterCannotBeCollected() throws SQLException {
        OrderRecord counter = payCounter(null);
        execute("UPDATE Orders SET CounterExpiresAt=DATEADD(SECOND,-1,GETDATE()) WHERE Id=?",
                counter.getId());
        CounterState before = counterState(counter.getId());

        StaffService.TicketLookup lookup = new StaffService()
                .lookupTicket(counter.getTicketCode(), staff());
        BookingException conflict = assertThrows(BookingException.class,
                () -> adminService.markCounterOrderPaid(counter.getId(), staff()));
        CounterState after = counterState(counter.getId());

        assertAll(
                () -> assertEquals("COUNTER_EXPIRED", lookup.getVerdictName()),
                () -> assertFalse(lookup.isCanCollectPayment()),
                () -> assertFalse(lookup.isCanCheckIn()),
                () -> assertEquals(409, conflict.getStatusCode()),
                () -> assertEquals(before, after),
                () -> assertEquals(0, invoiceRows(counter.getId())));
    }

    @Test
    @DisplayName("counter without an expiry cannot be collected")
    void missingCounterExpiryCannotBeCollected() throws SQLException {
        OrderRecord counter = payCounter(null);
        execute("UPDATE Orders SET CounterExpiresAt=NULL WHERE Id=?", counter.getId());
        CounterState before = counterState(counter.getId());

        BookingException conflict = assertThrows(BookingException.class,
                () -> adminService.markCounterOrderPaid(counter.getId(), staff()));

        assertAll(
                () -> assertEquals(409, conflict.getStatusCode()),
                () -> assertEquals(before, counterState(counter.getId())),
                () -> assertEquals(0, invoiceRows(counter.getId())));
    }

    @Test
    @DisplayName("loyalty failure rolls back payment; clean retry pays and awards exactly once")
    void loyaltyFailureRollsBackAndRetrySucceeds() throws SQLException {
        OrderRecord counter = payCounter(null);
        BigDecimal total = counter.getTotalAmount();
        CounterState before = counterState(counter.getId());
        createLoyaltyFailureTrigger(counter.getId());

        BookingException failure;
        try {
            failure = assertThrows(BookingException.class,
                    () -> adminService.markCounterOrderPaid(counter.getId(), staff()));
        } finally {
            dropTrigger(LOYALTY_TRIGGER);
        }

        assertAll(
                () -> assertEquals(500, failure.getStatusCode()),
                () -> assertEquals(before, counterState(counter.getId()),
                        "Loyalty insert failure must roll back payment and deadline clearing"),
                () -> assertEquals(0, pointRows(counter.getId())),
                () -> assertEquals(0, invoiceRows(counter.getId())));

        adminService.markCounterOrderPaid(counter.getId(), staff());

        CounterState paid = counterState(counter.getId());
        int expectedPoints = total.divide(BigDecimal.valueOf(1000)).intValue();
        assertAll(
                () -> assertEquals("paid", paid.paymentStatus()),
                () -> assertEquals("confirmed", paid.orderStatus()),
                () -> assertEquals("COUNTER-" + counter.getId(), paid.transactionId()),
                () -> assertNull(paid.counterExpiresAt()),
                () -> assertEquals(1, pointRows(counter.getId())),
                () -> assertEquals(expectedPoints, scalar(
                        "SELECT LoyaltyPoints FROM Users WHERE Id=?", memberId)),
                () -> assertEquals(0, total.compareTo(decimal(
                        "SELECT TotalSpent FROM Users WHERE Id=?", memberId))),
                () -> assertEquals(1, invoiceRows(counter.getId())));

        CounterState afterSuccess = counterState(counter.getId());
        BookingException replay = assertThrows(BookingException.class,
                () -> adminService.markCounterOrderPaid(counter.getId(), staff()));
        assertAll(
                () -> assertEquals(409, replay.getStatusCode()),
                () -> assertEquals(afterSuccess, counterState(counter.getId())),
                () -> assertEquals(1, pointRows(counter.getId())),
                () -> assertEquals(1, invoiceRows(counter.getId())));
    }

    @Test
    @DisplayName("concurrent counter collection has one winner, one conflict, one ledger and invoice")
    void concurrentCollectionHasExactlyOneWinner() throws Exception {
        OrderRecord counter = payCounter(null);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int attempt = 0; attempt < 2; attempt++) {
                futures.add(executor.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    try {
                        adminService.markCounterOrderPaid(counter.getId(), staff());
                        return 200;
                    } catch (BookingException ex) {
                        return ex.getStatusCode();
                    }
                }));
            }
            start.countDown();
            List<Integer> outcomes = new ArrayList<>();
            for (Future<Integer> future : futures) {
                outcomes.add(future.get(15, TimeUnit.SECONDS));
            }
            Collections.sort(outcomes);

            assertAll(
                    () -> assertEquals(List.of(200, 409), outcomes),
                    () -> assertEquals("paid", counterState(counter.getId()).paymentStatus()),
                    () -> assertEquals(1, pointRows(counter.getId())),
                    () -> assertEquals(1, invoiceRows(counter.getId())));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    @DisplayName("audit failure after commit cannot undo payment or suppress the sale invoice")
    void auditFailureIsPostCommitAndFailSafe() throws SQLException {
        OrderRecord counter = payCounter(null);
        createAuditFailureTrigger(counter.getId());

        try {
            assertDoesNotThrow(() -> adminService.markCounterOrderPaid(counter.getId(), staff()));
        } finally {
            dropTrigger(AUDIT_TRIGGER);
        }

        CounterState paid = counterState(counter.getId());
        assertAll(
                () -> assertEquals("paid", paid.paymentStatus()),
                () -> assertNotNull(paid.transactionId()),
                () -> assertNull(paid.counterExpiresAt()),
                () -> assertEquals(1, pointRows(counter.getId())),
                () -> assertEquals(1, invoiceRows(counter.getId())),
                () -> assertEquals(0, auditRows(counter.getId(), "MARK_COUNTER_PAID")));
    }

    private OrderRecord payCard(String promotionCode) {
        OrderRecord draft = bookingService.createDraftOrder(
                memberId, showtimeId, List.of(nextSeat()), Map.of(), promotionCode, "card");
        return bookingService.payOrder(
                memberId, draft.getId(), Map.of(), promotionCode, "card");
    }

    private OrderRecord payCounter(String promotionCode) {
        OrderRecord draft = bookingService.createDraftOrder(
                memberId, showtimeId, List.of(nextSeat()), Map.of(), promotionCode, "counter");
        return bookingService.payOrder(
                memberId, draft.getId(), Map.of(), promotionCode, "counter");
    }

    private int nextSeat() {
        return bookingService.getSeatMap(showtimeId).stream()
                .filter(seat -> seat.isAvailableFor(memberId))
                .filter(seat -> !"couple".equalsIgnoreCase(seat.getSeatType()))
                .map(ShowtimeSeat::getId)
                .findFirst()
                .orElseThrow();
    }

    private int createPromotion() throws SQLException {
        execute("""
                INSERT INTO Promotions
                    (Code,Description,DiscountPercent,MaxDiscount,StartDate,EndDate,
                     ConditionsJson,UsageLimit,UsedCount,Status,VoucherType,TargetTier,
                     PointsRequired,PerUserLimit)
                VALUES (?, 'finance hardening', 10, 50000,
                        DATEADD(DAY,-1,CAST(GETDATE() AS DATE)),
                        DATEADD(DAY,30,CAST(GETDATE() AS DATE)),NULL,100,0,
                        'active','PUBLIC',NULL,0,1)
                """, PROMO_CODE);
        return scalar("SELECT Id FROM Promotions WHERE Code=?", PROMO_CODE);
    }

    private int insertUser(String email, String role, int assignedCinemaId) throws SQLException {
        return insert("""
                INSERT INTO Users
                    (FullName,Email,PasswordHash,Role,CinemaId,MembershipTier,LoyaltyPoints,
                     LifetimeEarnedPoints,TotalSpent,IsLocked,Deleted)
                SELECT ?,?,PasswordHash,?,?, 'BRONZE',0,0,0,0,0
                FROM Users WHERE Id=(SELECT MIN(Id) FROM Users)
                """, "Finance " + role, email, role, assignedCinemaId);
    }

    private User staff() {
        User actor = new User();
        actor.setId(staffId);
        actor.setRole("staff");
        actor.setCinemaId(cinemaId);
        return actor;
    }

    private static User admin() {
        User actor = new User();
        actor.setId(5);
        actor.setRole("admin");
        return actor;
    }

    private FinancialState financialState(int orderId, int promotionId) throws SQLException {
        CounterState order = counterState(orderId);
        return new FinancialState(
                order.paymentStatus(), order.orderStatus(), order.transactionId(),
                order.counterExpiresAt(), order.seatStatus(),
                scalar("SELECT UsedCount FROM Promotions WHERE Id=?", promotionId),
                scalar("SELECT COUNT(*) FROM PromotionUsage WHERE OrderId=?", orderId),
                scalar("SELECT LoyaltyPoints FROM Users WHERE Id=?", memberId),
                scalar("SELECT LifetimeEarnedPoints FROM Users WHERE Id=?", memberId),
                decimal("SELECT TotalSpent FROM Users WHERE Id=?", memberId),
                pointRows(orderId));
    }

    private CounterState counterState(int orderId) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT o.PaymentStatus,o.OrderStatus,o.TransactionId,o.CounterExpiresAt,
                            ss.Status AS SeatStatus
                     FROM Orders o
                     JOIN OrderSeats os ON os.OrderId=o.Id
                     JOIN ShowtimeSeats ss ON ss.Id=os.ShowtimeSeatId
                     WHERE o.Id=?
                     """)) {
            statement.setInt(1, orderId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                Timestamp expiry = result.getTimestamp("CounterExpiresAt");
                return new CounterState(
                        result.getString("PaymentStatus"), result.getString("OrderStatus"),
                        result.getString("TransactionId"),
                        expiry == null ? null : expiry.toLocalDateTime(),
                        result.getString("SeatStatus"));
            }
        }
    }

    private int orderSeat(int orderId) throws SQLException {
        return scalar("SELECT ShowtimeSeatId FROM OrderSeats WHERE OrderId=?", orderId);
    }

    private int pointRows(int orderId) throws SQLException {
        return scalar("SELECT COUNT(*) FROM PointTransactions WHERE OrderId=?", orderId);
    }

    private int invoiceRows(int orderId) throws SQLException {
        return scalar("SELECT COUNT(*) FROM Invoices WHERE OrderId=? AND InvoiceType='sale'", orderId);
    }

    private int auditRows(int orderId, String action) throws SQLException {
        return scalar("""
                SELECT COUNT(*) FROM AuditLogs
                WHERE TargetType='Order' AND TargetId=? AND Action=?
                """, String.valueOf(orderId), action);
    }

    private void createLoyaltyFailureTrigger(int orderId) throws SQLException {
        executeDdl("""
                CREATE TRIGGER %s ON PointTransactions AFTER INSERT AS
                BEGIN
                    SET NOCOUNT ON;
                    IF EXISTS (SELECT 1 FROM inserted
                               WHERE OrderId=%d AND Type='EARN_PURCHASE')
                        THROW 51041, 'FIN injected loyalty failure', 1;
                END
                """.formatted(LOYALTY_TRIGGER, orderId));
    }

    private void createAuditFailureTrigger(int orderId) throws SQLException {
        executeDdl("""
                CREATE TRIGGER %s ON AuditLogs AFTER INSERT AS
                BEGIN
                    SET NOCOUNT ON;
                    IF EXISTS (SELECT 1 FROM inserted
                               WHERE Action='MARK_COUNTER_PAID' AND TargetId='%d')
                        THROW 51042, 'FIN injected audit failure', 1;
                END
                """.formatted(AUDIT_TRIGGER, orderId));
    }

    private void dropTrigger(String triggerName) throws SQLException {
        executeDdl("IF OBJECT_ID('" + triggerName + "','TR') IS NOT NULL DROP TRIGGER "
                + triggerName);
    }

    private void executeDdl(String sql) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void cleanupFixture() throws SQLException {
        dropTrigger(LOYALTY_TRIGGER);
        dropTrigger(AUDIT_TRIGGER);
        execute("""
                DELETE FROM NotificationRecipients WHERE NotificationId IN (
                  SELECT Id FROM AdminNotifications WHERE TargetType='Order' AND TRY_CONVERT(INT,TargetId) IN (
                    SELECT o.Id FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId
                    JOIN Films f ON f.Id=s.FilmId WHERE f.Title=?));
                DELETE FROM AdminNotifications WHERE TargetType='Order' AND TRY_CONVERT(INT,TargetId) IN (
                  SELECT o.Id FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId
                  JOIN Films f ON f.Id=s.FilmId WHERE f.Title=?);
                DELETE FROM AuditLogs WHERE ActorUserId IN (
                  SELECT Id FROM Users WHERE Email IN (?,?))
                  OR (TargetType='Order' AND TRY_CONVERT(INT,TargetId) IN (
                    SELECT o.Id FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId
                    JOIN Films f ON f.Id=s.FilmId WHERE f.Title=?));
                DELETE FROM PointTransactions WHERE UserId IN (
                  SELECT Id FROM Users WHERE Email IN (?,?)) OR OrderId IN (
                    SELECT o.Id FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId
                    JOIN Films f ON f.Id=s.FilmId WHERE f.Title=?);
                DELETE FROM Invoices WHERE OrderId IN (
                  SELECT o.Id FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId
                  JOIN Films f ON f.Id=s.FilmId WHERE f.Title=?);
                DELETE FROM PromotionUsage WHERE OrderId IN (
                  SELECT o.Id FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId
                  JOIN Films f ON f.Id=s.FilmId WHERE f.Title=?);
                DELETE FROM UserVouchers WHERE UserId IN (
                  SELECT Id FROM Users WHERE Email IN (?,?));
                DELETE FROM RefundTransactions WHERE OrderId IN (
                  SELECT o.Id FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId
                  JOIN Films f ON f.Id=s.FilmId WHERE f.Title=?);
                DELETE FROM OrderComboFoods WHERE OrderId IN (
                  SELECT o.Id FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId
                  JOIN Films f ON f.Id=s.FilmId WHERE f.Title=?);
                DELETE FROM OrderSeats WHERE OrderId IN (
                  SELECT o.Id FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId
                  JOIN Films f ON f.Id=s.FilmId WHERE f.Title=?);
                DELETE FROM Orders WHERE ShowtimeId IN (
                  SELECT s.Id FROM Showtimes s JOIN Films f ON f.Id=s.FilmId WHERE f.Title=?);
                DELETE FROM ShowtimeSeats WHERE ShowtimeId IN (
                  SELECT s.Id FROM Showtimes s JOIN Films f ON f.Id=s.FilmId WHERE f.Title=?);
                DELETE FROM Showtimes WHERE FilmId IN (SELECT Id FROM Films WHERE Title=?);
                DELETE FROM CinemaFilms WHERE FilmId IN (SELECT Id FROM Films WHERE Title=?);
                DELETE FROM Films WHERE Title=?;
                DELETE FROM Users WHERE Email IN (?,?);
                DELETE FROM PromotionUsage WHERE PromotionId IN (
                  SELECT Id FROM Promotions WHERE Code=?);
                DELETE FROM Promotions WHERE Code=?;
                """, FILM_TITLE, FILM_TITLE, MEMBER_EMAIL, STAFF_EMAIL, FILM_TITLE,
                MEMBER_EMAIL, STAFF_EMAIL, FILM_TITLE, FILM_TITLE, FILM_TITLE,
                MEMBER_EMAIL, STAFF_EMAIL, FILM_TITLE, FILM_TITLE, FILM_TITLE,
                FILM_TITLE, FILM_TITLE, FILM_TITLE, FILM_TITLE, FILM_TITLE,
                MEMBER_EMAIL, STAFF_EMAIL, PROMO_CODE, PROMO_CODE);
    }

    private int insert(String sql, Object... values) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, values);
            assertEquals(1, statement.executeUpdate());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getInt(1);
            }
        }
    }

    private void execute(String sql, Object... values) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            statement.executeUpdate();
        }
    }

    private int scalar(String sql, Object... values) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private BigDecimal decimal(String sql, Object... values) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getBigDecimal(1);
            }
        }
    }

    private String text(String sql, Object... values) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }

    private LocalDateTime timestamp(String sql, Object... values) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                Timestamp value = result.getTimestamp(1);
                return value == null ? null : value.toLocalDateTime();
            }
        }
    }

    private void bind(PreparedStatement statement, Object... values) throws SQLException {
        for (int index = 0; index < values.length; index++) {
            statement.setObject(index + 1, values[index]);
        }
    }

    private void assertTestDatabase() throws SQLException {
        assertEquals(System.getProperty("cinebook.it.database", "CineBookIT_REQUIRED"), text("SELECT DB_NAME()"));
    }

    private record CounterState(
            String paymentStatus,
            String orderStatus,
            String transactionId,
            LocalDateTime counterExpiresAt,
            String seatStatus) {
    }

    private record FinancialState(
            String paymentStatus,
            String orderStatus,
            String transactionId,
            LocalDateTime counterExpiresAt,
            String seatStatus,
            int promotionUsedCount,
            int promotionUsageRows,
            int loyaltyPoints,
            int lifetimeEarnedPoints,
            BigDecimal totalSpent,
            int pointRows) {
    }
}
