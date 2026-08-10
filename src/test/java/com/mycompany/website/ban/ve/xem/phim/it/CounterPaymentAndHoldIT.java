package com.mycompany.website.ban.ve.xem.phim.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.dao.OrderDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcOrderDAO;
import com.mycompany.website.ban.ve.xem.phim.model.OrderHoldStatus;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.model.ShowtimeSeat;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import com.mycompany.website.ban.ve.xem.phim.service.BookingService;
import com.mycompany.website.ban.ve.xem.phim.service.StaffService;
import com.mycompany.website.ban.ve.xem.phim.util.BusinessClock;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * CB-ISS-004 va CB-ISS-005 — thanh toan tai quay khong duoc tu dong thanh "da tra tien",
 * va dong ho giu ghe phai lay so giay con lai tu DB.
 */
@Tag("it")
public class CounterPaymentAndHoldIT {
    private static final int SHOWTIME_ID = 3;
    private static final int MEMBER = 1;
    private static final int OTHER_MEMBER = 2;

    private final BookingService bookingService = new BookingService();
    private final AdminService adminService = new AdminService();
    private final OrderDAO orderDAO = new JdbcOrderDAO();

    @BeforeAll
    public static void setUpTestDb() {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
        DBConnection.shutdown();
    }

    @BeforeEach
    @AfterEach
    public void resetFixtures() throws Exception {
        BusinessClock.resetForTesting();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement children = conn.prepareStatement(
                     "DELETE FROM Invoices WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = " + SHOWTIME_ID + ");"
                     + " DELETE FROM PromotionUsage WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = " + SHOWTIME_ID + ");"
                     + " DELETE FROM RefundTransactions WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = " + SHOWTIME_ID + ")");
             PreparedStatement seats = conn.prepareStatement(
                     "DELETE FROM OrderSeats WHERE OrderId IN (SELECT Id FROM Orders WHERE ShowtimeId = " + SHOWTIME_ID + ")");
             PreparedStatement orders = conn.prepareStatement(
                     "DELETE FROM Orders WHERE ShowtimeId = " + SHOWTIME_ID);
             PreparedStatement showtimeSeats = conn.prepareStatement(
                     "UPDATE ShowtimeSeats SET Status = 'available', HeldByUserId = NULL, HeldUntil = NULL"
                             + " WHERE ShowtimeId = " + SHOWTIME_ID + " AND Status != 'maintenance'")) {
            children.executeUpdate();
            seats.executeUpdate();
            orders.executeUpdate();
            showtimeSeats.executeUpdate();
        }
    }

    private List<Integer> oneSeat() {
        List<ShowtimeSeat> seats = bookingService.getSeatMap(SHOWTIME_ID);
        List<Integer> ids = new ArrayList<>();
        for (ShowtimeSeat seat : seats) {
            if (seat.isAvailableFor(MEMBER) && !"couple".equalsIgnoreCase(seat.getSeatType())) {
                ids.add(seat.getId());
                break;
            }
        }
        assertEquals(1, ids.size(), "Fixture phai con it nhat mot ghe thuong");
        return ids;
    }

    private static User staff() {
        User user = new User();
        user.setId(3);
        user.setRole("staff");
        user.setCinemaId(1);
        return user;
    }

    private static LocalDateTime showtimeStart() throws Exception {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT StartTime FROM Showtimes WHERE Id = ?")) {
            ps.setInt(1, SHOWTIME_ID);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getTimestamp(1).toLocalDateTime();
            }
        }
    }

    @Test
    @DisplayName("CB-ISS-004: chon thanh toan tai quay thi don giu pending, khong tu dong thanh paid")
    public void testCounterOrderStaysUnpaidUntilStaffCollects() throws Exception {
        OrderRecord draft = bookingService.createDraftOrder(
                MEMBER, SHOWTIME_ID, oneSeat(), Map.of(), null, "counter");
        OrderRecord placed = bookingService.payOrder(MEMBER, draft.getId(), Map.of(), null, "counter");

        assertEquals("pending", placed.getPaymentStatus(), "Don tai quay khong duoc tu gan paid");
        assertEquals("confirmed", placed.getOrderStatus(), "Ghe van phai duoc giu cho khach");
        assertEquals("counter", placed.getPaymentProvider());
        assertNull(placed.getTransactionId(), "Khong duoc sinh giao dich gia cho don tai quay");

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT PaymentStatus, OrderStatus, CounterExpiresAt, TransactionId FROM Orders WHERE Id = ?")) {
            ps.setInt(1, draft.getId());
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("pending", rs.getString("PaymentStatus"));
                assertEquals("confirmed", rs.getString("OrderStatus"));
                assertNotNull(rs.getTimestamp("CounterExpiresAt"), "Don tai quay phai co han thu tien");
                assertNull(rs.getString("TransactionId"));
            }
        }

        // Quay ve phai thay "can thu tien", khong duoc cho vao phong.
        StaffService staffService = new StaffService();
        StaffService.TicketLookup lookup = staffService.lookupTicket(placed.getTicketCode(), staff());
        assertEquals(StaffService.Verdict.NEEDS_PAYMENT, lookup.getVerdict(), lookup.getMessage());
        assertTrue(lookup.isCanCollectPayment());
        assertTrue(!lookup.isCanCheckIn(), "Chua tra tien thi khong duoc hien nut check-in");
        assertThrows(BookingException.class, () -> staffService.checkIn(placed.getTicketCode(), staff()));

        // Sau khi nhan vien thu tien moi thanh paid.
        adminService.markCounterOrderPaid(draft.getId(), staff());
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT PaymentStatus FROM Orders WHERE Id = ?")) {
            ps.setInt(1, draft.getId());
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("paid", rs.getString("PaymentStatus"));
            }
        }

        // Thu tien lan hai phai bi chan.
        BookingException twice = assertThrows(BookingException.class,
                () -> adminService.markCounterOrderPaid(draft.getId(), staff()));
        assertEquals(409, twice.getStatusCode(), twice.getMessage());

        // Da tra tien va dang trong khung gio thi moi READY.
        BusinessClock.useFixedTimeForTesting(showtimeStart().minusMinutes(10));
        assertEquals(StaffService.Verdict.READY,
                new StaffService().lookupTicket(placed.getTicketCode(), staff()).getVerdict());
    }

    @Test
    @DisplayName("CB-ISS-005: so giay giu ghe con lai do DB tinh, khong phai hang so 7 phut cua UI")
    public void testHoldRemainingSecondsComeFromDatabase() throws Exception {
        OrderRecord draft = bookingService.createDraftOrder(
                MEMBER, SHOWTIME_ID, oneSeat(), Map.of(), null, "card");

        OrderHoldStatus status = orderDAO.findHoldStatus(draft.getId(), MEMBER).orElseThrow();
        assertTrue(!status.isExpired(), "Don vua tao phai con giu ghe");
        assertNotNull(status.getHeldUntil());
        assertTrue(status.getHeldSeatCount() > 0);
        // BUG-01: han giu do SystemSettings quyet dinh, khong con la hang so HOLD_MINUTES.
        int holdMinutes = BookingService.holdMinutes();
        assertTrue(status.getRemainingSeconds() > 0
                        && status.getRemainingSeconds() <= holdMinutes * 60,
                "So giay con lai phai nam trong han giu " + holdMinutes
                        + " phut cua server, thuc te " + status.getRemainingSeconds());

        // Chu don khac khong doc duoc trang thai giu ghe cua don nay.
        Optional<OrderHoldStatus> foreign = orderDAO.findHoldStatus(draft.getId(), OTHER_MEMBER);
        assertTrue(foreign.isEmpty(), "Khong duoc xem han giu ghe cua don nguoi khac");
    }

    @Test
    @DisplayName("CB-ISS-005: het han giu ghe thi bao expired va thanh toan bi tu choi 409")
    public void testExpiredHoldBlocksPayment() throws Exception {
        OrderRecord draft = bookingService.createDraftOrder(
                MEMBER, SHOWTIME_ID, oneSeat(), Map.of(), null, "card");

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE ShowtimeSeats SET HeldUntil = DATEADD(MINUTE, -1, GETDATE())"
                             + " WHERE ShowtimeId = ? AND HeldByUserId = ? AND Status = 'held'")) {
            ps.setInt(1, SHOWTIME_ID);
            ps.setInt(2, MEMBER);
            assertTrue(ps.executeUpdate() > 0, "Phai co ghe dang giu de lam het han");
        }

        OrderHoldStatus expired = orderDAO.findHoldStatus(draft.getId(), MEMBER).orElseThrow();
        assertTrue(expired.isExpired(), "Qua han giu thi phai bao expired");
        assertTrue(expired.getRemainingSeconds() <= 0);

        BookingException ex = assertThrows(BookingException.class,
                () -> bookingService.payOrder(MEMBER, draft.getId(), Map.of(), null, "card"));
        assertEquals(409, ex.getStatusCode(), ex.getMessage());
    }
}
