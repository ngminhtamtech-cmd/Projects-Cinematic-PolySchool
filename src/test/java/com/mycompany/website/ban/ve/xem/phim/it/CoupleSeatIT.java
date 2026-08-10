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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("it")
public class CoupleSeatIT {

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
    @DisplayName("Splitting couple seats throws BookingException 400")
    public void testSplitCoupleSeatThrows400() throws Exception {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps1 = conn.prepareStatement("DELETE FROM Invoices WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = 3); DELETE FROM PromotionUsage WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = 3); DELETE FROM OrderSeats WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = 3)");
             PreparedStatement ps2 = conn.prepareStatement("DELETE FROM Orders WHERE ShowtimeId = 3");
             PreparedStatement ps3 = conn.prepareStatement("UPDATE ShowtimeSeats SET Status = 'available', HeldByUserId = NULL, HeldUntil = NULL WHERE ShowtimeId = 3 AND Status != 'maintenance'")) {
            ps1.executeUpdate();
            ps2.executeUpdate();
            ps3.executeUpdate();
        }

        List<ShowtimeSeat> seats = bookingService.getSeatMap(3);
        ShowtimeSeat couple1 = seats.stream().filter(s -> "couple".equalsIgnoreCase(s.getSeatType())).findFirst().orElseThrow();

        // Selecting only 1 of the couple pair must fail
        BookingException ex = assertThrows(BookingException.class, () -> {
            bookingService.createDraftOrder(1, 3, List.of(couple1.getId()), Map.of(), null, "card");
        });
        assertEquals(400, ex.getStatusCode());
    }

    @Test
    @DisplayName("Booking both couple seats together succeeds")
    public void testBookingBothCoupleSeatsSucceeds() throws Exception {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps1 = conn.prepareStatement("DELETE FROM Invoices WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = 3); DELETE FROM PromotionUsage WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = 3); DELETE FROM OrderSeats WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = 3)");
             PreparedStatement ps2 = conn.prepareStatement("DELETE FROM Orders WHERE ShowtimeId = 3");
             PreparedStatement ps3 = conn.prepareStatement("UPDATE ShowtimeSeats SET Status = 'available', HeldByUserId = NULL, HeldUntil = NULL WHERE ShowtimeId = 3 AND Status != 'maintenance'")) {
            ps1.executeUpdate();
            ps2.executeUpdate();
            ps3.executeUpdate();
        }

        List<ShowtimeSeat> seats = bookingService.getSeatMap(3);
        List<ShowtimeSeat> coupleSeats = seats.stream().filter(s -> "couple".equalsIgnoreCase(s.getSeatType())).toList();
        assertEquals(2, coupleSeats.size());

        List<Integer> coupleIds = coupleSeats.stream().map(ShowtimeSeat::getId).toList();
        OrderRecord order = bookingService.createDraftOrder(1, 3, coupleIds, Map.of(), null, "card");
        assertNotNull(order);
        assertEquals(2, order.getSeats().size());
    }
}
