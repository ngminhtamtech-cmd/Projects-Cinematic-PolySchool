package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.model.User;
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
public class CancelOrderIT {

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
    public void testCancelOrderReleasesSeatsAndAllowsRebooking() throws Exception {
        int showtimeId = 3;
        int userId1 = 1;
        int userId2 = 2;
        List<com.mycompany.website.ban.ve.xem.phim.model.ShowtimeSeat> seats = bookingService.getSeatMap(showtimeId);
        com.mycompany.website.ban.ve.xem.phim.model.ShowtimeSeat avail = seats.stream()
                .filter(s -> s.isAvailableFor(userId1) && !"couple".equalsIgnoreCase(s.getSeatType()))
                .findFirst().orElseThrow();
        int seatId = avail.getId();

        // 1. User 1 creates draft order
        OrderRecord draftOrder = bookingService.createDraftOrder(userId1, showtimeId, List.of(seatId),
                java.util.Map.of(), null, "card");
        assertNotNull(draftOrder);
        int orderId = draftOrder.getId();

        // Verify seat is held / booked
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT Status FROM ShowtimeSeats WHERE Id = ?")) {
            ps.setInt(1, seatId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("held", rs.getString("Status"));
            }
        }

        // 2. Admin cancels order
        User adminUser = new User();
        adminUser.setId(5);
        adminUser.setRole("admin");

        adminService.cancelOrder(orderId, adminUser, "Khách yêu cầu hủy đơn");

        // 3. Verify order status is cancelled
        OrderRecord cancelledOrder = adminService.findOrderById(orderId).orElseThrow();
        assertEquals("cancelled", cancelledOrder.getOrderStatus());
        assertNotNull(cancelledOrder.getCancelledAt());

        // 4. Verify seat status is released back to 'available'
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn
                        .prepareStatement("SELECT Status, HeldByUserId FROM ShowtimeSeats WHERE Id = ?")) {
            ps.setInt(1, seatId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("available", rs.getString("Status"));
                assertNull(rs.getString("HeldByUserId"));
            }
        }

        // 5. Verify User 2 can now book the exact same seat successfully
        OrderRecord newOrder = bookingService.createDraftOrder(userId2, showtimeId, List.of(seatId), java.util.Map.of(),
                null, "card");
        assertNotNull(newOrder);
        assertTrue(newOrder.getId() > 0);

        // Clean up user 2 draft order by cancelling it
        adminService.cancelOrder(newOrder.getId(), adminUser, "Cleanup test");
    }
}
