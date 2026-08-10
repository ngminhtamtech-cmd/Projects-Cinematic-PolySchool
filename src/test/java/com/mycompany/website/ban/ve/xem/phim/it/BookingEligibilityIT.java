package com.mycompany.website.ban.ve.xem.phim.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import com.mycompany.website.ban.ve.xem.phim.service.BookingService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("it")
@DisplayName("D.2 authoritative showtime booking eligibility")
class BookingEligibilityIT {
    private static final String PREFIX = "D2-ELIG-";

    static {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    private final BookingService bookingService = new BookingService();
    private int filmId;
    private int roomId;
    private int showtimeId;

    @BeforeAll
    static void configureDatabase() {
        DBConnection.shutdown();
    }

    @BeforeEach
    void createFixture() throws SQLException {
        assertTestDatabase();
        cleanupFixture();
        filmId = insert("""
                INSERT INTO Films (Title,ReleaseDate,EndDate,DurationMinutes,Status)
                VALUES (?,DATEADD(DAY,-1,CAST(GETDATE() AS DATE)),
                        DATEADD(DAY,30,CAST(GETDATE() AS DATE)),120,'showing')
                """, PREFIX + "FILM");
        roomId = insert("INSERT INTO Rooms (CinemaId,Name,Status) VALUES (1,?,'active')",
                PREFIX + "ROOM");
        execute("INSERT INTO CinemaFilms (CinemaId,FilmId) VALUES (1,?)", filmId);
        showtimeId = insert("""
                INSERT INTO Showtimes (FilmId,CinemaId,RoomId,StartTime,EndTime,BasePrice)
                VALUES (?,?,?,DATEADD(MINUTE,60,GETDATE()),DATEADD(MINUTE,180,GETDATE()),90000)
                """, filmId, 1, roomId);
    }

    @AfterEach
    void cleanup() throws SQLException {
        cleanupFixture();
    }

    @AfterAll
    static void shutdown() {
        DBConnection.shutdown();
    }

    @Test
    @DisplayName("valid showtime is eligible")
    void validShowtimeIsEligible() {
        BookingService.BookingEligibility result = bookingService.bookingEligibility(showtimeId);

        assertTrue(result.eligible());
        assertEquals("AVAILABLE", result.code());
        assertEquals(showtimeId, result.showtimeId());
    }

    @Test
    @DisplayName("DB-clock cutoff blocks a showtime less than 15 minutes away")
    void nearCutoffIsUnavailable() throws SQLException {
        execute("""
                UPDATE Showtimes
                SET StartTime=DATEADD(SECOND,850,GETDATE()), EndTime=DATEADD(SECOND,8050,GETDATE())
                WHERE Id=?
                """, showtimeId);

        assertUnavailable("CUTOFF_REACHED");
    }

    @Test
    @DisplayName("inactive room blocks booking")
    void inactiveRoomIsUnavailable() throws SQLException {
        execute("UPDATE Rooms SET Status='inactive' WHERE Id=?", roomId);

        assertUnavailable("ROOM_INACTIVE");
    }

    @Test
    @DisplayName("editorially ended film blocks booking")
    void endedFilmIsUnavailable() throws SQLException {
        execute("UPDATE Films SET Status='ended' WHERE Id=?", filmId);

        assertUnavailable("FILM_ENDED");
    }

    @Test
    @DisplayName("film past EndDate blocks booking")
    void expiredFilmIsUnavailable() throws SQLException {
        execute("UPDATE Films SET EndDate=DATEADD(DAY,-1,CAST(GETDATE() AS DATE)) WHERE Id=?", filmId);

        assertUnavailable("FILM_EXPIRED");
    }

    @Test
    @DisplayName("showtime before film ReleaseDate blocks booking")
    void preReleaseShowtimeIsUnavailable() throws SQLException {
        execute("UPDATE Films SET ReleaseDate=DATEADD(DAY,2,CAST(GETDATE() AS DATE)) WHERE Id=?", filmId);

        assertUnavailable("BEFORE_RELEASE");
    }

    @Test
    @DisplayName("missing showtime remains a real 404")
    void missingShowtimeIsNotFound() {
        BookingException exception = assertThrows(
                BookingException.class, () -> bookingService.bookingEligibility(Integer.MAX_VALUE));

        assertEquals(404, exception.getStatusCode());
    }

    private void assertUnavailable(String expectedCode) {
        BookingService.BookingEligibility result = bookingService.bookingEligibility(showtimeId);
        assertFalse(result.eligible());
        assertEquals(expectedCode, result.code());
    }

    private void cleanupFixture() throws SQLException {
        assertTestDatabase();
        execute("""
                DELETE FROM Showtimes WHERE FilmId IN (SELECT Id FROM Films WHERE Title LIKE ?);
                DELETE FROM Rooms WHERE Name LIKE ?;
                DELETE FROM CinemaFilms WHERE FilmId IN (SELECT Id FROM Films WHERE Title LIKE ?);
                DELETE FROM Films WHERE Title LIKE ?;
                """, PREFIX + "%", PREFIX + "%", PREFIX + "%", PREFIX + "%");
    }

    private int insert(String sql, Object... values) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            bind(statement, values);
            assertEquals(1, statement.executeUpdate());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getInt(1);
            }
        }
    }

    private void execute(String sql, Object... values) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            statement.executeUpdate();
        }
    }

    private void bind(PreparedStatement statement, Object... values) throws SQLException {
        for (int index = 0; index < values.length; index++) {
            statement.setObject(index + 1, values[index]);
        }
    }

    private void assertTestDatabase() throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT DB_NAME()");
             ResultSet result = statement.executeQuery()) {
            assertTrue(result.next());
            assertEquals(System.getProperty("cinebook.it.database", "CineBookIT_REQUIRED"), result.getString(1));
        }
    }
}
