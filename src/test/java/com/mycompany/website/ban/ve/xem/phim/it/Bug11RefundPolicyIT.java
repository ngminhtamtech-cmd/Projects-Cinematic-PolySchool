package com.mycompany.website.ban.ve.xem.phim.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.model.ShowtimeSeat;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import com.mycompany.website.ban.ve.xem.phim.service.BookingService;
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

/**
 * BUG-11 (INV-8) + BUG-17 (INV-12) — dieu kien hoan tien va tinh doi xung cua no.
 *
 * <p><b>BUG-11.</b> {@code AdminService.refundOrder} chi kiem {@code PaymentStatus='paid'} va
 * {@code 0 < refundAmount <= TotalAmount}. Cau SELECT co doc {@code OrderStatus} nhung khong dung
 * den, nen ve da check-in — khach da xem xong phim — van hoan tien duoc.</p>
 *
 * <p><b>BUG-17.</b> {@code restorePersonalVoucher} chi tra lai {@code UserVouchers}. Ma cong khai
 * thi {@code Promotions.UsedCount} khong bao gio giam va dong {@code PromotionUsage} khong bao gio
 * bi xoa: nguoi co {@code PerUserLimit} mat vinh vien mot luot dung cho mot don da duoc hoan.</p>
 */
@Tag("it")
public class Bug11RefundPolicyIT {

    private static final String FILM_TITLE = "BUG11-REFUND Enterprise";
    private static final String PROMO_CODE = "BUG17PROMO";
    private static final int CINEMA_ID = 1;
    private static final int ROOM_ID = 1;
    private static final int MEMBER_ID = 1;

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
    public static void tearDown() {
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
        execute("DELETE FROM AuditLogs WHERE Action IN ('REFUND_OVERRIDE','REFUND_ORDER')"
                + " AND TargetType='Order' AND TargetId IN ("
                + " SELECT CAST(o.Id AS NVARCHAR(50)) FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId"
                + " JOIN Films f ON f.Id=s.FilmId WHERE f.Title=?)", FILM_TITLE);
        cleanupFixtures();
    }

    @Test
    @DisplayName("BUG-11: ve da check-in (redeemed) khong duoc hoan tien")
    public void refundOfRedeemedTicketIsRejected() throws SQLException {
        OrderRecord paid = payOneSeat(null);
        execute("UPDATE Orders SET OrderStatus='redeemed', RedeemedAt=GETDATE() WHERE Id=?", paid.getId());

        BookingException ex = assertThrows(BookingException.class,
                () -> adminService.refundOrder(paid.getId(), paid.getTotalAmount(), "Khach doi hoan", admin()));
        assertEquals(400, ex.getStatusCode(),
                "Ve da su dung ma van hoan duoc la mat tien that");
        assertEquals("paid", paymentStatusOf(paid.getId()), "Don khong duoc chuyen sang refunded");
    }

    @Test
    @DisplayName("BUG-11: qua han cutoff truoc gio chieu thi khong duoc hoan tien")
    public void refundInsideCutoffIsRejected() throws SQLException {
        OrderRecord paid = payOneSeat(null);
        execute("""
                UPDATE Showtimes SET StartTime=DATEADD(MINUTE,5,GETDATE()),
                                     EndTime=DATEADD(MINUTE,125,GETDATE())
                WHERE Id=?
                """, showtimeId);

        BookingException ex = assertThrows(BookingException.class,
                () -> adminService.refundOrder(paid.getId(), paid.getTotalAmount(), "Khach doi hoan", admin()));
        assertEquals(400, ex.getStatusCode(),
                "Con 5 phut la toi gio chieu thi da qua han huy — ghe khong ban lai kip");
        assertEquals("paid", paymentStatusOf(paid.getId()));
    }

    @Test
    @DisplayName("BUG-11: bo qua dieu kien ma khong nhap ly do thi bi tu choi")
    public void overrideWithoutReasonIsRejected() throws SQLException {
        OrderRecord paid = payOneSeat(null);
        execute("UPDATE Orders SET OrderStatus='redeemed', RedeemedAt=GETDATE() WHERE Id=?", paid.getId());

        BookingException ex = assertThrows(BookingException.class,
                () -> adminService.refundOrder(paid.getId(), paid.getTotalAmount(), "  ", admin(), true));
        assertEquals(400, ex.getStatusCode(),
                "Khong co ly do thi quyet dinh cua quan ly khong doi chat duoc khi khach khieu nai");
        assertEquals("paid", paymentStatusOf(paid.getId()));
    }

    @Test
    @DisplayName("BUG-11: bo qua dieu kien co ly do thi thanh cong va ghi audit REFUND_OVERRIDE")
    public void overrideWithReasonSucceedsAndWritesAudit() throws SQLException {
        OrderRecord paid = payOneSeat(null);
        execute("UPDATE Orders SET OrderStatus='redeemed', RedeemedAt=GETDATE() WHERE Id=?", paid.getId());
        String reason = "May chieu hong giua phim, quan ly duyet hoan cho khach";

        adminService.refundOrder(paid.getId(), paid.getTotalAmount(), reason, admin(), true);

        assertEquals("refunded", paymentStatusOf(paid.getId()));
        assertEquals(1, scalar("""
                SELECT COUNT(*) FROM AuditLogs
                WHERE Action='REFUND_OVERRIDE' AND TargetType='Order' AND TargetId=?
                  AND DetailJson LIKE '%May chieu hong%'
                """, String.valueOf(paid.getId())),
                "Phai co dung mot dong audit REFUND_OVERRIDE kem ly do");
    }

    @Test
    @DisplayName("BUG-17: hoan don dung ma cong khai phai tra lai luot dung")
    public void refundReleasesPublicPromotionUsage() throws SQLException {
        int promotionId = createPublicPromotion();
        OrderRecord paid = payOneSeat(PROMO_CODE);

        assertEquals(1, usedCountOf(promotionId), "Fixture: thanh toan phai tieu 1 luot");
        assertEquals(1, promotionUsageRows(paid.getId()), "Fixture: phai co dong PromotionUsage");

        adminService.refundOrder(paid.getId(), paid.getTotalAmount(), "Khach doi hoan", admin());

        assertEquals(0, usedCountOf(promotionId),
                "Promotions.UsedCount phai giam 1 khi don da tieu ma bi hoan");
        assertEquals(0, promotionUsageRows(paid.getId()),
                "PromotionUsage phai bi xoa, neu khong nguoi co PerUserLimit mat vinh vien 1 luot");
    }

    // ------------------------------------------------------------------ helpers

    private OrderRecord payOneSeat(String promotionCode) {
        OrderRecord draft = bookingService.createDraftOrder(
                MEMBER_ID, showtimeId, List.of(oneBookableSeat()), Map.of(), promotionCode, "card");
        return bookingService.payOrder(MEMBER_ID, draft.getId(), Map.of(), promotionCode, "card");
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

    private int createPublicPromotion() throws SQLException {
        execute("""
                INSERT INTO Promotions (Code, Description, DiscountPercent, MaxDiscount, StartDate, EndDate,
                    ConditionsJson, UsageLimit, UsedCount, Status, VoucherType, TargetTier, PointsRequired, PerUserLimit)
                VALUES (?, 'BUG-17 regression', 10, 50000, DATEADD(DAY,-1,CAST(GETDATE() AS DATE)),
                    DATEADD(DAY,30,CAST(GETDATE() AS DATE)), NULL, 100, 0, 'active', 'PUBLIC', NULL, 0, 1)
                """, PROMO_CODE);
        return scalar("SELECT Id FROM Promotions WHERE Code=?", PROMO_CODE);
    }

    private int usedCountOf(int promotionId) throws SQLException {
        return scalar("SELECT UsedCount FROM Promotions WHERE Id=?", promotionId);
    }

    private int promotionUsageRows(int orderId) throws SQLException {
        return scalar("SELECT COUNT(*) FROM PromotionUsage WHERE OrderId=?", orderId);
    }

    private String paymentStatusOf(int orderId) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT PaymentStatus FROM Orders WHERE Id=?")) {
            ps.setInt(1, orderId);
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
                DELETE FROM PromotionUsage WHERE PromotionId IN (SELECT Id FROM Promotions WHERE Code=?);
                DELETE FROM Promotions WHERE Code=?;
                """, FILM_TITLE, FILM_TITLE, FILM_TITLE, FILM_TITLE, FILM_TITLE,
                FILM_TITLE, FILM_TITLE, FILM_TITLE, FILM_TITLE, PROMO_CODE, PROMO_CODE);
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
