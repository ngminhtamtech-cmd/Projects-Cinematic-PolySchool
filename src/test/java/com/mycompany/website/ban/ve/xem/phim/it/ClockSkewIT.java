package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.model.ShowtimeSeat;
import com.mycompany.website.ban.ve.xem.phim.service.BookingService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("it")
public class ClockSkewIT {

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
    @DisplayName("DB clock is the single source of time truth: HeldUntil is calculated using DB DATEADD")
    public void testDbClockSingleSourceOfTruth() throws Exception {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps1 = conn.prepareStatement("DELETE FROM Invoices WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = 3); DELETE FROM PromotionUsage WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = 3); DELETE FROM OrderSeats WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = 3)");
             PreparedStatement ps2 = conn.prepareStatement("DELETE FROM Orders WHERE ShowtimeId = 3");
             PreparedStatement ps3 = conn.prepareStatement("UPDATE ShowtimeSeats SET Status = 'available', HeldByUserId = NULL, HeldUntil = NULL WHERE ShowtimeId = 3 AND Status != 'maintenance'")) {
            ps1.executeUpdate();
            ps2.executeUpdate();
            ps3.executeUpdate();
        }

        List<ShowtimeSeat> seats = bookingService.getSeatMap(3);
        ShowtimeSeat avail = seats.stream().filter(s -> "available".equalsIgnoreCase(s.getStatus()) && !"couple".equalsIgnoreCase(s.getSeatType())).findFirst().orElseThrow();

        OrderRecord draft = bookingService.createDraftOrder(1, 3, List.of(avail.getId()), Map.of(), null, "card");
        assertNotNull(draft);

        // Query HeldUntil from database directly
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT HeldUntil, GETDATE() FROM ShowtimeSeats WHERE Id = ?")) {
            ps.setInt(1, avail.getId());
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                java.sql.Timestamp heldUntilTs = rs.getTimestamp(1);
                java.sql.Timestamp dbNowTs = rs.getTimestamp(2);

                assertNotNull(heldUntilTs);
                assertNotNull(dbNowTs);

                long diffSeconds = (heldUntilTs.getTime() - dbNowTs.getTime()) / 1000;
                // Hold is 10 minutes = 600 seconds (allowing +/- 10 seconds for query execution time)
                assertTrue(diffSeconds >= 590 && diffSeconds <= 610, "HeldUntil should be ~600s after DB GETDATE(), got diff: " + diffSeconds + "s");
            }
        }
    }

    @Test
    @DisplayName("DBConnection.dbNow returns DB server timestamp")
    public void testDbNow() {
        LocalDateTime dbTime = DBConnection.dbNow();
        assertNotNull(dbTime);
    }
}
