package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.model.ShowtimeSeat;
import com.mycompany.website.ban.ve.xem.phim.service.BookingService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("it")
public class SingleConnectionIT {

    static {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    private final BookingService bookingService = new BookingService();

    @BeforeAll
    public static void setUpTestDb() {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
        DBConnection.shutdown();
    }

    @AfterAll
    public static void tearDown() {
        DBConnection.shutdown();
    }

    @Test
    @DisplayName("Pay order executes with zero connection pool exhaustion under pool size = 10")
    public void testPayOrderDoesNotExhaustConnectionPool() throws Exception {
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

        List<ShowtimeSeat> seats = bookingService.getSeatMap(3);
        ShowtimeSeat avail = seats.stream().filter(s -> "available".equalsIgnoreCase(s.getStatus()) && !"couple".equalsIgnoreCase(s.getSeatType())).findFirst().orElseThrow();

        OrderRecord draft = bookingService.createDraftOrder(1, 3, List.of(avail.getId()), Map.of(), "PUBLIC10", "card");
        assertNotNull(draft);

        OrderRecord paid = bookingService.payOrder(1, draft.getId(), Map.of(), "PUBLIC10", "card");
        assertNotNull(paid);
        assertEquals("paid", paid.getPaymentStatus());
        assertEquals("confirmed", paid.getOrderStatus());
    }
}
