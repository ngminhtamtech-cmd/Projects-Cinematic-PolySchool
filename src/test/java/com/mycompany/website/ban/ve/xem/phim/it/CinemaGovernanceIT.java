package com.mycompany.website.ban.ve.xem.phim.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.ApprovalRequest;
import com.mycompany.website.ban.ve.xem.phim.model.Promotion;
import com.mycompany.website.ban.ve.xem.phim.model.Room;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
import com.mycompany.website.ban.ve.xem.phim.service.ApprovalService;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** End-to-end invariants for cinema-scoped manager governance. */
@Tag("it")
@DisplayName("Cinema-scoped manager/admin governance")
public class CinemaGovernanceIT {
    private static final int CINEMA_ONE = 1;
    private static final int CINEMA_TWO = 2;

    private static ApprovalService approvalService;
    private static AdminService adminService;
    private static User admin;
    private static User manager;

    @BeforeAll
    static void setUp() {
        System.setProperty("cinebook.db.config",
                System.getProperty("cinebook.it.config", "target/db.it.properties"));
        DBConnection.shutdown();
        approvalService = new ApprovalService();
        adminService = new AdminService();
        admin = actor(5, "admin", null, "Admin System Test");
        manager = actor(4, "manager", CINEMA_ONE, "Manager Cinema 1 Test");
    }

    @AfterAll
    static void tearDown() {
        DBConnection.shutdown();
    }

    @Test
    @DisplayName("Film is invisible to the cinema until admin approval, then notifies its manager")
    void filmAssignmentIsAtomicAndCinemaScoped() throws SQLException {
        int filmId = insertFilm("IT governance film " + token());
        int requestId = 0;
        try {
            requestId = approvalService.requestFilmAssignment(filmId, manager);
            final int approvalId = requestId;

            assertEquals(0, scalar("SELECT COUNT(*) FROM CinemaFilms WHERE CinemaId=? AND FilmId=? "
                    + "AND Status=N'active'", CINEMA_ONE, filmId));
            assertTrue(approvalService.listRequests(admin, "PENDING", CINEMA_ONE).stream()
                    .anyMatch(item -> item.getId() == approvalId));
            assertTrue(approvalService.listRequests(admin, "PENDING", CINEMA_TWO).stream()
                    .noneMatch(item -> item.getId() == approvalId));

            approvalService.approve(requestId, null, "Approved by integration test", admin);

            assertEquals(1, scalar("SELECT COUNT(*) FROM CinemaFilms WHERE CinemaId=? AND FilmId=? "
                    + "AND Status=N'active'", CINEMA_ONE, filmId));
            ApprovalRequest approved = approvalService.findRequest(requestId, admin, CINEMA_ONE);
            assertEquals("APPROVED", approved.getStatus());
            assertEquals(1, scalar("SELECT COUNT(*) FROM NotificationRecipients nr "
                    + "JOIN AdminNotifications n ON n.Id=nr.NotificationId "
                    + "WHERE nr.SourceType=N'admin' AND nr.UserId=? "
                    + "AND n.ApprovalRequestId=? AND n.EventType=N'FILM_ASSIGNED'",
                    manager.getId(), requestId));

            BookingException duplicateReview = assertThrows(BookingException.class,
                    () -> approvalService.approve(approvalId, null, null, admin));
            assertEquals(409, duplicateReview.getStatusCode());
        } finally {
            cleanupRequest(requestId);
            exec("DELETE FROM CinemaFilms WHERE FilmId=?", filmId);
            exec("DELETE FROM Films WHERE Id=?", filmId);
        }
    }

    @Test
    @DisplayName("Room and all requested seats are created only after one admin approval")
    void roomRequestCreatesRoomAndSeatsAtomically() throws SQLException {
        String roomName = "IT-GOV-" + token().substring(0, 8);
        Room room = new Room();
        room.setName(roomName);
        room.setRoomType("VIP");
        int requestId = 0;
        Integer roomId = null;
        try {
            requestId = approvalService.requestRoomCreation(room, 2, 3, List.of(), manager);
            assertEquals(0, scalar("SELECT COUNT(*) FROM Rooms WHERE CinemaId=? AND Name=?",
                    CINEMA_ONE, roomName));

            approvalService.approve(requestId, null, "Layout approved", admin);
            ApprovalRequest approved = approvalService.findRequest(requestId, admin, CINEMA_ONE);
            roomId = approved.getResolvedEntityId();
            assertNotNull(roomId);
            assertEquals("Room", approved.getResolvedEntityType());
            assertEquals(1, scalar("SELECT COUNT(*) FROM Rooms WHERE Id=? AND CinemaId=?",
                    roomId, CINEMA_ONE));
            assertEquals(6, scalar("SELECT COUNT(*) FROM Seats WHERE RoomId=?", roomId));
        } finally {
            if (roomId != null) {
                exec("DELETE FROM Seats WHERE RoomId=?", roomId);
                exec("DELETE FROM Rooms WHERE Id=?", roomId);
            }
            cleanupRequest(requestId);
        }
    }

    @Test
    @DisplayName("Reassigning a manager cancels old pending requests and revokes sessions")
    void managerReassignmentRevokesOldCinemaState() throws SQLException {
        User temporaryManager = new User();
        temporaryManager.setUsername("itgov_" + token().substring(0, 10));
        temporaryManager.setFullName("IT Governance Manager");
        temporaryManager.setEmail("itgov_" + token() + "@test.local");
        temporaryManager.setPasswordHash("Governance!2026x");
        temporaryManager.setCinemaId(CINEMA_ONE);
        int filmId = 0;
        int requestId = 0;
        try {
            adminService.createManager(temporaryManager, admin);
            filmId = insertFilm("IT reassignment film " + token());
            requestId = approvalService.requestFilmAssignment(filmId, temporaryManager);
            insertRefreshToken(temporaryManager.getId());

            adminService.reassignCinemaScopedUser(temporaryManager.getId(), CINEMA_TWO, admin);

            assertEquals(CINEMA_TWO, scalar("SELECT CinemaId FROM Users WHERE Id=?",
                    temporaryManager.getId()));
            assertEquals("CANCELLED", scalarString(
                    "SELECT Status FROM ApprovalRequests WHERE Id=?", requestId));
            assertEquals(1, scalar("SELECT COUNT(*) FROM RefreshTokens WHERE UserId=? "
                    + "AND RevokedAt IS NOT NULL AND RevocationReason=N'CINEMA_REASSIGNED'",
                    temporaryManager.getId()));
        } finally {
            cleanupRequest(requestId);
            if (filmId > 0) exec("DELETE FROM Films WHERE Id=?", filmId);
            cleanupTemporaryUser(temporaryManager.getId());
        }
    }

    @Test
    @DisplayName("Manager owns promotions they create; admin may still edit them")
    void promotionOwnershipIsEnforced() throws SQLException {
        User otherManager = createTemporaryManager(CINEMA_TWO);
        Promotion promotion = new Promotion();
        promotion.setCode("ITGOV" + token().substring(0, 8).toUpperCase());
        promotion.setDescription("Owned by cinema manager");
        promotion.setDiscountPercent(10D);
        promotion.setStartDate(LocalDate.now());
        promotion.setEndDate(LocalDate.now().plusDays(7));
        promotion.setStatus("inactive");
        promotion.setVoucherType("discount");
        try {
            adminService.savePromotion(promotion, manager);
            assertTrue(promotion.getId() > 0);
            assertEquals(manager.getId(), scalar(
                    "SELECT CreatedByUserId FROM Promotions WHERE Id=?", promotion.getId()));

            BookingException forbidden = assertThrows(BookingException.class,
                    () -> adminService.savePromotion(promotion, otherManager));
            assertEquals(403, forbidden.getStatusCode());

            promotion.setDescription("Admin reviewed this promotion");
            adminService.savePromotion(promotion, admin);
            assertEquals("Admin reviewed this promotion", scalarString(
                    "SELECT Description FROM Promotions WHERE Id=?", promotion.getId()));
        } finally {
            if (promotion.getId() > 0) {
                exec("DELETE FROM AuditLogs WHERE TargetType=N'Promotion' AND TargetId=?",
                        String.valueOf(promotion.getId()));
                exec("DELETE FROM Promotions WHERE Id=?", promotion.getId());
            }
            cleanupTemporaryUser(otherManager.getId());
        }
    }

    private static User createTemporaryManager(int cinemaId) {
        User user = new User();
        user.setUsername("itgov_" + token().substring(0, 10));
        user.setFullName("IT Governance Secondary Manager");
        user.setEmail("itgov_" + token() + "@test.local");
        user.setPasswordHash("Governance!2026x");
        user.setCinemaId(cinemaId);
        adminService.createManager(user, admin);
        return user;
    }

    private static User actor(int id, String role, Integer cinemaId, String name) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setCinemaId(cinemaId);
        user.setFullName(name);
        return user;
    }

    private static int insertFilm(String title) throws SQLException {
        return insertReturningId("INSERT INTO Films(Title,ReleaseDate,EndDate,DurationMinutes,Status) "
                + "VALUES(?,CONVERT(date,'2026-01-01'),CONVERT(date,'2026-12-31'),120,N'showing')",
                title);
    }

    private static void insertRefreshToken(int userId) throws SQLException {
        exec("INSERT INTO RefreshTokens(UserId,FamilyId,TokenHash,ExpiresAt) "
                + "VALUES(?,NEWID(),?,DATEADD(day,1,SYSDATETIME()))",
                userId, UUID.randomUUID().toString().replace("-", "")
                        + UUID.randomUUID().toString().replace("-", ""));
    }

    private static void cleanupRequest(int requestId) throws SQLException {
        if (requestId <= 0) return;
        exec("DELETE nr FROM NotificationRecipients nr JOIN AdminNotifications n "
                + "ON n.Id=nr.NotificationId WHERE nr.SourceType=N'admin' "
                + "AND n.ApprovalRequestId=?", requestId);
        exec("DELETE FROM AdminNotifications WHERE ApprovalRequestId=?", requestId);
        exec("DELETE FROM AuditLogs WHERE TargetType=N'ApprovalRequest' AND TargetId=?",
                String.valueOf(requestId));
        exec("DELETE FROM ApprovalRequests WHERE Id=?", requestId);
    }

    private static void cleanupTemporaryUser(int userId) throws SQLException {
        if (userId <= 0) return;
        exec("DELETE FROM NotificationRecipients WHERE UserId=?", userId);
        exec("DELETE FROM RefreshTokens WHERE UserId=?", userId);
        exec("DELETE FROM AuditLogs WHERE ActorUserId=? OR (TargetType=N'User' AND TargetId=?)",
                userId, String.valueOf(userId));
        exec("DELETE FROM Users WHERE Id=?", userId);
    }

    private static String token() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static int insertReturningId(String sql, Object... parameters) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, parameters);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Missing generated key");
                return keys.getInt(1);
            }
        }
    }

    private static void exec(String sql, Object... parameters) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            statement.executeUpdate();
        }
    }

    private static int scalar(String sql, Object... parameters) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("Missing scalar result");
                return result.getInt(1);
            }
        }
    }

    private static String scalarString(String sql, Object... parameters) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("Missing scalar result");
                return result.getString(1);
            }
        }
    }

    private static void bind(PreparedStatement statement, Object... parameters) throws SQLException {
        for (int i = 0; i < parameters.length; i++) {
            statement.setObject(i + 1, parameters[i]);
        }
    }
}
