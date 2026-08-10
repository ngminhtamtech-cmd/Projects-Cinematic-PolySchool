package com.mycompany.website.ban.ve.xem.phim.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.AdminNotification;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
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

/**
 * Doi chieu trang thai ghe user dat voi canh bao manager/admin.
 */
@Tag("it")
@DisplayName("Sold-out notification cross-role flow")
public class SoldOutNotificationFlowIT {
    private static final String FILM_TITLE = "FLOW-SOLDOUT Enterprise";
    private static final int CINEMA_ID = 1;
    private static final int ROOM_ID = 1;

    static {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    private final AdminService adminService = new AdminService();
    private int filmId;
    private int showtimeId;

    @BeforeAll
    static void configure() {
        DBConnection.shutdown();
    }

    @BeforeEach
    void createFixture() throws SQLException {
        cleanupFixtures();
        assertTestDatabase();
        filmId = insert("""
                INSERT INTO Films (Title, ReleaseDate, EndDate, DurationMinutes, Status)
                VALUES (?, CAST(GETDATE() AS DATE), DATEADD(DAY,30,CAST(GETDATE() AS DATE)),120,'showing')
                """, FILM_TITLE);
        execute("INSERT INTO CinemaFilms (CinemaId, FilmId) VALUES (?, ?)", CINEMA_ID, filmId);
        showtimeId = insert("""
                INSERT INTO Showtimes
                    (FilmId,CinemaId,RoomId,StartTime,EndTime,BasePrice,Format,Version,Language)
                VALUES (?, ?, ?, DATEADD(DAY,3,GETDATE()), DATEADD(MINUTE,120,DATEADD(DAY,3,GETDATE())),
                        90000,'2D','Subtitle','Vietnamese')
                """, filmId, CINEMA_ID, ROOM_ID);
        execute("""
                INSERT INTO ShowtimeSeats (ShowtimeId,SeatId,Status,ExtraFee)
                SELECT ?,Id,'available',0 FROM Seats WHERE RoomId=?
                """, showtimeId, ROOM_ID);
        assertTrue(queryInt("SELECT COUNT(*) FROM ShowtimeSeats WHERE ShowtimeId=?", showtimeId) > 0);
    }

    @AfterEach
    void cleanup() throws SQLException {
        cleanupFixtures();
    }

    @AfterAll
    static void shutdown() {
        DBConnection.shutdown();
    }

    @Test
    @DisplayName("FLOW-NOTIFY-SOLDOUT-001: temporary holds do not create a permanent sold-out alert")
    void temporaryHoldsDoNotTriggerSoldOut() throws SQLException {
        execute("""
                UPDATE ShowtimeSeats
                SET Status='held', HeldByUserId=1, HeldAt=GETDATE(), HeldUntil=DATEADD(MINUTE,10,GETDATE())
                WHERE ShowtimeId=?
                """, showtimeId);

        adminService.checkAndNotifySoldOutShowtimes();

        assertEquals(0, notificationCount(showtimeId),
                """
                FLOW-NOTIFY-SOLDOUT-001 all seats are only temporarily held.
                Admin/manager must not receive a permanent sold-out alert until seats are actually booked.
                """);
    }

    @Test
    @DisplayName("FLOW-NOTIFY-SOLDOUT-002: a genuinely sold-out showtime alerts manager/admin exactly once")
    void bookedShowtimeCreatesOneScopedAlert() throws SQLException {
        bookEverySeat();

        adminService.checkAndNotifySoldOutShowtimes();
        adminService.checkAndNotifySoldOutShowtimes();

        assertEquals(1, notificationCount(showtimeId), "sold-out scan must be idempotent");
        User manager = actor(4, "manager", CINEMA_ID);
        User admin = actor(5, "admin", null);
        assertEquals(1, notificationsFor(adminService.listAdminNotifications(manager), showtimeId).size(),
                "manager of the cinema must receive the sold-out alert");
        assertEquals(1, notificationsFor(adminService.listAdminNotifications(admin), showtimeId).size(),
                "admin must receive the sold-out alert");
    }

    @Test
    @DisplayName("FLOW-NOTIFY-SOLDOUT-005: concurrent publishers still create exactly one alert")
    void concurrentPublishersCreateExactlyOneAlert() throws Exception {
        bookEverySeat();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> publishAfter(start));
            Future<?> second = executor.submit(() -> publishAfter(start));
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        assertEquals(1, notificationCount(showtimeId));
    }

    private void publishAfter(CountDownLatch start) {
        try {
            assertTrue(start.await(5, TimeUnit.SECONDS));
            adminService.notifyShowtimeSoldOut(showtimeId, FILM_TITLE, "Cinema", "Room", "20:00");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
    }

    @Test
    @DisplayName("FLOW-NOTIFY-SOLDOUT-003: releasing a seat resolves the stale sold-out alert")
    void releasedSeatResolvesSoldOutAlert() throws SQLException {
        bookEverySeat();
        adminService.checkAndNotifySoldOutShowtimes();
        assertEquals(1, notificationCount(showtimeId));

        execute("""
                UPDATE ShowtimeSeats SET Status='available',HeldByUserId=NULL,HeldAt=NULL,HeldUntil=NULL
                WHERE Id=(SELECT MIN(Id) FROM ShowtimeSeats WHERE ShowtimeId=?)
                """, showtimeId);
        adminService.checkAndNotifySoldOutShowtimes();

        assertEquals(0, notificationCount(showtimeId),
                "FLOW-NOTIFY-SOLDOUT-003 cancellation/refund reopened a seat but the sold-out alert stayed active");
    }

    @Test
    @DisplayName("FLOW-NOTIFY-SOLDOUT-004: one manager reading an alert does not mark it read for admin")
    void readStateIsPerRecipient() throws SQLException {
        bookEverySeat();
        adminService.checkAndNotifySoldOutShowtimes();
        AdminNotification notification = findNotification(showtimeId);
        User manager = actor(4, "manager", CINEMA_ID);
        User admin = actor(5, "admin", null);

        adminService.markNotificationRead(notification.getId(), manager);

        AdminNotification adminView = notificationsFor(
                adminService.listAdminNotifications(admin), showtimeId).get(0);
        assertFalse(adminView.isRead(),
                "FLOW-NOTIFY-SOLDOUT-004 manager read state leaked into the admin recipient");
    }

    private void bookEverySeat() throws SQLException {
        execute("""
                UPDATE ShowtimeSeats
                SET Status='booked', HeldByUserId=NULL, HeldAt=NULL, HeldUntil=NULL
                WHERE ShowtimeId=?
                """, showtimeId);
    }

    private List<AdminNotification> notificationsFor(List<AdminNotification> notifications, int targetId) {
        return notifications.stream()
                .filter(note -> "Showtime_SoldOut".equalsIgnoreCase(note.getTargetType()))
                .filter(note -> String.valueOf(targetId).equals(note.getTargetId()))
                .toList();
    }

    private AdminNotification findNotification(int targetId) {
        return notificationsFor(adminService.listAdminNotifications(), targetId).stream()
                .findFirst().orElseThrow();
    }

    private int notificationCount(int targetId) throws SQLException {
        return queryInt("""
                SELECT COUNT(*) FROM AdminNotifications
                WHERE TargetType='Showtime_SoldOut' AND TargetId=?
                """, String.valueOf(targetId));
    }

    private User actor(int id, String role, Integer cinemaId) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setCinemaId(cinemaId);
        return user;
    }

    private void cleanupFixtures() throws SQLException {
        assertTestDatabase();
        execute("""
                DELETE FROM AdminNotifications
                WHERE TargetType='Showtime_SoldOut'
                  AND TargetId IN (
                    SELECT CAST(s.Id AS NVARCHAR(50))
                    FROM Showtimes s JOIN Films f ON f.Id=s.FilmId
                    WHERE f.Title=?
                  );
                DELETE FROM ShowtimeSeats
                WHERE ShowtimeId IN (
                    SELECT s.Id FROM Showtimes s JOIN Films f ON f.Id=s.FilmId WHERE f.Title=?
                  );
                DELETE FROM Showtimes WHERE FilmId IN (SELECT Id FROM Films WHERE Title=?);
                DELETE FROM CinemaFilms WHERE FilmId IN (SELECT Id FROM Films WHERE Title=?);
                DELETE FROM Films WHERE Title=?;
                """, FILM_TITLE, FILM_TITLE, FILM_TITLE, FILM_TITLE, FILM_TITLE);
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

    private int queryInt(String sql, Object value) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
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
