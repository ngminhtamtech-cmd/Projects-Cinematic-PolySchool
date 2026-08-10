package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.model.ShowtimeSeat;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import com.mycompany.website.ban.ve.xem.phim.service.BookingService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("it")
public class PayOrderIT {

    static {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    private final BookingService bookingService = new BookingService();

    @BeforeAll
    public static void setUpTestDb() {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
        DBConnection.shutdown();
    }

    @org.junit.jupiter.api.BeforeEach
    public void resetShowtimeSeats() throws Exception {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps0a = conn.prepareStatement(
                        "DELETE FROM Invoices WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = 3)");
                PreparedStatement ps0b = conn.prepareStatement(
                        "DELETE FROM PromotionUsage WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = 3)");
                PreparedStatement ps0c = conn.prepareStatement(
                        "DELETE FROM RefundTransactions WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = 3)");
                PreparedStatement ps1 = conn.prepareStatement(
                        "DELETE FROM OrderSeats WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = 3)");
                PreparedStatement ps2 = conn.prepareStatement("DELETE FROM Orders WHERE ShowtimeId = 3");
                PreparedStatement ps3 = conn.prepareStatement(
                        "UPDATE ShowtimeSeats SET Status = 'available', HeldByUserId = NULL, HeldUntil = NULL WHERE ShowtimeId = 3 AND Status != 'maintenance'")) {
            ps0a.executeUpdate();
            ps0b.executeUpdate();
            ps0c.executeUpdate();
            ps1.executeUpdate();
            ps2.executeUpdate();
            ps3.executeUpdate();
        }
    }

    @AfterAll
    public static void tearDown() {
        DBConnection.shutdown();
    }

    @Test
    @DisplayName("Invalid payment method must throw BookingException 400")
    public void testInvalidPaymentMethodThrows400() {
        List<ShowtimeSeat> seats = bookingService.getSeatMap(3);
        ShowtimeSeat avail = seats.stream()
                .filter(s -> s.isAvailableFor(1) && !"couple".equalsIgnoreCase(s.getSeatType())).findFirst()
                .orElseThrow();

        assertThrows(BookingException.class, () -> {
            bookingService.createDraftOrder(1, 3, List.of(avail.getId()), Map.of(), null, "bitcoin");
        });
    }

    @Test
    @DisplayName("Idempotency key prevents duplicate order creation")
    public void testIdempotencyKeyForDraftOrder() {
        String key = "IDEM-" + UUID.randomUUID().toString();
        List<ShowtimeSeat> seats = bookingService.getSeatMap(3);
        ShowtimeSeat avail = seats.stream()
                .filter(s -> s.isAvailableFor(1) && !"couple".equalsIgnoreCase(s.getSeatType())).findFirst()
                .orElseThrow();

        OrderRecord order1 = bookingService.createDraftOrder(1, 3, List.of(avail.getId()), Map.of(), null, "card", key);
        assertNotNull(order1);
        assertEquals(key, order1.getIdempotencyKey());

        // Calling with exact same idempotencyKey returns order1 directly
        OrderRecord order2 = bookingService.createDraftOrder(1, 3, List.of(avail.getId()), Map.of(), null, "card", key);
        assertEquals(order1.getId(), order2.getId());
    }

    @Test
    @DisplayName("payment.mode=live without live adapter must throw BookingException 503")
    public void testLivePaymentModeReturns503() throws Exception {
        // Set payment.mode to live in SystemSettings
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "MERGE SystemSettings AS target USING (SELECT 'payment.mode' AS SettingKey) AS source " +
                                "ON target.SettingKey = source.SettingKey WHEN MATCHED THEN UPDATE SET SettingValue = 'live' "
                                +
                                "WHEN NOT MATCHED THEN INSERT (SettingKey, SettingValue) VALUES ('payment.mode', 'live');")) {
            ps.executeUpdate();
        }

        List<ShowtimeSeat> seats = bookingService.getSeatMap(3);
        ShowtimeSeat avail = seats.stream()
                .filter(s -> s.isAvailableFor(1) && !"couple".equalsIgnoreCase(s.getSeatType())).findFirst()
                .orElseThrow();

        OrderRecord draft = bookingService.createDraftOrder(1, 3, List.of(avail.getId()), Map.of(), null, "card");

        BookingException ex = assertThrows(BookingException.class, () -> {
            bookingService.payOrder(1, draft.getId(), Map.of(), null, "card");
        });
        assertEquals(503, ex.getStatusCode());

        // Revert setting to simulated
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE SystemSettings SET SettingValue = 'simulated' WHERE SettingKey = 'payment.mode'")) {
            ps.executeUpdate();
        }
    }

    @Test
    @DisplayName("Simulated card payment generates SIM- transaction ID and sets paid status")
    public void testSimulatedCardPayment() throws Exception {
        // Reset payment.mode to simulated
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "MERGE SystemSettings AS target USING (SELECT 'payment.mode' AS SettingKey) AS source " +
                                "ON target.SettingKey = source.SettingKey WHEN MATCHED THEN UPDATE SET SettingValue = 'simulated' "
                                +
                                "WHEN NOT MATCHED THEN INSERT (SettingKey, SettingValue) VALUES ('payment.mode', 'simulated');")) {
            ps.executeUpdate();
        }

        List<ShowtimeSeat> seats = bookingService.getSeatMap(3);
        ShowtimeSeat avail = seats.stream()
                .filter(s -> s.isAvailableFor(1) && !"couple".equalsIgnoreCase(s.getSeatType())).findFirst()
                .orElseThrow();

        OrderRecord draft = bookingService.createDraftOrder(1, 3, List.of(avail.getId()), Map.of(), null, "card");
        OrderRecord paid = bookingService.payOrder(1, draft.getId(), Map.of(), null, "card");

        assertEquals("paid", paid.getPaymentStatus());
        assertEquals("confirmed", paid.getOrderStatus());
        assertEquals("simulated", paid.getPaymentProvider());
        assertTrue(paid.getTransactionId().startsWith("SIM-"));
    }
}
