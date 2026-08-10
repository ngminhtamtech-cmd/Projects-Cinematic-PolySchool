package com.mycompany.website.ban.ve.xem.phim.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.AdminNotification;
import com.mycompany.website.ban.ve.xem.phim.model.FilmComment;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * P1 tenant regression for manager comment moderation.
 *
 * <p>A member is in a manager's scope when its primary {@code CinemaId} matches or it owns
 * an order at that cinema. Admin remains global.</p>
 */
@Tag("it")
@DisplayName("Comment moderation tenant scope")
public class CommentModerationTenantScopeIT {
    private static final String EMAIL_PREFIX = "comment-scope-";
    private static final String FILM_PREFIX = "COMMENT-SCOPE-";
    private static final String TICKET_PREFIX = "CMSCOPE";

    static {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    private final AdminService service = new AdminService();
    private final List<Integer> createdUserIds = new ArrayList<>();
    private final List<Integer> createdCommentIds = new ArrayList<>();
    private final List<Integer> createdFilmIds = new ArrayList<>();
    private final List<Integer> createdNotificationIds = new ArrayList<>();
    private final List<Integer> createdAppealIds = new ArrayList<>();

    private int ownUserId;
    private int foreignUserId;
    private int orderScopedUserId;
    private int scopedStaffUserId;
    private int ownCommentId;
    private int foreignCommentId;
    private int orderScopedCommentId;
    private int foreignFilmId;

    @BeforeAll
    static void configure() {
        DBConnection.shutdown();
    }

    @BeforeEach
    void setup() throws SQLException {
        assertTestDatabase();
        cleanupByPrefix();

        ownUserId = createMember("own", 1);
        foreignUserId = createMember("foreign", 2);
        orderScopedUserId = createMember("order", 2);
        scopedStaffUserId = createScopedStaff("appeal-staff", 1);
        foreignFilmId = createForeignFilm();
        insertOrderAtManagerCinema(ownUserId);
        insertOrderAtManagerCinema(orderScopedUserId);

        ownCommentId = createComment(ownUserId, 1, "own comment");
        foreignCommentId = createComment(foreignUserId, foreignFilmId, "foreign comment");
        orderScopedCommentId = createComment(orderScopedUserId, 1, "order-scoped comment");
    }

    @AfterEach
    void cleanup() throws SQLException {
        cleanupNotifications();
        cleanupAppeals();
        cleanupAuditTargets();
        cleanupByPrefix();
    }

    @AfterAll
    static void shutdown() {
        DBConnection.shutdown();
    }

    @Test
    @DisplayName("manager list is SQL-scoped before ordering; admin list remains global")
    void managerListContainsPrimaryAndOrderScopeButNeverForeignRows() throws Exception {
        insertForeignNoise(205);

        Set<Integer> managerUsers = listComments(null, null, manager()).stream()
                .map(FilmComment::getUserId)
                .collect(Collectors.toSet());
        Set<Integer> adminUsers = listComments(null, null, admin()).stream()
                .map(FilmComment::getUserId)
                .collect(Collectors.toSet());

        assertTrue(managerUsers.containsAll(Set.of(ownUserId, orderScopedUserId)),
                "Foreign newer rows must not push the manager's older scoped comments past a global limit");
        Set<Integer> foreignFixtures = new HashSet<>(createdUserIds);
        foreignFixtures.remove(ownUserId);
        foreignFixtures.remove(orderScopedUserId);
        assertTrue(managerUsers.stream().noneMatch(foreignFixtures::contains),
                "Manager queue leaked a comment authored by a foreign member");
        assertTrue(adminUsers.containsAll(Set.of(ownUserId, foreignUserId, orderScopedUserId)));
        assertTrue(adminUsers.size() >= 208, "Admin must retain the global moderation queue");
    }

    @Test
    @DisplayName("foreign film filter reveals no comments to the manager")
    void managerCannotUseForeignFilmFilterToProbeComments() throws Exception {
        assertTrue(listComments(foreignFilmId, null, manager()).isEmpty());
    }

    @Test
    @DisplayName("warn rejects a foreign comment and leaves user/comment unchanged")
    void managerCannotWarnForeignComment() throws SQLException {
        int warningsBefore = userInt(foreignUserId, "WarningCount");
        boolean reportBefore = commentReport(foreignCommentId);

        BookingException forbidden = assertThrows(BookingException.class,
                () -> service.warnUserForComment(foreignCommentId, manager()));

        assertEquals(403, forbidden.getStatusCode());
        assertEquals(warningsBefore, userInt(foreignUserId, "WarningCount"));
        assertEquals(reportBefore, commentReport(foreignCommentId));
    }

    @Test
    @DisplayName("clear rejects a foreign comment and does not clear Report")
    void managerCannotClearForeignCommentReport() throws SQLException {
        BookingException forbidden = assertThrows(BookingException.class,
                () -> service.clearCommentReport(foreignCommentId, manager()));

        assertEquals(403, forbidden.getStatusCode());
        assertTrue(commentReport(foreignCommentId));
    }

    @Test
    @DisplayName("delete rejects a foreign comment and leaves the row intact")
    void managerCannotDeleteForeignComment() throws SQLException {
        BookingException forbidden = assertThrows(BookingException.class,
                () -> service.deleteComment(foreignCommentId, manager()));

        assertEquals(403, forbidden.getStatusCode());
        assertEquals(1, commentCount(foreignCommentId));
    }

    @Test
    @DisplayName("lock derives the member from the locked comment row")
    void managerCanLockOwnCommentAuthorButNotForeignCommentAuthor() throws Exception {
        lockUserForComment(ownCommentId, "COMMENT-SCOPE own violation", manager());

        assertTrue(userBoolean(ownUserId, "IsLocked"));
        assertFalse(userBoolean(foreignUserId, "IsLocked"));

        BookingException forbidden = assertThrows(BookingException.class,
                () -> lockUserForComment(
                        foreignCommentId, "COMMENT-SCOPE foreign violation", manager()));
        assertEquals(403, forbidden.getStatusCode());
        assertFalse(userBoolean(foreignUserId, "IsLocked"));
    }

    @Test
    @DisplayName("manager may moderate primary/order-scoped members and admin may moderate globally")
    void validManagerAndGlobalAdminMutationsStillWork() throws Exception {
        service.warnUserForComment(ownCommentId, manager());
        assertEquals(1, userInt(ownUserId, "WarningCount"));
        assertFalse(commentReport(ownCommentId));

        service.clearCommentReport(orderScopedCommentId, manager());
        assertFalse(commentReport(orderScopedCommentId));

        lockUserForComment(foreignCommentId, "COMMENT-SCOPE admin lock", admin());
        assertTrue(userBoolean(foreignUserId, "IsLocked"));

        execute("UPDATE Comments SET Report=1 WHERE Id=?", foreignCommentId);
        service.clearCommentReport(foreignCommentId, admin());
        assertFalse(commentReport(foreignCommentId));

        service.deleteComment(foreignCommentId, admin());
        assertEquals(0, commentCount(foreignCommentId));
    }

    @Test
    @DisplayName("the same reported comment can issue exactly one warning under concurrency")
    void concurrentWarningsAreAtomicAndIdempotent() throws Exception {
        execute("UPDATE Users SET WarningCount=2, IsLocked=0, LockReason=NULL, LockedAt=NULL WHERE Id=?",
                ownUserId);
        execute("UPDATE Comments SET Report=1 WHERE Id=?", ownCommentId);

        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = workers.submit(() -> warnAtBarrier(ready, start));
            Future<Boolean> second = workers.submit(() -> warnAtBarrier(ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS), "warning workers did not reach the barrier");
            start.countDown();

            boolean firstSucceeded = first.get(20, TimeUnit.SECONDS);
            boolean secondSucceeded = second.get(20, TimeUnit.SECONDS);
            assertTrue(firstSucceeded ^ secondSucceeded,
                    "one report may produce exactly one warning even when two managers submit together");
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }

        assertEquals(3, userInt(ownUserId, "WarningCount"));
        assertTrue(userBoolean(ownUserId, "IsLocked"));
        assertFalse(commentReport(ownCommentId));
        assertEquals(1, autoLockNotificationCount(ownUserId));
    }

    private boolean warnAtBarrier(CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS));
        try {
            service.warnUserForComment(ownCommentId, manager());
            return true;
        } catch (BookingException ex) {
            assertEquals(409, ex.getStatusCode(), "the losing concurrent warning must be an explicit conflict");
            return false;
        }
    }

    @Test
    @DisplayName("comment notifications follow member scope while admin remains global")
    void commentNotificationsAreScopedToCommentAuthor() throws SQLException {
        int ownReport = createNotification("CommentReport", ownCommentId);
        int orderComment = createNotification("Comment", orderScopedCommentId);
        int foreignReport = createNotification("CommentReport", foreignCommentId);
        int foreignComment = createNotification("Comment", foreignCommentId);

        Set<Integer> managerNotifications = service.listAdminNotifications(manager()).stream()
                .map(AdminNotification::getId)
                .collect(Collectors.toSet());
        Set<Integer> adminNotifications = service.listAdminNotifications(admin()).stream()
                .map(AdminNotification::getId)
                .collect(Collectors.toSet());

        assertTrue(managerNotifications.containsAll(Set.of(ownReport, orderComment)),
                "Manager must see comment alerts for primary and order-scoped members");
        assertFalse(managerNotifications.contains(foreignReport));
        assertFalse(managerNotifications.contains(foreignComment));
        assertTrue(adminNotifications.containsAll(
                Set.of(ownReport, orderComment, foreignReport, foreignComment)));
    }

    @Test
    @DisplayName("comment notification read state cannot be forged across cinemas")
    void managerCannotMarkForeignCommentNotificationRead() throws SQLException {
        int ownReport = createNotification("CommentReport", ownCommentId);
        int foreignReport = createNotification("CommentReport", foreignCommentId);

        service.markNotificationRead(ownReport, manager());
        assertEquals(1, recipientReadCount(ownReport, manager().getId()));

        BookingException forbidden = assertThrows(BookingException.class,
                () -> service.markNotificationRead(foreignReport, manager()));
        assertEquals(403, forbidden.getStatusCode());
        assertEquals(0, recipientReadCount(foreignReport, manager().getId()));

        service.markNotificationRead(foreignReport, admin());
        assertEquals(1, recipientReadCount(foreignReport, admin().getId()));
    }

    @Test
    @DisplayName("UserAppeal notifications expose no foreign member PII")
    void userAppealNotificationsFollowAccountAndRefundScope() throws SQLException {
        int memberAccountAppeal = createAppeal(ownUserId, null, "member account appeal");
        int staffAccountAppeal = createAppeal(scopedStaffUserId, null, "staff account appeal");
        int orderScopedRefundAppeal = createAppeal(orderScopedUserId,
                TICKET_PREFIX + orderScopedUserId, "order-scoped refund appeal");
        int foreignAccountAppeal = createAppeal(foreignUserId, null, "FOREIGN-PII-APPEAL");
        int memberNotification = createNotification(
                "UserAppeal", memberAccountAppeal, "member account appeal");
        int staffNotification = createNotification(
                "UserAppeal", staffAccountAppeal, "staff account appeal");
        int orderNotification = createNotification(
                "UserAppeal", orderScopedRefundAppeal, "order-scoped refund appeal");
        int foreignNotification = createNotification(
                "UserAppeal", foreignAccountAppeal, "FOREIGN-PII-APPEAL");

        List<AdminNotification> managerView = service.listAdminNotifications(manager());
        Set<Integer> managerIds = managerView.stream()
                .map(AdminNotification::getId)
                .collect(Collectors.toSet());
        Set<Integer> adminIds = service.listAdminNotifications(admin()).stream()
                .map(AdminNotification::getId)
                .collect(Collectors.toSet());

        assertTrue(managerIds.containsAll(Set.of(staffNotification, orderNotification)));
        assertFalse(managerIds.contains(memberNotification));
        assertFalse(managerIds.contains(foreignNotification));
        assertTrue(managerView.stream().noneMatch(note -> contains(
                note.getTitle(), "FOREIGN-PII-APPEAL")
                || contains(note.getMessage(), "FOREIGN-PII-APPEAL")),
                "Manager notification payload leaked foreign appeal PII");
        assertTrue(adminIds.containsAll(
                Set.of(memberNotification, staffNotification, orderNotification, foreignNotification)));
    }

    @Test
    @DisplayName("UserAppeal unread count includes only appeals in the manager cinema scope")
    void userAppealUnreadCountIsTenantScoped() throws SQLException {
        int managerBefore = service.getUnreadNotificationCount(manager());
        int adminBefore = service.getUnreadNotificationCount(admin());
        int ownAccountAppeal = createAppeal(scopedStaffUserId, null, "own unread appeal");
        int orderScopedRefundAppeal = createAppeal(orderScopedUserId,
                TICKET_PREFIX + orderScopedUserId, "order unread appeal");
        int foreignAccountAppeal = createAppeal(foreignUserId, null, "foreign unread appeal");
        createNotification("UserAppeal", ownAccountAppeal, "own unread appeal");
        createNotification("UserAppeal", orderScopedRefundAppeal, "order unread appeal");
        createNotification("UserAppeal", foreignAccountAppeal, "foreign unread appeal");

        assertEquals(managerBefore + 2, service.getUnreadNotificationCount(manager()));
        assertEquals(adminBefore + 3, service.getUnreadNotificationCount(admin()));
    }

    @Test
    @DisplayName("UserAppeal read state rejects a forged foreign notification id")
    void managerCannotMarkForeignAppealNotificationRead() throws SQLException {
        int ownAppeal = createAppeal(scopedStaffUserId, null, "own read appeal");
        int foreignAppeal = createAppeal(foreignUserId, null, "foreign read appeal");
        int ownNotification = createNotification("UserAppeal", ownAppeal, "own read appeal");
        int foreignNotification = createNotification(
                "UserAppeal", foreignAppeal, "foreign read appeal");

        service.markNotificationRead(ownNotification, manager());
        assertEquals(1, recipientReadCount(ownNotification, manager().getId()));

        BookingException forbidden = assertThrows(BookingException.class,
                () -> service.markNotificationRead(foreignNotification, manager()));
        assertEquals(403, forbidden.getStatusCode());
        assertEquals(0, recipientReadCount(foreignNotification, manager().getId()));

        service.markNotificationRead(foreignNotification, admin());
        assertEquals(1, recipientReadCount(foreignNotification, admin().getId()));
    }

    @SuppressWarnings("unchecked")
    private List<FilmComment> listComments(Integer filmId, Boolean reportedOnly, User actor)
            throws Exception {
        Method method = AdminService.class.getMethod(
                "listComments", Integer.class, Boolean.class, User.class);
        try {
            return (List<FilmComment>) method.invoke(service, filmId, reportedOnly, actor);
        } catch (InvocationTargetException ex) {
            throwCause(ex);
            throw ex;
        }
    }

    private void lockUserForComment(int commentId, String reason, User actor) throws Exception {
        Method method = AdminService.class.getMethod(
                "lockUserForComment", int.class, String.class, User.class);
        try {
            method.invoke(service, commentId, reason, actor);
        } catch (InvocationTargetException ex) {
            throwCause(ex);
        }
    }

    private static void throwCause(InvocationTargetException ex) throws Exception {
        Throwable cause = ex.getCause();
        if (cause instanceof BookingException bookingException) {
            throw bookingException;
        }
        if (cause instanceof Exception exception) {
            throw exception;
        }
        throw ex;
    }

    private void insertForeignNoise(int count) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement users = connection.prepareStatement("""
                     INSERT INTO Users (FullName, Email, PasswordHash, Role, CinemaId)
                     VALUES (?, ?, ?, 'member', 2)
                     """, Statement.RETURN_GENERATED_KEYS);
             PreparedStatement comments = connection.prepareStatement("""
                     INSERT INTO Comments (UserId, FilmId, Rate, Content, Report, CreatedAt)
                     VALUES (?, ?, 1, 'foreign noise', 1, DATEADD(MINUTE, ?, GETDATE()))
                     """, Statement.RETURN_GENERATED_KEYS)) {
            connection.setAutoCommit(false);
            try {
                for (int index = 0; index < count; index++) {
                    users.setString(1, "Comment Scope Noise " + index);
                    users.setString(2, EMAIL_PREFIX + "noise-" + index + "@test.local");
                    users.setString(3,
                            "$2a$12$Cja7B.jV5kPjNnjZfPWAR.5lZPcgJ9Z/mTRrIUTaSBxpO6iTyfoBm");
                    assertEquals(1, users.executeUpdate());
                    int userId;
                    try (ResultSet keys = users.getGeneratedKeys()) {
                        assertTrue(keys.next());
                        userId = keys.getInt(1);
                    }
                    createdUserIds.add(userId);

                    comments.setInt(1, userId);
                    comments.setInt(2, foreignFilmId);
                    comments.setInt(3, index + 1);
                    assertEquals(1, comments.executeUpdate());
                    try (ResultSet keys = comments.getGeneratedKeys()) {
                        assertTrue(keys.next());
                        createdCommentIds.add(keys.getInt(1));
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private int createMember(String suffix, int cinemaId) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     INSERT INTO Users (FullName, Email, PasswordHash, Role, CinemaId)
                     VALUES (?, ?, ?, 'member', ?)
                     """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, "Comment Scope " + suffix);
            ps.setString(2, EMAIL_PREFIX + suffix + "@test.local");
            ps.setString(3, "$2a$12$Cja7B.jV5kPjNnjZfPWAR.5lZPcgJ9Z/mTRrIUTaSBxpO6iTyfoBm");
            ps.setInt(4, cinemaId);
            assertEquals(1, ps.executeUpdate());
            try (ResultSet keys = ps.getGeneratedKeys()) {
                assertTrue(keys.next());
                int id = keys.getInt(1);
                createdUserIds.add(id);
                return id;
            }
        }
    }

    private int createScopedStaff(String suffix, int cinemaId) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     INSERT INTO Users (FullName, Email, PasswordHash, Role, CinemaId)
                     VALUES (?, ?, ?, 'staff', ?)
                     """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, "Comment Scope " + suffix);
            ps.setString(2, EMAIL_PREFIX + suffix + "@test.local");
            ps.setString(3, "$2a$12$Cja7B.jV5kPjNnjZfPWAR.5lZPcgJ9Z/mTRrIUTaSBxpO6iTyfoBm");
            ps.setInt(4, cinemaId);
            assertEquals(1, ps.executeUpdate());
            try (ResultSet keys = ps.getGeneratedKeys()) {
                assertTrue(keys.next());
                int id = keys.getInt(1);
                createdUserIds.add(id);
                return id;
            }
        }
    }

    private int createForeignFilm() throws SQLException {
        int filmId;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     INSERT INTO Films (Title, ReleaseDate, EndDate, DurationMinutes, Status)
                     VALUES (?, CAST(GETDATE() AS date), DATEADD(DAY,60,CAST(GETDATE() AS date)), 90, 'showing')
                     """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, FILM_PREFIX + "FOREIGN");
            assertEquals(1, ps.executeUpdate());
            try (ResultSet keys = ps.getGeneratedKeys()) {
                assertTrue(keys.next());
                filmId = keys.getInt(1);
            }
        }
        createdFilmIds.add(filmId);
        execute("INSERT INTO CinemaFilms (CinemaId, FilmId) VALUES (2, ?)", filmId);
        return filmId;
    }

    private void insertOrderAtManagerCinema(int userId) throws SQLException {
        execute("""
                INSERT INTO Orders
                    (UserId, ShowtimeId, SeatSubtotal, ComboSubtotal, DiscountAmount,
                     TotalAmount, TicketCode, PaymentMethod, PaymentStatus, OrderStatus)
                VALUES (?, 3, 100000, 0, 0, 100000, ?, 'card', 'paid', 'redeemed')
                """, userId, TICKET_PREFIX + userId);
    }

    private int createComment(int userId, int filmId, String content) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     INSERT INTO Comments (UserId, FilmId, Rate, Content, Report)
                     VALUES (?, ?, 1, ?, 1)
                     """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userId);
            ps.setInt(2, filmId);
            ps.setString(3, FILM_PREFIX + content);
            assertEquals(1, ps.executeUpdate());
            try (ResultSet keys = ps.getGeneratedKeys()) {
                assertTrue(keys.next());
                int id = keys.getInt(1);
                createdCommentIds.add(id);
                return id;
            }
        }
    }

    private int createNotification(String targetType, int targetId) throws SQLException {
        return createNotification(targetType, targetId, "Comment scope notification fixture");
    }

    private int createNotification(String targetType, int targetId, String message)
            throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     INSERT INTO AdminNotifications
                         (Title, Message, Category, Severity, TargetType, TargetId, IsRead)
                     VALUES (?, ?, 'comment', 'warning', ?, ?, 0)
                     """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, "Scope notification: " + message);
            ps.setString(2, message);
            ps.setString(3, targetType);
            ps.setString(4, String.valueOf(targetId));
            assertEquals(1, ps.executeUpdate());
            try (ResultSet keys = ps.getGeneratedKeys()) {
                assertTrue(keys.next());
                int id = keys.getInt(1);
                createdNotificationIds.add(id);
                return id;
            }
        }
    }

    private int createAppeal(int userId, String ticketCode, String reason) throws SQLException {
        String sql = ticketCode == null
                ? """
                  INSERT INTO UserAppeals
                      (UserId, Email, Reason, TicketCode, BankAccountInfo, Status,
                       AppealType, OrderId, CinemaId)
                  SELECT Id, Email, ?, NULL, NULL, 'pending', 'account', NULL,
                         CASE WHEN Role IN ('manager','staff') THEN CinemaId ELSE NULL END
                  FROM Users WHERE Id=?
                  """
                : """
                  INSERT INTO UserAppeals
                      (UserId, Email, Reason, TicketCode, BankAccountInfo, Status,
                       AppealType, OrderId, CinemaId)
                  SELECT u.Id, u.Email, ?, o.TicketCode, ?, 'pending', 'refund', o.Id,
                         s.CinemaId
                  FROM Users u
                  JOIN Orders o ON o.UserId=u.Id AND o.TicketCode=?
                  JOIN Showtimes s ON s.Id=o.ShowtimeId
                  WHERE u.Id=?
                  """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, reason);
            if (ticketCode == null) {
                ps.setInt(2, userId);
            } else {
                ps.setString(2, "COMMENT-SCOPE-TEST-ONLY");
                ps.setString(3, ticketCode);
                ps.setInt(4, userId);
            }
            assertEquals(1, ps.executeUpdate());
            try (ResultSet keys = ps.getGeneratedKeys()) {
                assertTrue(keys.next());
                int id = keys.getInt(1);
                createdAppealIds.add(id);
                return id;
            }
        }
    }

    private static User manager() {
        User manager = new User();
        manager.setId(4);
        manager.setRole("manager");
        manager.setCinemaId(1);
        return manager;
    }

    private static User admin() {
        User admin = new User();
        admin.setId(5);
        admin.setRole("admin");
        return admin;
    }

    private static int userInt(int userId, String column) throws SQLException {
        return scalar("SELECT " + column + " FROM Users WHERE Id=?", userId);
    }

    private static boolean userBoolean(int userId, String column) throws SQLException {
        return scalar("SELECT CONVERT(INT," + column + ") FROM Users WHERE Id=?", userId) == 1;
    }

    private static boolean commentReport(int commentId) throws SQLException {
        return scalar("SELECT CONVERT(INT,Report) FROM Comments WHERE Id=?", commentId) == 1;
    }

    private static int commentCount(int commentId) throws SQLException {
        return scalar("SELECT COUNT(*) FROM Comments WHERE Id=?", commentId);
    }

    private static int recipientReadCount(int notificationId, int userId) throws SQLException {
        return scalar("""
                SELECT COUNT(*) FROM NotificationRecipients
                WHERE SourceType='admin' AND NotificationId=? AND UserId=? AND ReadAt IS NOT NULL
                """, notificationId, userId);
    }

    private static int autoLockNotificationCount(int userId) throws SQLException {
        return scalar("""
                SELECT COUNT(*) FROM AdminNotifications
                WHERE TargetType='UserLock' AND TargetId=?
                """, String.valueOf(userId));
    }

    private void cleanupNotifications() throws SQLException {
        for (int notificationId : createdNotificationIds) {
            execute("DELETE FROM NotificationRecipients WHERE SourceType='admin' AND NotificationId=?",
                    notificationId);
            execute("DELETE FROM AdminNotifications WHERE Id=?", notificationId);
        }
    }

    private void cleanupAppeals() throws SQLException {
        for (int appealId : createdAppealIds) {
            execute("DELETE FROM UserAppeals WHERE Id=?", appealId);
        }
    }

    private void cleanupAuditTargets() throws SQLException {
        Set<Integer> targets = new HashSet<>(createdUserIds);
        targets.addAll(createdCommentIds);
        targets.addAll(createdAppealIds);
        for (int targetId : targets) {
            execute("DELETE FROM AuditLogs WHERE TargetId=? AND ActorUserId IN (4,5)",
                    String.valueOf(targetId));
        }
    }

    private static void cleanupByPrefix() throws SQLException {
        execute("""
                DELETE nr FROM NotificationRecipients nr
                JOIN AdminNotifications n ON n.Id=nr.NotificationId AND nr.SourceType='admin'
                JOIN Users u ON u.Id=TRY_CONVERT(INT,n.TargetId)
                WHERE n.TargetType='UserLock' AND u.Email LIKE 'comment-scope-%';
                DELETE n FROM AdminNotifications n
                JOIN Users u ON u.Id=TRY_CONVERT(INT,n.TargetId)
                WHERE n.TargetType='UserLock' AND u.Email LIKE 'comment-scope-%';
                DELETE nr FROM NotificationRecipients nr
                JOIN AdminNotifications n ON n.Id=nr.NotificationId AND nr.SourceType='admin'
                JOIN UserAppeals a ON a.Id=TRY_CONVERT(INT,n.TargetId)
                JOIN Users u ON u.Id=a.UserId
                WHERE LOWER(n.TargetType)='userappeal'
                  AND u.Email LIKE 'comment-scope-%';
                DELETE n FROM AdminNotifications n
                JOIN UserAppeals a ON a.Id=TRY_CONVERT(INT,n.TargetId)
                JOIN Users u ON u.Id=a.UserId
                WHERE LOWER(n.TargetType)='userappeal'
                  AND u.Email LIKE 'comment-scope-%';
                DELETE nr FROM NotificationRecipients nr
                JOIN AdminNotifications n ON n.Id=nr.NotificationId AND nr.SourceType='admin'
                JOIN Comments c ON c.Id=TRY_CONVERT(INT,n.TargetId)
                JOIN Users u ON u.Id=c.UserId
                WHERE LOWER(n.TargetType) IN ('comment','commentreport')
                  AND u.Email LIKE 'comment-scope-%';
                DELETE n FROM AdminNotifications n
                JOIN Comments c ON c.Id=TRY_CONVERT(INT,n.TargetId)
                JOIN Users u ON u.Id=c.UserId
                WHERE LOWER(n.TargetType) IN ('comment','commentreport')
                  AND u.Email LIKE 'comment-scope-%';
                DELETE a FROM UserAppeals a
                JOIN Users u ON u.Id=a.UserId
                WHERE u.Email LIKE 'comment-scope-%';
                DELETE cr FROM CommentReports cr
                JOIN Comments c ON c.Id=cr.CommentId
                JOIN Users u ON u.Id=c.UserId
                WHERE u.Email LIKE 'comment-scope-%';
                DELETE c FROM Comments c
                JOIN Users u ON u.Id=c.UserId
                WHERE u.Email LIKE 'comment-scope-%';
                DELETE FROM Orders WHERE TicketCode LIKE 'CMSCOPE%';
                DELETE cf FROM CinemaFilms cf
                JOIN Films f ON f.Id=cf.FilmId
                WHERE f.Title LIKE 'COMMENT-SCOPE-%';
                DELETE FROM Films WHERE Title LIKE 'COMMENT-SCOPE-%';
                DELETE FROM Users WHERE Email LIKE 'comment-scope-%';
                """);
    }

    private static int scalar(String sql, Object... parameters) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, parameters);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private static void execute(String sql, Object... parameters) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, parameters);
            ps.executeUpdate();
        }
    }

    private static void bind(PreparedStatement ps, Object... parameters) throws SQLException {
        for (int index = 0; index < parameters.length; index++) {
            ps.setObject(index + 1, parameters[index]);
        }
    }

    private static boolean contains(String text, String needle) {
        return text != null && text.contains(needle);
    }

    private static void assertTestDatabase() throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT DB_NAME()");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(System.getProperty("cinebook.it.database", "CineBookIT_REQUIRED"), rs.getString(1));
        }
    }
}
