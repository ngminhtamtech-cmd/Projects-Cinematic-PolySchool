package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * N-02 — transaction phai rollback khi mot lenh o GIUA transaction nem SQLException.
 *
 * <p>Ban cu chi bat {@code catch (RuntimeException ex)}. {@code SQLException} khong khop nhanh do
 * nen bay thang ra ngoai ma khong rollback; {@code finally { setAutoCommit(true); }} chay truoc, va
 * theo dac ta JDBC, bat lai auto-commit khi transaction dang mo se <b>commit</b> phan da lam.
 * Ket qua: lenh DELETE dau tien duoc commit vinh vien con lenh sau that bai.</p>
 *
 * <p>Hai ca duoi day tai hien dung tinh huong do bang rang buoc khoa ngoai that, khong mock:
 * lenh DELETE dau thanh cong, lenh DELETE sau vo FK va sinh SQLException.</p>
 */
@Tag("it")
@DisplayName("N-02 — rollback khi SQLException xay ra giua transaction")
public class TransactionRollbackIT {

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

    /**
     * {@code deleteRoom(..., forceHardDelete = true)}: {@code ensureRoomDeletable()} bi bo qua, nen
     * {@code DELETE FROM Seats} chay xong roi {@code DELETE FROM Rooms} moi vo
     * {@code FK__Showtimes__RoomId}. Ghe phai con nguyen sau khi loi.
     */
    @Test
    @DisplayName("deleteRoom: DELETE Rooms vo FK sau khi DELETE Seats -> Seats khong duoc mat")
    public void hardDeleteRoomRollsBackSeatsWhenRoomDeleteViolatesForeignKey() throws SQLException {
        int roomId = insertRoom("IT N-02 phong rollback");
        int showtimeId = 0;
        try {
            insertSeats(roomId, 3);
            showtimeId = insertShowtime(roomId);
            assertEquals(3, countSeats(roomId), "tien de: phong phai co 3 ghe truoc khi xoa");

            assertThrows(RuntimeException.class,
                    () -> adminService.deleteRoom(roomId, admin, true),
                    "DELETE FROM Rooms vo FK thi lenh xoa phai that bai");

            assertEquals(3, countSeats(roomId),
                    "Ghe phai con nguyen: DELETE Seats nam cung transaction voi DELETE Rooms");
            assertEquals(1, scalar("SELECT COUNT(*) FROM Rooms WHERE Id = " + roomId),
                    "Phong phai con nguyen");
        } finally {
            if (showtimeId > 0) {
                exec("DELETE FROM ShowtimeSeats WHERE ShowtimeId = " + showtimeId);
                exec("DELETE FROM Showtimes WHERE Id = " + showtimeId);
            }
            exec("DELETE FROM Seats WHERE RoomId = " + roomId);
            exec("DELETE FROM Rooms WHERE Id = " + roomId);
        }
    }

    /**
     * {@code deleteShowtime}: {@code ensureShowtimeEditable()} chi soi {@code ShowtimeSeats} va
     * {@code OrderSeats}. Mot don hang tro toi suat nhung chua co dong {@code OrderSeats} nao van
     * qua duoc cua nay, roi lam {@code DELETE FROM Showtimes} vo {@code FK__Orders__ShowtimeId}
     * sau khi {@code DELETE FROM ShowtimeSeats} da chay.
     */
    @Test
    @DisplayName("deleteShowtime: DELETE Showtimes vo FK sau khi DELETE ShowtimeSeats -> so do ghe khong mat")
    public void deleteShowtimeRollsBackShowtimeSeatsWhenShowtimeDeleteViolatesForeignKey()
            throws SQLException {
        int roomId = insertRoom("IT N-02 phong suat");
        int showtimeId = 0;
        int orderId = 0;
        try {
            insertSeats(roomId, 3);
            showtimeId = insertShowtime(roomId);
            insertShowtimeSeats(showtimeId, roomId);
            orderId = insertOrderWithoutSeats(showtimeId);
            assertEquals(3, countShowtimeSeats(showtimeId), "tien de: suat phai co 3 ghe");

            final int targetShowtime = showtimeId;
            assertThrows(RuntimeException.class,
                    () -> adminService.deleteShowtime(targetShowtime, admin),
                    "DELETE FROM Showtimes vo FK thi lenh xoa phai that bai");

            assertEquals(3, countShowtimeSeats(showtimeId),
                    "ShowtimeSeats phai con nguyen sau khi DELETE Showtimes that bai");
            assertEquals(1, scalar("SELECT COUNT(*) FROM Showtimes WHERE Id = " + showtimeId),
                    "Suat chieu phai con nguyen");
        } finally {
            if (orderId > 0) {
                exec("DELETE FROM Orders WHERE Id = " + orderId);
            }
            if (showtimeId > 0) {
                exec("DELETE FROM ShowtimeSeats WHERE ShowtimeId = " + showtimeId);
                exec("DELETE FROM Showtimes WHERE Id = " + showtimeId);
            }
            exec("DELETE FROM Seats WHERE RoomId = " + roomId);
            exec("DELETE FROM Rooms WHERE Id = " + roomId);
        }
    }

    // ---------------------------------------------------------------- fixtures

    private static int insertRoom(String name) throws SQLException {
        return insertReturningId("INSERT INTO Rooms (CinemaId, Name, Status) VALUES "
                + "(1, N'" + name + "', 'active')");
    }

    private static void insertSeats(int roomId, int count) throws SQLException {
        for (int i = 1; i <= count; i++) {
            exec("INSERT INTO Seats (RoomId, RowLabel, SeatNumber, SeatType, SeatKey, PriceSurcharge) "
                    + "VALUES (" + roomId + ", 'A', " + i + ", 'standard', 'A" + i + "', 0)");
        }
    }

    private static int insertShowtime(int roomId) throws SQLException {
        int filmId = scalar("SELECT TOP 1 Id FROM Films ORDER BY Id");
        return insertReturningId("INSERT INTO Showtimes (FilmId, CinemaId, RoomId, StartTime, EndTime, BasePrice) "
                + "VALUES (" + filmId + ", 1, " + roomId + ", '2026-12-20 19:00:00', "
                + "'2026-12-20 21:00:00', 90000)");
    }

    private static void insertShowtimeSeats(int showtimeId, int roomId) throws SQLException {
        exec("INSERT INTO ShowtimeSeats (ShowtimeId, SeatId, Status, ExtraFee) "
                + "SELECT " + showtimeId + ", Id, 'available', 0 FROM Seats WHERE RoomId = " + roomId);
    }

    /**
     * Don tro toi suat nhung KHONG co dong OrderSeats nao — du de vuot
     * {@code ensureShowtimeEditable()} ma van chan {@code DELETE FROM Showtimes}.
     */
    private static int insertOrderWithoutSeats(int showtimeId) throws SQLException {
        String ticketCode = "ITN02" + System.nanoTime();
        return insertReturningId("INSERT INTO Orders (UserId, ShowtimeId, SeatSubtotal, ComboSubtotal, "
                + "DiscountAmount, TotalAmount, TicketCode, PaymentMethod, PaymentStatus, OrderStatus) "
                + "VALUES (1, " + showtimeId + ", 0, 90000, 0, 90000, '" + ticketCode
                + "', 'card', 'paid', 'confirmed')");
    }

    private static int countSeats(int roomId) throws SQLException {
        return scalar("SELECT COUNT(*) FROM Seats WHERE RoomId = " + roomId);
    }

    private static int countShowtimeSeats(int showtimeId) throws SQLException {
        return scalar("SELECT COUNT(*) FROM ShowtimeSeats WHERE ShowtimeId = " + showtimeId);
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
