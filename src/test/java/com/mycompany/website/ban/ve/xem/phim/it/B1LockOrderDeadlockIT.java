package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.model.ShowtimeSeat;
import com.mycompany.website.ban.ve.xem.phim.service.BookingService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * B.1 — hai duong dat ve phai khoa theo CUNG mot thu tu.
 *
 * <p><b>Vong deadlock.</b> {@code createDraftOrder} khoa {@code ShowtimeSeats} truoc
 * ({@code lockSeats}) roi moi lay {@code UPDLOCK} tren {@code Orders}
 * ({@code ensureDraftQuotaAvailable}, them vao khi sua BUG-04b). {@code payOrder} di nguoc lai:
 * {@code UPDLOCK,HOLDLOCK} tren {@code Orders} ({@code findPendingOrderForUpdate}) roi moi
 * {@code lockSeats}. Mot nguoi vua tao don moi vua thanh toan don cu tren cung suat chieu la du
 * khep vong.</p>
 *
 * <p><b>Vi sao mot ben viet tay.</b> Cho deadlock la mot cua so vai mili giay; tha hai thread
 * chay tu do roi mong trung nhau se cho ra mot test khi do khi khong — vo dung. Nen ben
 * {@code payOrder} duoc dung lai bang chinh hai cau khoa cua no
 * ({@code Orders WITH (UPDLOCK, HOLDLOCK)} → {@code ShowtimeSeats WITH (UPDLOCK, HOLDLOCK)}) de
 * chan dung diem giua. Ben con lai la {@code createDraftOrder} THAT — thu tu khoa cua no chinh
 * la thu dang duoc do.</p>
 */
@Tag("it")
@DisplayName("B.1 — tao don va thanh toan khong duoc khoa nguoc chieu nhau")
public class B1LockOrderDeadlockIT {

    private static final String FILM_TITLE = "B1-LOCK-ORDER Enterprise";
    private static final int CINEMA_ID = 1;
    private static final int ROOM_ID = 1;
    private static final int MEMBER_ID = 1;

    /** Ma loi SQL Server cho "nan nhan bi chon de go vong deadlock". */
    private static final int DEADLOCK_VICTIM = 1205;

    static {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    private final BookingService bookingService = new BookingService();
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
    @DisplayName("thanh toan don cu song song voi tao don moi -> khong sinh deadlock victim")
    public void payingOldOrderWhileCreatingNewOneDoesNotDeadlock() throws Exception {
        // Don nhap cu cua CHINH nguoi nay tren CHINH suat nay: no vua la thu payOrder khoa,
        // vua la dong ma ensureDraftQuotaAvailable phai dem qua.
        OrderRecord existingDraft = bookingService.createDraftOrder(
                MEMBER_ID, showtimeId, List.of(bookableSeats().get(0)), Map.of(), null, "card");
        int freeSeatId = bookableSeats().get(1);

        AtomicReference<Throwable> creatorFailure = new AtomicReference<>();
        SQLException payerFailure = null;

        try (Connection payer = DBConnection.getConnection()) {
            payer.setAutoCommit(false);
            // Khong de test treo vinh vien neu vong khoa bien mat.
            execute(payer, "SET LOCK_TIMEOUT 15000");

            // payOrder buoc 1 — findPendingOrderForUpdate.
            queryOne(payer, "SELECT Id FROM Orders WITH (UPDLOCK, HOLDLOCK) WHERE Id=" + existingDraft.getId());

            Thread creator = new Thread(() -> {
                try {
                    bookingService.createDraftOrder(
                            MEMBER_ID, showtimeId, List.of(freeSeatId), Map.of(), null, "card");
                } catch (Throwable ex) {
                    creatorFailure.set(ex);
                }
            }, "b1-creator");
            creator.start();

            // Doi den khi createDraftOrder da di het buoc khoa dau tien cua no va dung lai.
            Thread.sleep(2500);

            try {
                // payOrder buoc 2 — lockSeats tren dung cai ghe kia.
                queryOne(payer, "SELECT Id FROM ShowtimeSeats WITH (UPDLOCK, HOLDLOCK) WHERE Id=" + freeSeatId);
            } catch (SQLException ex) {
                payerFailure = ex;
            }
            payer.commit();
            creator.join(60_000);
        }

        assertNoDeadlock(payerFailure, "duong thanh toan");
        assertNoDeadlock(creatorFailure.get(), "duong tao don");
    }

    @Test
    @DisplayName("tran so don nhap dang mo van con hieu luc sau khi doi thu tu khoa")
    public void draftQuotaStillEnforcedAfterReordering() {
        List<Integer> seats = bookableSeats();
        assertTrue(seats.size() >= 3, "fixture can it nhat 3 ghe dat duoc, dang co " + seats.size());

        for (int index = 0; index < 3; index++) {
            bookingService.createDraftOrder(
                    MEMBER_ID, showtimeId, List.of(seats.get(index)), Map.of(), null, "card");
        }

        RuntimeException blocked = org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> bookingService.createDraftOrder(
                        MEMBER_ID, showtimeId, List.of(bookableSeats().get(0)), Map.of(), null, "card"),
                "Don thu 4 phai bi tran chan — doi thu tu khoa khong duoc lam mat luat BUG-04b");
        assertTrue(blocked.getMessage().contains("đơn giữ chỗ chưa thanh toán"), blocked.getMessage());
    }

    // ------------------------------------------------------------------ helpers

    private static void assertNoDeadlock(Throwable failure, String side) {
        for (Throwable cursor = failure; cursor != null; cursor = cursor.getCause()) {
            if (cursor instanceof SQLException sqlException && sqlException.getErrorCode() == DEADLOCK_VICTIM) {
                fail(side + " bi chon lam nan nhan deadlock (SQL 1205): " + sqlException.getMessage()
                        + "\nHai duong dang khoa nguoc chieu nhau tren Orders va ShowtimeSeats.");
            }
        }
    }

    private List<Integer> bookableSeats() {
        return bookingService.getSeatMap(showtimeId).stream()
                .filter(seat -> seat.isAvailableFor(MEMBER_ID))
                .filter(seat -> !"couple".equalsIgnoreCase(seat.getSeatType()))
                .map(ShowtimeSeat::getId)
                .toList();
    }

    private static void queryOne(Connection connection, String sql) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rs.getInt(1);
            }
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.execute();
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
