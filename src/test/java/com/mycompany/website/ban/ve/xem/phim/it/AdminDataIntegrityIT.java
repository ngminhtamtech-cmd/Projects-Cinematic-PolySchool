package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Toan ven du lieu khi xoa phong va xoa phim (RM-01, FL-01).
 */
@Tag("it")
@DisplayName("Xoa phong / xoa phim — thong bao va phu thuoc")
public class AdminDataIntegrityIT {

    static {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    private static AdminService adminService;
    private static User admin;

    @BeforeAll
    public static void setUp() {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
        DBConnection.shutdown();
        adminService = new AdminService();
        admin = new User();
        admin.setId(5);
        admin.setRole("admin");
    }

    @AfterAll
    public static void tearDown() {
        DBConnection.shutdown();
    }

    // ------------------------------------------------------------------ RM-01

    @Test
    @DisplayName("RM-01: xoa cung phong thi thong bao tro toi phong do bien mat")
    public void hardDeleteRoomRemovesItsNotifications() throws SQLException {
        int roomId = insertRoom("IT phong xoa cung", 1);
        insertNotification("Room", String.valueOf(roomId), "IT thong bao xoa phong");
        assertEquals(1, countNotifications("Room", String.valueOf(roomId)));

        adminService.deleteRoom(roomId, admin, true);

        assertEquals(0, countNotifications("Room", String.valueOf(roomId)),
                "Sau khi xoa cung phong, khong duoc con thong bao mo coi tro toi phong da mat");
        assertEquals(0, countRooms(roomId), "Phong phai da bi xoa");
    }

    @Test
    @DisplayName("RM-01: xoa phong THAT BAI thi thong bao van con nguyen")
    public void failedRoomDeleteKeepsNotification() throws SQLException {
        int roomId = insertRoom("IT phong co ve", 1);
        insertNotification("Room", String.valueOf(roomId), "IT thong bao giu lai");
        int showtimeId = insertShowtime(roomId);
        int orderId = insertPaidOrder(showtimeId);

        try {
            // Con don ve -> hard delete phai bi tu choi de giu du lieu doi soat.
            assertThrows(BookingException.class, () -> adminService.deleteRoom(roomId, admin, true));

            assertEquals(1, countNotifications("Room", String.valueOf(roomId)),
                    "Xoa that bai thi thong bao phai con nguyen — khong duoc xoa truoc roi rollback nua voi");
            assertEquals(1, countRooms(roomId), "Phong phai con nguyen");
        } finally {
            exec("DELETE FROM OrderSeats WHERE OrderId = " + orderId);
            exec("DELETE FROM Orders WHERE Id = " + orderId);
            exec("DELETE FROM ShowtimeSeats WHERE ShowtimeId = " + showtimeId);
            exec("DELETE FROM Showtimes WHERE Id = " + showtimeId);
            exec("DELETE FROM AdminNotifications WHERE TargetType='Room' AND TargetId='" + roomId + "'");
            exec("DELETE FROM Seats WHERE RoomId = " + roomId);
            exec("DELETE FROM Rooms WHERE Id = " + roomId);
        }
    }

    // ------------------------------------------------------------------ D-03 / D-04

    /**
     * D-03: phong xoa mem truoc day <b>bien mat hoan toan</b> — {@code listRooms()} loc
     * {@code Status != 'deleted'} va khong man hinh nao hien no nua. Doanh thu van an toan, nhung
     * nguoi dung khong con cho nao doi chieu.
     */
    @Test
    @DisplayName("D-03: phong xoa mem nam o tab 'deleted', khong nam o tab dang quan ly")
    public void softDeletedRoomMovesToDeletedLifecycleTab() throws SQLException {
        int roomId = insertRoom("IT phong xoa mem", 1);
        int showtimeId = insertPastShowtime(roomId);
        try {
            adminService.deleteRoom(roomId, admin);

            assertEquals("deleted", roomStatus(roomId), "phong co lich su phai duoc xoa mem");
            assertTrue(adminService.listRooms(AdminService.LIFECYCLE_ACTIVE).stream()
                            .noneMatch(room -> room.getId() == roomId),
                    "phong da xoa khong duoc nam trong tab dang quan ly");
            assertTrue(adminService.listRooms(AdminService.LIFECYCLE_DELETED).stream()
                            .anyMatch(room -> room.getId() == roomId),
                    "phong da xoa phai xuat hien o muc \"Da bi xoa\" chu khong bien mat");
        } finally {
            exec("DELETE FROM AuditLogs WHERE TargetType='Room' AND TargetId='" + roomId + "'");
            exec("DELETE FROM ShowtimeSeats WHERE ShowtimeId = " + showtimeId);
            exec("DELETE FROM Showtimes WHERE Id = " + showtimeId);
            exec("DELETE FROM Seats WHERE RoomId = " + roomId);
            exec("DELETE FROM Rooms WHERE Id = " + roomId);
        }
    }

    /**
     * D-04: {@code dashboardCounts} dem {@code Rooms} ma khong loc {@code deleted} trong khi
     * {@code /admin/rooms} thi co — ngay khi xoa mem mot phong la hai con so lech nhau.
     */
    @Test
    @DisplayName("D-04: Tong quan dem phong khop chinh xac danh sach sau khi xoa mem")
    public void dashboardRoomCountMatchesRoomListAfterSoftDelete() throws SQLException {
        int roomId = insertRoom("IT phong dem tong quan", 1);
        int showtimeId = insertPastShowtime(roomId);
        try {
            adminService.deleteRoom(roomId, admin);

            assertEquals((long) adminService.listRooms(admin).size(),
                    adminService.dashboardCounts(admin).get("roomCount").longValue(),
                    "o \"Phong chieu\" tren Tong quan phai bang so dong trong danh sach phong");
        } finally {
            exec("DELETE FROM AuditLogs WHERE TargetType='Room' AND TargetId='" + roomId + "'");
            exec("DELETE FROM ShowtimeSeats WHERE ShowtimeId = " + showtimeId);
            exec("DELETE FROM Showtimes WHERE Id = " + showtimeId);
            exec("DELETE FROM Seats WHERE RoomId = " + roomId);
            exec("DELETE FROM Rooms WHERE Id = " + roomId);
        }
    }

    /** Phong da xoa mem van con nguyen dong trong DB, nen phai chan o tang service. */
    @Test
    @DisplayName("Khong xep duoc suat chieu vao phong da xoa")
    public void deletedRoomRejectsNewShowtime() throws SQLException {
        int roomId = insertRoom("IT phong da xoa", 1);
        try {
            exec("UPDATE Rooms SET Status = 'deleted' WHERE Id = " + roomId);

            com.mycompany.website.ban.ve.xem.phim.model.Showtime showtime =
                    new com.mycompany.website.ban.ve.xem.phim.model.Showtime();
            showtime.setFilmId(scalar("SELECT TOP 1 Id FROM Films ORDER BY Id"));
            showtime.setCinemaId(scalar("SELECT CinemaId FROM Rooms WHERE Id = " + roomId));
            showtime.setRoomId(roomId);
            showtime.setStartTime(java.time.LocalDateTime.now().plusDays(3));
            showtime.setEndTime(java.time.LocalDateTime.now().plusDays(3).plusMinutes(120));
            showtime.setBasePrice(new java.math.BigDecimal("90000"));

            BookingException ex = assertThrows(BookingException.class,
                    () -> adminService.saveShowtime(showtime, admin));
            assertTrue(ex.getMessage().contains("đã bị xóa"), ex.getMessage());
            assertEquals(0, scalar("SELECT COUNT(*) FROM Showtimes WHERE RoomId = " + roomId));
        } finally {
            exec("DELETE FROM Seats WHERE RoomId = " + roomId);
            exec("DELETE FROM Rooms WHERE Id = " + roomId);
        }
    }

    // ------------------------------------------------------------------ FL-01

    @Test
    @DisplayName("FL-01: impact summary dem dung tung loai phu thuoc cua phim")
    public void filmDeleteImpactCountsDependencies() throws SQLException {
        int filmId = insertFilm("IT phim co phu thuoc");
        exec("INSERT INTO CinemaFilms (CinemaId, FilmId) VALUES (1, " + filmId + ")");
        insertComment(filmId, 1);

        try {
            Map<String, Object> impact = adminService.getFilmDeleteImpactInfo(filmId);
            assertEquals("IT phim co phu thuoc", impact.get("title"));
            assertEquals(1, impact.get("commentCount"));
            assertEquals(1, impact.get("cinemaCount"));
            assertEquals(0, impact.get("showtimeCount"));
            assertEquals(0, impact.get("orderCount"));
        } finally {
            exec("DELETE FROM CommentReports WHERE CommentId IN (SELECT Id FROM Comments WHERE FilmId = " + filmId + ")");
            exec("DELETE FROM Comments WHERE FilmId = " + filmId);
            exec("DELETE FROM CinemaFilms WHERE FilmId = " + filmId);
            exec("DELETE FROM Films WHERE Id = " + filmId);
        }
    }

    @Test
    @DisplayName("FL-01: phim có bình luận được tombstone và giữ lịch sử mặc định")
    public void filmWithOnlyCommentsAndCinemaLinksIsDeletable() throws SQLException {
        int filmId = insertFilm("IT phim test xoa duoc");
        exec("INSERT INTO CinemaFilms (CinemaId, FilmId) VALUES (1, " + filmId + ")");
        insertComment(filmId, 1);
        exec("UPDATE Films SET Status='ended', EndDate='2026-08-01' WHERE Id=" + filmId);

        adminService.deleteFilm(filmId, admin);

        assertEquals(1, scalar("SELECT COUNT(*) FROM Films WHERE Id = " + filmId + " AND DeletedAt IS NOT NULL"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM Comments WHERE FilmId = " + filmId));
        exec("DELETE FROM Comments WHERE FilmId=" + filmId);
        exec("DELETE FROM CinemaFilms WHERE FilmId=" + filmId);
        exec("DELETE FROM Films WHERE Id=" + filmId);
    }

    @Test
    @DisplayName("FL-01: phim con don ve KHONG bi xoa, va thong bao noi ro con bao nhieu")
    public void filmWithOrdersIsProtectedWithSpecificMessage() throws SQLException {
        int filmId = insertFilm("IT phim co lich su ban ve");
        exec("INSERT INTO CinemaFilms (CinemaId, FilmId) VALUES (1, " + filmId + ")");
        int showtimeId = insertShowtimeForFilm(filmId, 1);
        int orderId = insertPaidOrder(showtimeId);
        exec("UPDATE Films SET Status='ended' WHERE Id=" + filmId);

        try {
            BookingException ex = assertThrows(BookingException.class,
                    () -> adminService.deleteFilm(filmId, admin));
            assertEquals(409, ex.getStatusCode());
            assertTrue(ex.getMessage().contains("suất đang diễn ra hoặc tương lai"), ex.getMessage());

            assertEquals(1, scalar("SELECT COUNT(*) FROM Films WHERE Id = " + filmId),
                    "phim phai con nguyen");
        } finally {
            exec("DELETE FROM OrderSeats WHERE OrderId = " + orderId);
            exec("DELETE FROM Orders WHERE Id = " + orderId);
            exec("DELETE FROM ShowtimeSeats WHERE ShowtimeId = " + showtimeId);
            exec("DELETE FROM Showtimes WHERE Id = " + showtimeId);
            exec("DELETE FROM CinemaFilms WHERE FilmId = " + filmId);
            exec("DELETE FROM Films WHERE Id = " + filmId);
        }
    }

    // ------------------------------------------------------------------ EX-01

    @Test
    @DisplayName("EX-01: EndDate truoc ReleaseDate bi tu choi o service")
    public void endDateBeforeReleaseDateIsRejected() {
        com.mycompany.website.ban.ve.xem.phim.model.Film film =
                new com.mycompany.website.ban.ve.xem.phim.model.Film();
        film.setTitle("IT phim ngay sai");
        completeRequiredFilmFields(film);
        film.setReleaseDate(LocalDate.now().plusDays(2));
        film.setEndDate(LocalDate.now().plusDays(1));
        film.setDurationMinutes(120);
        film.setStatus("showing");

        BookingException ex = assertThrows(BookingException.class,
                () -> adminService.saveFilm(film, admin, java.util.List.of(1)));
        assertEquals(400, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("không được trước ngày khởi chiếu"), ex.getMessage());
    }

    @Test
    @DisplayName("EX-01: gia han cap nhat EndDate va ghi audit")
    public void extendFilmEndDateUpdatesAndAudits() throws SQLException {
        int filmId = insertFilm("IT phim gia han");
        exec("UPDATE Films SET ReleaseDate='2026-07-01', EndDate='2026-07-10' WHERE Id=" + filmId);
        try {
            adminService.extendFilmEndDate(filmId, LocalDate.of(2026, 9, 30), admin);

            try (Connection connection = DBConnection.getConnection();
                 PreparedStatement ps = connection.prepareStatement(
                         "SELECT EndDate FROM Films WHERE Id = ?")) {
                ps.setInt(1, filmId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(LocalDate.of(2026, 9, 30), rs.getDate(1).toLocalDate());
                }
            }
            assertTrue(scalar("SELECT COUNT(*) FROM AuditLogs WHERE Action='EXTEND_FILM_ENDDATE' "
                    + "AND TargetId='" + filmId + "'") >= 1, "phai co ban ghi audit cho viec gia han");
        } finally {
            exec("DELETE FROM AuditLogs WHERE Action='EXTEND_FILM_ENDDATE' AND TargetId='" + filmId + "'");
            exec("DELETE FROM Films WHERE Id = " + filmId);
        }
    }

    @Test
    @DisplayName("EX-01: khong rut ngan EndDate ve truoc mot suat chieu da xep")
    public void cannotShrinkEndDateBeforeExistingShowtime() throws SQLException {
        int filmId = insertFilm("IT phim rut ngan");
        exec("UPDATE Films SET ReleaseDate='2026-07-01', EndDate='2026-12-31' WHERE Id=" + filmId);
        exec("INSERT INTO CinemaFilms (CinemaId, FilmId) VALUES (1, " + filmId + ")");
        int showtimeId = insertShowtimeAt(filmId, 1, "2026-10-15 19:00:00");
        try {
            BookingException ex = assertThrows(BookingException.class,
                    () -> adminService.extendFilmEndDate(filmId, LocalDate.of(2026, 8, 1), admin));
            assertEquals(409, ex.getStatusCode());
            assertTrue(ex.getMessage().contains("suất"), ex.getMessage());
        } finally {
            exec("DELETE FROM ShowtimeSeats WHERE ShowtimeId = " + showtimeId);
            exec("DELETE FROM Showtimes WHERE Id = " + showtimeId);
            exec("DELETE FROM CinemaFilms WHERE FilmId = " + filmId);
            exec("DELETE FROM Films WHERE Id = " + filmId);
        }
    }

    // ---------------------------------------------------------------- fixtures

    private static int insertRoom(String name, int cinemaId) throws SQLException {
        return insertReturningId(
                "INSERT INTO Rooms (CinemaId, Name, Status) VALUES (" + cinemaId + ", N'" + name + "', 'active')");
    }

    private static int insertFilm(String title) throws SQLException {
        return insertReturningId("INSERT INTO Films (Title, ReleaseDate, EndDate, DurationMinutes, Status) "
                + "VALUES (N'" + title + "', '2026-01-01', '2026-12-31', 120, 'showing')");
    }

    private static void completeRequiredFilmFields(com.mycompany.website.ban.ve.xem.phim.model.Film film) {
        film.setActors("IT actor");
        film.setDirectors("IT director");
        film.setCategories("IT");
        film.setCountry("VN");
        film.setDescription("IT description");
        film.setLanguage("vi");
        film.setFormat("2D");
        film.setAgeRating("P");
        film.setThumbnail("/it-poster.jpg");
        film.setBanner("/it-banner.jpg");
        film.setTrailerUrl("https://example.test/trailer");
    }

    private static int insertShowtime(int roomId) throws SQLException {
        int cinemaId = scalar("SELECT CinemaId FROM Rooms WHERE Id = " + roomId);
        int filmId = scalar("SELECT TOP 1 Id FROM Films ORDER BY Id");
        return insertShowtimeForRoom(filmId, cinemaId, roomId, "2026-12-01 19:00:00");
    }

    private static int insertShowtimeForFilm(int filmId, int cinemaId) throws SQLException {
        int roomId = scalar("SELECT TOP 1 Id FROM Rooms WHERE CinemaId = " + cinemaId);
        return insertShowtimeForRoom(filmId, cinemaId, roomId, "2026-12-01 19:00:00");
    }

    private static int insertShowtimeAt(int filmId, int cinemaId, String startTime) throws SQLException {
        int roomId = scalar("SELECT TOP 1 Id FROM Rooms WHERE CinemaId = " + cinemaId);
        return insertShowtimeForRoom(filmId, cinemaId, roomId, startTime);
    }

    private static int insertShowtimeForRoom(int filmId, int cinemaId, int roomId, String startTime)
            throws SQLException {
        return insertReturningId(
                "INSERT INTO Showtimes (FilmId, CinemaId, RoomId, StartTime, EndTime, BasePrice) VALUES ("
                + filmId + ", " + cinemaId + ", " + roomId + ", '" + startTime + "', "
                + "DATEADD(MINUTE, 120, '" + startTime + "'), 90000)");
    }

    private static int insertPaidOrder(int showtimeId) throws SQLException {
        String ticketCode = "ITTEST" + System.nanoTime();
        return insertReturningId(
                "INSERT INTO Orders (UserId, ShowtimeId, SeatSubtotal, ComboSubtotal, DiscountAmount, "
                + "TotalAmount, TicketCode, PaymentMethod, PaymentStatus, OrderStatus) VALUES "
                + "(1, " + showtimeId + ", 90000, 0, 0, 90000, '" + ticketCode + "', 'card', 'paid', 'confirmed')");
    }

    private static void insertComment(int filmId, int userId) throws SQLException {
        exec("INSERT INTO Comments (UserId, FilmId, Rate, Content) VALUES ("
                + userId + ", " + filmId + ", 5, N'IT binh luan')");
    }

    private static void insertNotification(String targetType, String targetId, String title)
            throws SQLException {
        exec("INSERT INTO AdminNotifications (Title, Message, Category, Severity, TargetType, TargetId, IsRead) "
                + "VALUES (N'" + title + "', N'IT', 'IT', 'info', '" + targetType + "', '" + targetId + "', 0)");
    }

    private static int countNotifications(String targetType, String targetId) throws SQLException {
        return scalar("SELECT COUNT(*) FROM AdminNotifications WHERE TargetType='"
                + targetType + "' AND TargetId='" + targetId + "'");
    }

    private static int countRooms(int roomId) throws SQLException {
        return scalar("SELECT COUNT(*) FROM Rooms WHERE Id = " + roomId);
    }

    private static String roomStatus(int roomId) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT ISNULL(Status, 'active') FROM Rooms WHERE Id = " + roomId);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    /** Suat da ket thuc: du de {@code deleteRoom} di nhanh xoa mem thay vi xoa cung. */
    private static int insertPastShowtime(int roomId) throws SQLException {
        int cinemaId = scalar("SELECT CinemaId FROM Rooms WHERE Id = " + roomId);
        int filmId = scalar("SELECT TOP 1 Id FROM Films ORDER BY Id");
        return insertReturningId("INSERT INTO Showtimes (FilmId, CinemaId, RoomId, StartTime, EndTime, BasePrice)"
                + " VALUES (" + filmId + ", " + cinemaId + ", " + roomId
                + ", DATEADD(DAY, -30, GETDATE()), DATEADD(MINUTE, -1680, GETDATE()), 90000)");
    }

    private static int insertReturningId(String sql) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    private static int scalar(String sql) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static void exec(String sql) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.executeUpdate();
        }
    }
}
