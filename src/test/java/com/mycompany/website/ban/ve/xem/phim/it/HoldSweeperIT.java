package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.OrderHoldStatus;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.model.ShowtimeSeat;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import com.mycompany.website.ban.ve.xem.phim.service.BookingService;
import com.mycompany.website.ban.ve.xem.phim.service.HoldSweeper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P04 / B5 + B3: sweeper nen va han giu ghe that.
 *
 * <p>Chay tren {@code CineBookDB_Test}, suat chieu 3 (suat tuong lai cua fixture).</p>
 */
@Tag("it")
public class HoldSweeperIT {

    private static final int SHOWTIME_ID = 3;
    private static final int MEMBER_ID = 2;

    private final BookingService bookingService = new BookingService();
    private final HoldSweeper sweeper = new HoldSweeper();

    @BeforeAll
    public static void setUpConfig() {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    @BeforeEach
    public void resetShowtimeSeats() throws Exception {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps0a = conn.prepareStatement(
                     "DELETE FROM Invoices WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = ?)");
             PreparedStatement ps0b = conn.prepareStatement(
                     "DELETE FROM PromotionUsage WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = ?)");
             PreparedStatement ps0c = conn.prepareStatement(
                     "DELETE FROM RefundTransactions WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = ?)");
             PreparedStatement ps1 = conn.prepareStatement(
                     "DELETE FROM OrderSeats WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = ?)");
             PreparedStatement ps2 = conn.prepareStatement("DELETE FROM Orders WHERE ShowtimeId = ?");
             PreparedStatement ps3 = conn.prepareStatement(
                     "UPDATE ShowtimeSeats SET Status = 'available', HeldByUserId = NULL, HeldAt = NULL, HeldUntil = NULL"
                             + " WHERE ShowtimeId = ? AND Status != 'maintenance'");
             PreparedStatement ps4 = conn.prepareStatement(
                     "DELETE FROM SystemSettings WHERE SettingKey = '" + HoldSweeper.SETTING_ENABLED + "'")) {
            ps0a.setInt(1, SHOWTIME_ID);
            ps0b.setInt(1, SHOWTIME_ID);
            ps0c.setInt(1, SHOWTIME_ID);
            ps1.setInt(1, SHOWTIME_ID);
            ps2.setInt(1, SHOWTIME_ID);
            ps3.setInt(1, SHOWTIME_ID);
            ps0a.executeUpdate();
            ps0b.executeUpdate();
            ps0c.executeUpdate();
            ps1.executeUpdate();
            ps2.executeUpdate();
            ps3.executeUpdate();
            ps4.executeUpdate();
        }
    }

    // ------------------------------------------------------------------ B5

    @Test
    public void testExpiredHoldIsReleased() throws Exception {
        int seatId = firstAvailableSeatId();
        bookingService.createDraftOrder(MEMBER_ID, SHOWTIME_ID, List.of(seatId), Map.of(), null, "card");
        assertEquals("held", seatStatus(seatId), "Sau khi tao don nhap, ghe phai o trang thai held");

        expireHold(seatId);
        HoldSweeper.SweepResult result = sweeper.sweepOnce();

        assertFalse(result.isSkipped(), "Sweeper phai chay duoc");
        assertTrue(result.getReleasedSeats() >= 1, "Phai tra it nhat 1 ghe giu qua han");
        assertEquals("available", seatStatus(seatId), "Ghe giu qua han phai tro ve available");
    }

    @Test
    public void testOrphanDraftOrderIsCancelledWithAutoExpiredReason() throws Exception {
        int seatId = firstAvailableSeatId();
        OrderRecord draft = bookingService.createDraftOrder(
                MEMBER_ID, SHOWTIME_ID, List.of(seatId), Map.of(), null, "card");

        expireHold(seatId);
        ageOrder(draft.getId(), 60);

        HoldSweeper.SweepResult result = sweeper.sweepOnce();

        assertTrue(result.getCancelledOrphanOrders() >= 1, "Don nhap mo coi phai bi huy");
        assertEquals("cancelled", orderColumn(draft.getId(), "OrderStatus"));
        assertEquals(HoldSweeper.CANCEL_REASON, orderColumn(draft.getId(), "CancelReason"),
                "Sweeper phai ghi CancelReason = auto-expired");
        assertEquals("available", seatStatus(seatId));
    }

    @Test
    public void testExpiredCounterOrderIsCancelledAndSeatReleased() throws Exception {
        int seatId = firstAvailableSeatId();
        OrderRecord draft = bookingService.createDraftOrder(
                MEMBER_ID, SHOWTIME_ID, List.of(seatId), Map.of(), null, "counter");
        OrderRecord counterOrder = bookingService.payOrder(MEMBER_ID, draft.getId(), Map.of(), null, "counter");

        assertEquals("booked", seatStatus(seatId), "Don tai quay da xac nhan thi ghe phai la booked");
        expireCounterDeadline(counterOrder.getId());

        HoldSweeper.SweepResult result = sweeper.sweepOnce();

        assertTrue(result.getCancelledCounterOrders() >= 1, "Don tai quay qua han phai bi huy");
        assertEquals("cancelled", orderColumn(counterOrder.getId(), "OrderStatus"));
        assertEquals(HoldSweeper.CANCEL_REASON, orderColumn(counterOrder.getId(), "CancelReason"));
        assertEquals("available", seatStatus(seatId), "Ghe cua don tai quay bi huy phai duoc tra lai");
    }

    /** Chay hai lan lien tiep: vong sau khong duoc doi them gi (dieu kien de chay song song 2 instance). */
    @Test
    public void testSweepIsIdempotent() throws Exception {
        int seatId = firstAvailableSeatId();
        OrderRecord draft = bookingService.createDraftOrder(
                MEMBER_ID, SHOWTIME_ID, List.of(seatId), Map.of(), null, "card");
        expireHold(seatId);
        ageOrder(draft.getId(), 60);

        HoldSweeper.SweepResult first = sweeper.sweepOnce();
        assertTrue(first.getTotalTouched() > 0, "Vong dau phai co viec de lam");

        HoldSweeper.SweepResult second = sweeper.sweepOnce();
        assertEquals(0, second.getTotalTouched(), "Vong sau khong duoc dong vao gi nua");
    }

    @Test
    public void testSweeperCanBeDisabledBySetting() throws Exception {
        int seatId = firstAvailableSeatId();
        bookingService.createDraftOrder(MEMBER_ID, SHOWTIME_ID, List.of(seatId), Map.of(), null, "card");
        expireHold(seatId);

        setSweeperEnabled(false);
        try {
            HoldSweeper.SweepResult off = sweeper.sweepOnce();
            assertTrue(off.isSkipped(), "Cong tat = false thi sweeper phai bo qua vong nay");
            assertEquals("held", seatStatus(seatId), "Bi tat thi khong duoc dong vao ghe");
        } finally {
            setSweeperEnabled(true);
        }

        HoldSweeper.SweepResult on = sweeper.sweepOnce();
        assertFalse(on.isSkipped());
        assertEquals("available", seatStatus(seatId), "Bat lai thi ghe qua han phai duoc tra");
    }

    // ------------------------------------------------------------------ B3

    @Test
    public void testHoldStatusComesFromDatabase() throws Exception {
        int seatId = firstAvailableSeatId();
        OrderRecord draft = bookingService.createDraftOrder(
                MEMBER_ID, SHOWTIME_ID, List.of(seatId), Map.of(), null, "card");

        OrderHoldStatus status = bookingService.getHoldStatus(draft.getId(), MEMBER_ID);
        assertNotNull(status.getHeldUntil(), "Phai co HeldUntil that tu DB");
        assertFalse(status.isExpired(), "Don vua tao thi chua het han");
        assertEquals(1, status.getHeldSeatCount());
        assertTrue(status.getRemainingSeconds() > 0 && status.getRemainingSeconds() <= 600,
                "So giay con lai phai nam trong han giu 10 phut, nhan duoc: " + status.getRemainingSeconds());

        expireHold(seatId);
        OrderHoldStatus expired = bookingService.getHoldStatus(draft.getId(), MEMBER_ID);
        assertTrue(expired.isExpired(), "Het han giu thi status phai bao expired");
    }

    /** Khong duoc xem han giu ghe cua don nguoi khac (IDOR). */
    @Test
    public void testHoldStatusOfAnotherUserIsNotFound() throws Exception {
        int seatId = firstAvailableSeatId();
        OrderRecord draft = bookingService.createDraftOrder(
                MEMBER_ID, SHOWTIME_ID, List.of(seatId), Map.of(), null, "card");

        BookingException ex = assertThrows(BookingException.class,
                () -> bookingService.getHoldStatus(draft.getId(), MEMBER_ID + 1));
        assertEquals(404, ex.getStatusCode());
    }

    // -------------------------------------------------------------- helpers

    private int firstAvailableSeatId() {
        List<ShowtimeSeat> seats = bookingService.getSeatMap(SHOWTIME_ID);
        ShowtimeSeat seat = seats.stream()
                .filter(s -> s.isAvailableFor(MEMBER_ID) && !"couple".equalsIgnoreCase(s.getSeatType()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Fixture thieu ghe don kha dung o suat " + SHOWTIME_ID));
        return seat.getId();
    }

    /** Day han giu ghe ve qua khu bang gio DB, khong dung gio may chay test. */
    private void expireHold(int showtimeSeatId) throws Exception {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE ShowtimeSeats SET HeldUntil = DATEADD(MINUTE, -1, GETDATE()) WHERE Id = ?")) {
            ps.setInt(1, showtimeSeatId);
            ps.executeUpdate();
        }
    }

    private void ageOrder(int orderId, int minutes) throws Exception {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE Orders SET CreatedAt = DATEADD(MINUTE, ?, GETDATE()) WHERE Id = ?")) {
            ps.setInt(1, -minutes);
            ps.setInt(2, orderId);
            ps.executeUpdate();
        }
    }

    private void expireCounterDeadline(int orderId) throws Exception {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE Orders SET CounterExpiresAt = DATEADD(MINUTE, -10, GETDATE()) WHERE Id = ?")) {
            ps.setInt(1, orderId);
            ps.executeUpdate();
        }
    }

    private void setSweeperEnabled(boolean enabled) throws Exception {
        String sql = """
                MERGE SystemSettings AS target
                USING (SELECT ? AS SettingKey, ? AS SettingValue) AS src
                ON target.SettingKey = src.SettingKey
                WHEN MATCHED THEN UPDATE SET SettingValue = src.SettingValue, UpdatedAt = GETDATE()
                WHEN NOT MATCHED THEN INSERT (SettingKey, SettingValue, UpdatedAt)
                     VALUES (src.SettingKey, src.SettingValue, GETDATE());
                """;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, HoldSweeper.SETTING_ENABLED);
            ps.setString(2, String.valueOf(enabled));
            ps.executeUpdate();
        }
    }

    private String seatStatus(int showtimeSeatId) throws Exception {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT Status FROM ShowtimeSeats WHERE Id = ?")) {
            ps.setInt(1, showtimeSeatId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Khong tim thay ghe " + showtimeSeatId);
                return rs.getString(1);
            }
        }
    }

    private String orderColumn(int orderId, String column) throws Exception {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT " + column + " FROM Orders WHERE Id = ?")) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Khong tim thay don " + orderId);
                return rs.getString(1);
            }
        }
    }
}
