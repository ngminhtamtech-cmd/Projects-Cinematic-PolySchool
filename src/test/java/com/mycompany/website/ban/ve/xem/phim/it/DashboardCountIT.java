package com.mycompany.website.ban.ve.xem.phim.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.website.ban.ve.xem.phim.config.AppConstants;
import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * F-003 — so don tren dashboard phai la tong that, khong phai so dong cua mot trang.
 *
 * <p>Don rac duoc danh dau bang tien to {@code TicketCode} rieng va xoa sach trong {@code finally},
 * nen so dong cua bang {@code Orders} tro lai dung nhu truoc khi chay.</p>
 */
@Tag("it")
public class DashboardCountIT {
    private static final String MARKER = "F003CNT";

    @BeforeAll
    public static void setUpConfig() {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    private static User admin() {
        User user = new User();
        user.setId(5);
        user.setRole(AppConstants.ROLE_ADMIN);
        return user;
    }

    private static long scalar(String sql) throws Exception {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static void deleteMarkedOrders() throws Exception {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM Orders WHERE TicketCode LIKE '" + MARKER + "%'")) {
            ps.executeUpdate();
        }
    }

    private static void insertMarkedOrders(int howMany, int startIndex) throws Exception {
        String sql = """
                INSERT INTO Orders (UserId, ShowtimeId, SeatSubtotal, ComboSubtotal, DiscountAmount,
                                    TotalAmount, TicketCode, PaymentMethod, PaymentStatus, OrderStatus)
                SELECT TOP 1 u.Id, s.Id, 0, 0, 0, 0, ?, 'card', 'pending', 'pending'
                FROM Showtimes s
                JOIN Films f ON f.Id = s.FilmId
                JOIN Cinemas c ON c.Id = s.CinemaId
                JOIN Rooms r ON r.Id = s.RoomId
                CROSS JOIN Users u
                ORDER BY s.Id, u.Id
                """;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < howMany; i++) {
                ps.setString(1, MARKER + String.format("%04d", startIndex + i));
                assertEquals(1, ps.executeUpdate(), "Khong chen duoc don gia lap");
            }
        }
    }

    @Test
    @DisplayName("Dem don vuot moc 49/50/51 khong bi ket o 50 nhu khi lay .size() cua trang")
    public void testOrderCountCrossesPageSizeBoundary() throws Exception {
        AdminService adminService = new AdminService();
        long ordersBefore = scalar("SELECT COUNT_BIG(*) FROM Orders");
        try {
            deleteMarkedOrders();
            assertEquals(0L, adminService.countOrdersForAdmin(null, null, null, null, MARKER),
                    "Chua chen gi thi phai dem ra 0");

            insertMarkedOrders(49, 1);
            assertEquals(49L, adminService.countOrdersForAdmin(null, null, null, null, MARKER));

            insertMarkedOrders(1, 50);
            assertEquals(50L, adminService.countOrdersForAdmin(null, null, null, null, MARKER));

            insertMarkedOrders(1, 51);
            assertEquals(51L, adminService.countOrdersForAdmin(null, null, null, null, MARKER),
                    "Moc 51 la cho code cu bi ket o 50");

            long realTotal = adminService.countOrdersForAdmin(null, null, null, null, null);
            assertTrue(realTotal >= 51L, "Tong that phai tinh ca 51 don vua chen, thuc te " + realTotal);

            // Chinh xac cai bay cua F-003: trang mac dinh chi tra 50 dong.
            int pageRows = adminService.listOrdersForAdmin().size();
            assertEquals(50, pageRows, "Trang mac dinh van la 50 dong — nen khong duoc dung lam so dem");
            assertTrue(realTotal > pageRows, "Tong that phai lon hon so dong mot trang");

            Map<String, Long> counts = adminService.dashboardCounts(admin());
            assertEquals(realTotal, counts.get("orderCount"),
                    "O so lieu dashboard phai bang tong that, khong phai so dong mot trang");
        } finally {
            deleteMarkedOrders();
        }
        assertEquals(ordersBefore, scalar("SELECT COUNT_BIG(*) FROM Orders"),
                "So dong bang Orders phai tro lai nguyen trang thai truoc test");
    }

    @Test
    @DisplayName("Cac o so lieu khac cung la COUNT that, khong bi cat theo TOP")
    public void testOtherDashboardCountsAreRealTotals() throws Exception {
        Map<String, Long> counts = new AdminService().dashboardCounts(admin());

        assertEquals(scalar("SELECT COUNT_BIG(*) FROM Films"), counts.get("filmCount"));
        assertEquals(scalar("SELECT COUNT_BIG(*) FROM Users WHERE Role = 'member'"), counts.get("memberCount"));
        assertEquals(scalar("SELECT COUNT_BIG(*) FROM Promotions"), counts.get("promotionCount"));
        assertEquals(scalar("SELECT COUNT_BIG(*) FROM AuditLogs"), counts.get("auditCount"));
        assertEquals(scalar("SELECT COUNT_BIG(*) FROM Users WHERE Role = 'manager'"), counts.get("managerCount"));
        assertEquals(scalar("""
                SELECT COUNT_BIG(*) FROM Showtimes s
                JOIN Films f ON f.Id = s.FilmId
                JOIN Cinemas c ON c.Id = s.CinemaId
                JOIN Rooms r ON r.Id = s.RoomId
                """), counts.get("showtimeCount"));
    }
}
