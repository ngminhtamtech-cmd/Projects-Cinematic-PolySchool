package com.mycompany.website.ban.ve.xem.phim.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.website.ban.ve.xem.phim.config.AppConstants;
import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.controller.admin.ManagerPortalServlet;
import com.mycompany.website.ban.ve.xem.phim.controller.booking.TicketRefundAppealServlet;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.model.UserAppeal;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
import com.mycompany.website.ban.ve.xem.phim.service.AppealResolutionResult;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Regression contract for the two different workflows sharing UserAppeals.
 * Every row and DDL object created here carries the APRF marker and is confined
 * to CineBookDB_Test.
 */
@Tag("it")
@DisplayName("Account appeal and missed-ticket refund appeal workflow")
public class AppealRefundWorkflowIT {
    private static final String PREFIX = "APRF-";
    private static final String EMAIL_LIKE = "aprf-%@test.local";
    private static final String PASSWORD_HASH =
            "$2a$12$Cja7B.jV5kPjNnjZfPWAR.5lZPcgJ9Z/mTRrIUTaSBxpO6iTyfoBm";
    private static final int CINEMA_A = 1;
    private static final int CINEMA_B = 2;
    private static final int ROOM_A = 1;
    private static final int ROOM_B = 2;
    private static final int ADMIN_ID = 5;
    private static final String FAIL_TRIGGER = "TR_APRF_FAIL_REFUND";

    static {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    private final AdminService adminService = new AdminService();
    private int memberA;
    private int memberB;
    private int managerB;
    private int filmId;
    private int endedA;
    private int endedB;
    private int futureA;

    @BeforeAll
    static void configureDatabase() {
        DBConnection.shutdown();
    }

    @BeforeEach
    void createFixture() throws SQLException {
        assertTestDatabase();
        cleanupFixture();
        memberA = insertUser("member-a", "member", CINEMA_A, true, 500, new BigDecimal("100000"));
        memberB = insertUser("member-b", "member", CINEMA_B, true, 500, new BigDecimal("100000"));
        managerB = insertUser("manager-b", "manager", CINEMA_B, false, 0, BigDecimal.ZERO);
        filmId = insert("""
                INSERT INTO Films (Title,DurationMinutes,ReleaseDate,EndDate,Status)
                VALUES (?,120,DATEADD(DAY,-30,CAST(GETDATE() AS DATE)),
                        DATEADD(DAY,30,CAST(GETDATE() AS DATE)),'showing')
                """, PREFIX + "Workflow Film");
        execute("INSERT INTO CinemaFilms (CinemaId,FilmId) VALUES (?,?),(?,?)",
                CINEMA_A, filmId, CINEMA_B, filmId);
        endedA = insertShowtime(CINEMA_A, ROOM_A, -240, -120);
        endedB = insertShowtime(CINEMA_B, ROOM_B, -240, -120);
        futureA = insertShowtime(CINEMA_A, ROOM_A, 120, 240);
    }

    @AfterEach
    void cleanup() throws SQLException {
        cleanupFixture();
    }

    @AfterAll
    static void shutdown() {
        DBConnection.shutdown();
    }

    @Test
    @DisplayName("member GET/POST rejects future, redeemed, cancelled, refunded and rejected tickets")
    void memberEligibilityUsesPersistedTerminalStateWithoutSideEffects() throws Exception {
        int future = insertOrder(memberA, futureA, PREFIX + "FUTURE", "paid", "confirmed", false);
        int redeemed = insertOrder(memberA, endedA, PREFIX + "REDEEMED", "paid", "redeemed", false);
        execute("UPDATE Orders SET RedeemedAt=GETDATE() WHERE Id=?", redeemed);
        insertOrder(memberA, endedA, PREFIX + "CANCELLED", "paid", "cancelled", false);
        int refunded = insertOrder(memberA, endedA, PREFIX + "REFUNDED", "refunded", "cancelled", false);
        execute("UPDATE Orders SET RefundedAt=GETDATE(),RefundAmount=TotalAmount WHERE Id=?", refunded);
        int rejected = insertOrder(memberA, endedA, PREFIX + "REJECTED", "paid", "confirmed", false);
        execute("UPDATE Orders SET RefundRejectedAt=GETDATE(),RefundRejectReason='test' WHERE Id=?", rejected);

        // Thiet ke 05/08/2026: client API nhan 409 kem ma loi cu the; trinh duyet nhan 302 +
        // flash trong giao dien (xem TicketRefundAppealServletTest cho nua con lai cua hop dong).
        for (String ticket : List.of(PREFIX + "FUTURE", PREFIX + "REDEEMED",
                PREFIX + "CANCELLED", PREFIX + "REFUNDED", PREFIX + "REJECTED")) {
            Exchange post = refundPost(member(memberA, CINEMA_A), ticket).asApiClient();
            new TicketRefundAppealServlet().service(post.request(), post.response());
            assertEquals(HttpServletResponse.SC_CONFLICT, post.status, ticket);
            assertTrue(post.body.toString().contains("REFUND_APPEAL_NOT_ELIGIBLE"), ticket);
            assertEquals(0, queryInt("SELECT COUNT(*) FROM UserAppeals WHERE TicketCode=?", ticket), ticket);

            Exchange browser = refundPost(member(memberA, CINEMA_A), ticket);
            new TicketRefundAppealServlet().service(browser.request(), browser.response());
            assertEquals(HttpServletResponse.SC_FOUND, browser.status, ticket);
            assertEquals(0, queryInt("SELECT COUNT(*) FROM UserAppeals WHERE TicketCode=?", ticket), ticket);
        }

        Exchange get = new Exchange(member(memberA, CINEMA_A), "GET", "/ticket-refund-appeal",
                Map.of("ticketCode", PREFIX + "FUTURE")).asApiClient();
        new TicketRefundAppealServlet().service(get.request(), get.response());
        assertEquals(HttpServletResponse.SC_CONFLICT, get.status);
        assertFalse(get.forwarded);
        assertEquals(0, queryInt("SELECT COUNT(*) FROM AdminNotifications WHERE Title LIKE ?", "%" + PREFIX + "%"));
        assertEquals(future, queryInt("SELECT Id FROM Orders WHERE TicketCode=?", PREFIX + "FUTURE"));
    }

    @Test
    @DisplayName("two concurrent submissions create exactly one pending appeal and one notification")
    void concurrentDuplicateSubmissionIsIdempotent() throws Exception {
        String ticket = PREFIX + "CONCURRENT";
        insertOrder(memberA, endedA, ticket, "paid", "confirmed", false);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    // Duong API: ke thua cuoc phai doc duoc 409, khong phai mot redirect
                    // trong nhu the da gui thanh cong.
                    Exchange exchange = refundPost(member(memberA, CINEMA_A), ticket).asApiClient();
                    new TicketRefundAppealServlet().service(exchange.request(), exchange.response());
                    return exchange.status;
                }));
            }
            ready.await();
            start.countDown();
            List<Integer> statuses = results.stream().map(this::get).sorted().toList();

            assertEquals(List.of(HttpServletResponse.SC_FOUND, HttpServletResponse.SC_CONFLICT), statuses);
            assertEquals(1, queryInt("SELECT COUNT(*) FROM UserAppeals WHERE TicketCode=? AND Status='pending'", ticket));
            assertEquals(1, queryInt("""
                    SELECT COUNT(*) FROM AdminNotifications n
                    JOIN UserAppeals a ON n.TargetType='UserAppeal' AND n.TargetId=CONVERT(VARCHAR(20),a.Id)
                    WHERE a.TicketCode=?
                    """, ticket));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("manager queue filters account/refund appeals in SQL and foreign POST is 403 without mutation")
    void managerQueueAndDecisionAreTenantScoped() throws Exception {
        int orderA = insertOrder(memberA, endedA, PREFIX + "TENANT-A", "paid", "confirmed", false);
        int orderB = insertOrder(memberB, endedB, PREFIX + "TENANT-B", "paid", "confirmed", false);
        int refundA = insertAppeal(memberA, PREFIX + "TENANT-A");
        int refundB = insertAppeal(memberB, PREFIX + "TENANT-B");
        int accountA = insertAppeal(memberA, null);
        int accountB = insertAppeal(memberB, null);
        int staffA = insertUser("staff-a", "staff", CINEMA_A, true, 0, BigDecimal.ZERO);
        int staffAccountA = insertAppeal(staffA, null);

        Exchange managerGet = new Exchange(manager(4, CINEMA_A), "GET", "/admin/appeals", Map.of());
        new ManagerPortalServlet().service(managerGet.request(), managerGet.response());
        @SuppressWarnings("unchecked")
        List<UserAppeal> visible = (List<UserAppeal>) managerGet.attributes.get("appeals");

        assertTrue(visible.stream().anyMatch(item -> item.getId() == refundA));
        assertTrue(visible.stream().anyMatch(item -> item.getId() == staffAccountA));
        assertFalse(visible.stream().anyMatch(item -> item.getId() == accountA));
        assertFalse(visible.stream().anyMatch(item -> item.getId() == refundB));
        assertFalse(visible.stream().anyMatch(item -> item.getId() == accountB));
        assertTrue(visible.stream().noneMatch(item -> (PREFIX + "TENANT-B").equals(item.getTicketCode())));

        Exchange forged = appealDecision(manager(4, CINEMA_A), refundB, true, "forged");
        new ManagerPortalServlet().service(forged.request(), forged.response());
        assertEquals(HttpServletResponse.SC_FORBIDDEN, forged.status);
        assertEquals("pending", queryString("SELECT Status FROM UserAppeals WHERE Id=?", refundB));
        assertEquals("paid", queryString("SELECT PaymentStatus FROM Orders WHERE Id=?", orderB));
        assertEquals("paid", queryString("SELECT PaymentStatus FROM Orders WHERE Id=?", orderA));

        Exchange adminGet = new Exchange(admin(), "GET", "/admin/appeals", Map.of());
        new ManagerPortalServlet().service(adminGet.request(), adminGet.response());
        @SuppressWarnings("unchecked")
        List<UserAppeal> global = (List<UserAppeal>) adminGet.attributes.get("appeals");
        assertTrue(global.stream().map(UserAppeal::getId)
                .collect(java.util.stream.Collectors.toSet())
                .containsAll(List.of(refundA, refundB, accountA, accountB, staffAccountA)));
    }

    @Test
    @DisplayName("refund approve/reject updates appeal and canonical order workflow, never account lock")
    void refundDecisionUsesCanonicalWorkflowAndReplayIsConflict() throws Exception {
        String approvedTicket = PREFIX + "APPROVE";
        int approvedOrder = insertOrder(memberA, endedA, approvedTicket, "paid", "confirmed", true);
        int approvedAppeal = insertAppeal(memberA, approvedTicket);

        AppealResolutionResult route = adminService.resolveAppeal(
                approvedAppeal, true, "Duyet hoan toan bo", admin());
        assertTrue(route.requiresRefundWorkflow());
        assertEquals(approvedOrder, route.orderId());
        assertEquals("pending", queryString("SELECT Status FROM UserAppeals WHERE Id=?", approvedAppeal));

        Exchange transfer = appealDecision(admin(), approvedAppeal, true, "must-route");
        new ManagerPortalServlet().service(transfer.request(), transfer.response());
        assertEquals(HttpServletResponse.SC_SEE_OTHER, transfer.status);
        assertTrue(transfer.headers.get("Location").contains("/admin/orders?tab=refund&ticketCode="));
        assertTrue(transfer.headers.get("Location").contains(approvedTicket));
        assertEquals("pending", queryString("SELECT Status FROM UserAppeals WHERE Id=?", approvedAppeal));

        adminService.refundOrder(approvedOrder, new BigDecimal("100000"),
                "Duyet khieu nai ve suat da ket thuc", admin(), true);

        assertEquals("approved", queryString("SELECT Status FROM UserAppeals WHERE Id=?", approvedAppeal));
        assertEquals("refunded", queryString("SELECT PaymentStatus FROM Orders WHERE Id=?", approvedOrder));
        assertEquals("cancelled", queryString("SELECT OrderStatus FROM Orders WHERE Id=?", approvedOrder));
        assertEquals(0, new BigDecimal("100000").compareTo(
                queryDecimal("SELECT RefundAmount FROM Orders WHERE Id=?", approvedOrder)));
        assertEquals(1, queryInt("SELECT COUNT(*) FROM RefundTransactions WHERE OrderId=?", approvedOrder));
        assertEquals("available", queryString("""
                SELECT ss.Status FROM ShowtimeSeats ss
                JOIN OrderSeats os ON os.ShowtimeSeatId=ss.Id WHERE os.OrderId=?
                """, approvedOrder));
        assertEquals(0, queryInt("SELECT COUNT(*) FROM PromotionUsage WHERE OrderId=?", approvedOrder));
        assertEquals(0, queryInt("""
                SELECT p.UsedCount FROM Promotions p JOIN Orders o ON o.PromotionId=p.Id WHERE o.Id=?
                """, approvedOrder));
        assertEquals(400, queryInt("SELECT LoyaltyPoints FROM Users WHERE Id=?", memberA));
        assertEquals(0, BigDecimal.ZERO.compareTo(queryDecimal("SELECT TotalSpent FROM Users WHERE Id=?", memberA)));
        assertEquals(1, queryInt("SELECT COUNT(*) FROM PointTransactions WHERE UserId=? AND Type='REFUND_DEDUCT'", memberA));
        assertEquals(1, queryInt("SELECT CAST(IsLocked AS INT) FROM Users WHERE Id=?", memberA),
                "refund appeal approval must never unlock an account");
        assertEquals(1, queryInt("SELECT COUNT(*) FROM Invoices WHERE OrderId=? AND InvoiceType='refund'", approvedOrder));

        BookingException replay = assertThrows(BookingException.class,
                () -> adminService.refundOrder(
                        approvedOrder, new BigDecimal("100000"), "replay", admin(), true));
        assertEquals(HttpServletResponse.SC_CONFLICT, replay.getStatusCode());
        assertEquals(1, queryInt("SELECT COUNT(*) FROM RefundTransactions WHERE OrderId=?", approvedOrder));

        String rejectedTicket = PREFIX + "REJECT";
        int rejectedOrder = insertOrder(memberB, endedB, rejectedTicket, "paid", "confirmed", false);
        int rejectedAppeal = insertAppeal(memberB, rejectedTicket);
        adminService.rejectRefund(
                rejectedOrder, "Khong du dieu kien ho tro", manager(managerB, CINEMA_B));

        assertEquals("rejected", queryString("SELECT Status FROM UserAppeals WHERE Id=?", rejectedAppeal));
        assertEquals("paid", queryString("SELECT PaymentStatus FROM Orders WHERE Id=?", rejectedOrder));
        assertNotNull(queryString("SELECT CONVERT(VARCHAR(30),RefundRejectedAt,126) FROM Orders WHERE Id=?", rejectedOrder));
        assertEquals("Khong du dieu kien ho tro",
                queryString("SELECT RefundRejectReason FROM Orders WHERE Id=?", rejectedOrder));
        assertEquals(1, queryInt("SELECT CAST(IsLocked AS INT) FROM Users WHERE Id=?", memberB));
    }

    @Test
    @DisplayName("account appeal approval unlocks only account and replay returns 409")
    void accountAppealRemainsUnlockOnly() throws Exception {
        int unrelatedOrder = insertOrder(memberA, endedA, PREFIX + "ACCOUNT-ORDER", "paid", "confirmed", false);
        int accountAppeal = insertAppeal(memberA, null);

        adminService.resolveAppeal(accountAppeal, true, "Chap nhan mo khoa", admin());

        assertEquals("approved", queryString("SELECT Status FROM UserAppeals WHERE Id=?", accountAppeal));
        assertEquals(0, queryInt("SELECT CAST(IsLocked AS INT) FROM Users WHERE Id=?", memberA));
        assertEquals("paid", queryString("SELECT PaymentStatus FROM Orders WHERE Id=?", unrelatedOrder));
        assertEquals(0, queryInt("SELECT COUNT(*) FROM RefundTransactions WHERE OrderId=?", unrelatedOrder));
        BookingException replay = assertThrows(BookingException.class,
                () -> adminService.resolveAppeal(accountAppeal, true, "replay", admin()));
        assertEquals(HttpServletResponse.SC_CONFLICT, replay.getStatusCode());
    }

    @Test
    @DisplayName("refund core failure rolls back order, loyalty, seat and appeal status together")
    void refundFailureRollsBackAppealAndAllOrderEffects() throws Exception {
        String ticket = PREFIX + "ROLLBACK";
        int orderId = insertOrder(memberA, endedA, ticket, "paid", "confirmed", true);
        int appealId = insertAppeal(memberA, ticket);
        execute("""
                CREATE TRIGGER %s ON RefundTransactions AFTER INSERT AS
                BEGIN
                    THROW 51991, 'APRF injected refund failure', 1;
                END
                """.formatted(FAIL_TRIGGER));
        try {
            BookingException failure = assertThrows(BookingException.class,
                    () -> adminService.refundOrder(
                            orderId, new BigDecimal("100000"), "rollback-test", admin(), true));
            assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, failure.getStatusCode());
        } finally {
            execute("IF OBJECT_ID('TR_APRF_FAIL_REFUND', 'TR') IS NOT NULL "
                    + "DROP TRIGGER TR_APRF_FAIL_REFUND");
        }

        assertEquals("pending", queryString("SELECT Status FROM UserAppeals WHERE Id=?", appealId));
        assertEquals("paid", queryString("SELECT PaymentStatus FROM Orders WHERE Id=?", orderId));
        assertEquals("confirmed", queryString("SELECT OrderStatus FROM Orders WHERE Id=?", orderId));
        assertEquals("booked", queryString("""
                SELECT ss.Status FROM ShowtimeSeats ss
                JOIN OrderSeats os ON os.ShowtimeSeatId=ss.Id WHERE os.OrderId=?
                """, orderId));
        assertEquals(1, queryInt("SELECT COUNT(*) FROM PromotionUsage WHERE OrderId=?", orderId));
        assertEquals(500, queryInt("SELECT LoyaltyPoints FROM Users WHERE Id=?", memberA));
        assertEquals(0, queryInt("SELECT COUNT(*) FROM RefundTransactions WHERE OrderId=?", orderId));
    }

    private int insertShowtime(int cinemaId, int roomId, int startMinutes, int endMinutes) throws SQLException {
        return insert("""
                INSERT INTO Showtimes
                    (FilmId,CinemaId,RoomId,StartTime,EndTime,BasePrice,Format,Version,Language)
                VALUES (?,?,?,DATEADD(MINUTE,?,GETDATE()),DATEADD(MINUTE,?,GETDATE()),
                        100000,'2D','Subtitle','Vietnamese')
                """, filmId, cinemaId, roomId, startMinutes, endMinutes);
    }

    private int insertOrder(int userId, int showtimeId, String ticket, String paymentStatus,
            String orderStatus, boolean withCanonicalEffects) throws SQLException {
        Integer promotionId = null;
        if (withCanonicalEffects) {
            promotionId = insert("""
                    INSERT INTO Promotions
                        (Code,Description,DiscountPercent,MaxDiscount,StartDate,EndDate,
                         UsageLimit,UsedCount,Status,VoucherType,PerUserLimit)
                    VALUES (?,? ,10,10000,DATEADD(DAY,-1,CAST(GETDATE() AS DATE)),
                            DATEADD(DAY,30,CAST(GETDATE() AS DATE)),100,1,'active','PUBLIC',1)
                    """, ticket + "-PROMO", PREFIX + "promotion");
        }
        int orderId = insert("""
                INSERT INTO Orders
                    (UserId,ShowtimeId,PromotionId,SeatSubtotal,ComboSubtotal,DiscountAmount,
                     TotalAmount,TicketCode,TicketQrUrl,PaymentMethod,PaymentStatus,TransactionId,OrderStatus)
                VALUES (?,?,?,100000,0,0,100000,?,?,'card',?,? ,?)
                """, userId, showtimeId, promotionId, ticket, "/tickets/qr/" + ticket,
                paymentStatus, PREFIX + "TX-" + ticket, orderStatus);
        if (withCanonicalEffects) {
            int seatId = queryInt("SELECT TOP 1 Id FROM Seats WHERE RoomId=? ORDER BY Id",
                    queryInt("SELECT RoomId FROM Showtimes WHERE Id=?", showtimeId));
            int showtimeSeatId = insert("""
                    INSERT INTO ShowtimeSeats (ShowtimeId,SeatId,Status,ExtraFee)
                    VALUES (?,?,'booked',0)
                    """, showtimeId, seatId);
            execute("""
                    INSERT INTO OrderSeats (OrderId,ShowtimeSeatId,SeatKey,SeatType,UnitPrice)
                    SELECT ?,ss.Id,s.SeatKey,s.SeatType,100000
                    FROM ShowtimeSeats ss JOIN Seats s ON s.Id=ss.SeatId WHERE ss.Id=?
                    """, orderId, showtimeSeatId);
            execute("INSERT INTO PromotionUsage (PromotionId,UserId,OrderId) VALUES (?,?,?)",
                    promotionId, userId, orderId);
        }
        return orderId;
    }

    private int insertAppeal(int userId, String ticketCode) throws SQLException {
        if (ticketCode == null) {
            return insert("""
                    INSERT INTO UserAppeals
                        (UserId,Email,Reason,TicketCode,BankAccountInfo,Status,AppealType,OrderId,CinemaId)
                    SELECT Id,Email,?,NULL,'TEST-ONLY','pending','account',NULL,
                           CASE WHEN Role IN ('manager','staff') THEN CinemaId ELSE NULL END
                    FROM Users WHERE Id=?
                    """, PREFIX + "appeal", userId);
        }
        return insert("""
                INSERT INTO UserAppeals
                    (UserId,Email,Reason,TicketCode,BankAccountInfo,Status,AppealType,OrderId,CinemaId)
                SELECT u.Id,u.Email,?,o.TicketCode,'TEST-ONLY','pending','refund',o.Id,s.CinemaId
                FROM Users u JOIN Orders o ON o.UserId=u.Id AND o.TicketCode=?
                JOIN Showtimes s ON s.Id=o.ShowtimeId
                WHERE u.Id=?
                """, PREFIX + "appeal", ticketCode, userId);
    }

    private int insertUser(String suffix, String role, int cinemaId, boolean locked,
            int loyaltyPoints, BigDecimal totalSpent) throws SQLException {
        return insert("""
                INSERT INTO Users
                    (FullName,Email,PasswordHash,Role,CinemaId,IsLocked,LockReason,WarningCount,
                     LoyaltyPoints,TotalSpent,MembershipTier)
                VALUES (?,?,?,?,?,?,?,2,?,?, 'BRONZE')
                """, PREFIX + suffix, "aprf-" + suffix + "@test.local", PASSWORD_HASH, role,
                cinemaId, locked, locked ? PREFIX + "locked" : null, loyaltyPoints, totalSpent);
    }

    private Exchange refundPost(User actor, String ticket) {
        return new Exchange(actor, "POST", "/ticket-refund-appeal", Map.of(
                "ticketCode", ticket,
                "reason", PREFIX + "missed showtime",
                "bankAccountInfo", PREFIX + "BANK",
                "contactPhone", "0900000000"));
    }

    private Exchange appealDecision(User actor, int appealId, boolean approve, String response) {
        return new Exchange(actor, "POST", "/admin/appeals", Map.of(
                "id", String.valueOf(appealId),
                "action", approve ? "approve" : "reject",
                "adminResponse", response));
    }

    private Integer get(Future<Integer> result) {
        try {
            return result.get();
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private void cleanupFixture() throws SQLException {
        assertTestDatabase();
        execute("IF OBJECT_ID('TR_APRF_FAIL_REFUND', 'TR') IS NOT NULL "
                + "DROP TRIGGER TR_APRF_FAIL_REFUND");
        execute("""
                DELETE FROM NotificationRecipients WHERE SourceType='admin' AND NotificationId IN (
                  SELECT Id FROM AdminNotifications WHERE Title LIKE ? OR Message LIKE ?
                );
                DELETE FROM AdminNotifications WHERE Title LIKE ? OR Message LIKE ?;
                DELETE FROM AuditLogs WHERE
                  (TargetType='Order' AND TRY_CONVERT(INT,TargetId) IN (
                    SELECT Id FROM Orders WHERE TicketCode LIKE ?
                  )) OR
                  (TargetType='UserAppeal' AND TRY_CONVERT(INT,TargetId) IN (
                    SELECT Id FROM UserAppeals WHERE TicketCode LIKE ? OR UserId IN (
                      SELECT Id FROM Users WHERE Email LIKE ?
                    )
                  )) OR
                  (TargetType='User' AND TRY_CONVERT(INT,TargetId) IN (
                    SELECT Id FROM Users WHERE Email LIKE ?
                  ));
                DELETE FROM Invoices WHERE OrderId IN (SELECT Id FROM Orders WHERE TicketCode LIKE ?);
                DELETE FROM RefundTransactions WHERE OrderId IN (SELECT Id FROM Orders WHERE TicketCode LIKE ?);
                DELETE FROM PromotionUsage WHERE OrderId IN (SELECT Id FROM Orders WHERE TicketCode LIKE ?);
                DELETE FROM UserVouchers WHERE UsedOrderId IN (SELECT Id FROM Orders WHERE TicketCode LIKE ?);
                DELETE FROM PointTransactions WHERE UserId IN (SELECT Id FROM Users WHERE Email LIKE ?);
                DELETE FROM UserAppeals WHERE TicketCode LIKE ? OR UserId IN (
                  SELECT Id FROM Users WHERE Email LIKE ?
                );
                DELETE FROM OrderSeats WHERE OrderId IN (SELECT Id FROM Orders WHERE TicketCode LIKE ?);
                DELETE FROM OrderComboFoods WHERE OrderId IN (SELECT Id FROM Orders WHERE TicketCode LIKE ?);
                DELETE FROM Orders WHERE TicketCode LIKE ?;
                DELETE FROM ShowtimeSeats WHERE ShowtimeId IN (
                  SELECT Id FROM Showtimes WHERE FilmId IN (SELECT Id FROM Films WHERE Title LIKE ?)
                );
                DELETE FROM Showtimes WHERE FilmId IN (SELECT Id FROM Films WHERE Title LIKE ?);
                DELETE FROM CinemaFilms WHERE FilmId IN (SELECT Id FROM Films WHERE Title LIKE ?);
                DELETE FROM Promotions WHERE Code LIKE ?;
                DELETE FROM Films WHERE Title LIKE ?;
                DELETE FROM Users WHERE Email LIKE ?;
                """,
                "%" + PREFIX + "%", "%" + PREFIX + "%",
                "%" + PREFIX + "%", "%" + PREFIX + "%",
                PREFIX + "%", PREFIX + "%", EMAIL_LIKE, EMAIL_LIKE,
                PREFIX + "%", PREFIX + "%", PREFIX + "%", PREFIX + "%",
                EMAIL_LIKE, PREFIX + "%", EMAIL_LIKE,
                PREFIX + "%", PREFIX + "%", PREFIX + "%",
                PREFIX + "%", PREFIX + "%", PREFIX + "%", PREFIX + "%",
                PREFIX + "%", EMAIL_LIKE);
    }

    private int insert(String sql, Object... values) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
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

    private int queryInt(String sql, Object... values) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private String queryString(String sql, Object... values) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }

    private BigDecimal queryDecimal(String sql, Object... values) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getBigDecimal(1);
            }
        }
    }

    private void bind(PreparedStatement statement, Object... values) throws SQLException {
        for (int index = 0; index < values.length; index++) {
            statement.setObject(index + 1, values[index]);
        }
    }

    private void assertTestDatabase() throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT DB_NAME()");
             ResultSet result = statement.executeQuery()) {
            assertTrue(result.next());
            assertEquals(System.getProperty("cinebook.it.database", "CineBookIT_REQUIRED"), result.getString(1));
        }
    }

    private static User member(int id, int cinemaId) {
        return actor(id, AppConstants.ROLE_MEMBER, cinemaId);
    }

    private static User manager(int id, int cinemaId) {
        return actor(id, AppConstants.ROLE_MANAGER, cinemaId);
    }

    private static User admin() {
        return actor(ADMIN_ID, AppConstants.ROLE_ADMIN, null);
    }

    private static User actor(int id, String role, Integer cinemaId) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setCinemaId(cinemaId);
        user.setEmail("aprf-session@test.local");
        user.setFullName("APRF session");
        user.setPhone("0900000000");
        return user;
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == long.class) {
            return 0L;
        }
        return 0;
    }

    private static final class Exchange {
        private final String method;
        private final String servletPath;
        private final Map<String, String> parameters;
        private final Map<String, Object> session = new HashMap<>();
        private final Map<String, Object> attributes = new HashMap<>();
        private final Map<String, String> headers = new HashMap<>();
        private final Map<String, String> requestHeaders = new HashMap<>();
        private final java.io.StringWriter body = new java.io.StringWriter();
        private int status = HttpServletResponse.SC_OK;
        private boolean forwarded;

        private Exchange(User actor, String method, String servletPath, Map<String, String> parameters) {
            this.method = method;
            this.servletPath = servletPath;
            this.parameters = parameters;
            session.put(AppConstants.SESSION_USER, actor);
        }

        /** Danh dau luot goi nay la client API, khong phai dieu huong cua trinh duyet. */
        private Exchange asApiClient() {
            requestHeaders.put("Accept", "application/json");
            return this;
        }

        private HttpServletRequest request() {
            HttpSession httpSession = session();
            RequestDispatcher dispatcher = dispatcher();
            InvocationHandler handler = (proxy, invoked, args) -> switch (invoked.getName()) {
                case "getMethod" -> method;
                case "getServletPath" -> servletPath;
                case "getContextPath" -> "/Website-ban-ve-xem-phim";
                case "getParameter" -> parameters.get((String) args[0]);
                case "getHeader" -> requestHeaders.get((String) args[0]);
                case "getSession" -> httpSession;
                case "setAttribute" -> {
                    attributes.put((String) args[0], args[1]);
                    yield null;
                }
                case "getAttribute" -> attributes.get((String) args[0]);
                case "getRequestDispatcher" -> dispatcher;
                case "getRemoteAddr" -> "127.0.0.1";
                default -> defaultValue(invoked.getReturnType());
            };
            return (HttpServletRequest) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[] {HttpServletRequest.class}, handler);
        }

        private HttpSession session() {
            InvocationHandler handler = (proxy, invoked, args) -> switch (invoked.getName()) {
                case "getAttribute" -> session.get((String) args[0]);
                case "setAttribute" -> {
                    session.put((String) args[0], args[1]);
                    yield null;
                }
                case "removeAttribute" -> {
                    session.remove((String) args[0]);
                    yield null;
                }
                default -> defaultValue(invoked.getReturnType());
            };
            return (HttpSession) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[] {HttpSession.class}, handler);
        }

        private RequestDispatcher dispatcher() {
            return (RequestDispatcher) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[] {RequestDispatcher.class},
                    (proxy, invoked, args) -> {
                        if ("forward".equals(invoked.getName())) {
                            forwarded = true;
                        }
                        return null;
                    });
        }

        private HttpServletResponse response() {
            InvocationHandler handler = (proxy, invoked, args) -> {
                switch (invoked.getName()) {
                    case "sendError", "setStatus" -> status = (Integer) args[0];
                    case "sendRedirect" -> status = HttpServletResponse.SC_FOUND;
                    case "setHeader" -> headers.put((String) args[0], (String) args[1]);
                    case "getWriter" -> {
                        return new java.io.PrintWriter(body);
                    }
                    case "getStatus" -> {
                        return status;
                    }
                    default -> {
                        return defaultValue(invoked.getReturnType());
                    }
                }
                return null;
            };
            return (HttpServletResponse) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[] {HttpServletResponse.class}, handler);
        }
    }
}
