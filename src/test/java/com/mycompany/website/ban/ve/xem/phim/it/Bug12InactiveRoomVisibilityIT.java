package com.mycompany.website.ban.ve.xem.phim.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.website.ban.ve.xem.phim.api.dto.Dtos;
import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcShowtimeDAO;
import com.mycompany.website.ban.ve.xem.phim.model.Showtime;
import java.lang.reflect.RecordComponent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * BUG-12 (INV-7) — khong nhan tien cho thu khong giao duoc, va cung khong <b>chao ban</b> no.
 *
 * <p>Sau khi phong chuyen {@code inactive}, dat ve moi da bi chan dung (400) tu dot 1, nhung API
 * cong khai van tra suat do: {@code GET /api/v1/showtimes?cinemaId=7} tra
 * {@code {"id":9,...,"roomName":"QA Phong Rap7","roomStatus":"inactive"}}. Khach van thay suat,
 * bam vao thi nhan loi — va {@code roomStatus} con lo trang thai van hanh noi bo ra ngoai.</p>
 *
 * <p>Ve DA BAN cho phong do van phai check-in duoc: loc chi ap cho be mat cong khai, khong duoc
 * chan nham duong tra cuu theo id.</p>
 */
@Tag("it")
public class Bug12InactiveRoomVisibilityIT {

    private static final String FILM_TITLE = "BUG12-HIDDEN Enterprise";
    private static final String ROOM_NAME = "BUG12 Phong Ngung Hoat Dong";
    private static final int CINEMA_ID = 1;

    static {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    private final JdbcShowtimeDAO showtimeDAO = new JdbcShowtimeDAO();
    private int filmId;
    private int roomId;
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
        roomId = insert("INSERT INTO Rooms (CinemaId,Name,Status) VALUES (?,?,'active')",
                CINEMA_ID, ROOM_NAME);
        showtimeId = insert("""
                INSERT INTO Showtimes
                    (FilmId,CinemaId,RoomId,StartTime,EndTime,BasePrice,Format,Version,Language)
                VALUES (?,?,?,DATEADD(MINUTE,120,GETDATE()),DATEADD(MINUTE,240,GETDATE()),
                        90000,'2D','Subtitle','Vietnamese')
                """, filmId, CINEMA_ID, roomId);
    }

    @AfterEach
    public void cleanup() throws SQLException {
        cleanupFixtures();
    }

    @Test
    @DisplayName("BUG-12: suat chieu cua phong inactive bien mat khoi danh sach cong khai")
    public void publicListingHidesShowtimeOfInactiveRoom() throws SQLException {
        assertTrue(publicListingContainsFixture(), "Fixture: phong con active thi suat phai hien");

        execute("UPDATE Rooms SET Status='inactive' WHERE Id=?", roomId);

        assertFalse(publicListingContainsFixture(),
                "Phong ngung hoat dong ma suat van hien thi la moi khach bam vao mot cai loi");
    }

    @Test
    @DisplayName("BUG-12: DTO cong khai khong duoc lo trang thai van hanh cua phong")
    public void publicDtoDoesNotExposeRoomOperationalState() {
        List<String> leaked = Arrays.stream(Dtos.ShowtimeDto.class.getRecordComponents())
                .map(RecordComponent::getName)
                .filter(name -> name.equals("roomStatus") || name.equals("roomActive"))
                .toList();

        assertTrue(leaked.isEmpty(),
                "Trang thai van hanh noi bo cua phong khong duoc nam trong DTO cong khai: " + leaked);
    }

    @Test
    @DisplayName("BUG-12: tra cuu theo id van tra ve suat cua phong inactive (ve da ban phai check-in duoc)")
    public void lookupByIdStillResolvesInactiveRoomShowtime() throws SQLException {
        execute("UPDATE Rooms SET Status='inactive' WHERE Id=?", roomId);

        Showtime found = showtimeDAO.findById(showtimeId).orElseThrow();

        assertEquals(showtimeId, found.getId(),
                "Loc be mat cong khai khong duoc chan nham duong tra cuu theo id");
        assertFalse(found.isRoomActive(), "Model noi bo van phai biet phong dang ngung hoat dong");
    }

    // ------------------------------------------------------------------ helpers

    private boolean publicListingContainsFixture() {
        return showtimeDAO.findByFilmAndCinema(filmId, CINEMA_ID).stream()
                .anyMatch(item -> item.getId() == showtimeId);
    }

    private void cleanupFixtures() throws SQLException {
        execute("""
                DELETE FROM ShowtimeSeats WHERE ShowtimeId IN (
                  SELECT s.Id FROM Showtimes s JOIN Films f ON f.Id=s.FilmId WHERE f.Title=?);
                DELETE FROM Showtimes WHERE FilmId IN (SELECT Id FROM Films WHERE Title=?);
                DELETE FROM CinemaFilms WHERE FilmId IN (SELECT Id FROM Films WHERE Title=?);
                DELETE FROM Films WHERE Title=?;
                DELETE FROM Seats WHERE RoomId IN (SELECT Id FROM Rooms WHERE Name=?);
                DELETE FROM Rooms WHERE Name=?;
                """, FILM_TITLE, FILM_TITLE, FILM_TITLE, FILM_TITLE, ROOM_NAME, ROOM_NAME);
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
