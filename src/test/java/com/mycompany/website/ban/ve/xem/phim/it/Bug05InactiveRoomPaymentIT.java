package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.model.ShowtimeSeat;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import com.mycompany.website.ban.ve.xem.phim.service.BookingService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BUG-05 (INV-7) — khong nhan tien cho thu khong giao duoc.
 *
 * <p>{@code createDraftOrder} chan phong {@code inactive}, nhung {@code payOrder} chi goi
 * {@code ensureShowtimeBookable} (cutoff + vong doi phim) va <b>khong</b> kiem phong. Do thuc te:
 * quan ly chuyen phong sang inactive khi khach dang giu ghe, khach van thanh toan thanh cong vao
 * phong da ngung hoat dong.</p>
 *
 * <p>INV-7 phai dung o MOI duong thu tien, nen bai test kiem ca duong thu tien tai quay.</p>
 */
@Tag("it")
public class Bug05InactiveRoomPaymentIT {

    private static final int SHOWTIME_ID = 3;
    private static final int MEMBER = 1;

    static {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    private final BookingService bookingService = new BookingService();
    private final AdminService adminService = new AdminService();

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
    public void resetFixture() throws Exception {
        setRoomStatus("active");
        cleanShowtime();
    }

    @AfterEach
    public void restoreRoom() throws Exception {
        setRoomStatus("active");
        cleanShowtime();
    }

    @Test
    @DisplayName("BUG-05: phong chuyen inactive khi khach dang giu ghe thi khong duoc thanh toan")
    public void payMustRejectInactiveRoom() throws Exception {
        OrderRecord draft = bookingService.createDraftOrder(
                MEMBER, SHOWTIME_ID, List.of(firstAvailableSeatId()), Map.of(), null, "card");

        setRoomStatus("inactive");

        BookingException ex = assertThrows(BookingException.class,
                () -> bookingService.payOrder(MEMBER, draft.getId(), Map.of(), null, "card"));
        assertTrue(ex.getStatusCode() == 400 || ex.getStatusCode() == 409,
                "Phai tra 400/409, thuc te " + ex.getStatusCode());

        assertNotEquals("paid", paymentStatusOf(draft.getId()),
                "Don khong duoc thanh 'paid' vao phong da ngung hoat dong");
    }

    @Test
    @DisplayName("BUG-05: thu tien tai quay cung phai chiu cung mot chot")
    public void counterPaymentMustRejectInactiveRoom() throws Exception {
        OrderRecord draft = bookingService.createDraftOrder(
                MEMBER, SHOWTIME_ID, List.of(firstAvailableSeatId()), Map.of(), null, "counter");
        bookingService.payOrder(MEMBER, draft.getId(), Map.of(), null, "counter");

        setRoomStatus("inactive");

        BookingException ex = assertThrows(BookingException.class,
                () -> adminService.markCounterOrderPaid(draft.getId(), staffActor()));
        assertTrue(ex.getStatusCode() == 400 || ex.getStatusCode() == 409,
                "Phai tra 400/409, thuc te " + ex.getStatusCode());

        assertNotEquals("paid", paymentStatusOf(draft.getId()),
                "Quay khong duoc thu tien cho suat o phong da ngung hoat dong");
    }

    @Test
    @DisplayName("BUG-05: phong active van thanh toan binh thuong")
    public void activeRoomStillPaysNormally() throws Exception {
        OrderRecord draft = bookingService.createDraftOrder(
                MEMBER, SHOWTIME_ID, List.of(firstAvailableSeatId()), Map.of(), null, "card");

        OrderRecord paid = bookingService.payOrder(MEMBER, draft.getId(), Map.of(), null, "card");

        assertEquals("paid", paid.getPaymentStatus());
    }

    private static User staffActor() {
        User actor = new User();
        actor.setId(3);
        actor.setRole("staff");
        actor.setCinemaId(1);
        return actor;
    }

    private int firstAvailableSeatId() {
        List<ShowtimeSeat> seats = bookingService.getSeatMap(SHOWTIME_ID);
        return seats.stream()
                .filter(s -> s.isAvailableFor(MEMBER) && !"couple".equalsIgnoreCase(s.getSeatType()))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    private static String paymentStatusOf(int orderId) throws Exception {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT PaymentStatus FROM Orders WHERE Id = ?")) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("Khong tim thay don " + orderId);
                }
                return rs.getString(1);
            }
        }
    }

    private static void setRoomStatus(String status) throws Exception {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE Rooms SET Status = ? WHERE Id = (SELECT RoomId FROM Showtimes WHERE Id = ?)")) {
            ps.setString(1, status);
            ps.setInt(2, SHOWTIME_ID);
            ps.executeUpdate();
        }
    }

    private static void cleanShowtime() throws Exception {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps1 = conn.prepareStatement(
                     "DELETE FROM Invoices WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = 3)");
             PreparedStatement ps2 = conn.prepareStatement(
                     "DELETE FROM PromotionUsage WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = 3)");
             PreparedStatement ps3 = conn.prepareStatement(
                     "DELETE FROM RefundTransactions WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = 3)");
             PreparedStatement ps4 = conn.prepareStatement(
                     "DELETE FROM OrderSeats WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = 3)");
             PreparedStatement ps5 = conn.prepareStatement("DELETE FROM Orders WHERE ShowtimeId = 3");
             PreparedStatement ps6 = conn.prepareStatement(
                     "UPDATE ShowtimeSeats SET Status = 'available', HeldByUserId = NULL, HeldAt = NULL,"
                     + " HeldUntil = NULL WHERE ShowtimeId = 3 AND Status != 'maintenance'")) {
            ps1.executeUpdate();
            ps2.executeUpdate();
            ps3.executeUpdate();
            ps4.executeUpdate();
            ps5.executeUpdate();
            ps6.executeUpdate();
        }
    }
}
