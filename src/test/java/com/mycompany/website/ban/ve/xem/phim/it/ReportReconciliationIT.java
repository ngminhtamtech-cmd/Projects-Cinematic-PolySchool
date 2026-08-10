package com.mycompany.website.ban.ve.xem.phim.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.ReportSummaryDto;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
import com.mycompany.website.ban.ve.xem.phim.util.BusinessClock;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Doi soat output that cua AdminService voi cac cong thuc SQL doc lap.
 */
@Tag("it")
@DisplayName("Report reconciliation against independent SQL")
public class ReportReconciliationIT {
    private static final String KEY_PREFIX = "FLOW-REPORT-";
    /** Tien to SeatKey cua ghe do chinh test nay dung them — dung de don sach ve sau. */
    private static final String SEAT_MARKER = "FLOWRPT-";
    private static final int USER_ID = 1;
    private static final int SHOWTIME_ID = 3;
    private static final int CINEMA_ID = 1;

    static {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    private final AdminService adminService = new AdminService();

    @BeforeAll
    static void configure() {
        DBConnection.shutdown();
    }

    @BeforeEach
    @AfterEach
    void cleanup() throws SQLException {
        assertTestDatabase();
        execute("""
                DELETE FROM OrderSeats
                WHERE OrderId IN (SELECT Id FROM Orders WHERE IdempotencyKey LIKE 'FLOW-REPORT-%');
                DELETE FROM RefundTransactions
                WHERE OrderId IN (SELECT Id FROM Orders WHERE IdempotencyKey LIKE 'FLOW-REPORT-%');
                DELETE FROM Orders WHERE IdempotencyKey LIKE 'FLOW-REPORT-%';
                DELETE FROM ShowtimeSeats
                WHERE SeatId IN (SELECT Id FROM Seats WHERE SeatKey LIKE 'FLOWRPT-%');
                DELETE FROM Seats WHERE SeatKey LIKE 'FLOWRPT-%';
                """);
        BusinessClock.resetForTesting();
    }

    @AfterAll
    static void shutdown() {
        DBConnection.shutdown();
    }

    @Test
    @DisplayName("FLOW-REPORT-001: paid-but-cancelled orders never inflate dashboard revenue")
    void paidCancelledOrderIsExcludedFromSummaryRevenue() throws SQLException {
        BigDecimal before = adminService.getReportSummary().getTotalRevenueCurrent();
        insertOrder(KEY_PREFIX + "CANCELLED", new BigDecimal("123456.00"),
                "paid", "cancelled", null, null);

        BigDecimal after = adminService.getReportSummary().getTotalRevenueCurrent();

        assertEquals(0, before.compareTo(after),
                "FLOW-REPORT-001 dashboard counted a paid order whose OrderStatus is cancelled. "
                + "before=" + before + ", after=" + after);
    }

    @Test
    @DisplayName("FLOW-REPORT-002: partial refund contributes total minus actual refund amount")
    void partialRefundUsesNetAmountInsteadOfDroppingWholeOrder() throws SQLException {
        BigDecimal before = adminService.getReportSummary().getTotalRevenueCurrent();
        insertOrder(KEY_PREFIX + "PARTIAL", new BigDecimal("100000.00"),
                "refunded", "confirmed", new BigDecimal("30000.00"), "partial-refund");

        BigDecimal after = adminService.getReportSummary().getTotalRevenueCurrent();
        BigDecimal expected = before.add(new BigDecimal("70000.00"));

        assertEquals(0, expected.compareTo(after),
                "FLOW-REPORT-002 net revenue must add 100000 - 30000. "
                + "expected=" + expected + ", actual=" + after);
    }

    @Test
    @DisplayName("FLOW-REPORT-003: manager average tickets/day counts seats, not orders")
    void managerAverageTicketsPerDayCountsOrderSeats() throws SQLException {
        int orderId = insertOrder(KEY_PREFIX + "TICKETS", new BigDecimal("3100000.00"),
                "paid", "confirmed", null, null);
        int days = YearMonth.from(BusinessClock.now().toLocalDate()).lengthOfMonth();
        insertOrderSeats(orderId, days);

        User manager = actor(4, "manager", CINEMA_ID);
        ReportSummaryDto summary = adminService.getReportSummary(manager);
        int independentSoldSeats = currentMonthSoldSeatsForCinema(CINEMA_ID);
        // Ky vong duoc dung lai doc lap voi service, cung cach nhu FLOW-REPORT-002 o tren.
        // Truoc day dong nay la `independentSoldSeats / days` — dung chinh phep chia nguyen dang
        // can kiem — va fixture lai chen dung `days` ghe nen ti le ra tron 1, loi cat thap phan
        // khong bao gio lo ra. Phan so hoc gio duoc canh rieng boi
        // service/ReportAveragePerDayTest (23/31 -> 0.7); ca nay chi con giu hai dieu:
        // dem GHE chu khong phai dem DON, va ton trong pham vi cum rap.
        BigDecimal expectedAverage = BigDecimal.valueOf(independentSoldSeats)
                .divide(BigDecimal.valueOf(days), 1, java.math.RoundingMode.HALF_UP);

        assertEquals(0, expectedAverage.compareTo(summary.getAvgTicketsPerDayCurrent()),
                "FLOW-REPORT-003 manager report divides paid order count by days instead of sold seats. "
                + "soldSeats=" + independentSoldSeats + ", days=" + days
                + ", expected=" + expectedAverage
                + ", actual=" + summary.getAvgTicketsPerDayCurrent());
    }

    @Test
    @DisplayName("FLOW-REPORT-004: manager revenue obeys cinema scope while admin sees the same order globally")
    void managerAndAdminRevenueShareFormulaButRespectScope() throws SQLException {
        BigDecimal adminBefore = adminService.getReportSummary().getTotalRevenueCurrent();
        BigDecimal managerBefore = adminService.getReportSummary(actor(4, "manager", CINEMA_ID))
                .getTotalRevenueCurrent();
        insertOrder(KEY_PREFIX + "SCOPE", new BigDecimal("88000.00"),
                "paid", "confirmed", null, null);

        BigDecimal adminDelta = adminService.getReportSummary().getTotalRevenueCurrent().subtract(adminBefore);
        BigDecimal managerDelta = adminService.getReportSummary(actor(4, "manager", CINEMA_ID))
                .getTotalRevenueCurrent().subtract(managerBefore);

        assertEquals(0, new BigDecimal("88000.00").compareTo(adminDelta));
        assertEquals(0, new BigDecimal("88000.00").compareTo(managerDelta));
    }

    private int insertOrder(String idempotencyKey, BigDecimal total, String paymentStatus,
            String orderStatus, BigDecimal refundAmount, String refundReason) throws SQLException {
        String sql = """
                INSERT INTO Orders
                    (UserId,ShowtimeId,SeatSubtotal,ComboSubtotal,DiscountAmount,TotalAmount,
                     PaymentMethod,PaymentStatus,OrderStatus,PaymentProvider,IdempotencyKey,
                     RefundedAt,RefundAmount,RefundReason)
                VALUES (?,?,?,0,0,?,'card',?,?, 'simulated',?,
                        CASE WHEN ? IS NULL THEN NULL ELSE GETDATE() END,?,?)
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, USER_ID);
            ps.setInt(2, SHOWTIME_ID);
            ps.setBigDecimal(3, total);
            ps.setBigDecimal(4, total);
            ps.setString(5, paymentStatus);
            ps.setString(6, orderStatus);
            ps.setString(7, idempotencyKey);
            ps.setBigDecimal(8, refundAmount);
            ps.setBigDecimal(9, refundAmount);
            ps.setString(10, refundReason);
            assertEquals(1, ps.executeUpdate());
            try (ResultSet keys = ps.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getInt(1);
            }
        }
    }

    /**
     * N-05: {@code count} ghe cua mot don phai la {@code count} ghe KHAC NHAU.
     *
     * <p>Ban cu lay {@code MIN(Id)} roi chen {@code count} dong voi cung mot
     * {@code ShowtimeSeatId} — tuc la ban cung mot ghe vat ly nhieu lan trong mot don.
     * Khoa kep {@code (OrderId, ShowtimeSeatId)} cua production chan viec do tu dau; ban cu
     * chi chay duoc vi {@code CineBookDB_Test} con cot {@code Id IDENTITY}.</p>
     *
     * <p>Suat chieu seed chi co 4 ghe con phep do can it nhat {@code count} (28–31) ghe de
     * phep chia con y nghia, nen fixture tu dung them ghe cho phong cua suat do va don lai
     * o {@link #cleanup()}.</p>
     */
    private void insertOrderSeats(int orderId, int count) throws SQLException {
        List<Integer> showtimeSeatIds = ensureShowtimeSeats(count);
        assertEquals(count, showtimeSeatIds.size(), "fixture phai chuan bi du " + count + " ghe khac nhau");
        String sql = """
                INSERT INTO OrderSeats (OrderId,ShowtimeSeatId,SeatKey,SeatType,UnitPrice)
                VALUES (?,?,?,'standard',100000)
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int index = 0; index < count; index++) {
                ps.setInt(1, orderId);
                ps.setInt(2, showtimeSeatIds.get(index));
                ps.setString(3, "FLOW-" + (index + 1));
                ps.addBatch();
            }
            assertEquals(count, ps.executeBatch().length);
        }
    }

    /**
     * Bao dam suat {@link #SHOWTIME_ID} co it nhat {@code count} ghe, tra ve dung
     * {@code count} id dau tien. Ghe them moi mang {@code SeatKey} tien to {@link #SEAT_MARKER}
     * de {@link #cleanup()} don sach.
     */
    private List<Integer> ensureShowtimeSeats(int count) throws SQLException {
        int roomId = queryInt("SELECT RoomId FROM Showtimes WHERE Id=" + SHOWTIME_ID);
        int existing = queryInt("SELECT COUNT(*) FROM ShowtimeSeats WHERE ShowtimeId=" + SHOWTIME_ID);
        for (int index = existing; index < count; index++) {
            String seatKey = SEAT_MARKER + index;
            execute("INSERT INTO Seats (RoomId, RowLabel, SeatNumber, SeatType, SeatKey, PriceSurcharge) "
                    + "VALUES (" + roomId + ", 'Z', " + index + ", 'standard', '" + seatKey + "', 0)");
            execute("INSERT INTO ShowtimeSeats (ShowtimeId, SeatId, Status, ExtraFee) "
                    + "SELECT " + SHOWTIME_ID + ", Id, 'available', 0 FROM Seats WHERE SeatKey = '" + seatKey + "'");
        }
        List<Integer> ids = new java.util.ArrayList<>();
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT TOP (?) Id FROM ShowtimeSeats WHERE ShowtimeId=? ORDER BY Id")) {
            ps.setInt(1, count);
            ps.setInt(2, SHOWTIME_ID);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
            }
        }
        return ids;
    }

    private int currentMonthSoldSeatsForCinema(int cinemaId) throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM OrderSeats os
                JOIN Orders o ON o.Id=os.OrderId
                JOIN Showtimes s ON s.Id=o.ShowtimeId
                WHERE s.CinemaId=?
                  AND o.PaymentStatus='paid'
                  AND o.OrderStatus<>'cancelled'
                  AND o.RefundedAt IS NULL
                  AND o.CreatedAt>=? AND o.CreatedAt<?
                """;
        YearMonth month = YearMonth.from(BusinessClock.now().toLocalDate());
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, cinemaId);
            ps.setTimestamp(2, java.sql.Timestamp.valueOf(month.atDay(1).atStartOfDay()));
            ps.setTimestamp(3, java.sql.Timestamp.valueOf(month.plusMonths(1).atDay(1).atStartOfDay()));
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private User actor(int id, String role, Integer cinemaId) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setCinemaId(cinemaId);
        return user;
    }

    private int queryInt(String sql) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            return rs.getInt(1);
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
