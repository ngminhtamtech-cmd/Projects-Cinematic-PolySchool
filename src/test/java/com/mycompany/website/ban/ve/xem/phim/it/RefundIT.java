package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import com.mycompany.website.ban.ve.xem.phim.service.BookingService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("it")
public class RefundIT {

    private final BookingService bookingService = new BookingService();
    private final AdminService adminService = new AdminService();

    @BeforeAll
    public static void setUpConfig() {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    @org.junit.jupiter.api.BeforeEach
    public void resetShowtimeSeats() throws Exception {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps0a = conn.prepareStatement("DELETE FROM Invoices WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = 3)");
             PreparedStatement ps0b = conn.prepareStatement("DELETE FROM PromotionUsage WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = 3)");
             PreparedStatement ps0c = conn.prepareStatement("DELETE FROM RefundTransactions WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = 3)");
             PreparedStatement ps1 = conn.prepareStatement("DELETE FROM OrderSeats WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = 3)");
             PreparedStatement ps2 = conn.prepareStatement("DELETE FROM Orders WHERE ShowtimeId = 3");
             PreparedStatement ps3 = conn.prepareStatement("UPDATE ShowtimeSeats SET Status = 'available', HeldByUserId = NULL, HeldUntil = NULL WHERE ShowtimeId = 3 AND Status != 'maintenance'")) {
            ps0a.executeUpdate();
            ps0b.executeUpdate();
            ps0c.executeUpdate();
            ps1.executeUpdate();
            ps2.executeUpdate();
            ps3.executeUpdate();
        }
    }

    @Test
    public void testRefundOrderDeductsPointsAndTotalSpent() throws Exception {
        int userId = 3; // member_diamond
        int showtimeId = 3;
        List<com.mycompany.website.ban.ve.xem.phim.model.ShowtimeSeat> seats = bookingService.getSeatMap(3);
        com.mycompany.website.ban.ve.xem.phim.model.ShowtimeSeat avail = seats.stream()
                .filter(s -> s.isAvailableFor(userId) && !"couple".equalsIgnoreCase(s.getSeatType()))
                .findFirst().orElseThrow();
        int seatId = avail.getId();

        User adminUser = new User();
        adminUser.setId(5);
        adminUser.setRole("admin");

        // 1. Create & Pay Order
        OrderRecord draft = bookingService.createDraftOrder(userId, showtimeId, List.of(seatId), java.util.Map.of(), null, "card");
        OrderRecord paidOrder = bookingService.payOrder(userId, draft.getId(), java.util.Map.of(), null, "card", "IDEM-REFUND-TEST-001");
        BigDecimal orderTotal = paidOrder.getTotalAmount();

        // Check points before refund
        int initialPoints = 0;
        BigDecimal initialSpent = BigDecimal.ZERO;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT LoyaltyPoints, TotalSpent FROM Users WHERE Id = ?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                initialPoints = rs.getInt("LoyaltyPoints");
                initialSpent = rs.getBigDecimal("TotalSpent");
            }
        }

        // 2. Refund Order
        adminService.refundOrder(paidOrder.getId(), orderTotal, "Khách xin hoàn tiền", adminUser);

        // 3. Verify user points & total spent decreased
        int expectedDeductPoints = orderTotal.divide(BigDecimal.valueOf(1000), 0, java.math.RoundingMode.FLOOR).intValue();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT LoyaltyPoints, TotalSpent FROM Users WHERE Id = ?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(initialPoints - expectedDeductPoints, rs.getInt("LoyaltyPoints"));
                assertEquals(initialSpent.subtract(orderTotal), rs.getBigDecimal("TotalSpent"));
            }
        }

        // 4. Verify negative PointTransaction recorded
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT Points, Type FROM PointTransactions WHERE UserId = ? AND Type = 'REFUND_DEDUCT' ORDER BY CreatedAt DESC")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(-expectedDeductPoints, rs.getInt("Points"));
                assertEquals("REFUND_DEDUCT", rs.getString("Type"));
            }
        }

        // 5. Verify order state
        OrderRecord refundedOrder = adminService.findOrderById(paidOrder.getId()).orElseThrow();
        assertEquals("refunded", refundedOrder.getPaymentStatus());
        assertEquals("cancelled", refundedOrder.getOrderStatus());
        assertNotNull(refundedOrder.getRefundedAt());
        assertEquals(0, orderTotal.compareTo(refundedOrder.getRefundAmount()));
    }

    @Test
    public void testRefundValidationErrors() {
        User adminUser = new User();
        adminUser.setId(5);
        adminUser.setRole("admin");

        // Refunding non-existent order
        assertThrows(BookingException.class, () -> adminService.refundOrder(999999, BigDecimal.valueOf(100000), "Invalid", adminUser));
    }
}
