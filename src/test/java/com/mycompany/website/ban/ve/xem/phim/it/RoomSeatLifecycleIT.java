package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.Seat;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
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
 * Vong doi khoa ghe (D-01) — dung ca "QA Phong Rap7".
 *
 * <p>Loi goc: {@code seatIsReferenced()} co hai ve {@code OR}, ve thu hai khong co rang buoc thoi
 * gian nao. Bat ky ghe nao tung nam trong mot don chua huy deu bi coi la "dang duoc tham chieu"
 * <b>vinh vien</b>, ke ca khi suat chieu da ket thuc tu nhieu thang truoc. Cung loi do con o
 * bieu thuc {@code Occupied} cua {@code getSeatsByRoomId()} — cho ay con te hon vi khong loc ca
 * don da huy, nen ghe cua mot don <i>da huy</i> cung bi khoa mai mai.</p>
 *
 * <p>Moc thoi gian dung la {@code EndTime > GETDATE()}: het phim la het khoa, nhung suat dang
 * chieu do thi van phai chan.</p>
 */
@Tag("it")
@DisplayName("Ghe chi bi khoa khi suat chieu chua ket thuc (D-01)")
public class RoomSeatLifecycleIT {

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
     * Ca chinh: suat chieu ket thuc tu 6 ngay truoc, ghe da ban xong. Truoc khi sua, thao tac nay
     * nem 409 "đã được giữ/bán/tham chiếu" mac du khong con hold nao.
     */
    @Test
    @DisplayName("Suat da ket thuc + don confirmed -> ghe SUA DUOC va khong con hien khoa")
    public void finishedShowtimeReleasesSeatForEditing() throws SQLException {
        Fixture fx = new Fixture("IT ghe suat da xong");
        try {
            fx.showtime(-6 * 24, 120);
            fx.bookSeat(0, "booked", "confirmed");

            assertFalse(occupied(fx.roomId, "A1"),
                    "suat da ket thuc thi ghe khong con duoc coi la dang giu");

            adminService.saveCustomRoomSeats(fx.roomId, fx.layoutWith("A1", "vip"), admin);

            assertEquals("vip", seatType(fx.roomId, "A1"), "phai luu duoc loai ghe moi");
            assertEquals(1, scalar("SELECT COUNT(*) FROM OrderSeats WHERE OrderId = " + fx.orderId),
                    "don cu phai con nguyen ban ghi ghe");
            assertEquals("standard", orderSeatType(fx.orderId),
                    "OrderSeats luu ban sao rieng, sua so do khong duoc lam lech don da ban");
        } finally {
            fx.cleanup();
        }
    }

    /** Ghe A4/A5 phong 3: chi dinh mot don DA HUY ma van bi khoa vinh vien truoc khi sua. */
    @Test
    @DisplayName("Ghe chi dinh don DA HUY -> sua duoc, khong bi khoa")
    public void cancelledOrderDoesNotLockSeat() throws SQLException {
        Fixture fx = new Fixture("IT ghe don da huy");
        try {
            fx.showtime(-6 * 24, 120);
            fx.bookSeat(0, "available", "cancelled");

            assertFalse(occupied(fx.roomId, "A1"), "don da huy khong duoc khoa ghe");

            adminService.saveCustomRoomSeats(fx.roomId, fx.layoutWith("A1", "vip"), admin);
            assertEquals("vip", seatType(fx.roomId, "A1"));
        } finally {
            fx.cleanup();
        }
    }

    /** Chot bao ve that su: khong duoc noi long cho suat chua dien ra. */
    @Test
    @DisplayName("Suat TUONG LAI con ghe booked -> van 409")
    public void futureShowtimeStillLocksSeat() throws SQLException {
        Fixture fx = new Fixture("IT ghe suat tuong lai");
        try {
            fx.showtime(48, 120);
            fx.bookSeat(0, "booked", "confirmed");

            assertTrue(occupied(fx.roomId, "A1"), "suat chua dien ra thi ghe phai hien khoa");

            BookingException ex = assertThrows(BookingException.class,
                    () -> adminService.saveCustomRoomSeats(fx.roomId, fx.layoutWith("A1", "vip"), admin));
            assertEquals(409, ex.getStatusCode());
            assertTrue(ex.getMessage().contains("chưa kết thúc"), ex.getMessage());
            assertEquals("standard", seatType(fx.roomId, "A1"), "409 thi khong duoc ghi gi");
        } finally {
            fx.cleanup();
        }
    }

    /**
     * Vi sao moc la {@code EndTime} chu khong phai {@code StartTime}: suat bat dau tu 30 phut
     * truoc nhung con 90 phut nua moi het — khan gia dang ngoi trong phong.
     */
    @Test
    @DisplayName("Suat DANG CHIEU DO -> van 409 (moc la EndTime, khong phai StartTime)")
    public void inProgressShowtimeStillLocksSeat() throws SQLException {
        Fixture fx = new Fixture("IT ghe suat dang chieu");
        try {
            fx.showtimeMinutes(-30, 120);
            fx.bookSeat(0, "booked", "confirmed");

            assertTrue(occupied(fx.roomId, "A1"), "suat chua het thi ghe van phai khoa");

            BookingException ex = assertThrows(BookingException.class,
                    () -> adminService.saveCustomRoomSeats(fx.roomId, fx.layoutWith("A1", "vip"), admin));
            assertEquals(409, ex.getStatusCode());
        } finally {
            fx.cleanup();
        }
    }

    /** Suat tombstone khong con giu ghe nao — no da bi xoa khoi van hanh. */
    @Test
    @DisplayName("Suat tombstone SaleStatus='DELETED' khong khoa ghe")
    public void tombstonedShowtimeDoesNotLockSeat() throws SQLException {
        Fixture fx = new Fixture("IT ghe suat tombstone");
        try {
            fx.showtime(48, 120);
            fx.bookSeat(0, "booked", "confirmed");
            // CK_Showtimes_DeleteMetadata doi du ba cot metadata khi roi khoi ON_SALE.
            exec("UPDATE Showtimes SET SaleStatus = 'DELETED', DeleteRequestedAt = SYSDATETIME(),"
                    + " DeleteNotBefore = SYSDATETIME() WHERE Id = " + fx.showtimeId);

            assertFalse(occupied(fx.roomId, "A1"), "suat da tombstone thi khong con giu ghe");
            adminService.saveCustomRoomSeats(fx.roomId, fx.layoutWith("A1", "vip"), admin);
            assertEquals("vip", seatType(fx.roomId, "A1"));
        } finally {
            fx.cleanup();
        }
    }

    /**
     * Noi long chi ap cho duong SUA. Duong XOA CUNG van phai giu nguyen dieu kien cu, neu khong
     * la mat doi chieu cua don da ban (khoa ngoai {@code ShowtimeSeats.SeatId} la NO_ACTION).
     */
    @Test
    @DisplayName("Bo ghe co lich su -> NGUNG DUNG chu khong XOA, OrderSeats con nguyen")
    public void removingHistoricalSeatRetiresItInsteadOfDeleting() throws SQLException {
        Fixture fx = new Fixture("IT ghe bo khoi so do");
        try {
            fx.showtime(-6 * 24, 120);
            fx.bookSeat(0, "booked", "confirmed");
            int seatId = seatId(fx.roomId, "A1");

            List<Seat> withoutA1 = new ArrayList<>(fx.layout());
            withoutA1.removeIf(seat -> "A1".equals(seat.getSeatKey()));
            adminService.saveCustomRoomSeats(fx.roomId, withoutA1, admin);

            assertEquals(1, scalar("SELECT COUNT(*) FROM Seats WHERE Id = " + seatId),
                    "ghe co lich su khong duoc xoa cung");
            assertEquals(0, scalar("SELECT CAST(IsActive AS INT) FROM Seats WHERE Id = " + seatId),
                    "ghe phai chuyen sang ngung dung");
            assertEquals(1, scalar("SELECT COUNT(*) FROM OrderSeats WHERE OrderId = " + fx.orderId),
                    "don da ban phai con nguyen ban ghi ghe");
        } finally {
            fx.cleanup();
        }
    }

    /** Ghe chua tung co ShowtimeSeats nao thi xoa cung duoc, khong de rac lai trong so do. */
    @Test
    @DisplayName("Bo ghe chua co lich su -> XOA CUNG hang")
    public void removingVirginSeatDeletesTheRow() throws SQLException {
        Fixture fx = new Fixture("IT ghe chua dung");
        try {
            int seatId = seatId(fx.roomId, "A3");
            List<Seat> withoutA3 = new ArrayList<>(fx.layout());
            withoutA3.removeIf(seat -> "A3".equals(seat.getSeatKey()));

            adminService.saveCustomRoomSeats(fx.roomId, withoutA3, admin);

            assertEquals(0, scalar("SELECT COUNT(*) FROM Seats WHERE Id = " + seatId),
                    "ghe khong co lich su thi xoa han, khong de lai dong IsActive=0");
        } finally {
            fx.cleanup();
        }
    }

    // ---------------------------------------------------------------- fixtures

    /** Mot phong 3 ghe A1..A3 kem tuy chon suat chieu va don hang. */
    private static final class Fixture {
        private final int roomId;
        private final int cinemaId;
        private int showtimeId;
        private int orderId;
        private final List<Integer> showtimeSeatIds = new ArrayList<>();

        Fixture(String roomName) throws SQLException {
            cinemaId = scalar("SELECT TOP 1 Id FROM Cinemas ORDER BY Id");
            roomId = insertReturningId("INSERT INTO Rooms (CinemaId, Name, Status) VALUES ("
                    + cinemaId + ", N'" + roomName + " " + System.nanoTime() + "', 'active')");
            for (int i = 1; i <= 3; i++) {
                exec("INSERT INTO Seats (RoomId, RowLabel, SeatNumber, SeatType, SeatKey, PriceSurcharge, IsActive)"
                        + " VALUES (" + roomId + ", 'A', " + i + ", 'standard', 'A" + i + "', 0, 1)");
            }
        }

        void showtime(int startOffsetHours, int durationMinutes) throws SQLException {
            showtimeMinutes(startOffsetHours * 60, durationMinutes);
        }

        void showtimeMinutes(int startOffsetMinutes, int durationMinutes) throws SQLException {
            int filmId = scalar("SELECT TOP 1 Id FROM Films ORDER BY Id");
            showtimeId = insertReturningId("INSERT INTO Showtimes (FilmId, CinemaId, RoomId, StartTime, EndTime,"
                    + " BasePrice) VALUES (" + filmId + ", " + cinemaId + ", " + roomId
                    + ", DATEADD(MINUTE, " + startOffsetMinutes + ", GETDATE())"
                    + ", DATEADD(MINUTE, " + (startOffsetMinutes + durationMinutes) + ", GETDATE()), 90000)");
            try (Connection connection = DBConnection.getConnection();
                 PreparedStatement ps = connection.prepareStatement(
                         "SELECT Id FROM Seats WHERE RoomId = ? ORDER BY SeatNumber")) {
                ps.setInt(1, roomId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        showtimeSeatIds.add(insertReturningId(
                                "INSERT INTO ShowtimeSeats (ShowtimeId, SeatId, Status) VALUES ("
                                        + showtimeId + ", " + rs.getInt(1) + ", 'available')"));
                    }
                }
            }
        }

        /** Gan mot don hang vao ghe thu {@code index} va dat trang thai cho {@code ShowtimeSeats}. */
        void bookSeat(int index, String seatStatus, String orderStatus) throws SQLException {
            int showtimeSeatId = showtimeSeatIds.get(index);
            String paymentStatus = "cancelled".equals(orderStatus) ? "pending" : "paid";
            orderId = insertReturningId("INSERT INTO Orders (UserId, ShowtimeId, SeatSubtotal, ComboSubtotal,"
                    + " DiscountAmount, TotalAmount, TicketCode, PaymentMethod, PaymentStatus, OrderStatus)"
                    + " VALUES (1, " + showtimeId + ", 90000, 0, 0, 90000, 'ITSEAT" + System.nanoTime()
                    + "', 'card', '" + paymentStatus + "', '" + orderStatus + "')");
            exec("INSERT INTO OrderSeats (OrderId, ShowtimeSeatId, SeatKey, SeatType, UnitPrice) VALUES ("
                    + orderId + ", " + showtimeSeatId + ", 'A1', 'standard', 90000)");
            exec("UPDATE ShowtimeSeats SET Status = '" + seatStatus + "' WHERE Id = " + showtimeSeatId);
        }

        List<Seat> layout() {
            List<Seat> seats = new ArrayList<>();
            for (int i = 1; i <= 3; i++) {
                seats.add(seat("A", i, "standard"));
            }
            return seats;
        }

        List<Seat> layoutWith(String seatKey, String seatType) {
            List<Seat> seats = layout();
            seats.stream().filter(seat -> seatKey.equals(seat.getSeatKey()))
                    .forEach(seat -> seat.setSeatType(seatType));
            return seats;
        }

        private Seat seat(String row, int number, String type) {
            Seat seat = new Seat();
            seat.setRoomId(roomId);
            seat.setRowLabel(row);
            seat.setSeatNumber(number);
            seat.setSeatType(type);
            seat.setSeatKey(row + number);
            seat.setPriceSurcharge(BigDecimal.ZERO);
            return seat;
        }

        void cleanup() throws SQLException {
            if (orderId > 0) {
                exec("DELETE FROM OrderSeats WHERE OrderId = " + orderId);
                exec("DELETE FROM Orders WHERE Id = " + orderId);
            }
            if (showtimeId > 0) {
                exec("DELETE FROM ShowtimeSeats WHERE ShowtimeId = " + showtimeId);
                exec("DELETE FROM Showtimes WHERE Id = " + showtimeId);
            }
            exec("DELETE FROM AuditLogs WHERE TargetType = 'Room' AND TargetId = '" + roomId + "'");
            exec("DELETE FROM Seats WHERE RoomId = " + roomId);
            exec("DELETE FROM Rooms WHERE Id = " + roomId);
        }
    }

    private static boolean occupied(int roomId, String seatKey) {
        return adminService.getSeatsByRoomId(roomId).stream()
                .filter(seat -> seatKey.equals(seat.getSeatKey()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("khong tim thay ghe " + seatKey))
                .isOccupied();
    }

    private static String seatType(int roomId, String seatKey) throws SQLException {
        return text("SELECT SeatType FROM Seats WHERE RoomId = " + roomId
                + " AND SeatKey = '" + seatKey + "' AND IsActive = 1");
    }

    private static String orderSeatType(int orderId) throws SQLException {
        return text("SELECT TOP 1 SeatType FROM OrderSeats WHERE OrderId = " + orderId);
    }

    private static int seatId(int roomId, String seatKey) throws SQLException {
        return scalar("SELECT Id FROM Seats WHERE RoomId = " + roomId + " AND SeatKey = '" + seatKey + "'");
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
}
