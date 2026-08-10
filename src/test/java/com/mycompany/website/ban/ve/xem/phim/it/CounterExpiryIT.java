package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
import com.mycompany.website.ban.ve.xem.phim.service.BookingService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("it")
public class CounterExpiryIT {

    private final BookingService bookingService = new BookingService();
    private final AdminService adminService = new AdminService();

    @BeforeAll
    public static void setUpConfig() {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    @org.junit.jupiter.api.BeforeEach
    public void resetShowtimeSeats() throws Exception {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps0 = conn.prepareStatement(
                        "DELETE FROM Invoices WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = 3); DELETE FROM PromotionUsage WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = 3); DELETE FROM RefundTransactions WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = 3)");
                PreparedStatement ps1 = conn.prepareStatement(
                        "DELETE FROM OrderSeats WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = 3)");
                PreparedStatement ps2 = conn.prepareStatement("DELETE FROM Orders WHERE ShowtimeId = 3");
                PreparedStatement ps3 = conn.prepareStatement(
                        "UPDATE ShowtimeSeats SET Status = 'available', HeldByUserId = NULL, HeldUntil = NULL WHERE ShowtimeId = 3 AND Status != 'maintenance'")) {
            ps0.executeUpdate();
            ps1.executeUpdate();
            ps2.executeUpdate();
            ps3.executeUpdate();
        }
    }

    @Test
    public void testExpiredCounterOrderIsCancelledAndSeatReleased() throws Exception {
        int userId = 2;
        int showtimeId = 3;
        List<com.mycompany.website.ban.ve.xem.phim.model.ShowtimeSeat> seats = bookingService.getSeatMap(3);
        com.mycompany.website.ban.ve.xem.phim.model.ShowtimeSeat avail = seats.stream()
                .filter(s -> s.isAvailableFor(userId) && !"couple".equalsIgnoreCase(s.getSeatType()))
                .findFirst().orElseThrow();
        int seatId = avail.getId();

        // 1. Create & confirm counter order
        OrderRecord draft = bookingService.createDraftOrder(userId, showtimeId, List.of(seatId), java.util.Map.of(),
                null, "counter");
        OrderRecord confirmedCounterOrder = bookingService.payOrder(userId, draft.getId(), java.util.Map.of(), null,
                "counter");
        int orderId = confirmedCounterOrder.getId();

        // 2. Set CounterExpiresAt to past timestamp in DB
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE Orders SET CounterExpiresAt = DATEADD(MINUTE, -10, GETDATE()) WHERE Id = ?")) {
            ps.setInt(1, orderId);
            ps.executeUpdate();
        }

        // 3. Run cancelExpiredCounterOrders
        int cancelledCount = adminService.cancelExpiredCounterOrders();
        assertTrue(cancelledCount >= 1);

        // 4. Verify order status is cancelled
        OrderRecord cancelledOrder = adminService.findOrderById(orderId).orElseThrow();
        assertEquals("cancelled", cancelledOrder.getOrderStatus());

        // 5. Verify seat status is released back to available
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT Status FROM ShowtimeSeats WHERE Id = ?")) {
            ps.setInt(1, seatId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("available", rs.getString("Status"));
            }
        }
    }
}
