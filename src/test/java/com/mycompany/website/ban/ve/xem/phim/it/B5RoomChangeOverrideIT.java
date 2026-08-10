package com.mycompany.website.ban.ve.xem.phim.it;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B.5 — o tick "Xac nhan anh huong" khong ap dung duoc cho viec DOI PHONG.
 *
 * <p><b>Van de.</b> Khi doi {@code RoomId}, {@code persistShowtime} co y bo qua
 * {@code ensureShowtimeEditable} (dong 1897-1899) vi quan ly da tick xac nhan. Nhung ngay sau do
 * no goi {@code recreateShowtimeSeats}, ma ham nay lai goi {@code ensureShowtimeEditable} o dong
 * dau tien — nen doi phong cho suat da ban ve <b>luon</b> nem 400, kem mot thong bao khong he
 * nhac toi override. O tick tren {@code showtime-form.jsp} hua mot kha nang khong bao gio chay
 * duoc.</p>
 *
 * <p><b>Quyet dinh: phuong an (a).</b> Bo {@code RoomId} khoi danh sach truong duoc override, va
 * bao loi tuong minh ngay o tang service. Ly do khong chon (b): doi phong bat buoc dung lai so do
 * ghe, ma hai phong co so do khac nhau — khong co cach nao suy ra "ghe E1 phong 01 thanh ghe nao
 * o phong 02" ma khong dat ra mot luat nghiep vu moi (ghep ghe theo hang? theo loai? khach mua
 * ghe doi ma phong moi khong co ghe doi thi sao?). Dat ra luat do la doi luat nghiep vu, va ke
 * hoach yeu cau DUNG lai neu roi vao truong hop nay. Phuong an (a) khong lam mat
 * {@code OrderSeats} (loi N-02) va noi that voi quan ly rang thao tac nay khong lam duoc.</p>
 */
@Tag("it")
@DisplayName("B.5 — doi phong cho suat da ban ve phai bao loi tuong minh")
public class B5RoomChangeOverrideIT {

    private static final String FILM_TITLE = "B5-ROOM-CHANGE Enterprise";
    private static final String ROOM_NAME = "B5 Phong phu";
    private static final int CINEMA_ID = 1;
    private static final int ROOM_ID = 1;
    private static final int MEMBER_ID = 1;

    static {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    private final BookingService bookingService = new BookingService();
    private final AdminService adminService = new AdminService();
    private int showtimeId;
    private int otherRoomId;

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

        // Phong thu hai CUNG rap, de doi phong khong vuong kiem tra pham vi cum rap.
        otherRoomId = insert("INSERT INTO Rooms (CinemaId, Name, Status) VALUES (?,?,'active')",
                CINEMA_ID, ROOM_NAME);
        execute("""
                INSERT INTO Seats (RoomId, RowLabel, SeatNumber, SeatType, SeatKey, PriceSurcharge)
                VALUES (?,'A',1,'standard','B5-A1',0), (?,'A',2,'standard','B5-A2',0)
                """, otherRoomId, otherRoomId);

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

    @Test
    @DisplayName("doi phong thieu SeatKey -> 409 va giu nguyen ve da ban")
    public void roomChangeMissingSeatKeyIsConflictWithoutMutation() throws SQLException {
        OrderRecord paid = payOneSeat();

        Showtime edited = loadShowtime();
        edited.setRoomId(otherRoomId);

        BookingException blocked = assertThrows(BookingException.class,
                () -> adminService.saveShowtime(edited, admin(), true));

        assertEquals(409, blocked.getStatusCode());
        String message = blocked.getMessage();
        assertTrue(message.contains("không có ghế"), message);

        assertEquals(ROOM_ID, roomIdOf(showtimeId), "Phong phai giu nguyen");
        assertEquals(1, scalar("SELECT COUNT(*) FROM OrderSeats WHERE OrderId=?", paid.getId()),
                "Khong duoc lam mat OrderSeats — day chinh la loi N-02");
    }

    @Test
    @DisplayName("doi phong du SeatKey -> giu nguyen ShowtimeSeat va OrderSeat")
    public void roomChangeWithMatchingSeatKeyPreservesSoldSeat() throws SQLException {
        OrderRecord paid = payOneSeat();
        int showtimeSeatId = scalar(
                "SELECT ShowtimeSeatId FROM OrderSeats WHERE OrderId=?", paid.getId());
        String seatKey = text("""
                SELECT s.SeatKey FROM ShowtimeSeats ss
                JOIN Seats s ON s.Id=ss.SeatId WHERE ss.Id=?
                """, showtimeSeatId);
        execute("UPDATE TOP(1) Seats SET SeatKey=? WHERE RoomId=?", seatKey, otherRoomId);

        Showtime edited = loadShowtime();
        edited.setRoomId(otherRoomId);
        adminService.saveShowtime(edited, admin(), true);

        assertEquals(otherRoomId, roomIdOf(showtimeId));
        assertEquals(showtimeSeatId,
                scalar("SELECT ShowtimeSeatId FROM OrderSeats WHERE OrderId=?", paid.getId()));
        assertEquals(otherRoomId, scalar("""
                SELECT s.RoomId FROM ShowtimeSeats ss
                JOIN Seats s ON s.Id=ss.SeatId WHERE ss.Id=?
                """, showtimeSeatId));
    }

    @Test
    @DisplayName("doi sang phong VIP -> 409 va khong doi phong")
    public void roomChangeInvolvingVipIsConflict() throws SQLException {
        execute("UPDATE Rooms SET RoomType='VIP' WHERE Id=?", otherRoomId);
        Showtime edited = loadShowtime();
        edited.setRoomId(otherRoomId);

        BookingException blocked = assertThrows(BookingException.class,
                () -> adminService.saveShowtime(edited, admin(), true));

        assertEquals(409, blocked.getStatusCode());
        assertTrue(blocked.getMessage().contains("VIP"));
        assertEquals(ROOM_ID, roomIdOf(showtimeId));
    }

    @Test
    @DisplayName("doi phong cho suat CHUA ban ve nao van lam binh thuong")
    public void roomChangeOnUntouchedShowtimeStillWorks() throws SQLException {
        Showtime edited = loadShowtime();
        edited.setRoomId(otherRoomId);

        adminService.saveShowtime(edited, admin(), true);

        assertEquals(otherRoomId, roomIdOf(showtimeId));
        assertEquals(2, scalar("SELECT COUNT(*) FROM ShowtimeSeats WHERE ShowtimeId=?", showtimeId),
                "So do ghe phai duoc dung lai theo phong moi");
    }

    @Test
    @DisplayName("doi GIO cho suat da ban ve + tick xac nhan -> van lam duoc (khong siet nham)")
    public void timeChangeWithConfirmImpactStillWorks() throws SQLException {
        payOneSeat();
        Showtime edited = loadShowtime();
        edited.setStartTime(edited.getStartTime().plusMinutes(45));
        edited.setEndTime(edited.getEndTime().plusMinutes(45));

        adminService.saveShowtime(edited, admin(), true);

        assertEquals(ROOM_ID, roomIdOf(showtimeId));
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
             PreparedStatement ps = connection.prepareStatement("SELECT * FROM Showtimes WHERE Id=?")) {
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

    private int roomIdOf(int id) throws SQLException {
        return scalar("SELECT RoomId FROM Showtimes WHERE Id=?", id);
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

    private void cleanupFixtures() throws SQLException {
        execute("""
                DELETE FROM NotificationRecipients WHERE NotificationId IN (
                  SELECT Id FROM UserNotifications WHERE Title LIKE N'%thay đổi%');
                DELETE FROM UserNotifications WHERE Title LIKE N'%thay đổi%';
                DELETE FROM AuditLogs WHERE TargetType='Showtime' AND TargetId IN (
                  SELECT CAST(s.Id AS NVARCHAR(50)) FROM Showtimes s JOIN Films f ON f.Id=s.FilmId
                  WHERE f.Title=?);
                DELETE FROM Invoices WHERE OrderId IN (
                  SELECT o.Id FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId
                  JOIN Films f ON f.Id=s.FilmId WHERE f.Title=?);
                DELETE FROM PromotionUsage WHERE OrderId IN (
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
                DELETE FROM ShowtimeSeats WHERE SeatId IN (
                  SELECT Id FROM Seats WHERE RoomId IN (SELECT Id FROM Rooms WHERE Name=?));
                DELETE FROM Seats WHERE RoomId IN (SELECT Id FROM Rooms WHERE Name=?);
                DELETE FROM Rooms WHERE Name=?;
                """, FILM_TITLE, FILM_TITLE, FILM_TITLE, FILM_TITLE, FILM_TITLE, FILM_TITLE,
                FILM_TITLE, FILM_TITLE, FILM_TITLE, ROOM_NAME, ROOM_NAME, ROOM_NAME);
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
