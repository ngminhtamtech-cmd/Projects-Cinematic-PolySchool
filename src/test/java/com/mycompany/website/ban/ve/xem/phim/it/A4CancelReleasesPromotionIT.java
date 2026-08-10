package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.model.ShowtimeSeat;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A.4 (INV-12) — huy don phai tra lai luot dung ma khuyen mai.
 *
 * <p><b>Van de.</b> {@code payOrder} tang {@code Promotions.UsedCount} va ghi
 * {@code PromotionUsage} ngay ca voi don <i>counter</i> con {@code pending}.
 * {@code refundOrder} co goi {@code releasePromotionUsage}, nhung {@code cancelOrder} thi
 * <b>khong</b> — no chi goi {@code restorePersonalVoucher}. Ma don counter qua han lai bi
 * {@code cancelExpiredCounterOrders} huy bang chinh {@code cancelOrder}.</p>
 *
 * <p>Hau qua tich luy: quy ma cong khai can dan vinh vien (moi don counter bo quen an mot luot
 * that), va nguoi co {@code PerUserLimit} mat mot luot cho tam ve ho chua bao gio nhan.</p>
 */
@Tag("it")
@DisplayName("A.4 — huy don phai tra lai luot dung ma khuyen mai")
public class A4CancelReleasesPromotionIT {

    private static final String FILM_TITLE = "A4-CANCEL-PROMO Enterprise";
    private static final String PROMO_CODE = "A4PROMO";
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
        cleanupFixtures();
    }

    @Test
    @DisplayName("don counter dung ma cong khai bi huy -> UsedCount giam 1, PromotionUsage bi xoa")
    public void cancellingCounterOrderReturnsThePromotionSlot() throws SQLException {
        int promotionId = createPublicPromotion();
        OrderRecord order = payOneSeatAtCounter();

        assertEquals(1, usedCountOf(promotionId), "Tien de: thanh toan counter phai tieu 1 luot");
        assertEquals(1, promotionUsageRows(order.getId()), "Tien de: phai co dong PromotionUsage");

        adminService.cancelOrder(order.getId(), admin(), "auto-expired");

        assertEquals(0, usedCountOf(promotionId),
                "Huy don ma khong tra lai luot thi quy ma cong khai can dan vinh vien");
        assertEquals(0, promotionUsageRows(order.getId()),
                "PromotionUsage phai bi xoa, neu khong nguoi co PerUserLimit mat 1 luot cho ve chua nhan");
    }

    @Test
    @DisplayName("cancelExpiredCounterOrders (duong that cua don qua han) cung tra lai luot")
    public void expiredCounterSweepAlsoReturnsThePromotionSlot() throws SQLException {
        int promotionId = createPublicPromotion();
        OrderRecord order = payOneSeatAtCounter();
        execute("UPDATE Orders SET CounterExpiresAt=DATEADD(MINUTE,-1,GETDATE()) WHERE Id=?", order.getId());

        assertEquals(1, usedCountOf(promotionId), "Tien de: don dang giu 1 luot");

        adminService.cancelExpiredCounterOrders();

        assertEquals("cancelled", orderStatusOf(order.getId()), "Tien de: don qua han phai bi huy");
        assertEquals(0, usedCountOf(promotionId),
                "Day la duong that sinh ra loi: don counter qua han bi quet huy hang loat");
        assertEquals(0, promotionUsageRows(order.getId()));
    }

    @Test
    @DisplayName("huy hai lan khong duoc tru quy hai lan")
    public void cancellingTwiceDoesNotDoubleRelease() throws SQLException {
        int promotionId = createPublicPromotion();
        execute("UPDATE Promotions SET UsedCount=5 WHERE Id=?", promotionId);
        OrderRecord order = payOneSeatAtCounter();

        assertEquals(6, usedCountOf(promotionId), "Tien de: 5 luot cu + 1 luot cua don nay");

        adminService.cancelOrder(order.getId(), admin(), "auto-expired");
        adminService.cancelOrder(order.getId(), admin(), "auto-expired");

        assertEquals(5, usedCountOf(promotionId),
                "Lan huy thu hai la no-op — khong duoc an them mot luot cua don khac");
    }

    // ------------------------------------------------------------------ helpers

    private OrderRecord payOneSeatAtCounter() {
        OrderRecord draft = bookingService.createDraftOrder(
                MEMBER_ID, showtimeId, List.of(oneBookableSeat()), Map.of(), PROMO_CODE, "counter");
        return bookingService.payOrder(MEMBER_ID, draft.getId(), Map.of(), PROMO_CODE, "counter");
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
                VALUES (?, 'A.4 regression', 10, 50000, DATEADD(DAY,-1,CAST(GETDATE() AS DATE)),
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

    private String orderStatusOf(int orderId) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT OrderStatus FROM Orders WHERE Id=?")) {
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
