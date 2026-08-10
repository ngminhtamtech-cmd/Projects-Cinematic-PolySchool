package com.mycompany.website.ban.ve.xem.phim.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcShowtimeDAO;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.model.Showtime;
import com.mycompany.website.ban.ve.xem.phim.model.ShowtimeSeat;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import com.mycompany.website.ban.ve.xem.phim.service.BookingService;
import com.mycompany.website.ban.ve.xem.phim.util.BusinessClock;
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
 * Doi chieu cung mot suat chieu o admin, public catalog va booking backend.
 */
@Tag("it")
@DisplayName("Showtime past/cutoff cross-layer flow")
public class BookingCutoffAndPastFlowIT {
    private static final String FILM_TITLE = "FLOW-CUTOFF Enterprise";
    private static final int CINEMA_ID = 1;
    private static final int ROOM_ID = 1;
    private static final int MEMBER_ID = 1;

    static {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    private final BookingService bookingService = new BookingService();
    private final AdminService adminService = new AdminService();
    private final JdbcShowtimeDAO showtimeDAO = new JdbcShowtimeDAO();
    private int filmId;
    private int showtimeId;
    private String originalCutoff;

    @BeforeAll
    static void configure() {
        DBConnection.shutdown();
    }

    @BeforeEach
    void createFixture() throws SQLException {
        cleanupFixtures();
        assertTestDatabase();
        originalCutoff = setting("booking.cutoffMinutes");
        saveSetting("booking.cutoffMinutes", "15");
        filmId = insert("""
                INSERT INTO Films (Title,ReleaseDate,EndDate,DurationMinutes,Status)
                VALUES (?,DATEADD(DAY,-1,CAST(GETDATE() AS DATE)),
                        DATEADD(DAY,30,CAST(GETDATE() AS DATE)),120,'showing')
                """, FILM_TITLE);
        execute("INSERT INTO CinemaFilms (CinemaId,FilmId) VALUES (?,?)", CINEMA_ID, filmId);
        showtimeId = insert("""
                INSERT INTO Showtimes
                    (FilmId,CinemaId,RoomId,StartTime,EndTime,BasePrice,Format,Version,Language)
                VALUES (?,?,?,DATEADD(MINUTE,5,GETDATE()),DATEADD(MINUTE,125,GETDATE()),
                        90000,'2D','Subtitle','Vietnamese')
                """, filmId, CINEMA_ID, ROOM_ID);
        execute("""
                INSERT INTO ShowtimeSeats (ShowtimeId,SeatId,Status,ExtraFee)
                SELECT ?,Id,'available',0 FROM Seats WHERE RoomId=?
                """, showtimeId, ROOM_ID);
    }

    @AfterEach
    void cleanup() throws SQLException {
        cleanupFixtures();
        if (originalCutoff == null) {
            execute("DELETE FROM SystemSettings WHERE SettingKey='booking.cutoffMinutes'");
        } else {
            saveSetting("booking.cutoffMinutes", originalCutoff);
        }
        BusinessClock.resetForTesting();
    }

    @AfterAll
    static void shutdown() {
        DBConnection.shutdown();
    }

    @Test
    @DisplayName("FLOW-SHOWTIME-001: online booking closes configured minutes before start")
    void configuredCutoffBlocksLastMinuteBooking() {
        int seatId = oneBookableSeat();

        BookingException ex = assertThrows(BookingException.class,
                () -> bookingService.createDraftOrder(
                        MEMBER_ID, showtimeId, List.of(seatId), Map.of(), null, "card"));
        assertEquals(400, ex.getStatusCode(),
                "FLOW-SHOWTIME-001 a showtime starting in 5 minutes must be closed by a 15-minute cutoff");
    }

    @Test
    @DisplayName("FLOW-SHOWTIME-002: a showtime safely beyond cutoff remains bookable")
    void futureShowtimeBeyondCutoffCanBeBooked() throws SQLException {
        execute("""
                UPDATE Showtimes
                SET StartTime=DATEADD(MINUTE,30,GETDATE()), EndTime=DATEADD(MINUTE,150,GETDATE())
                WHERE Id=?
                """, showtimeId);

        OrderRecord draft = bookingService.createDraftOrder(
                MEMBER_ID, showtimeId, List.of(oneBookableSeat()), Map.of(), null, "card");
        assertTrue(draft.getId() > 0);
        assertTrue(!"cancelled".equalsIgnoreCase(draft.getOrderStatus()),
                "a valid draft must remain active; actual state=" + draft.getOrderStatus());
    }

    @Test
    @DisplayName("FLOW-SHOWTIME-003: past showtime disappears from public listing and cannot be booked")
    void pastShowtimeIsHiddenAndUnbookable() throws SQLException {
        execute("""
                UPDATE Showtimes
                SET StartTime=DATEADD(MINUTE,-5,GETDATE()), EndTime=DATEADD(MINUTE,115,GETDATE())
                WHERE Id=?
                """, showtimeId);

        assertTrue(showtimeDAO.findByFilmAndCinema(filmId, CINEMA_ID).stream()
                        .noneMatch(item -> item.getId() == showtimeId),
                "past showtime leaked into the public DAO listing");
        BookingException ex = assertThrows(BookingException.class,
                () -> bookingService.createDraftOrder(
                        MEMBER_ID, showtimeId, List.of(oneBookableSeat()), Map.of(), null, "card"));
        assertEquals(400, ex.getStatusCode());
    }

    @Test
    @DisplayName("FLOW-SHOWTIME-004: manager/admin cannot create a showtime in the past")
    void adminServiceRejectsPastShowtime() {
        LocalDateTime start = BusinessClock.now().minusMinutes(1);
        Showtime showtime = new Showtime();
        showtime.setFilmId(filmId);
        showtime.setCinemaId(CINEMA_ID);
        showtime.setRoomId(ROOM_ID);
        showtime.setStartTime(start);
        showtime.setEndTime(start.plusMinutes(120));
        showtime.setBasePrice(new BigDecimal("90000"));
        showtime.setFormat("2D");
        showtime.setVersion("Subtitle");
        showtime.setLanguage("Vietnamese");
        User admin = actor(5, "admin", null);

        BookingException ex = assertThrows(BookingException.class,
                () -> adminService.saveShowtime(showtime, admin));
        assertEquals(400, ex.getStatusCode());
    }

    private int oneBookableSeat() {
        return bookingService.getSeatMap(showtimeId).stream()
                .filter(seat -> seat.isAvailableFor(MEMBER_ID))
                .filter(seat -> !"couple".equalsIgnoreCase(seat.getSeatType()))
                .map(ShowtimeSeat::getId)
                .findFirst()
                .orElseThrow();
    }

    private User actor(int id, String role, Integer cinemaId) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setCinemaId(cinemaId);
        return user;
    }

    private void cleanupFixtures() throws SQLException {
        assertTestDatabase();
        execute("""
                DELETE FROM Invoices
                WHERE OrderId IN (
                  SELECT o.Id FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId
                  JOIN Films f ON f.Id=s.FilmId WHERE f.Title=?
                );
                DELETE FROM PromotionUsage
                WHERE OrderId IN (
                  SELECT o.Id FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId
                  JOIN Films f ON f.Id=s.FilmId WHERE f.Title=?
                );
                DELETE FROM RefundTransactions
                WHERE OrderId IN (
                  SELECT o.Id FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId
                  JOIN Films f ON f.Id=s.FilmId WHERE f.Title=?
                );
                DELETE FROM OrderSeats
                WHERE OrderId IN (
                  SELECT o.Id FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId
                  JOIN Films f ON f.Id=s.FilmId WHERE f.Title=?
                );
                DELETE FROM Orders WHERE ShowtimeId IN (
                  SELECT s.Id FROM Showtimes s JOIN Films f ON f.Id=s.FilmId WHERE f.Title=?
                );
                DELETE FROM ShowtimeSeats WHERE ShowtimeId IN (
                  SELECT s.Id FROM Showtimes s JOIN Films f ON f.Id=s.FilmId WHERE f.Title=?
                );
                DELETE FROM Showtimes WHERE FilmId IN (SELECT Id FROM Films WHERE Title=?);
                DELETE FROM CinemaFilms WHERE FilmId IN (SELECT Id FROM Films WHERE Title=?);
                DELETE FROM Films WHERE Title=?;
                """, FILM_TITLE, FILM_TITLE, FILM_TITLE, FILM_TITLE, FILM_TITLE,
                FILM_TITLE, FILM_TITLE, FILM_TITLE, FILM_TITLE);
    }

    private String setting(String key) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT SettingValue FROM SystemSettings WHERE SettingKey=?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private void saveSetting(String key, String value) throws SQLException {
        execute("""
                MERGE SystemSettings AS target
                USING (SELECT ? AS SettingKey, ? AS SettingValue) AS source
                ON target.SettingKey=source.SettingKey
                WHEN MATCHED THEN UPDATE SET SettingValue=source.SettingValue,UpdatedAt=GETDATE()
                WHEN NOT MATCHED THEN INSERT (SettingKey,SettingValue) VALUES (source.SettingKey,source.SettingValue);
                """, key, value);
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
