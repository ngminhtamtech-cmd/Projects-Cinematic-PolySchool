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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B.3 (BUG-09 chua dat nghiem thu) — thao tac dung tien phai ghi duoc <b>trang thai truoc</b>.
 *
 * <p>{@code REFUND_ORDER}, {@code REFUND_OVERRIDE}, {@code CANCEL_ORDER} va {@code PAY_ORDER} deu
 * goi ban {@code logAction} 5 tham so, ma ban do truyen {@code beforeJson = null}. Gia tri truoc
 * khi doi da duoc doc san vao bien cuc bo trong cung ham roi vut di — nen khi doi chat voi khach
 * ("luc do don dang o trang thai nao?") thi audit khong tra loi duoc.</p>
 *
 * <p>{@code rejectRefund} da lam dung: doc trang thai cu ra {@code beforeJson} roi dung overload
 * co day du truong. Bon cho tren phai giong no.</p>
 */
@Tag("it")
@DisplayName("B.3 — BeforeJson phai khac NULL o cac thao tac dung tien")
public class B3AuditBeforeJsonIT {

    private static final String FILM_TITLE = "B3-BEFOREJSON Enterprise";
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
        // Ngu canh HTTP that: BUG-09 doi ca ba truong IpAddress/UserAgent/BeforeJson.
        RequestContext.set("203.0.113.7", "Mozilla/5.0 (B3 regression)");
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
    @DisplayName("PAY_ORDER ghi du IpAddress, UserAgent, BeforeJson")
    public void payOrderRecordsFullContext() throws SQLException {
        OrderRecord paid = payOneSeat();

        assertFullContext(paid.getId(), "PAY_ORDER");
        assertTrue(beforeJsonOf(paid.getId(), "PAY_ORDER").contains("pending"),
                "BeforeJson phai mo ta don LUC CHUA thanh toan: "
                + beforeJsonOf(paid.getId(), "PAY_ORDER"));
    }

    @Test
    @DisplayName("REFUND_ORDER ghi du IpAddress, UserAgent, BeforeJson")
    public void refundOrderRecordsFullContext() throws SQLException {
        OrderRecord paid = payOneSeat();

        adminService.refundOrder(paid.getId(), paid.getTotalAmount(), "Khach doi hoan", admin());

        assertFullContext(paid.getId(), "REFUND_ORDER");
        assertTrue(beforeJsonOf(paid.getId(), "REFUND_ORDER").contains("paid"),
                "BeforeJson phai mo ta don LUC CON 'paid': "
                + beforeJsonOf(paid.getId(), "REFUND_ORDER"));
    }

    @Test
    @DisplayName("REFUND_OVERRIDE ghi du IpAddress, UserAgent, BeforeJson")
    public void refundOverrideRecordsFullContext() throws SQLException {
        OrderRecord paid = payOneSeat();
        execute("UPDATE Orders SET OrderStatus='redeemed', RedeemedAt=GETDATE() WHERE Id=?", paid.getId());

        adminService.refundOrder(paid.getId(), paid.getTotalAmount(),
                "May chieu hong giua phim", admin(), true);

        assertFullContext(paid.getId(), "REFUND_OVERRIDE");
        assertTrue(beforeJsonOf(paid.getId(), "REFUND_OVERRIDE").contains("redeemed"),
                "BeforeJson phai cho thay don da 'redeemed' — chinh la dieu kien bi bo qua: "
                + beforeJsonOf(paid.getId(), "REFUND_OVERRIDE"));
    }

    @Test
    @DisplayName("CANCEL_ORDER ghi du IpAddress, UserAgent, BeforeJson")
    public void cancelOrderRecordsFullContext() throws SQLException {
        OrderRecord draft = draftOneSeat();

        adminService.cancelOrder(draft.getId(), admin(), "Khach doi lich");

        assertFullContext(draft.getId(), "CANCEL_ORDER");
        assertTrue(beforeJsonOf(draft.getId(), "CANCEL_ORDER").contains("pending"),
                "BeforeJson phai mo ta don unpaid LUC CHUA huy: "
                + beforeJsonOf(draft.getId(), "CANCEL_ORDER"));
    }

    // ------------------------------------------------------------------ helpers

    private void assertFullContext(int orderId, String action) throws SQLException {
        assertEquals(1, scalar("""
                SELECT COUNT(*) FROM AuditLogs
                WHERE Action=? AND TargetType='Order' AND TargetId=?
                  AND IpAddress IS NOT NULL AND UserAgent IS NOT NULL AND BeforeJson IS NOT NULL
                """, action, String.valueOf(orderId)),
                action + ": BUG-09 doi ca ba truong IpAddress/UserAgent/BeforeJson khac NULL."
                + " Dang co: " + auditSnapshot(orderId, action));
    }

    private String auditSnapshot(int orderId, String action) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     SELECT IpAddress, UserAgent, BeforeJson FROM AuditLogs
                     WHERE Action=? AND TargetType='Order' AND TargetId=?
                     """)) {
            ps.setString(1, action);
            ps.setString(2, String.valueOf(orderId));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return "(khong co dong audit nao)";
                }
                return "IpAddress=" + rs.getString(1) + ", UserAgent=" + rs.getString(2)
                        + ", BeforeJson=" + rs.getString(3);
            }
        }
    }

    private String beforeJsonOf(int orderId, String action) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     SELECT BeforeJson FROM AuditLogs
                     WHERE Action=? AND TargetType='Order' AND TargetId=?
                     """)) {
            ps.setString(1, action);
            ps.setString(2, String.valueOf(orderId));
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "khong tim thay dong audit " + action);
                String value = rs.getString(1);
                assertNotNull(value, action + ": BeforeJson van dang NULL");
                return value;
            }
        }
    }

    private OrderRecord payOneSeat() {
        OrderRecord draft = draftOneSeat();
        return bookingService.payOrder(MEMBER_ID, draft.getId(), Map.of(), null, "card");
    }

    private OrderRecord draftOneSeat() {
        int seatId = bookingService.getSeatMap(showtimeId).stream()
                .filter(seat -> seat.isAvailableFor(MEMBER_ID))
                .filter(seat -> !"couple".equalsIgnoreCase(seat.getSeatType()))
                .map(ShowtimeSeat::getId)
                .findFirst()
                .orElseThrow();
        return bookingService.createDraftOrder(
                MEMBER_ID, showtimeId, List.of(seatId), Map.of(), null, "card");
    }

    private static User admin() {
        User user = new User();
        user.setId(5);
        user.setRole("admin");
        user.setCinemaId(null);
        return user;
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
