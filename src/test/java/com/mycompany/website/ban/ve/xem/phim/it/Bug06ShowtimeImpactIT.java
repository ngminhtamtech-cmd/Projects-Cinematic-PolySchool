package com.mycompany.website.ban.ve.xem.phim.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.model.Showtime;
import com.mycompany.website.ban.ve.xem.phim.model.ShowtimeSeat;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import com.mycompany.website.ban.ve.xem.phim.service.BookingService;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
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
 * BUG-06 (INV-8) — sau khi da ban ve, doi tham so suat chieu phai <b>hoac bi chan, hoac kem thong
 * bao</b> cho nguoi giu ve.
 *
 * <p>{@code persistShowtime} chi goi {@code ensureShowtimeEditable} KHI DOI PHONG. Doi
 * {@code StartTime} cua suat da ban ve khong qua kiem tra nao. Do thuc te: doi suat tu 20:00 sang
 * 23:00 khi da co 2 ve thanh toan → thanh cong, khong canh bao, khong ban ghi thong bao nao.</p>
 *
 * <p>Mau thuan noi bo can xoa bo: XOA suat da ban thi bi chan dung, nhung DOI 3 tieng lai duoc.</p>
 */
@Tag("it")
public class Bug06ShowtimeImpactIT {

    private static final String FILM_TITLE = "BUG06-IMPACT Enterprise";
    private static final int CINEMA_ID = 1;
    private static final int ROOM_ID = 1;
    private static final int MEMBER_ID = 1;

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
                VALUES (?,?,?,DATEADD(MINUTE,120,GETDATE()),DATEADD(MINUTE,240,GETDATE()),
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
    @DisplayName("BUG-06: doi StartTime cua suat da ban ve phai bi chan")
    public void movingStartTimeOfSoldShowtimeIsBlocked() throws SQLException {
        payOneSeat();
        LocalDateTime originalStart = startTimeOf(showtimeId);

        BookingException ex = assertThrows(BookingException.class,
                () -> adminService.saveShowtime(showtimeWithStartShiftedBy(180), admin()));

        assertEquals(400, ex.getStatusCode());
        assertEquals(originalStart, startTimeOf(showtimeId),
                "Bi chan thi gio chieu phai giu nguyen trong DB");
    }

    @Test
    @DisplayName("BUG-06: confirmImpact=true thi doi duoc, nhung phai co thong bao cho moi nguoi giu ve")
    public void confirmedImpactNotifiesEveryTicketHolder() throws SQLException {
        payOneSeat();
        LocalDateTime originalStart = startTimeOf(showtimeId);

        adminService.saveShowtime(showtimeWithStartShiftedBy(180), admin(), true);

        assertTrue(startTimeOf(showtimeId).isAfter(originalStart), "Gio chieu phai duoc doi");
        assertEquals(1, notifiedHolderCount(),
                "Moi nguoi dang giu ve cua suat do phai nhan duoc mot ban ghi thong bao");
        assertEquals(1, auditRows("UPDATE_SHOWTIME_IMPACT"),
                "Phai co dong audit rieng cho lan doi co anh huong");
    }

    @Test
    @DisplayName("BUG-06: suat chua ban ve nao van doi gio binh thuong, khong sinh thong bao")
    public void untouchedShowtimeStillEditable() throws SQLException {
        LocalDateTime originalStart = startTimeOf(showtimeId);

        adminService.saveShowtime(showtimeWithStartShiftedBy(30), admin());

        assertTrue(startTimeOf(showtimeId).isAfter(originalStart));
        assertEquals(0, notifiedHolderCount(), "Khong co ai giu ve thi khong gui thong bao cho ai");
    }

    // ------------------------------------------------------------------ helpers

    private Showtime showtimeWithStartShiftedBy(int minutes) throws SQLException {
        LocalDateTime start = startTimeOf(showtimeId).plusMinutes(minutes);
        Showtime showtime = new Showtime();
        showtime.setId(showtimeId);
        showtime.setFilmId(filmId);
        showtime.setCinemaId(CINEMA_ID);
        showtime.setRoomId(ROOM_ID);
        showtime.setStartTime(start);
        showtime.setEndTime(start.plusMinutes(120));
        showtime.setBasePrice(new BigDecimal("90000"));
        showtime.setFormat("2D");
        showtime.setVersion("Subtitle");
        showtime.setLanguage("Vietnamese");
        return showtime;
    }

    private void payOneSeat() {
        OrderRecord draft = bookingService.createDraftOrder(
                MEMBER_ID, showtimeId, List.of(oneBookableSeat()), Map.of(), null, "card");
        bookingService.payOrder(MEMBER_ID, draft.getId(), Map.of(), null, "card");
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

    private LocalDateTime startTimeOf(int id) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT StartTime FROM Showtimes WHERE Id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getTimestamp(1).toLocalDateTime();
            }
        }
    }

    /**
     * So nguoi giu ve that su nhan duoc ban ghi thong bao ve suat chieu nay.
     *
     * <p>B.4 doi {@code TargetType} tu {@code 'USER'} sang {@code 'Showtime'}: {@code TargetId}
     * von la id suat chieu, nen cap cu khong khop nhau va khong tra cuu lai duoc theo suat.</p>
     */
    private int notifiedHolderCount() throws SQLException {
        String sql = """
                SELECT COUNT(DISTINCT r.UserId)
                FROM UserNotifications n
                JOIN NotificationRecipients r ON r.NotificationId = n.Id AND r.SourceType = 'user'
                WHERE n.TargetType = 'Showtime' AND n.TargetId = ?
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, String.valueOf(showtimeId));
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private int auditRows(String action) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT COUNT(*) FROM AuditLogs WHERE Action=? AND TargetType='Showtime' AND TargetId=?")) {
            ps.setString(1, action);
            ps.setString(2, String.valueOf(showtimeId));
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private void cleanupFixtures() throws SQLException {
        execute("""
                DELETE FROM NotificationRecipients WHERE SourceType='user' AND NotificationId IN (
                  SELECT Id FROM UserNotifications WHERE TargetType='USER' AND TargetId IN (
                    SELECT CAST(s.Id AS NVARCHAR(50)) FROM Showtimes s
                    JOIN Films f ON f.Id=s.FilmId WHERE f.Title=?));
                DELETE FROM UserNotifications WHERE TargetType='USER' AND TargetId IN (
                  SELECT CAST(s.Id AS NVARCHAR(50)) FROM Showtimes s
                  JOIN Films f ON f.Id=s.FilmId WHERE f.Title=?);
                DELETE FROM AuditLogs WHERE TargetType='Showtime' AND TargetId IN (
                  SELECT CAST(s.Id AS NVARCHAR(50)) FROM Showtimes s
                  JOIN Films f ON f.Id=s.FilmId WHERE f.Title=?);
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
                """, FILM_TITLE, FILM_TITLE, FILM_TITLE, FILM_TITLE, FILM_TITLE, FILM_TITLE,
                FILM_TITLE, FILM_TITLE, FILM_TITLE, FILM_TITLE, FILM_TITLE, FILM_TITLE);
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
