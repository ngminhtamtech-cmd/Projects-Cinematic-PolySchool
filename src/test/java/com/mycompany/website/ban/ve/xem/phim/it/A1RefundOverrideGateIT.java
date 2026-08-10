package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.config.AppConstants;
import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.controller.admin.ManagerPortalServlet;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.model.ShowtimeSeat;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.BookingService;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A.1 (BUG-11, INV-8) — chinh sach hoan tien phai co hieu luc tren DUONG THAT.
 *
 * <p><b>Van de.</b> {@code AdminService.refundOrder} da co day du luat "chan ve redeemed" va
 * "chan khi qua cutoff", va {@code Bug11RefundPolicyIT} do chung xanh. Nhung
 * {@code ManagerPortalServlet.handleOrderPost} truyen {@code overrideRestrictions = true}
 * <b>cung</b> cho moi lan duyet, nen trong ung dung that hai luat do chua bao gio chay: bam
 * "Duyet hoan tien" cho mot ve khach da xem xong van hoan duoc tien. Ban overload 4 tham so
 * (mac dinh {@code false}) khong co caller nao.</p>
 *
 * <p>Vi vay lop test nay <b>khong goi service</b> — no di qua {@code doPost} dung nhu form
 * {@code /admin/orders} gui len. Do thang vao service se lap lai dung diem mu cu.</p>
 *
 * <p><b>Ve ma trang thai.</b> Cong quan tri chuyen {@code BookingException(400)} thanh flash
 * error + redirect (quy uoc san co cua {@code ManagerPortalServlet.doPost}), nen o day do
 * <i>ket qua quan sat duoc</i>: don co bi chuyen sang {@code refunded} khong, va quan ly co doc
 * duoc ly do bi chan khong.</p>
 */
@Tag("it")
@DisplayName("A.1 — duyet hoan tien phai bat buoc tick bo qua dieu kien")
public class A1RefundOverrideGateIT {

    private static final String FILM_TITLE = "A1-REFUND-GATE Enterprise";
    private static final int CINEMA_ID = 1;
    private static final int ROOM_ID = 1;
    private static final int MEMBER_ID = 1;
    private static final int ADMIN_ID = 5;

    static {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    private final BookingService bookingService = new BookingService();
    private final ManagerPortalServlet servlet = new ManagerPortalServlet();
    private int showtimeId;

    @BeforeAll
    public static void setUpTestDb() {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
        DBConnection.shutdown();
    }

    @AfterAll
    public static void tearDownAll() {
        DBConnection.shutdown();
    }

    @BeforeEach
    public void createFixture() throws SQLException {
        assertTestDatabase();
        cleanupFixtures();
        int filmId = insert("""
                INSERT INTO Films (Title,ReleaseDate,EndDate,DurationMinutes,Status)
                VALUES (?,DATEADD(DAY,-1,CAST(GETDATE() AS DATE)),
                        DATEADD(DAY,30,CAST(GETDATE() AS DATE)),120,'showing')
                """, FILM_TITLE);
        execute("INSERT INTO CinemaFilms (CinemaId,FilmId) VALUES (?,?)", CINEMA_ID, filmId);
        showtimeId = insert("""
                INSERT INTO Showtimes
                    (FilmId,CinemaId,RoomId,StartTime,EndTime,BasePrice,Format,Version,Language)
                VALUES (?,?,?,DATEADD(MINUTE,90,GETDATE()),DATEADD(MINUTE,210,GETDATE()),
                        90000,'2D','Subtitle','Vietnamese')
                """, filmId, CINEMA_ID, ROOM_ID);
        execute("""
                INSERT INTO ShowtimeSeats (ShowtimeId,SeatId,Status,ExtraFee)
                SELECT ?,Id,'available',0 FROM Seats WHERE RoomId=?
                """, showtimeId, ROOM_ID);
    }

    @AfterEach
    public void cleanup() throws SQLException {
        execute("DELETE FROM AuditLogs WHERE Action IN ('REFUND_OVERRIDE','REFUND_ORDER')"
                + " AND TargetType='Order' AND TargetId IN ("
                + " SELECT CAST(o.Id AS NVARCHAR(50)) FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId"
                + " JOIN Films f ON f.Id=s.FilmId WHERE f.Title=?)", FILM_TITLE);
        cleanupFixtures();
    }

    @Test
    @DisplayName("ve da check-in, KHONG tick bo qua -> bi chan, don van 'paid'")
    public void approvingRedeemedTicketWithoutOverrideIsBlocked() throws Exception {
        OrderRecord paid = payOneSeat();
        execute("UPDATE Orders SET OrderStatus='redeemed', RedeemedAt=GETDATE() WHERE Id=?", paid.getId());

        Exchange exchange = post(Map.of(
                "action", "approveRefund",
                "id", String.valueOf(paid.getId()),
                "refundReason", "Khach doi hoan sau khi da xem"));

        assertEquals("paid", paymentStatusOf(paid.getId()),
                "Khong tick bo qua ma van hoan duoc tien = luat chan chua bao gio chay tren duong that");
        assertNotNull(exchange.flashError(), "Quan ly phai doc duoc ly do bi chan");
        assertTrue(exchange.flashError().contains("đã được sử dụng"),
                "Thong bao phai neu dung dieu kien nao chan: " + exchange.flashError());
        assertEquals("/cinebook/admin/dashboard", exchange.redirectedTo,
                "Lỗi nghiệp vụ phải quay về dashboard quản trị");
        assertEquals(0, auditRows(paid.getId(), "REFUND_ORDER"),
                "Bi chan thi khong duoc de lai dong audit hoan tien");
    }

    @Test
    @DisplayName("qua cutoff, KHONG tick bo qua -> bi chan, don van 'paid'")
    public void approvingPastCutoffWithoutOverrideIsBlocked() throws Exception {
        OrderRecord paid = payOneSeat();
        execute("""
                UPDATE Showtimes SET StartTime=DATEADD(MINUTE,5,GETDATE()),
                                     EndTime=DATEADD(MINUTE,125,GETDATE())
                WHERE Id=?
                """, showtimeId);

        Exchange exchange = post(Map.of(
                "action", "approveRefund",
                "id", String.valueOf(paid.getId()),
                "refundReason", "Khach bao ban dot xuat"));

        assertEquals("paid", paymentStatusOf(paid.getId()));
        assertEquals("/cinebook/admin/dashboard", exchange.redirectedTo,
                "Lỗi nghiệp vụ phải quay về dashboard quản trị");
        assertNotNull(exchange.flashError());
        assertTrue(exchange.flashError().contains("quá hạn hoàn tiền"), exchange.flashError());
    }

    @Test
    @DisplayName("tick bo qua + co ly do -> hoan duoc va ghi audit REFUND_OVERRIDE")
    public void approvingWithOverrideTickedSucceedsAndAudits() throws Exception {
        OrderRecord paid = payOneSeat();
        execute("UPDATE Orders SET OrderStatus='redeemed', RedeemedAt=GETDATE() WHERE Id=?", paid.getId());
        String reason = "May chieu hong giua phim, quan ly duyet hoan cho khach";

        Exchange exchange = post(Map.of(
                "action", "approveRefund",
                "id", String.valueOf(paid.getId()),
                "refundReason", reason,
                "overrideRefundRestrictions", "on"));

        assertEquals("refunded", paymentStatusOf(paid.getId()),
                "Tick bo qua kem ly do thi phai hoan duoc: " + exchange.flashError());
        assertEquals("/cinebook/admin/orders", exchange.redirectedTo,
                "Duyệt hoàn tiền thành công phải quay về danh sách đơn hàng");
        assertEquals(1, auditRows(paid.getId(), "REFUND_OVERRIDE"),
                "Mot lan bo qua dieu kien phai de lai dung mot dong REFUND_OVERRIDE");
    }

    @Test
    @DisplayName("don binh thuong (chua check-in, con han) van hoan duoc khi KHONG tick")
    public void ordinaryRefundStillWorksWithoutOverride() throws Exception {
        OrderRecord paid = payOneSeat();

        Exchange exchange = post(Map.of(
                "action", "approveRefund",
                "id", String.valueOf(paid.getId()),
                "refundReason", "Khach doi lich, con han huy"));

        assertEquals("refunded", paymentStatusOf(paid.getId()),
                "Siet override khong duoc lam hong duong hoan tien binh thuong: " + exchange.flashError());
        assertEquals("/cinebook/admin/orders", exchange.redirectedTo,
                "Duyệt hoàn tiền thành công phải quay về danh sách đơn hàng");
        assertEquals(0, auditRows(paid.getId(), "REFUND_OVERRIDE"),
                "Khong bo qua dieu kien nao thi khong duoc ghi REFUND_OVERRIDE");
    }

    // ------------------------------------------------------------------ helpers

    private Exchange post(Map<String, String> params) throws Exception {
        Exchange exchange = new Exchange(admin(), params);
        servlet.service(exchange.request(), exchange.response());
        return exchange;
    }

    private OrderRecord payOneSeat() {
        OrderRecord draft = bookingService.createDraftOrder(
                MEMBER_ID, showtimeId, List.of(oneBookableSeat()), Map.of(), null, "card");
        return bookingService.payOrder(MEMBER_ID, draft.getId(), Map.of(), null, "card");
    }

    private int oneBookableSeat() {
        return bookingService.getSeatMap(showtimeId).stream()
                .filter(seat -> seat.isAvailableFor(MEMBER_ID))
                .filter(seat -> !"couple".equalsIgnoreCase(seat.getSeatType()))
                .map(ShowtimeSeat::getId)
                .findFirst()
                .orElseThrow();
    }

    private static User admin() {
        User user = new User();
        user.setId(ADMIN_ID);
        user.setRole("admin");
        user.setCinemaId(null);
        return user;
    }

    private String paymentStatusOf(int orderId) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT PaymentStatus FROM Orders WHERE Id=?")) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getString(1);
            }
        }
    }

    private int auditRows(int orderId, String action) throws SQLException {
        return scalar("SELECT COUNT(*) FROM AuditLogs WHERE Action=? AND TargetType='Order' AND TargetId=?",
                action, String.valueOf(orderId));
    }

    private int scalar(String sql, Object... values) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, values);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private void cleanupFixtures() throws SQLException {
        execute("""
                DELETE FROM Invoices WHERE OrderId IN (
                  SELECT o.Id FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId
                  JOIN Films f ON f.Id=s.FilmId WHERE f.Title=?);
                DELETE FROM PromotionUsage WHERE OrderId IN (
                  SELECT o.Id FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId
                  JOIN Films f ON f.Id=s.FilmId WHERE f.Title=?);
                DELETE FROM RefundTransactions WHERE OrderId IN (
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
                """, FILM_TITLE, FILM_TITLE, FILM_TITLE, FILM_TITLE, FILM_TITLE,
                FILM_TITLE, FILM_TITLE, FILM_TITLE, FILM_TITLE);
    }

    private int insert(String sql, Object... values) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
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

    private void bind(PreparedStatement ps, Object... values) throws SQLException {
        for (int index = 0; index < values.length; index++) {
            ps.setObject(index + 1, values[index]);
        }
    }

    private void assertTestDatabase() throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT DB_NAME()");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(System.getProperty("cinebook.it.database", "CineBookIT_REQUIRED"), rs.getString(1));
        }
    }

    /**
     * Mot lan POST /admin/orders. Dung Proxy dong thay vi mot thu vien mock — cung cach
     * {@code AdminDashboardCountTest} da dung, khong them phu thuoc moi.
     */
    private static final class Exchange {
        private final Map<String, Object> sessionAttributes = new HashMap<>();
        private final Map<String, String> parameters;
        private HttpServletRequest request;
        private HttpServletResponse response;
        private String redirectedTo;

        private Exchange(User actor, Map<String, String> parameters) {
            this.parameters = parameters;
            sessionAttributes.put(AppConstants.SESSION_USER, actor);
        }

        private String flashError() {
            Object value = sessionAttributes.get(AppConstants.FLASH_ERROR);
            return value == null ? null : value.toString();
        }

        private HttpServletRequest request() {
            if (request == null) {
                HttpSession session = session();
                InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
                    case "getMethod" -> "POST";
                    case "getServletPath" -> "/admin/orders";
                    case "getContextPath" -> "/cinebook";
                    case "getSession" -> session;
                    case "getParameter" -> parameters.get((String) args[0]);
                    case "getHeader" -> null;
                    default -> defaultValue(method.getReturnType());
                };
                request = (HttpServletRequest) Proxy.newProxyInstance(
                        getClass().getClassLoader(), new Class<?>[] {HttpServletRequest.class}, handler);
            }
            return request;
        }

        private HttpSession session() {
            InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
                case "getAttribute" -> sessionAttributes.get((String) args[0]);
                case "setAttribute" -> {
                    sessionAttributes.put((String) args[0], args[1]);
                    yield null;
                }
                case "removeAttribute" -> {
                    sessionAttributes.remove((String) args[0]);
                    yield null;
                }
                default -> defaultValue(method.getReturnType());
            };
            return (HttpSession) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[] {HttpSession.class}, handler);
        }

        private HttpServletResponse response() {
            if (response == null) {
                InvocationHandler handler = (proxy, method, args) -> {
                    if ("sendRedirect".equals(method.getName())) {
                        redirectedTo = (String) args[0];
                        return null;
                    }
                    return defaultValue(method.getReturnType());
                };
                response = (HttpServletResponse) Proxy.newProxyInstance(
                        getClass().getClassLoader(), new Class<?>[] {HttpServletResponse.class}, handler);
            }
            return response;
        }

        private static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive() || type == void.class) {
                return null;
            }
            if (type == boolean.class) {
                return false;
            }
            if (type == long.class) {
                return 0L;
            }
            return 0;
        }
    }
}
