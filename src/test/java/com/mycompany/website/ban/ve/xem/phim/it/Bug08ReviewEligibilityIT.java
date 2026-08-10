package com.mycompany.website.ban.ve.xem.phim.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.model.ShowtimeSeat;
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
 * BUG-08 (INV-11) — chi nguoi <b>da thuc su dung dich vu</b> moi duoc danh gia, va <b>mot lan</b>.
 *
 * <p>{@code AdminService.addComment} la mot cau INSERT tran: khong kiem quyen danh gia, khong chan
 * trung. Do thuc te: tai khoan 0 don hang danh gia duoc (201); cung tai khoan danh gia 3 lan lien
 * tiep cung mot phim deu 201 — diem trung binh bi thao tung tuy y.</p>
 *
 * <p>Luat dat o tang service nen ca hai duong vao (FilmServlet cua JSP va
 * {@code /api/v1/films/&#123;id&#125;/comments}) deu chiu chung mot chot, khong nhan doi o controller.</p>
 */
@Tag("it")
public class Bug08ReviewEligibilityIT {

    private static final String FILM_TITLE = "BUG08-REVIEW Enterprise";
    private static final int CINEMA_ID = 1;
    private static final int ROOM_ID = 1;
    private static final int VIEWER_ID = 1;
    private static final int STRANGER_ID = 2;

    static {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    private final BookingService bookingService = new BookingService();
    private final AdminService adminService = new AdminService();
    private int filmId;
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
        filmId = insert("""
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
    @DisplayName("BUG-08: tai khoan chua tung xem phim thi khong duoc danh gia")
    public void reviewWithoutRedeemedTicketIsRejected() throws SQLException {
        BookingException ex = assertThrows(BookingException.class,
                () -> adminService.addComment(STRANGER_ID, filmId, 5, "Phim hay lam"));

        assertEquals(403, ex.getStatusCode(),
                "Khong co don redeemed nao cho phim nay thi khong duoc danh gia");
        assertEquals(0, commentCount(STRANGER_ID), "Khong duoc ghi dong danh gia nao");
    }

    @Test
    @DisplayName("BUG-08: don da mua nhung chua check-in cung chua duoc danh gia")
    public void paidButNotRedeemedTicketIsNotEnough() {
        payOneSeat(VIEWER_ID);

        BookingException ex = assertThrows(BookingException.class,
                () -> adminService.addComment(VIEWER_ID, filmId, 4, "Chua xem ma van cham diem"));
        assertEquals(403, ex.getStatusCode());
    }

    @Test
    @DisplayName("BUG-08: nguoi da check-in ve danh gia duoc dung mot lan, lan hai bi 409")
    public void redeemedViewerMayReviewExactlyOnce() throws SQLException {
        redeemOneSeat(VIEWER_ID);

        adminService.addComment(VIEWER_ID, filmId, 5, "Phim rat dang xem");
        assertEquals(1, commentCount(VIEWER_ID));

        BookingException ex = assertThrows(BookingException.class,
                () -> adminService.addComment(VIEWER_ID, filmId, 1, "Danh gia lan hai de dim diem"));
        assertEquals(409, ex.getStatusCode(),
                "Mot nguoi mot danh gia cho mot phim — neu khong, diem trung binh bi thao tung");
        assertEquals(1, commentCount(VIEWER_ID), "Lan hai khong duoc ghi them dong nao");
    }

    // ------------------------------------------------------------------ helpers

    private OrderRecord payOneSeat(int userId) {
        OrderRecord draft = bookingService.createDraftOrder(
                userId, showtimeId, List.of(oneBookableSeat(userId)), Map.of(), null, "card");
        return bookingService.payOrder(userId, draft.getId(), Map.of(), null, "card");
    }

    private void redeemOneSeat(int userId) throws SQLException {
        OrderRecord paid = payOneSeat(userId);
        execute("UPDATE Orders SET OrderStatus='redeemed', RedeemedAt=GETDATE() WHERE Id=?", paid.getId());
    }

    private int oneBookableSeat(int userId) {
        return bookingService.getSeatMap(showtimeId).stream()
                .filter(seat -> seat.isAvailableFor(userId))
                .filter(seat -> !"couple".equalsIgnoreCase(seat.getSeatType()))
                .map(ShowtimeSeat::getId)
                .findFirst()
                .orElseThrow();
    }

    private int commentCount(int userId) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT COUNT(*) FROM Comments WHERE UserId=? AND FilmId=?")) {
            ps.setInt(1, userId);
            ps.setInt(2, filmId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private void cleanupFixtures() throws SQLException {
        execute("""
                DELETE FROM Comments WHERE FilmId IN (SELECT Id FROM Films WHERE Title=?);
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
                FILM_TITLE, FILM_TITLE, FILM_TITLE, FILM_TITLE, FILM_TITLE);
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
