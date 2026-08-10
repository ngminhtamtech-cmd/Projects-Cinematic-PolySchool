package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.model.ShowtimeSeat;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import com.mycompany.website.ban.ve.xem.phim.service.BookingService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("it")
public class SeatHoldConcurrencyIT {
    private static final List<Integer> LOAD_USER_IDS = new ArrayList<>();

    static {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    private final BookingService bookingService = new BookingService();

    @BeforeAll
    public static void setUpTestDb() throws Exception {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
        DBConnection.shutdown();
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     WITH n AS (
                         SELECT TOP (100) ROW_NUMBER() OVER (ORDER BY (SELECT NULL)) rn
                         FROM sys.all_objects
                     )
                     INSERT INTO Users(FullName, Email, PasswordHash, Role, MembershipTier,
                                       TotalSpent, IsLocked, Deleted)
                     OUTPUT inserted.Id
                     SELECT CONCAT('P16 concurrent ', rn),
                            CONCAT('p16_concurrent_', rn, '@test.local'),
                            (SELECT TOP(1) PasswordHash FROM Users ORDER BY Id),
                            'member', 'BRONZE', 0, 0, 0
                     FROM n
                     WHERE NOT EXISTS (
                         SELECT 1 FROM Users u
                         WHERE u.Email=CONCAT('p16_concurrent_', rn, '@test.local')
                     )
                     """)) {
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) LOAD_USER_IDS.add(rs.getInt(1));
            }
        }
        if (LOAD_USER_IDS.size() < 100) {
            try (Connection connection = DBConnection.getConnection();
                 PreparedStatement ps = connection.prepareStatement(
                         "SELECT TOP(100) Id FROM Users WHERE Email LIKE 'p16_concurrent_%' ORDER BY Id");
                 java.sql.ResultSet rs = ps.executeQuery()) {
                LOAD_USER_IDS.clear();
                while (rs.next()) LOAD_USER_IDS.add(rs.getInt(1));
            }
        }
    }

    @AfterAll
    public static void tearDown() throws Exception {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     DELETE FROM Invoices WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId=3);
                     DELETE FROM PromotionUsage WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId=3);
                     DELETE FROM RefundTransactions WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId=3);
                     DELETE FROM OrderSeats WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId=3);
                     DELETE FROM Orders WHERE ShowtimeId=3;
                     UPDATE ShowtimeSeats SET Status='available', HeldByUserId=NULL, HeldUntil=NULL
                     WHERE ShowtimeId=3 AND Status<>'maintenance';
                     DELETE FROM Users WHERE Email LIKE 'p16_concurrent_%@test.local';
                     """)) {
            ps.executeUpdate();
        }
        DBConnection.shutdown();
    }

    @Test
    @DisplayName("100 concurrent threads trying to hold the same seat 50 times: exactly 1 succeeds per iteration")
    public void testConcurrentSeatHold50Iterations() throws Exception {
        int iterations = 50;
        int threadCount = 100;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        try {
            for (int iter = 1; iter <= iterations; iter++) {
                // Reset seats for showtime 3
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
                ShowtimeSeat targetSeat = seats.stream()
                        .filter(s -> "available".equalsIgnoreCase(s.getStatus()) && !"couple".equalsIgnoreCase(s.getSeatType()))
                        .findFirst()
                        .orElseThrow();

                AtomicInteger successCount = new AtomicInteger(0);
                AtomicInteger conflictCount = new AtomicInteger(0);
                ConcurrentLinkedQueue<String> failures = new ConcurrentLinkedQueue<>();
                java.util.Set<String> failureKinds = ConcurrentHashMap.newKeySet();
                List<Callable<Boolean>> tasks = new ArrayList<>();

                for (int i = 1; i <= threadCount; i++) {
                    final int userId = LOAD_USER_IDS.get(i - 1);
                    tasks.add(() -> {
                        try {
                            OrderRecord order = bookingService.createDraftOrder(userId, 3, List.of(targetSeat.getId()), Map.of(), null, "card");
                            if (order != null && order.getId() > 0) {
                                successCount.incrementAndGet();
                                return true;
                            }
                            failureKinds.add(order == null ? "returned null order"
                                    : "returned order id=" + order.getId() + ", ticket=" + order.getTicketCode());
                        } catch (BookingException ex) {
                            conflictCount.incrementAndGet();
                            failureKinds.add(ex.getStatusCode() + ": " + ex.getMessage());
                            if (failures.size() < 5) failures.add(ex.getStatusCode() + ": " + ex.getMessage());
                        } catch (Exception ex) {
                            conflictCount.incrementAndGet();
                            failureKinds.add(ex.getClass().getSimpleName() + ": " + ex.getMessage());
                            if (failures.size() < 5) failures.add(ex.getClass().getSimpleName() + ": " + ex.getMessage());
                        }
                        return false;
                    });
                }

                List<Future<Boolean>> futures = executor.invokeAll(tasks);
                for (Future<Boolean> f : futures) {
                    f.get();
                }

                assertEquals(1, successCount.get(), "Iteration " + iter
                        + ": Exactly 1 thread must succeed in holding seat; kinds=" + failureKinds
                        + "; samples=" + failures);
                assertEquals(threadCount - 1, conflictCount.get(), "Iteration " + iter + ": Remaining threads must be rejected with conflict");
            }
        } finally {
            executor.shutdown();
        }
    }
}
