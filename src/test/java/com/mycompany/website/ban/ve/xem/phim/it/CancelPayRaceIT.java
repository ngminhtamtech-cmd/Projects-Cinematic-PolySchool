package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.model.ShowtimeSeat;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import com.mycompany.website.ban.ve.xem.phim.service.BookingService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P0 regression coverage for the member cancellation versus card-payment race. */
@Tag("it")
@DisplayName("P0 - cancelUserDraftOrder serializes with payOrder")
public class CancelPayRaceIT {
    private static final String FILM_TITLE = "P0-CANCEL-PAY-RACE";
    private static final String MEMBER_EMAIL = "p0-cancel-pay-race@test.local";

    static {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    private final BookingService bookingService = new BookingService();
    private int memberId;
    private int showtimeId;
    private int seatId;

    @BeforeAll
    static void setUpTestDatabase() {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
        DBConnection.shutdown();
    }

    @AfterAll
    static void shutDownPool() {
        DBConnection.shutdown();
    }

    @BeforeEach
    void createFixture() throws SQLException {
        assertTestDatabase();
        cleanupFixture();

        memberId = insert("""
                INSERT INTO Users
                    (FullName,Email,PasswordHash,Role,MembershipTier,LoyaltyPoints,
                     LifetimeEarnedPoints,TotalSpent,IsLocked,Deleted)
                SELECT 'P0 cancel/pay member', ?, PasswordHash, 'member', 'BRONZE', 0, 0, 0, 0, 0
                FROM Users WHERE Id=(SELECT MIN(Id) FROM Users)
                """, MEMBER_EMAIL);

        int cinemaId;
        int roomId;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     SELECT TOP (1) r.CinemaId, r.Id
                     FROM Rooms r
                     WHERE r.Status='active'
                       AND EXISTS (SELECT 1 FROM Seats se WHERE se.RoomId=r.Id)
                     ORDER BY r.Id
                     """);
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next(), "Test DB needs one active room with seats");
            cinemaId = rs.getInt(1);
            roomId = rs.getInt(2);
        }

        int filmId = insert("""
                INSERT INTO Films (Title,ReleaseDate,EndDate,DurationMinutes,Status)
                VALUES (?,DATEADD(DAY,-1,CAST(GETDATE() AS DATE)),
                        DATEADD(DAY,30,CAST(GETDATE() AS DATE)),120,'showing')
                """, FILM_TITLE);
        execute("INSERT INTO CinemaFilms (CinemaId,FilmId) VALUES (?,?)", cinemaId, filmId);
        showtimeId = insert("""
                INSERT INTO Showtimes
                    (FilmId,CinemaId,RoomId,StartTime,EndTime,BasePrice,Format,Version,Language)
                VALUES (?,?,?,DATEADD(MINUTE,90,GETDATE()),DATEADD(MINUTE,210,GETDATE()),
                        90000,'2D','Subtitle','Vietnamese')
                """, filmId, cinemaId, roomId);
        execute("""
                INSERT INTO ShowtimeSeats (ShowtimeId,SeatId,Status,ExtraFee)
                SELECT ?,Id,'available',0 FROM Seats WHERE RoomId=?
                """, showtimeId, roomId);
        seatId = bookingService.getSeatMap(showtimeId).stream()
                .filter(seat -> seat.isAvailableFor(memberId))
                .filter(seat -> !"couple".equalsIgnoreCase(seat.getSeatType()))
                .map(ShowtimeSeat::getId)
                .findFirst()
                .orElseThrow();
    }

    @AfterEach
    void cleanup() throws SQLException {
        cleanupFixture();
    }

    @Test
    @DisplayName("cancel locks first: pay cannot commit behind its stale read")
    void cancellationLockSerializesConcurrentPayment() throws Exception {
        OrderRecord draft = bookingService.createDraftOrder(
                memberId, showtimeId, List.of(seatId), Map.of(), null, "card");
        PausingBookingService pausingService = new PausingBookingService();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Throwable> cancellation = executor.submit(() -> capture(
                    () -> pausingService.cancelUserDraftOrder(memberId, draft.getId())));
            assertTrue(pausingService.orderRead.await(5, TimeUnit.SECONDS),
                    "Cancellation did not reach its order checkpoint");

            CountDownLatch paymentStarted = new CountDownLatch(1);
            Future<Throwable> payment = executor.submit(() -> {
                paymentStarted.countDown();
                return capture(() -> bookingService.payOrder(
                        memberId, draft.getId(), Map.of(), null, "card"));
            });
            assertTrue(paymentStarted.await(5, TimeUnit.SECONDS));

            boolean paidWhileCancellationPaused = awaitPaymentStatus(
                    draft.getId(), "paid", Duration.ofSeconds(3));
            pausingService.resumeCancellation.countDown();

            Throwable cancelFailure = cancellation.get(10, TimeUnit.SECONDS);
            Throwable payFailure = payment.get(10, TimeUnit.SECONDS);
            OrderState state = orderState(draft.getId());

            assertAll(
                    () -> assertFalse(paidWhileCancellationPaused,
                            "Payment committed while cancellation was paused after reading its order"),
                    () -> assertNull(cancelFailure, "The lock-first cancellation must win this schedule"),
                    () -> assertInstanceOf(BookingException.class, payFailure,
                            "Payment must fail after cancellation commits"),
                    () -> assertEquals("cancelled", state.orderStatus()),
                    () -> assertEquals("cancelled", state.paymentStatus()),
                    () -> assertNull(state.transactionId(), "Cancel-first outcome must not record a charge"),
                    () -> assertEquals("available", state.seatStatus()),
                    () -> assertEquals(0, pointTransactions(draft.getId()),
                            "Cancel-first outcome must not award loyalty points"));
        } finally {
            pausingService.resumeCancellation.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    @DisplayName("pay commits first: cancel returns 409 and cannot release a booked seat")
    void committedPaymentRejectsCancellationWithoutMutation() throws SQLException {
        OrderRecord draft = bookingService.createDraftOrder(
                memberId, showtimeId, List.of(seatId), Map.of(), null, "card");
        bookingService.payOrder(memberId, draft.getId(), Map.of(), null, "card");
        OrderState before = orderState(draft.getId());

        BookingException conflict = assertThrows(BookingException.class,
                () -> bookingService.cancelUserDraftOrder(memberId, draft.getId()));
        OrderState after = orderState(draft.getId());

        assertAll(
                () -> assertEquals(409, conflict.getStatusCode()),
                () -> assertEquals("paid", before.paymentStatus()),
                () -> assertEquals("confirmed", before.orderStatus()),
                () -> assertEquals("booked", before.seatStatus()),
                () -> assertNotNull(before.transactionId()),
                () -> assertEquals(before, after, "Rejected cancellation must not mutate paid order or seat"),
                () -> assertEquals(1, pointTransactions(draft.getId()),
                        "The one successful payment has exactly one loyalty ledger row"));
    }

    private boolean awaitPaymentStatus(int orderId, String expected, Duration timeout)
            throws SQLException, InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        do {
            if (expected.equals(scalarString("SELECT PaymentStatus FROM Orders WHERE Id=?", orderId))) {
                return true;
            }
            Thread.sleep(25);
        } while (System.nanoTime() < deadline);
        return false;
    }

    private OrderState orderState(int orderId) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     SELECT o.PaymentStatus,o.OrderStatus,o.TransactionId,ss.Status
                     FROM Orders o
                     JOIN OrderSeats os ON os.OrderId=o.Id
                     JOIN ShowtimeSeats ss ON ss.Id=os.ShowtimeSeatId
                     WHERE o.Id=?
                     """)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return new OrderState(rs.getString(1), rs.getString(2),
                        rs.getString(3), rs.getString(4));
            }
        }
    }

    private int pointTransactions(int orderId) throws SQLException {
        return scalarInt("SELECT COUNT(*) FROM PointTransactions WHERE OrderId=?", orderId);
    }

    private void cleanupFixture() throws SQLException {
        execute("""
                DELETE FROM AuditLogs
                WHERE ActorUserId IN (SELECT Id FROM Users WHERE Email=?)
                   OR (TargetType='Order' AND TRY_CONVERT(INT,TargetId) IN (
                     SELECT o.Id FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId
                     JOIN Films f ON f.Id=s.FilmId WHERE f.Title=?));
                DELETE FROM PointTransactions WHERE OrderId IN (
                  SELECT o.Id FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId
                  JOIN Films f ON f.Id=s.FilmId WHERE f.Title=?);
                DELETE FROM Invoices WHERE OrderId IN (
                  SELECT o.Id FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId
                  JOIN Films f ON f.Id=s.FilmId WHERE f.Title=?);
                DELETE FROM PromotionUsage WHERE OrderId IN (
                  SELECT o.Id FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId
                  JOIN Films f ON f.Id=s.FilmId WHERE f.Title=?);
                DELETE FROM RefundTransactions WHERE OrderId IN (
                  SELECT o.Id FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId
                  JOIN Films f ON f.Id=s.FilmId WHERE f.Title=?);
                DELETE FROM OrderComboFoods WHERE OrderId IN (
                  SELECT o.Id FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId
                  JOIN Films f ON f.Id=s.FilmId WHERE f.Title=?);
                DELETE FROM OrderSeats WHERE OrderId IN (
                  SELECT o.Id FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId
                  JOIN Films f ON f.Id=s.FilmId WHERE f.Title=?);
                DELETE FROM Orders WHERE ShowtimeId IN (
                  SELECT s.Id FROM Showtimes s JOIN Films f ON f.Id=s.FilmId WHERE f.Title=?);
                DELETE FROM ShowtimeSeats WHERE ShowtimeId IN (
                  SELECT s.Id FROM Showtimes s JOIN Films f ON f.Id=s.FilmId WHERE f.Title=?);
                DELETE FROM Showtimes WHERE FilmId IN (SELECT Id FROM Films WHERE Title=?);
                DELETE FROM CinemaFilms WHERE FilmId IN (SELECT Id FROM Films WHERE Title=?);
                DELETE FROM Films WHERE Title=?;
                DELETE FROM Users WHERE Email=?;
                """, MEMBER_EMAIL, FILM_TITLE, FILM_TITLE, FILM_TITLE, FILM_TITLE, FILM_TITLE,
                FILM_TITLE, FILM_TITLE, FILM_TITLE, FILM_TITLE, FILM_TITLE, FILM_TITLE,
                FILM_TITLE, MEMBER_EMAIL);
    }

    private int insert(String sql, Object... values) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            bind(ps, values);
            assertEquals(1, ps.executeUpdate());
            try (ResultSet keys = ps.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getInt(1);
            }
        }
    }

    private void execute(String sql, Object... values) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, values);
            ps.executeUpdate();
        }
    }

    private int scalarInt(String sql, Object... values) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, values);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private String scalarString(String sql, Object... values) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, values);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getString(1);
            }
        }
    }

    private void bind(PreparedStatement ps, Object... values) throws SQLException {
        for (int index = 0; index < values.length; index++) {
            ps.setObject(index + 1, values[index]);
        }
    }

    private void assertTestDatabase() throws SQLException {
        assertEquals(System.getProperty("cinebook.it.database", "CineBookIT_REQUIRED"), scalarString("SELECT DB_NAME()"));
    }

    private static Throwable capture(ThrowingAction action) {
        try {
            action.run();
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    private record OrderState(
            String paymentStatus, String orderStatus, String transactionId, String seatStatus) {
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }

    private static final class PausingBookingService extends BookingService {
        private final CountDownLatch orderRead = new CountDownLatch(1);
        private final CountDownLatch resumeCancellation = new CountDownLatch(1);

        @Override
        protected void afterCancellationOrderRead(int orderId) {
            orderRead.countDown();
            try {
                if (!resumeCancellation.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to resume cancellation");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Cancellation checkpoint interrupted", ex);
            }
        }
    }
}
