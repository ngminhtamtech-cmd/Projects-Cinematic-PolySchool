package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
import com.mycompany.website.ban.ve.xem.phim.service.BookingService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("it")
public class RevenueIT {

    private final BookingService bookingService = new BookingService();
    private final AdminService adminService = new AdminService();

    @BeforeAll
    public static void setUpConfig() {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    @org.junit.jupiter.api.BeforeEach
    public void resetShowtimeSeats() throws Exception {
        try (java.sql.Connection conn = com.mycompany.website.ban.ve.xem.phim.config.DBConnection.getConnection();
             java.sql.PreparedStatement ps0a = conn.prepareStatement("DELETE FROM Invoices WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = 3)");
             java.sql.PreparedStatement ps0b = conn.prepareStatement("DELETE FROM PromotionUsage WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = 3)");
             java.sql.PreparedStatement ps0c = conn.prepareStatement("DELETE FROM RefundTransactions WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = 3)");
             java.sql.PreparedStatement ps1 = conn.prepareStatement("DELETE FROM OrderSeats WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = 3)");
             java.sql.PreparedStatement ps2 = conn.prepareStatement("DELETE FROM Orders WHERE ShowtimeId = 3");
             java.sql.PreparedStatement ps3 = conn.prepareStatement("UPDATE ShowtimeSeats SET Status = 'available', HeldByUserId = NULL, HeldUntil = NULL WHERE ShowtimeId = 3 AND Status != 'maintenance'")) {
            ps0a.executeUpdate();
            ps0b.executeUpdate();
            ps0c.executeUpdate();
            ps1.executeUpdate();
            ps2.executeUpdate();
            ps3.executeUpdate();
        }
    }

    @Test
    public void testRevenueDecreasesAfterOrderCancellationAndRefund() {
        int userId = 2;
        int showtimeId = 3;
        List<com.mycompany.website.ban.ve.xem.phim.model.ShowtimeSeat> seats = bookingService.getSeatMap(3);
        com.mycompany.website.ban.ve.xem.phim.model.ShowtimeSeat avail = seats.stream()
                .filter(s -> s.isAvailableFor(userId) && !"couple".equalsIgnoreCase(s.getSeatType()))
                .findFirst().orElseThrow();
        int seatId = avail.getId();

        User adminUser = new User();
        adminUser.setId(5);
        adminUser.setRole("admin");

        // 1. Create and pay order
        OrderRecord draft = bookingService.createDraftOrder(userId, showtimeId, List.of(seatId), java.util.Map.of(), null, "card");
        OrderRecord paidOrder = bookingService.payOrder(userId, draft.getId(), java.util.Map.of(), null, "card", "IDEM-REV-TEST-001");
        assertEquals("paid", paidOrder.getPaymentStatus());

        // Get revenue before cancel
        BigDecimal initialRevenue = adminService.dailyRevenueRows().stream()
                .map(r -> r.getTotalRevenue())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 2. Refund order
        adminService.refundOrder(paidOrder.getId(), paidOrder.getTotalAmount(), "Test revenue refund", adminUser);

        // 3. Verify total revenue decreased
        BigDecimal revenueAfterRefund = adminService.dailyRevenueRows().stream()
                .map(r -> r.getTotalRevenue())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(0, initialRevenue.subtract(paidOrder.getTotalAmount()).compareTo(revenueAfterRefund));
    }
}
