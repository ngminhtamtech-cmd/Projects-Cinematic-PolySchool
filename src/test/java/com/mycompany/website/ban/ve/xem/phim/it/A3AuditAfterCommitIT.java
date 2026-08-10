package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.model.ShowtimeSeat;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
import com.mycompany.website.ban.ve.xem.phim.service.BookingService;
import com.mycompany.website.ban.ve.xem.phim.util.RequestContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A.3 (BUG-09) — mot dong audit hong khong duoc lam hong mot giao dich DA CHOT.
 *
 * <p><b>Van de.</b> {@code RequestContext.set} cat {@code ipAddress} bang
 * {@code MAX_USER_AGENT_LENGTH = 400}, trong khi {@code AuditLogs.IpAddress} la
 * {@code NVARCHAR(64)}. Mot header {@code X-Forwarded-For} dai — client tu gui duoc — lam
 * INSERT audit loi truncation, {@code logAction} nem {@code BookingException(500)}.</p>
 *
 * <p>Cho nem do nam <b>ngay sau</b> {@code connection.commit()} trong {@code refundOrder}: tien
 * da hoan that, {@code rollback()} la no-op, quan ly nhan 500 tuong la that bai, va
 * {@code issueRefundAdjustment} phia sau khong bao gio chay — thieu han hoa don dieu chinh.</p>
 *
 * <p>Ke tan cong khong can quyen gi: chi can dat header dai roi doi quan ly bam duyet hoan tien.</p>
 */
@Tag("it")
@DisplayName("A.3 — audit hong khong duoc lam hong giao dich da commit")
public class A3AuditAfterCommitIT {

    private static final String FILM_TITLE = "A3-AUDIT-COMMIT Enterprise";
    private static final int CINEMA_ID = 1;
    private static final int ROOM_ID = 1;
    private static final int MEMBER_ID = 1;

    /** Dai hon 64 (gioi han that cua cot) nhung ngan hon 400 (gioi han dang duoc ap). */
    private static final String LONG_FORWARDED_FOR = "203.0.113.".repeat(10);

    static {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    private final BookingService bookingService = new BookingService();
    private final AdminService adminService = new AdminService();
    private int showtimeId;

    @BeforeAll
    public static void setUpTestDb() {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
        DBConnection.shutdown();
    }

    @AfterAll
    public static void tearDownAll() {
        DBConnection.shutdown();
    }

    @BeforeEach
    public void createFixture() throws SQLException {
        assertTestDatabase();
        cleanupFixtures();
        int filmId = insert("""
                INSERT INTO Films (Title,ReleaseDate,EndDate,DurationMinutes,Status)
                VALUES (?,DATEADD(DAY,-1,CAST(GETDATE() AS DATE)),
                        DATEADD(DAY,30,CAST(GETDATE() AS DATE)),120,'showing')
                """, FILM_TITLE);
        execute("INSERT INTO CinemaFilms (CinemaId,FilmId) VALUES (?,?)", CINEMA_ID, filmId);
        showtimeId = insert("""
                INSERT INTO Showtimes
                    (FilmId,CinemaId,RoomId,StartTime,EndTime,BasePrice,Format,Version,Language)
                VALUES (?,?,?,DATEADD(MINUTE,90,GETDATE()),DATEADD(MINUTE,210,GETDATE()),
                        90000,'2D','Subtitle','Vietnamese')
                """, filmId, CINEMA_ID, ROOM_ID);
        execute("""
                INSERT INTO ShowtimeSeats (ShowtimeId,SeatId,Status,ExtraFee)
                SELECT ?,Id,'available',0 FROM Seats WHERE RoomId=?
                """, showtimeId, ROOM_ID);
    }

    @AfterEach
    public void cleanup() throws SQLException {
        RequestContext.clear();
        execute("DELETE FROM AuditLogs WHERE TargetType='Order' AND TargetId IN ("
                + " SELECT CAST(o.Id AS NVARCHAR(50)) FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId"
                + " JOIN Films f ON f.Id=s.FilmId WHERE f.Title=?)", FILM_TITLE);
        cleanupFixtures();
    }

    @Test
    @DisplayName("ipAddress phai duoc cat theo gioi han that cua cot (64), khong phai cua User-Agent")
    public void ipAddressIsTrimmedToTheColumnWidth() {
        RequestContext.set(LONG_FORWARDED_FOR, "Mozilla/5.0");

        String stored = RequestContext.ipAddress();

        assertNotNull(stored);
        assertTrue(stored.length() <= 64,
                "AuditLogs.IpAddress la NVARCHAR(64); dai " + stored.length()
                + " ky tu se lam INSERT audit loi truncation");
    }

    @Test
    @DisplayName("X-Forwarded-For dai -> hoan tien VAN thanh cong, audit ghi IP da cat")
    public void longForwardedForDoesNotBreakACommittedRefund() throws SQLException {
        OrderRecord paid = payOneSeat();
        RequestContext.set(LONG_FORWARDED_FOR, "Mozilla/5.0 (A3 regression)");

        assertDoesNotThrow(
                () -> adminService.refundOrder(paid.getId(), paid.getTotalAmount(), "Khach doi hoan", admin()),
                "Tien da chuyen roi ma quan ly nhan 500 la hong nang nhat cua BUG-09");

        assertEquals("refunded", paymentStatusOf(paid.getId()), "Don phai thuc su duoc hoan");
        assertEquals(1, scalar("""
                SELECT COUNT(*) FROM AuditLogs
                WHERE Action='REFUND_ORDER' AND TargetType='Order' AND TargetId=?
                  AND IpAddress IS NOT NULL AND LEN(IpAddress) <= 64
                """, String.valueOf(paid.getId())),
                "Phai co dong audit REFUND_ORDER voi IP da cat, khong phai NULL va khong phai loi");
    }

    @Test
    @DisplayName("X-Forwarded-For dai -> hoa don dieu chinh VAN duoc sinh ra")
    public void refundAdjustmentInvoiceIsStillIssued() throws SQLException {
        OrderRecord paid = payOneSeat();
        RequestContext.set(LONG_FORWARDED_FOR, "Mozilla/5.0 (A3 regression)");

        adminService.refundOrder(paid.getId(), paid.getTotalAmount(), "Khach doi hoan", admin());

        assertEquals(1, scalar("SELECT COUNT(*) FROM Invoices WHERE OrderId=? AND InvoiceType='refund'",
                paid.getId()),
                "issueRefundAdjustment nam SAU cho nem cu, nen no bi bo qua im lang — thieu hoa don dieu chinh");
    }

    @Test
    @DisplayName("X-Forwarded-For dai -> huy don VAN thanh cong")
    public void longForwardedForDoesNotBreakACommittedCancel() throws SQLException {
        OrderRecord draft = draftOneSeat();
        RequestContext.set(LONG_FORWARDED_FOR, "Mozilla/5.0 (A3 regression)");

        assertDoesNotThrow(() -> adminService.cancelOrder(draft.getId(), admin(), "Khach doi lich"));

        assertEquals("cancelled", orderStatusOf(draft.getId()));
    }

    // ------------------------------------------------------------------ helpers

    private OrderRecord payOneSeat() {
        OrderRecord draft = draftOneSeat();
        return bookingService.payOrder(MEMBER_ID, draft.getId(), Map.of(), null, "card");
    }

    private OrderRecord draftOneSeat() {
        return bookingService.createDraftOrder(
                MEMBER_ID, showtimeId, List.of(oneBookableSeat()), Map.of(), null, "card");
    }

    private int oneBookableSeat() {
        return bookingService.getSeatMap(showtimeId).stream()
                .filter(seat -> seat.isAvailableFor(MEMBER_ID))
                .filter(seat -> !"couple".equalsIgnoreCase(seat.getSeatType()))
                .map(ShowtimeSeat::getId)
                .findFirst()
                .orElseThrow();
    }

    private static User admin() {
        User user = new User();
        user.setId(5);
        user.setRole("admin");
        user.setCinemaId(null);
        return user;
    }

    private String paymentStatusOf(int orderId) throws SQLException {
        return text("SELECT PaymentStatus FROM Orders WHERE Id=?", orderId);
    }

    private String orderStatusOf(int orderId) throws SQLException {
        return text("SELECT OrderStatus FROM Orders WHERE Id=?", orderId);
    }

    private String text(String sql, Object... values) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, values);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getString(1);
            }
        }
    }

    private int scalar(String sql, Object... values) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, values);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private void cleanupFixtures() throws SQLException {
        execute("""
                DELETE FROM Invoices WHERE OrderId IN (
                  SELECT o.Id FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId
                  JOIN Films f ON f.Id=s.FilmId WHERE f.Title=?);
                DELETE FROM PromotionUsage WHERE OrderId IN (
                  SELECT o.Id FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId
                  JOIN Films f ON f.Id=s.FilmId WHERE f.Title=?);
                DELETE FROM RefundTransactions WHERE OrderId IN (
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
                """, FILM_TITLE, FILM_TITLE, FILM_TITLE, FILM_TITLE, FILM_TITLE,
                FILM_TITLE, FILM_TITLE, FILM_TITLE, FILM_TITLE);
    }

    private int insert(String sql, Object... values) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            bind(ps, values);
            assertEquals(1, ps.executeUpdate());
            try (ResultSet keys = ps.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getInt(1);
            }
        }
    }

    private void execute(String sql, Object... values) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, values);
            ps.executeUpdate();
        }
    }

    private void bind(PreparedStatement ps, Object... values) throws SQLException {
        for (int index = 0; index < values.length; index++) {
            ps.setObject(index + 1, values[index]);
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
