package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcCinemaDAO;
import com.mycompany.website.ban.ve.xem.phim.model.RevenueRow;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vong doi xoa rap (D-02).
 *
 * <p>Loi goc: {@code deleteCinema} chay {@code UPDATE Combos SET CinemaId = NULL} trong khi bang
 * that ten la {@code ComboFoods}. Cau lenh nam vo dieu kien truoc {@code DELETE FROM Cinemas}, nen
 * <b>moi</b> rap qua duoc hai chot kiem tra deu chet voi {@code Msg 208 Invalid object name}
 * — va loi do bi doi thanh thong bao "còn dữ liệu ràng buộc", do toi sai cho du lieu.</p>
 *
 * <p>Cach sua khong phai la "cho chay duoc": moi khoa ngoai tro toi {@code Cinemas} la
 * {@code NO_ACTION} va chung dang vo tinh bao ve {@code Showtimes} — tuc la bao ve doanh thu.
 * Rap con lich su chuyen sang <b>soft delete</b> giong phong chieu.</p>
 */
@Tag("it")
@DisplayName("Xoa rap: soft delete khi con lich su, hard delete khi sach (D-02)")
public class CinemaDeleteLifecycleIT {

    static {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    private static AdminService adminService;
    private static JdbcCinemaDAO cinemaDAO;
    private static User admin;

    @BeforeAll
    public static void setUp() {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
        DBConnection.shutdown();
        adminService = new AdminService();
        cinemaDAO = new JdbcCinemaDAO();
        admin = new User();
        admin.setId(5);
        admin.setRole("admin");
    }

    @AfterAll
    public static void tearDown() {
        DBConnection.shutdown();
    }

    /** Dung ca rap "CineBook Imax": 0 phong, 0 tai khoan, 0 suat, 0 combo. */
    @Test
    @DisplayName("Rap sach hoan toan -> XOA CUNG (truoc khi sua thi chet o bang 'Combos')")
    public void spotlessCinemaIsHardDeleted() throws SQLException {
        int cinemaId = insertCinema("IT rap rong");
        try {
            adminService.deleteCinema(cinemaId, admin);

            assertEquals(0, scalar("SELECT COUNT(*) FROM Cinemas WHERE Id = " + cinemaId),
                    "rap khong con dau vet nao thi duoc xoa han");
            assertTrue(scalar("SELECT COUNT(*) FROM AuditLogs WHERE Action = 'DELETE_CINEMA_HARD'"
                    + " AND TargetId = '" + cinemaId + "'") >= 1, "phai ghi audit hard delete");
        } finally {
            exec("DELETE FROM AuditLogs WHERE TargetType = 'Cinema' AND TargetId = '" + cinemaId + "'");
            exec("DELETE FROM Cinemas WHERE Id = " + cinemaId);
        }
    }

    /**
     * Chot quan trong nhat cua ca dot sua: xoa rap <b>khong duoc lam mat mot dong doanh thu nao</b>.
     */
    @Test
    @DisplayName("Rap con suat chieu -> XOA MEM, doanh thu khong doi mot dong")
    public void cinemaWithHistoryIsSoftDeletedAndKeepsRevenue() throws SQLException {
        int cinemaId = insertCinema("IT rap co lich su");
        int roomId = insertRoom(cinemaId, "IT phong cua rap co lich su");
        int showtimeId = insertShowtime(cinemaId, roomId);
        int orderId = insertPaidOrder(showtimeId);
        try {
            List<String> before = revenueSnapshot();
            // Phong phai duoc xoa truoc — do la chot da co san va van phai giu.
            adminService.deleteRoom(roomId, admin);

            adminService.deleteCinema(cinemaId, admin);

            assertEquals("deleted", text("SELECT Status FROM Cinemas WHERE Id = " + cinemaId),
                    "rap con suat chieu phai duoc xoa mem, khong xoa cung");
            assertEquals(1, scalar("SELECT COUNT(*) FROM Showtimes WHERE Id = " + showtimeId),
                    "suat chieu phai con nguyen — day la thu giu doanh thu");
            assertEquals(1, scalar("SELECT COUNT(*) FROM Orders WHERE Id = " + orderId),
                    "don hang phai con nguyen");
            assertEquals(before, revenueSnapshot(),
                    "bao cao doanh thu theo ngay phai giong het truoc va sau khi xoa rap");

            assertFalse(cinemaDAO.findAll(null).stream().anyMatch(c -> c.getId() == cinemaId),
                    "rap da xoa phai bien khoi moi be mat cong khai");
            assertFalse(adminService.listCinemas(AdminService.LIFECYCLE_ACTIVE).stream()
                            .anyMatch(c -> c.getId() == cinemaId),
                    "rap da xoa khong con o tab dang quan ly");
            assertTrue(adminService.listCinemas(AdminService.LIFECYCLE_DELETED).stream()
                            .anyMatch(c -> c.getId() == cinemaId),
                    "rap da xoa phai xuat hien o muc \"Da bi xoa\" chu khong bien mat");
        } finally {
            exec("DELETE FROM AuditLogs WHERE TargetType = 'Cinema' AND TargetId = '" + cinemaId + "'");
            exec("DELETE FROM AuditLogs WHERE TargetType = 'Room' AND TargetId = '" + roomId + "'");
            exec("DELETE FROM OrderSeats WHERE OrderId = " + orderId);
            exec("DELETE FROM Orders WHERE Id = " + orderId);
            exec("DELETE FROM ShowtimeSeats WHERE ShowtimeId = " + showtimeId);
            exec("DELETE FROM Showtimes WHERE Id = " + showtimeId);
            exec("DELETE FROM CinemaFilms WHERE CinemaId = " + cinemaId);
            exec("DELETE FROM Seats WHERE RoomId = " + roomId);
            exec("DELETE FROM Rooms WHERE Id = " + roomId);
            exec("DELETE FROM Cinemas WHERE Id = " + cinemaId);
        }
    }

    /** Rap chi co combo cung phai xoa mem: {@code ComboFoods.CinemaId} la khoa ngoai NO_ACTION. */
    @Test
    @DisplayName("Rap chi con combo -> XOA MEM, combo con nguyen")
    public void cinemaWithComboIsSoftDeleted() throws SQLException {
        int cinemaId = insertCinema("IT rap co combo");
        int comboId = insertReturningId("INSERT INTO ComboFoods (Name, Price, CinemaId) VALUES "
                + "(N'IT combo " + System.nanoTime() + "', 69000, " + cinemaId + ")");
        try {
            adminService.deleteCinema(cinemaId, admin);

            assertEquals("deleted", text("SELECT Status FROM Cinemas WHERE Id = " + cinemaId));
            assertEquals(cinemaId, scalar("SELECT CinemaId FROM ComboFoods WHERE Id = " + comboId),
                    "combo van thuoc rap do — xoa mem khong duoc go lien ket");
        } finally {
            exec("DELETE FROM AuditLogs WHERE TargetType = 'Cinema' AND TargetId = '" + cinemaId + "'");
            exec("DELETE FROM ComboFoods WHERE Id = " + comboId);
            exec("DELETE FROM Cinemas WHERE Id = " + cinemaId);
        }
    }

    @Test
    @DisplayName("Rap con phong dang hoat dong -> 400, khong dong nao bi dung toi")
    public void cinemaWithActiveRoomIsRejected() throws SQLException {
        int cinemaId = insertCinema("IT rap con phong");
        int roomId = insertRoom(cinemaId, "IT phong con hoat dong");
        try {
            BookingException ex = assertThrows(BookingException.class,
                    () -> adminService.deleteCinema(cinemaId, admin));
            assertEquals(400, ex.getStatusCode());
            assertTrue(ex.getMessage().contains("phòng chiếu"), ex.getMessage());
            assertEquals("active", text("SELECT Status FROM Cinemas WHERE Id = " + cinemaId));
        } finally {
            exec("DELETE FROM Seats WHERE RoomId = " + roomId);
            exec("DELETE FROM Rooms WHERE Id = " + roomId);
            exec("DELETE FROM Cinemas WHERE Id = " + cinemaId);
        }
    }

    /** Rap da xoa mem khong duoc nhan them suat chieu qua mot request sua tay. */
    @Test
    @DisplayName("Khong xep duoc suat chieu vao rap da xoa")
    public void deletedCinemaRejectsNewShowtime() throws SQLException {
        int cinemaId = insertCinema("IT rap da xoa");
        int roomId = insertRoom(cinemaId, "IT phong cua rap da xoa");
        try {
            exec("UPDATE Cinemas SET Status = 'deleted' WHERE Id = " + cinemaId);

            com.mycompany.website.ban.ve.xem.phim.model.Showtime showtime =
                    new com.mycompany.website.ban.ve.xem.phim.model.Showtime();
            showtime.setFilmId(scalar("SELECT TOP 1 Id FROM Films ORDER BY Id"));
            showtime.setCinemaId(cinemaId);
            showtime.setRoomId(roomId);
            showtime.setStartTime(java.time.LocalDateTime.now().plusDays(3));
            showtime.setEndTime(java.time.LocalDateTime.now().plusDays(3).plusMinutes(120));
            showtime.setBasePrice(new java.math.BigDecimal("90000"));

            BookingException ex = assertThrows(BookingException.class,
                    () -> adminService.saveShowtime(showtime, admin));
            assertTrue(ex.getMessage().contains("đã bị xóa"), ex.getMessage());
            assertEquals(0, scalar("SELECT COUNT(*) FROM Showtimes WHERE CinemaId = " + cinemaId));
        } finally {
            exec("DELETE FROM Seats WHERE RoomId = " + roomId);
            exec("DELETE FROM Rooms WHERE Id = " + roomId);
            exec("DELETE FROM Cinemas WHERE Id = " + cinemaId);
        }
    }

    // ---------------------------------------------------------------- fixtures

    private static int insertCinema(String name) throws SQLException {
        int cityId = scalar("SELECT TOP 1 Id FROM Cities ORDER BY Id");
        return insertReturningId("INSERT INTO Cinemas (CityId, Name, Address, Status, CinemaType) VALUES ("
                + cityId + ", N'" + name + " " + System.nanoTime() + "', N'IT dia chi', 'active', 'Standard')");
    }

    private static int insertRoom(int cinemaId, String name) throws SQLException {
        return insertReturningId("INSERT INTO Rooms (CinemaId, Name, Status) VALUES ("
                + cinemaId + ", N'" + name + " " + System.nanoTime() + "', 'active')");
    }

    private static int insertShowtime(int cinemaId, int roomId) throws SQLException {
        int filmId = scalar("SELECT TOP 1 Id FROM Films ORDER BY Id");
        exec("INSERT INTO CinemaFilms (CinemaId, FilmId) VALUES (" + cinemaId + ", " + filmId + ")");
        return insertReturningId("INSERT INTO Showtimes (FilmId, CinemaId, RoomId, StartTime, EndTime, BasePrice)"
                + " VALUES (" + filmId + ", " + cinemaId + ", " + roomId
                + ", DATEADD(DAY, -30, GETDATE()), DATEADD(MINUTE, -1680, GETDATE()), 90000)");
    }

    private static int insertPaidOrder(int showtimeId) throws SQLException {
        return insertReturningId("INSERT INTO Orders (UserId, ShowtimeId, SeatSubtotal, ComboSubtotal,"
                + " DiscountAmount, TotalAmount, TicketCode, PaymentMethod, PaymentStatus, OrderStatus)"
                + " VALUES (1, " + showtimeId + ", 90000, 0, 0, 90000, 'ITCIN" + System.nanoTime()
                + "', 'card', 'paid', 'confirmed')");
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
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static String text(String sql) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private static void exec(String sql) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.executeUpdate();
        }
    }

    /** {@link RevenueRow} khong co {@code equals()}, nen so sanh bang anh chieu tung o mot. */
    private static List<String> revenueSnapshot() {
        return adminService.dailyRevenueRows().stream()
                .map(row -> row.getLabel() + "|" + row.getOrderCount() + "|" + row.getTotalRevenue())
                .toList();
    }
}
