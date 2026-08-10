package com.mycompany.website.ban.ve.xem.phim.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.dao.OrderDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcOrderDAO;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.model.OrderSeatItem;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * F-007 (cung lop loi) — lich su don gio nap ghe/combo theo lo, ghe khong duoc gan lech don.
 */
@Tag("it")
public class OrderHistoryBatchIT {
    private static final String MARKER = "F007HIS";
    private static final int MEMBER_ID = 1;

    @BeforeAll
    public static void setUpConfig() {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    private static long scalar(String sql) throws Exception {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static void cleanUp() throws Exception {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement seats = conn.prepareStatement(
                     "DELETE FROM OrderSeats WHERE OrderId IN (SELECT Id FROM Orders WHERE TicketCode LIKE '"
                             + MARKER + "%')");
             PreparedStatement orders = conn.prepareStatement(
                     "DELETE FROM Orders WHERE TicketCode LIKE '" + MARKER + "%'")) {
            seats.executeUpdate();
            orders.executeUpdate();
        }
    }

    private static int insertOrder(String ticketCode) throws Exception {
        String sql = """
                INSERT INTO Orders (UserId, ShowtimeId, SeatSubtotal, ComboSubtotal, DiscountAmount,
                                    TotalAmount, TicketCode, PaymentMethod, PaymentStatus, OrderStatus)
                SELECT TOP 1 ?, s.Id, 0, 0, 0, 0, ?, 'card', 'paid', 'confirmed'
                FROM Showtimes s
                JOIN Films f ON f.Id = s.FilmId
                JOIN Cinemas c ON c.Id = s.CinemaId
                JOIN Rooms r ON r.Id = s.RoomId
                ORDER BY s.Id
                """;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, MEMBER_ID);
            ps.setString(2, ticketCode);
            assertEquals(1, ps.executeUpdate());
        }
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT Id FROM Orders WHERE TicketCode = ?")) {
            ps.setString(1, ticketCode);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    /**
     * N-05: moi lan goi phai lay mot {@code ShowtimeSeats} KHAC.
     *
     * <p>Ban cu dung {@code SELECT TOP 1 ss.Id FROM ShowtimeSeats ORDER BY ss.Id} nen lan
     * nao cung tra ve dung mot ghe. Hai ghe cua cung mot don vi vay tro ve mot dong
     * {@code (OrderId, ShowtimeSeatId)} trung nhau — du lieu khong the ton tai that. Ban cu
     * chay duoc chi vi {@code CineBookDB_Test} con cot {@code Id IDENTITY}; tren production
     * khoa kep {@code (OrderId, ShowtimeSeatId)} da chan tu dau.</p>
     */
    private static void insertSeat(int orderId, String seatKey) throws Exception {
        String sql = """
                INSERT INTO OrderSeats (OrderId, ShowtimeSeatId, SeatKey, SeatType, UnitPrice)
                SELECT TOP 1 ?, ss.Id, ?, 'standard', 0
                FROM ShowtimeSeats ss
                WHERE NOT EXISTS (SELECT 1 FROM OrderSeats os WHERE os.ShowtimeSeatId = ss.Id)
                ORDER BY ss.Id
                """;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.setString(2, seatKey);
            assertEquals(1, ps.executeUpdate(),
                    "khong con ShowtimeSeats trong de gan cho don — fixture can them ghe truoc");
        }
    }

    @Test
    @DisplayName("Nap theo lo: moi don chi nhan dung ghe cua no")
    public void testHistoryAttachesSeatsToTheRightOrder() throws Exception {
        OrderDAO orderDAO = new JdbcOrderDAO();
        long ordersBefore = scalar("SELECT COUNT_BIG(*) FROM Orders");
        long seatsBefore = scalar("SELECT COUNT_BIG(*) FROM OrderSeats");
        try {
            cleanUp();
            int firstOrder = insertOrder(MARKER + "-A");
            int secondOrder = insertOrder(MARKER + "-B");
            insertSeat(firstOrder, "ZZ1");
            insertSeat(firstOrder, "ZZ2");
            insertSeat(secondOrder, "ZZ3");

            List<OrderRecord> history = orderDAO.findHistoryByUserId(MEMBER_ID);

            OrderRecord first = history.stream().filter(order -> order.getId() == firstOrder)
                    .findFirst().orElseThrow();
            OrderRecord second = history.stream().filter(order -> order.getId() == secondOrder)
                    .findFirst().orElseThrow();

            assertEquals(Set.of("ZZ1", "ZZ2"), seatKeys(first));
            assertEquals(Set.of("ZZ3"), seatKeys(second));
        } finally {
            cleanUp();
        }
        assertEquals(ordersBefore, scalar("SELECT COUNT_BIG(*) FROM Orders"));
        assertEquals(seatsBefore, scalar("SELECT COUNT_BIG(*) FROM OrderSeats"));
    }

    private static Set<String> seatKeys(OrderRecord order) {
        return order.getSeats().stream().map(OrderSeatItem::getSeatKey).collect(Collectors.toSet());
    }
}
