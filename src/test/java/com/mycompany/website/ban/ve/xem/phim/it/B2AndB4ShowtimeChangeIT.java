package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.model.Showtime;
import com.mycompany.website.ban.ve.xem.phim.model.ShowtimeSeat;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B.2 va B.4 — audit va thong bao cua thao tac sua suat chieu.
 *
 * <p><b>B.2.</b> {@code notifyTicketHoldersOfShowtimeChange} goi {@code logAction} trong khi
 * transaction sua suat chieu con dang mo va dang giu khoa tren {@code Showtimes} /
 * {@code UserNotifications} / {@code NotificationRecipients} — vi pham thang quy uoc "khong mo
 * connection moi khi mot transaction dang giu khoa". He qua do duoc: connection thu hai do chay
 * autocommit, nen khi {@code saveShowtimes} luu mot lo va suat thu hai lam ca lo rollback, dong
 * audit cua suat thu nhat <b>van nam lai</b> — audit mo ta mot thay doi da bi huy.</p>
 *
 * <p><b>B.4.</b> Thong bao dat {@code TargetType = "USER"} nhung {@code TargetId = showtimeId}:
 * hai truong khong khop nhau, khong tra cuu lai duoc theo suat. Va noi dung luon in "da doi tu
 * &lt;gio cu&gt; sang &lt;gio moi&gt;" ke ca khi thu doi la PHIM — khach doc duoc mot cau co hai
 * moc gio giong het nhau va khong biet cai gi da doi.</p>
 */
@Tag("it")
@DisplayName("B.2/B.4 — audit sau commit va thong bao doi suat dung noi dung")
public class B2AndB4ShowtimeChangeIT {

    private static final String FILM_TITLE = "B24-SHOWTIME Enterprise";
    private static final String OTHER_FILM_TITLE = "B24-SHOWTIME Enterprise (phim khac)";
    private static final int CINEMA_ID = 1;
    private static final int ROOM_ID = 1;
    private static final int MEMBER_ID = 1;

    static {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    private final BookingService bookingService = new BookingService();
    private final AdminService adminService = new AdminService();
    private int filmId;
    private int otherFilmId;
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
        filmId = createFilm(FILM_TITLE);
        otherFilmId = createFilm(OTHER_FILM_TITLE);
        showtimeId = insert("""
                INSERT INTO Showtimes
                    (FilmId,CinemaId,RoomId,StartTime,EndTime,BasePrice,Format,Version,Language)
                VALUES (?,?,?,DATEADD(MINUTE,180,GETDATE()),DATEADD(MINUTE,300,GETDATE()),
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

    // ------------------------------------------------------------------------ B.2

    @Test
    @DisplayName("B.2: lo bi rollback vi suat thu hai -> KHONG duoc de lai audit cua suat thu nhat")
    public void rolledBackBatchLeavesNoAuditBehind() throws SQLException {
        payOneSeat();
        LocalDateTime originalStart = showtimeStart();

        Showtime edited = loadShowtime();
        edited.setStartTime(originalStart.plusMinutes(45));
        edited.setEndTime(originalStart.plusMinutes(165));

        // Suat thu hai trung phong + trung khung gio voi suat thu nhat SAU khi sua -> ca lo hong.
        Showtime clashing = loadShowtime();
        clashing.setId(0);
        clashing.setStartTime(originalStart.plusMinutes(60));
        clashing.setEndTime(originalStart.plusMinutes(180));

        assertThrows(RuntimeException.class,
                () -> adminService.saveShowtimes(List.of(edited, clashing), admin(), true),
                "tien de: suat thu hai phai lam ca lo rollback");

        assertEquals(originalStart, showtimeStart(), "tien de: thay doi phai bi hoan tac that");
        assertEquals(0, auditRows("UPDATE_SHOWTIME"),
                "Audit mo ta mot thay doi DA BI HUY — dong nay duoc ghi bang connection thu hai"
                + " chay autocommit nen rollback khong voi toi.");
        assertEquals(0, auditRows("UPDATE_SHOWTIME_IMPACT"),
                "UPDATE_SHOWTIME_IMPACT cung phai bien mat cung lo bi rollback");
    }

    @Test
    @DisplayName("B.2: lo thanh cong thi audit van phai duoc ghi day du")
    public void successfulBatchStillWritesAudit() throws SQLException {
        payOneSeat();
        LocalDateTime originalStart = showtimeStart();

        Showtime edited = loadShowtime();
        edited.setStartTime(originalStart.plusMinutes(45));
        edited.setEndTime(originalStart.plusMinutes(165));

        adminService.saveShowtimes(List.of(edited), admin(), true);

        assertEquals(1, auditRows("UPDATE_SHOWTIME"), "Doi audit ra sau commit khong duoc lam mat audit");
        assertEquals(1, auditRows("UPDATE_SHOWTIME_IMPACT"));
    }

    // ------------------------------------------------------------------------ B.4

    @Test
    @DisplayName("B.4: thong bao phai mang TargetType 'Showtime' + TargetId la id suat chieu")
    public void notificationCarriesShowtimeTargetMetadata() throws SQLException {
        payOneSeat();

        Showtime edited = loadShowtime();
        edited.setFilmId(otherFilmId);
        adminService.saveShowtimes(List.of(edited), admin(), true);

        assertEquals(1, scalar("""
                SELECT COUNT(*) FROM UserNotifications
                WHERE TargetType='Showtime' AND TargetId=? AND Title LIKE N'%thay đổi%'
                """, String.valueOf(showtimeId)),
                "TargetType='USER' kem TargetId=showtimeId la hai truong khong khop nhau:"
                + " khong tra cuu lai duoc thong bao theo suat chieu.");
    }

    @Test
    @DisplayName("B.4: doi PHIM -> noi dung phai noi phim doi, khong in hai moc gio giong het nhau")
    public void notificationDescribesTheFieldThatActuallyChanged() throws SQLException {
        payOneSeat();
        LocalDateTime unchangedStart = showtimeStart();

        Showtime edited = loadShowtime();
        edited.setFilmId(otherFilmId);
        adminService.saveShowtimes(List.of(edited), admin(), true);

        String message = latestNotificationMessage();
        assertFalse(message.contains(String.valueOf(unchangedStart) + " sang " + unchangedStart),
                "Khong doi gio ma van in 'doi tu X sang X' — khach khong hieu gi da xay ra: " + message);
        assertTrue(message.toLowerCase().contains("phim"),
                "Thu thuc su doi la PHIM, thong bao phai noi ro dieu do: " + message);
    }

    @Test
    @DisplayName("B.4: doi GIO -> noi dung van phai neu dung moc cu va moc moi")
    public void notificationStillReportsTimeChanges() throws SQLException {
        payOneSeat();
        LocalDateTime originalStart = showtimeStart();

        Showtime edited = loadShowtime();
        edited.setStartTime(originalStart.plusMinutes(45));
        edited.setEndTime(originalStart.plusMinutes(165));
        adminService.saveShowtimes(List.of(edited), admin(), true);

        String message = latestNotificationMessage();
        assertTrue(message.toLowerCase().contains("giờ"), message);
        assertTrue(message.contains(String.valueOf(originalStart.plusMinutes(45))),
                "Phai co moc gio moi de khach biet den luc nao: " + message);
    }

    // ------------------------------------------------------------------ helpers

    private OrderRecord payOneSeat() {
        int seatId = bookingService.getSeatMap(showtimeId).stream()
                .filter(seat -> seat.isAvailableFor(MEMBER_ID))
                .filter(seat -> !"couple".equalsIgnoreCase(seat.getSeatType()))
                .map(ShowtimeSeat::getId)
                .findFirst()
                .orElseThrow();
        OrderRecord draft = bookingService.createDraftOrder(
                MEMBER_ID, showtimeId, List.of(seatId), Map.of(), null, "card");
        return bookingService.payOrder(MEMBER_ID, draft.getId(), Map.of(), null, "card");
    }

    private Showtime loadShowtime() throws SQLException {
        Showtime showtime = new Showtime();
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT * FROM Showtimes WHERE Id=?")) {
            ps.setInt(1, showtimeId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                showtime.setId(rs.getInt("Id"));
                showtime.setFilmId(rs.getInt("FilmId"));
                showtime.setCinemaId(rs.getInt("CinemaId"));
                showtime.setRoomId(rs.getInt("RoomId"));
                showtime.setStartTime(rs.getTimestamp("StartTime").toLocalDateTime());
                showtime.setEndTime(rs.getTimestamp("EndTime").toLocalDateTime());
                showtime.setBasePrice(rs.getBigDecimal("BasePrice") == null
                        ? BigDecimal.valueOf(90000) : rs.getBigDecimal("BasePrice"));
                showtime.setFormat(rs.getString("Format"));
                showtime.setVersion(rs.getString("Version"));
                showtime.setLanguage(rs.getString("Language"));
            }
        }
        return showtime;
    }

    private LocalDateTime showtimeStart() throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT StartTime FROM Showtimes WHERE Id=?")) {
            ps.setInt(1, showtimeId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getTimestamp(1).toLocalDateTime();
            }
        }
    }

    private String latestNotificationMessage() throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT TOP 1 Message FROM UserNotifications WHERE Title LIKE N'%thay đổi%' ORDER BY Id DESC")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "khong tim thay thong bao doi suat chieu nao");
                return rs.getString(1);
            }
        }
    }

    private int auditRows(String action) throws SQLException {
        return scalar("SELECT COUNT(*) FROM AuditLogs WHERE Action=? AND TargetType='Showtime' AND TargetId=?",
                action, String.valueOf(showtimeId));
    }

    private int createFilm(String title) throws SQLException {
        int id = insert("""
                INSERT INTO Films (Title,ReleaseDate,EndDate,DurationMinutes,Status)
                VALUES (?,DATEADD(DAY,-1,CAST(GETDATE() AS DATE)),
                        DATEADD(DAY,30,CAST(GETDATE() AS DATE)),120,'showing')
                """, title);
        execute("INSERT INTO CinemaFilms (CinemaId,FilmId) VALUES (?,?)", CINEMA_ID, id);
        return id;
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
                DELETE FROM NotificationRecipients WHERE NotificationId IN (
                  SELECT Id FROM UserNotifications WHERE Title LIKE N'%thay đổi%');
                DELETE FROM UserNotifications WHERE Title LIKE N'%thay đổi%';
                DELETE FROM AuditLogs WHERE TargetType='Showtime' AND TargetId IN (
                  SELECT CAST(s.Id AS NVARCHAR(50)) FROM Showtimes s JOIN Films f ON f.Id=s.FilmId
                  WHERE f.Title IN (?,?));
                DELETE FROM Invoices WHERE OrderId IN (
                  SELECT o.Id FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId
                  JOIN Films f ON f.Id=s.FilmId WHERE f.Title IN (?,?));
                DELETE FROM PromotionUsage WHERE OrderId IN (
                  SELECT o.Id FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId
                  JOIN Films f ON f.Id=s.FilmId WHERE f.Title IN (?,?));
                DELETE FROM OrderSeats WHERE OrderId IN (
                  SELECT o.Id FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId
                  JOIN Films f ON f.Id=s.FilmId WHERE f.Title IN (?,?));
                DELETE FROM Orders WHERE ShowtimeId IN (
                  SELECT s.Id FROM Showtimes s JOIN Films f ON f.Id=s.FilmId WHERE f.Title IN (?,?));
                DELETE FROM ShowtimeSeats WHERE ShowtimeId IN (
                  SELECT s.Id FROM Showtimes s JOIN Films f ON f.Id=s.FilmId WHERE f.Title IN (?,?));
                DELETE FROM Showtimes WHERE FilmId IN (SELECT Id FROM Films WHERE Title IN (?,?));
                DELETE FROM CinemaFilms WHERE FilmId IN (SELECT Id FROM Films WHERE Title IN (?,?));
                DELETE FROM Films WHERE Title IN (?,?);
                """,
                FILM_TITLE, OTHER_FILM_TITLE, FILM_TITLE, OTHER_FILM_TITLE, FILM_TITLE, OTHER_FILM_TITLE,
                FILM_TITLE, OTHER_FILM_TITLE, FILM_TITLE, OTHER_FILM_TITLE, FILM_TITLE, OTHER_FILM_TITLE,
                FILM_TITLE, OTHER_FILM_TITLE, FILM_TITLE, OTHER_FILM_TITLE, FILM_TITLE, OTHER_FILM_TITLE);
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
