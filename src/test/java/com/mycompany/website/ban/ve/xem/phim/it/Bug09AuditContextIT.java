package com.mycompany.website.ban.ve.xem.phim.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcOrderDAO;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.model.ShowtimeSeat;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import com.mycompany.website.ban.ve.xem.phim.service.BookingService;
import com.mycompany.website.ban.ve.xem.phim.util.BusinessClock;
import com.mycompany.website.ban.ve.xem.phim.util.RequestContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * BUG-09 + BUG-10 (INV-9) — audit phai du de dieu tra: ai, gi, khi nao, <b>tu dau</b>, gia tri
 * truoc/sau.
 *
 * <p><b>BUG-09.</b> Bang {@code AuditLogs} co san {@code IpAddress}, {@code UserAgent},
 * {@code BeforeJson}, nhung {@code logAction} 5 tham so truyen thang {@code null} cho ba cot do.
 * Do thuc te tren 71 dong: {@code BeforeJson} 0, {@code IpAddress} 0, {@code UserAgent} 0.</p>
 *
 * <p><b>BUG-10.</b> Nhanh {@code rejectRefund} chi hien flash "Da ghi nhan tu choi hoan tien" ma
 * khong doi trang thai, khong ghi audit, khong luu ly do. Quyet dinh cua quan ly bien mat.</p>
 */
@Tag("it")
public class Bug09AuditContextIT {

    private static final String FILM_TITLE = "BUG09-AUDIT Enterprise";
    private static final int CINEMA_ID = 1;
    private static final int ROOM_ID = 1;
    private static final int MEMBER_ID = 1;
    private static final String TEST_IP = "203.0.113.9";
    private static final String TEST_AGENT = "JUnit-AuditProbe/1.0";

    static {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    private final BookingService bookingService = new BookingService();
    private final AdminService adminService = new AdminService();
    private final JdbcOrderDAO orderDAO = new JdbcOrderDAO();
    private int showtimeId;

    @BeforeAll
    public static void setUpTestDb() {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
        DBConnection.shutdown();
    }

    @AfterAll
    public static void tearDown() {
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
        RequestContext.set(TEST_IP, TEST_AGENT);
    }

    @AfterEach
    public void cleanup() throws SQLException {
        RequestContext.clear();
        execute("DELETE FROM AuditLogs WHERE UserAgent=? OR TargetId IN ("
                + " SELECT CAST(o.Id AS NVARCHAR(50)) FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId"
                + " JOIN Films f ON f.Id=s.FilmId WHERE f.Title=?)", TEST_AGENT, FILM_TITLE);
        cleanupFixtures();
    }

    @Test
    @DisplayName("BUG-09: dong audit moi phai co IpAddress, UserAgent va BeforeJson khac NULL")
    public void auditRowCarriesHttpContextAndBeforeState() throws SQLException {
        int orderId = payOneSeat().getId();

        adminService.rejectRefund(orderId, "Khach den muon, khong thuoc dien hoan", manager());

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     SELECT TOP 1 IpAddress, UserAgent, BeforeJson, AfterJson, ActorUserId
                     FROM AuditLogs WHERE Action='REJECT_REFUND' AND TargetId=?
                     ORDER BY Id DESC
                     """)) {
            ps.setString(1, String.valueOf(orderId));
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Phai co dong audit REJECT_REFUND");
                assertEquals(TEST_IP, rs.getString("IpAddress"), "IpAddress khong duoc NULL");
                assertEquals(TEST_AGENT, rs.getString("UserAgent"), "UserAgent khong duoc NULL");
                assertNotNull(rs.getString("BeforeJson"), "BeforeJson khong duoc NULL");
                assertNotNull(rs.getString("AfterJson"));
                assertEquals(4, rs.getInt("ActorUserId"));
            }
        }
    }

    @Test
    @DisplayName("BUG-09: audit cua thao tac quan tri thong thuong cung phai co IP/UserAgent")
    public void ordinaryAdminActionAlsoCarriesHttpContext() throws SQLException {
        adminService.logAction(4, "BUG09_PROBE", "Order", "0", "probe");

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     SELECT TOP 1 IpAddress, UserAgent FROM AuditLogs
                     WHERE Action='BUG09_PROBE' ORDER BY Id DESC
                     """);
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(TEST_IP, rs.getString("IpAddress"),
                    "logAction 5 tham so phai tu lay ngu canh HTTP, khong truyen null");
            assertEquals(TEST_AGENT, rs.getString("UserAgent"));
        } finally {
            execute("DELETE FROM AuditLogs WHERE Action='BUG09_PROBE'");
        }
    }

    @Test
    @DisplayName("BUG-10: tu choi hoan tien phai doi trang thai, luu ly do va ghi audit")
    public void rejectRefundRecordsTheDecision() throws SQLException {
        OrderRecord paid = payOneSeat();
        movePastShowtime();
        OrderRecord beforeReject = orderDAO.findById(paid.getId()).orElseThrow();
        beforeReject.setBusinessNow(BusinessClock.now());
        assertTrue(beforeReject.isRefundReview(), "Fixture: don phai dang nam trong tab cho hoan tien");

        String reason = "Khach vang mat khong bao truoc, khong thuoc dien hoan";
        adminService.rejectRefund(paid.getId(), reason, manager());

        OrderRecord afterReject = orderDAO.findById(paid.getId()).orElseThrow();
        afterReject.setBusinessNow(BusinessClock.now());
        assertNotNull(afterReject.getRefundRejectedAt(), "Phai luu moc thoi gian tu choi");
        assertEquals(reason, afterReject.getRefundRejectReason(), "Phai luu ly do tu choi");
        assertFalse(afterReject.isRefundReview(),
                "Don da bi tu choi thi khong con nam trong tab cho hoan tien nua");
        assertEquals(1, auditRows("REJECT_REFUND", paid.getId()),
                "Phai co dung mot dong audit REJECT_REFUND");
    }

    @Test
    @DisplayName("BUG-10: tu choi hoan tien khong co ly do thi bi tu choi")
    public void rejectRefundRequiresReason() {
        OrderRecord paid = payOneSeat();

        BookingException ex = assertThrows(BookingException.class,
                () -> adminService.rejectRefund(paid.getId(), "   ", manager()));
        assertEquals(400, ex.getStatusCode());
    }

    @Test
    @DisplayName("BUG-10: tu choi hai lan cho cung mot don phai bi chan")
    public void rejectRefundTwiceIsBlocked() throws SQLException {
        OrderRecord paid = payOneSeat();
        adminService.rejectRefund(paid.getId(), "Lan mot", manager());

        BookingException ex = assertThrows(BookingException.class,
                () -> adminService.rejectRefund(paid.getId(), "Lan hai", manager()));
        assertEquals(409, ex.getStatusCode());
        assertEquals(1, auditRows("REJECT_REFUND", paid.getId()));
    }

    // ------------------------------------------------------------------ helpers

    private OrderRecord payOneSeat() {
        OrderRecord draft = bookingService.createDraftOrder(
                MEMBER_ID, showtimeId, List.of(oneBookableSeat()), Map.of(), null, "card");
        return bookingService.payOrder(MEMBER_ID, draft.getId(), Map.of(), null, "card");
    }

    private void movePastShowtime() throws SQLException {
        execute("""
                UPDATE Showtimes SET StartTime=DATEADD(MINUTE,-200,GETDATE()),
                                     EndTime=DATEADD(MINUTE,-80,GETDATE())
                WHERE Id=?
                """, showtimeId);
    }

    private int oneBookableSeat() {
        return bookingService.getSeatMap(showtimeId).stream()
                .filter(seat -> seat.isAvailableFor(MEMBER_ID))
                .filter(seat -> !"couple".equalsIgnoreCase(seat.getSeatType()))
                .map(ShowtimeSeat::getId)
                .findFirst()
                .orElseThrow();
    }

    private static User manager() {
        User user = new User();
        user.setId(4);
        user.setRole("manager");
        // ScopeUtil bat buoc manager phai duoc gan cum rap; fixture nam o cum rap CINEMA_ID.
        user.setCinemaId(CINEMA_ID);
        return user;
    }

    private int auditRows(String action, int orderId) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT COUNT(*) FROM AuditLogs WHERE Action=? AND TargetType='Order' AND TargetId=?")) {
            ps.setString(1, action);
            ps.setString(2, String.valueOf(orderId));
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
}
