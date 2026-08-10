package com.mycompany.website.ban.ve.xem.phim.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.model.ShowtimeSeat;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.BookingService;
import com.mycompany.website.ban.ve.xem.phim.service.StaffService;
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
 * BUG-07 (INV-4) — truy cap tai nguyen gan rap phai kiem ca vai tro <b>lan pham vi cum rap</b>,
 * o ca duong doc lan duong ghi.
 *
 * <p>{@code StaffService.lookupTicket} khong he goi {@code ScopeUtil}: duong ghi
 * ({@code redeemTicket}) co kiem, duong doc thi khong. Do thuc te: staff cua rap 8 tra ma ve cua
 * rap 7 nhan HTTP 200 kem ten phong "QA Phong Rap7" va ten rap.</p>
 *
 * <p>Yeu cau chat hon "chan lai": ve ngoai pham vi phai tra ve <b>y het</b> ma ve khong ton tai.
 * Neu tra mot verdict rieng kieu "ngoai pham vi", nhan vien van do duoc ma ve nao co that o rap
 * khac — van la ro ri, chi it hon.</p>
 */
@Tag("it")
public class Bug07TicketScopeLeakIT {

    private static final String FILM_TITLE = "BUG07-SCOPE Enterprise";
    private static final String ROOM_NAME = "BUG07 Phong Rap Mot";
    private static final int HOME_CINEMA_ID = 1;
    private static final int MEMBER_ID = 1;

    static {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    private final BookingService bookingService = new BookingService();
    private final StaffService staffService = new StaffService();
    private int showtimeId;
    private int roomId;
    private String ticketCode;

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
        execute("INSERT INTO CinemaFilms (CinemaId,FilmId) VALUES (?,?)", HOME_CINEMA_ID, filmId);
        roomId = insert("INSERT INTO Rooms (CinemaId,Name,Status) VALUES (?,?,'active')",
                HOME_CINEMA_ID, ROOM_NAME);
        execute("""
                INSERT INTO Seats (RoomId,RowLabel,SeatNumber,SeatType,SeatKey,PriceSurcharge)
                VALUES (?,'A',1,'standard','A1',0), (?,'A',2,'standard','A2',0)
                """, roomId, roomId);
        showtimeId = insert("""
                INSERT INTO Showtimes
                    (FilmId,CinemaId,RoomId,StartTime,EndTime,BasePrice,Format,Version,Language)
                VALUES (?,?,?,DATEADD(MINUTE,30,GETDATE()),DATEADD(MINUTE,150,GETDATE()),
                        90000,'2D','Subtitle','Vietnamese')
                """, filmId, HOME_CINEMA_ID, roomId);
        execute("""
                INSERT INTO ShowtimeSeats (ShowtimeId,SeatId,Status,ExtraFee)
                SELECT ?,Id,'available',0 FROM Seats WHERE RoomId=?
                """, showtimeId, roomId);

        OrderRecord draft = bookingService.createDraftOrder(
                MEMBER_ID, showtimeId, List.of(oneBookableSeat()), Map.of(), null, "card");
        ticketCode = bookingService.payOrder(MEMBER_ID, draft.getId(), Map.of(), null, "card").getTicketCode();
    }

    @AfterEach
    public void cleanup() throws SQLException {
        cleanupFixtures();
    }

    @Test
    @DisplayName("BUG-07: staff rap khac tra ma ve phai nhan NOT_FOUND, khong lo bat ky truong nao")
    public void foreignStaffGetsNotFoundWithoutAnyOrderData() {
        StaffService.TicketLookup lookup = staffService.lookupTicket(ticketCode, staffOfOtherCinema());

        assertEquals(StaffService.Verdict.NOT_FOUND, lookup.getVerdict(),
                "Verdict rieng cho 'ngoai pham vi' van cho phep do ma ve co that o rap khac");
        assertNull(lookup.getOrder(), "Khong duoc tra kem bat ky du lieu nao cua don ngoai pham vi");
        assertFalse(lookup.getMessage().contains(ROOM_NAME),
                "Thong bao khong duoc chua ten phong: " + lookup.getMessage());
        assertFalse(lookup.getMessage().contains(FILM_TITLE),
                "Thong bao khong duoc chua ten phim: " + lookup.getMessage());
    }

    @Test
    @DisplayName("BUG-07: thong bao phai giong het truong hop ma ve khong ton tai")
    public void foreignTicketIsIndistinguishableFromMissingTicket() {
        StaffService.TicketLookup foreign = staffService.lookupTicket(ticketCode, staffOfOtherCinema());
        StaffService.TicketLookup missing = staffService.lookupTicket(ticketCode, staffOfOtherCinema());
        StaffService.TicketLookup neverExisted =
                staffService.lookupTicket("BUG07KHONGTONTAI", staffOfOtherCinema());

        assertEquals(foreign.getVerdict(), missing.getVerdict());
        assertEquals(neverExisted.getVerdict(), foreign.getVerdict());
        assertEquals(
                neverExisted.getMessage().replace("BUG07KHONGTONTAI", ticketCode),
                foreign.getMessage(),
                "Hai thong bao phai khong phan biet duoc, chi khac dung ma ve da go");
    }

    @Test
    @DisplayName("BUG-07: staff dung cum rap van tra cuu duoc binh thuong")
    public void staffOfSameCinemaStillSeesTheTicket() {
        StaffService.TicketLookup lookup = staffService.lookupTicket(ticketCode, staffOfHomeCinema());

        assertEquals(StaffService.Verdict.READY, lookup.getVerdict(),
                "Fixture: ve hop le trong khung gio check-in, thuc te " + lookup.getMessage());
        assertTrue(lookup.getMessage().contains(ROOM_NAME));
    }

    @Test
    @DisplayName("BUG-07: admin toan he thong khong bi chan")
    public void systemWideAdminIsNotBlocked() {
        StaffService.TicketLookup lookup = staffService.lookupTicket(ticketCode, systemAdmin());

        assertEquals(StaffService.Verdict.READY, lookup.getVerdict());
    }

    // ------------------------------------------------------------------ helpers

    private static User staffOfOtherCinema() {
        return actor(3, "staff", HOME_CINEMA_ID + 1);
    }

    private static User staffOfHomeCinema() {
        return actor(3, "staff", HOME_CINEMA_ID);
    }

    private static User systemAdmin() {
        return actor(5, "admin", null);
    }

    private static User actor(int id, String role, Integer cinemaId) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setCinemaId(cinemaId);
        return user;
    }

    private int oneBookableSeat() {
        return bookingService.getSeatMap(showtimeId).stream()
                .filter(seat -> seat.isAvailableFor(MEMBER_ID))
                .map(ShowtimeSeat::getId)
                .findFirst()
                .orElseThrow();
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
                DELETE FROM Seats WHERE RoomId IN (SELECT Id FROM Rooms WHERE Name=?);
                DELETE FROM Rooms WHERE Name=?;
                """, FILM_TITLE, FILM_TITLE, FILM_TITLE, FILM_TITLE, FILM_TITLE,
                FILM_TITLE, FILM_TITLE, FILM_TITLE, FILM_TITLE, ROOM_NAME, ROOM_NAME);
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
