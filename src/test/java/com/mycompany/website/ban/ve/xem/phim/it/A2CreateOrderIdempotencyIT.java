package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.config.AppConstants;
import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.controller.booking.OrderServlet;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.model.ShowtimeSeat;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.BookingService;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A.2 (BUG-02, INV-2) — khoa idempotency o duong TAO DON cung phai gan voi chu don.
 *
 * <p><b>Van de.</b> {@code payOrder} da co {@code settledReplay} kiem ca {@code UserId} lan
 * {@code orderId}, nhung {@code createDraftOrder} thi tra ve don tim duoc theo khoa NGAY, khong
 * kiem chu don. Ai biet khoa cua nguoi khac thi {@code POST /orders} tra ve
 * {@code orderId} + {@code ticketCode} THAT cua ho.</p>
 *
 * <p>Do o tang servlet, khong phai tang service: cai bi ro ri la <b>than response</b>, nen phai
 * doc chinh JSON ma nguoi tan cong nhan duoc.</p>
 */
@Tag("it")
@DisplayName("A.2 — POST /orders bang khoa cua nguoi khac khong duoc lo don cua ho")
public class A2CreateOrderIdempotencyIT {

    private static final String FILM_TITLE = "A2-IDEMPOTENCY Enterprise";
    private static final int CINEMA_ID = 1;
    private static final int ROOM_ID = 1;
    private static final int VICTIM_ID = 1;
    private static final int ATTACKER_ID = 2;

    static {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    private final BookingService bookingService = new BookingService();
    private final OrderServlet servlet = new OrderServlet();
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
        cleanupFixtures();
    }

    @Test
    @DisplayName("khoa cua nguoi khac -> 409 va than response KHONG chua ticketCode")
    public void foreignKeyOnCreatePathIsRejectedWithoutLeaking() throws Exception {
        String victimKey = "A2-VICTIM-" + UUID.randomUUID();
        OrderRecord victimPaid = payOneSeatWithKey(VICTIM_ID, victimKey);
        assertNotNull(victimPaid.getTicketCode(), "Tien de: don nan nhan phai co ma ve that");

        Exchange exchange = postCreate(ATTACKER_ID, victimKey);

        assertEquals(409, exchange.status,
                "Khoa da thuoc ve don cua nguoi khac thi phai bi tu choi");
        String body = exchange.body();
        assertFalse(body.contains("ticketCode"),
                "Than response lo truong ticketCode cho ke tan cong: " + body);
        assertFalse(body.contains(victimPaid.getTicketCode()),
                "Than response lo chinh ma ve cua nan nhan: " + body);
        assertFalse(body.contains("\"orderId\""),
                "Than response lo id don cua nan nhan: " + body);
    }

    @Test
    @DisplayName("thong bao tu choi phai trung tinh, khong noi don do la cua ai")
    public void rejectionMessageDoesNotIdentifyTheOwner() throws Exception {
        String victimKey = "A2-VICTIM-" + UUID.randomUUID();
        payOneSeatWithKey(VICTIM_ID, victimKey);

        String body = postCreate(ATTACKER_ID, victimKey).body();

        assertTrue(body.contains("\"error\":true"),
                "Phai la mot phan hoi loi, khong phai don cua nguoi khac: " + body);
        assertFalse(body.contains("member_bronze@test.com"), body);
        assertFalse(body.toLowerCase().contains("nguoi dung 1"), body);
    }

    @Test
    @DisplayName("chinh chu gui lai khoa cua minh -> tra ve dung don cu, khong tao don thu hai")
    public void ownerReplayingOwnKeyGetsTheSameOrder() throws Exception {
        String key = "A2-OWNER-" + UUID.randomUUID();
        OrderRecord first = bookingService.createDraftOrder(
                VICTIM_ID, showtimeId, List.of(oneBookableSeat(VICTIM_ID)), Map.of(), null, "card", key);

        Exchange exchange = postCreate(VICTIM_ID, key);

        assertEquals(200, exchange.status, "Lan gui lai cua chinh chu la retry hop le: " + exchange.body());
        assertTrue(exchange.body().contains("\"orderId\":" + first.getId()),
                "Phai tra ve dung don cu, khong tao don thu hai: " + exchange.body());
        assertEquals(1, scalar("SELECT COUNT(*) FROM Orders WHERE IdempotencyKey=?", key),
                "Mot khoa chi duoc ung voi mot don");
    }

    // ------------------------------------------------------------------ helpers

    private Exchange postCreate(int userId, String idempotencyKey) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("showtimeId", String.valueOf(showtimeId));
        params.put("seatIds", String.valueOf(oneBookableSeat(userId)));
        params.put("paymentMethod", "card");
        Exchange exchange = new Exchange(member(userId), params, idempotencyKey);
        servlet.service(exchange.request(), exchange.response());
        return exchange;
    }

    private OrderRecord payOneSeatWithKey(int userId, String key) {
        OrderRecord draft = bookingService.createDraftOrder(
                userId, showtimeId, List.of(oneBookableSeat(userId)), Map.of(), null, "card", key);
        return bookingService.payOrder(userId, draft.getId(), Map.of(), null, "card", key);
    }

    private int oneBookableSeat(int userId) {
        return bookingService.getSeatMap(showtimeId).stream()
                .filter(seat -> seat.isAvailableFor(userId))
                .filter(seat -> !"couple".equalsIgnoreCase(seat.getSeatType()))
                .map(ShowtimeSeat::getId)
                .findFirst()
                .orElseThrow();
    }

    private static User member(int id) {
        User user = new User();
        user.setId(id);
        user.setRole("member");
        return user;
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

    /** Mot lan POST /orders, giu lai status va than JSON de doc lai. */
    private static final class Exchange {
        private final Map<String, Object> sessionAttributes = new HashMap<>();
        private final Map<String, String> parameters;
        private final String idempotencyKey;
        private final StringWriter payload = new StringWriter();
        private final PrintWriter writer = new PrintWriter(payload);
        private HttpServletRequest request;
        private HttpServletResponse response;
        private int status = 200;

        private Exchange(User actor, Map<String, String> parameters, String idempotencyKey) {
            this.parameters = parameters;
            this.idempotencyKey = idempotencyKey;
            sessionAttributes.put(AppConstants.SESSION_USER, actor);
        }

        private String body() {
            writer.flush();
            return payload.toString();
        }

        private HttpServletRequest request() {
            if (request == null) {
                HttpSession session = session();
                InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
                    case "getMethod" -> "POST";
                    case "getPathInfo" -> null;
                    case "getServletPath" -> "/orders";
                    case "getContextPath" -> "/cinebook";
                    case "getSession" -> session;
                    case "getParameter" -> parameters.get((String) args[0]);
                    case "getHeader" -> "X-Idempotency-Key".equalsIgnoreCase((String) args[0])
                            ? idempotencyKey : null;
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
                InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
                    case "getWriter" -> writer;
                    case "setStatus", "sendError" -> {
                        status = (Integer) args[0];
                        yield null;
                    }
                    case "getStatus" -> status;
                    default -> defaultValue(method.getReturnType());
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
