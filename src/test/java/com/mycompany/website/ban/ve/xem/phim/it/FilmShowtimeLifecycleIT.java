package com.mycompany.website.ban.ve.xem.phim.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
import com.mycompany.website.ban.ve.xem.phim.service.FilmDeleteImpact;
import com.mycompany.website.ban.ve.xem.phim.service.FilmDeletionMode;
import com.mycompany.website.ban.ve.xem.phim.service.FilmDeletionOutcome;
import com.mycompany.website.ban.ve.xem.phim.service.ShowtimeDeletionImpact;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("it")
@DisplayName("Film and showtime safe deletion lifecycle")
class FilmShowtimeLifecycleIT {
    private static final AdminService ADMIN_SERVICE = new AdminService();
    private static User admin;
    private static User manager;

    @BeforeAll
    static void configure() {
        System.setProperty("cinebook.db.config",
                System.getProperty("cinebook.it.config", "target/db.it.properties"));
        DBConnection.shutdown();
        admin = actor(5, "admin", null);
        manager = actor(4, "manager", 1);
    }

    @AfterAll
    static void shutdown() {
        DBConnection.shutdown();
    }

    @Test
    void filmDeletionModesPreserveTransactionsAndApplyCommentPolicy() throws SQLException {
        int preservedFilm = insertEndedFilm("IT lifecycle preserve");
        int preservedComment = insertComment(preservedFilm);
        insertReport(preservedComment);

        assertEquals(FilmDeletionOutcome.TOMBSTONED,
                ADMIN_SERVICE.deleteFilm(preservedFilm, FilmDeletionMode.PRESERVE_COMMENTS,
                        "", admin));
        assertEquals(1, scalar("SELECT COUNT(*) FROM Films WHERE Id=" + preservedFilm
                + " AND DeletedAt IS NOT NULL AND DeletionMode='PRESERVE_COMMENTS'"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM Comments WHERE FilmId=" + preservedFilm));
        assertEquals(1, scalar("SELECT COUNT(*) FROM CommentReports WHERE CommentId=" + preservedComment));

        int purgedFilm = insertEndedFilm("IT lifecycle purge");
        int purgedComment = insertComment(purgedFilm);
        insertReport(purgedComment);
        assertEquals(FilmDeletionOutcome.TOMBSTONED,
                ADMIN_SERVICE.deleteFilm(purgedFilm, FilmDeletionMode.PURGE_COMMENTS,
                        "IT lifecycle purge", admin));
        assertEquals(0, scalar("SELECT COUNT(*) FROM Comments WHERE FilmId=" + purgedFilm));
        assertEquals(0, scalar("SELECT COUNT(*) FROM CommentReports WHERE CommentId=" + purgedComment));

        int unreferencedFilm = insertActiveFilm("IT lifecycle hard");
        FilmDeleteImpact preview = ADMIN_SERVICE.previewFilmDeleteImpact(unreferencedFilm, admin);
        assertTrue(preview.eligible(), preview.blockedReason());
        assertEquals(FilmDeletionOutcome.HARD_DELETED,
                ADMIN_SERVICE.deleteFilm(unreferencedFilm, FilmDeletionMode.PRESERVE_COMMENTS,
                        "", admin));
        assertEquals(0, scalar("SELECT COUNT(*) FROM Films WHERE Id=" + unreferencedFilm));

        cleanupTombstone(preservedFilm);
        cleanupTombstone(purgedFilm);
    }

    @Test
    void showtimeRequiresSuspendFiveMinuteServerWindowAndFreshConfirmation() throws SQLException {
        int showtimeId = insertFutureShowtime();
        try {
            ShowtimeDeletionImpact suspended = ADMIN_SERVICE.requestShowtimeDeletion(showtimeId, manager);
            assertEquals("SUSPENDED", suspended.saleStatus());
            assertTrue(suspended.secondsRemaining() >= 295 && suspended.secondsRemaining() <= 300,
                    "the wait must be computed by SQL Server");
            assertFalse(suspended.ready());

            ShowtimeDeletionImpact resumed = ADMIN_SERVICE.resumeShowtimeSale(showtimeId, manager);
            assertEquals("ON_SALE", resumed.saleStatus());

            ADMIN_SERVICE.requestShowtimeDeletion(showtimeId, manager);
            execute("UPDATE Showtimes SET DeleteNotBefore=DATEADD(SECOND,-1,SYSDATETIME()) WHERE Id=" + showtimeId);
            ShowtimeDeletionImpact ready = ADMIN_SERVICE.previewShowtimeDeletion(showtimeId, manager);
            assertTrue(ready.ready(), ready.blockedReason());
            assertTrue(ADMIN_SERVICE.confirmShowtimeDeletion(showtimeId, manager));
            assertEquals(0, scalar("SELECT COUNT(*) FROM Showtimes WHERE Id=" + showtimeId));
        } finally {
            execute("DELETE FROM ShowtimeSeats WHERE ShowtimeId=" + showtimeId);
            execute("DELETE FROM Showtimes WHERE Id=" + showtimeId);
        }
    }

    private static User actor(int id, String role, Integer cinemaId) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setCinemaId(cinemaId);
        return user;
    }

    private static int insertEndedFilm(String title) throws SQLException {
        return insertId("""
                INSERT INTO Films(Title,ReleaseDate,EndDate,DurationMinutes,Status)
                VALUES(?,DATEADD(DAY,-10,CAST(GETDATE() AS date)),
                         DATEADD(DAY,-1,CAST(GETDATE() AS date)),90,'ended')
                """, title);
    }

    private static int insertActiveFilm(String title) throws SQLException {
        return insertId("""
                INSERT INTO Films(Title,ReleaseDate,EndDate,DurationMinutes,Status)
                VALUES(?,CAST(GETDATE() AS date),DATEADD(DAY,30,CAST(GETDATE() AS date)),90,'showing')
                """, title);
    }

    private static int insertComment(int filmId) throws SQLException {
        return insertId("INSERT INTO Comments(UserId,FilmId,Rate,Content,Report) VALUES(1,?,5,N'IT',1)", filmId);
    }

    private static void insertReport(int commentId) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO CommentReports(CommentId,ReporterUserId,Reason) VALUES(?,2,N'IT')")) {
            ps.setInt(1, commentId);
            ps.executeUpdate();
        }
    }

    private static int insertFutureShowtime() throws SQLException {
        return insertId("""
                INSERT INTO Showtimes(FilmId,CinemaId,RoomId,StartTime,EndTime,BasePrice)
                VALUES(1,1,1,DATEADD(DAY,30,GETDATE()),DATEADD(MINUTE,120,DATEADD(DAY,30,GETDATE())),90000)
                """, null);
    }

    private static int insertId(String sql, Object value) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (value instanceof String text) ps.setString(1, text);
            else if (value instanceof Integer number) ps.setInt(1, number);
            assertEquals(1, ps.executeUpdate());
            try (ResultSet keys = ps.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getInt(1);
            }
        }
    }

    private static int scalar(String sql) throws SQLException {
        try (Connection connection = DBConnection.getConnection(); Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }

    private static void execute(String sql) throws SQLException {
        try (Connection connection = DBConnection.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static void cleanupTombstone(int filmId) throws SQLException {
        execute("DELETE FROM CommentReports WHERE CommentId IN (SELECT Id FROM Comments WHERE FilmId=" + filmId + ")");
        execute("DELETE FROM Comments WHERE FilmId=" + filmId);
        execute("DELETE FROM Films WHERE Id=" + filmId);
    }
}
