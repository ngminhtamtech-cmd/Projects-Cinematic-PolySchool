package com.mycompany.website.ban.ve.xem.phim.controller.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.website.ban.ve.xem.phim.config.AppConstants;
import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
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

/** Proves the comment lock POST cannot choose a different user from the target comment. */
@Tag("it")
@DisplayName("Comment moderation forged lock POST")
class CommentModerationTenantScopeServletIT {
    private static final String EMAIL_PREFIX = "comment-http-scope-";
    private final List<Integer> userIds = new ArrayList<>();
    private final List<Integer> commentIds = new ArrayList<>();
    private int ownUserId;
    private int foreignUserId;
    private int ownCommentId;
    private int foreignCommentId;

    static {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

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
        createRedeemedOrderForFilmOneAtCinemaOne(ownUserId);
        ownCommentId = createComment(ownUserId, "own");
        foreignCommentId = createComment(foreignUserId, "foreign");
    }

    @AfterEach
    void cleanup() throws SQLException {
        for (int id : userIds) {
            execute("DELETE FROM AuditLogs WHERE TargetId=? AND ActorUserId=4", String.valueOf(id));
        }
        for (int id : commentIds) {
            execute("DELETE FROM AuditLogs WHERE TargetId=? AND ActorUserId=4", String.valueOf(id));
        }
        cleanupByPrefix();
    }

    @AfterAll
    static void shutdown() {
        DBConnection.shutdown();
    }

    @Test
    @DisplayName("foreign comment plus forged own userId returns 403 and locks nobody")
    void submittedOwnUserCannotAuthorizeForeignComment() throws Exception {
        Exchange exchange = postLock(foreignCommentId, ownUserId);

        assertEquals(HttpServletResponse.SC_FORBIDDEN, exchange.status);
        assertNull(exchange.redirect);
        assertFalse(isLocked(ownUserId));
        assertFalse(isLocked(foreignUserId));
    }

    @Test
    @DisplayName("own comment ignores forged foreign userId and locks its real author")
    void submittedForeignUserCannotRedirectLockAwayFromCommentAuthor() throws Exception {
        Exchange exchange = postLock(ownCommentId, foreignUserId);

        assertEquals("/Website-ban-ve-xem-phim/admin/comments", exchange.redirect);
        assertTrue(isLocked(ownUserId));
        assertFalse(isLocked(foreignUserId));
    }

    private Exchange postLock(int commentId, int submittedUserId) throws Exception {
        Exchange exchange = new Exchange(manager(), Map.of(
                "action", "lock",
                "id", String.valueOf(commentId),
                "userId", String.valueOf(submittedUserId),
                "lockReason", "COMMENT-SCOPE forged form"));
        new ManagerPortalServlet().doPost(exchange.request, exchange.response);
        return exchange;
    }

    private int createMember(String suffix, int cinemaId) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     INSERT INTO Users (FullName, Email, PasswordHash, Role, CinemaId)
                     VALUES (?, ?, ?, 'member', ?)
                     """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, "Comment HTTP Scope " + suffix);
            ps.setString(2, EMAIL_PREFIX + suffix + "@test.local");
            ps.setString(3, "$2a$12$Cja7B.jV5kPjNnjZfPWAR.5lZPcgJ9Z/mTRrIUTaSBxpO6iTyfoBm");
            ps.setInt(4, cinemaId);
            assertEquals(1, ps.executeUpdate());
            try (ResultSet keys = ps.getGeneratedKeys()) {
                assertTrue(keys.next());
                int id = keys.getInt(1);
                userIds.add(id);
                return id;
            }
        }
    }

    private int createComment(int userId, String suffix) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     INSERT INTO Comments (UserId, FilmId, Rate, Content, Report)
                     VALUES (?, 1, 1, ?, 1)
                     """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userId);
            ps.setString(2, "COMMENT-SCOPE HTTP " + suffix);
            assertEquals(1, ps.executeUpdate());
            try (ResultSet keys = ps.getGeneratedKeys()) {
                assertTrue(keys.next());
                int id = keys.getInt(1);
                commentIds.add(id);
                return id;
            }
        }
    }

    private static void createRedeemedOrderForFilmOneAtCinemaOne(int userId) throws SQLException {
        execute("""
                INSERT INTO Orders
                    (UserId, ShowtimeId, SeatSubtotal, ComboSubtotal, DiscountAmount,
                     TotalAmount, TicketCode, PaymentMethod, PaymentStatus, OrderStatus)
                SELECT ?, s.Id, 100000, 0, 0, 100000, ?, 'card', 'paid', 'redeemed'
                FROM (SELECT TOP 1 Id FROM Showtimes WHERE FilmId=1 AND CinemaId=1 ORDER BY Id) s
                """, userId, "COMMENT-HTTP-SCOPE-" + userId);
    }

    private static User manager() {
        User manager = new User();
        manager.setId(4);
        manager.setRole("manager");
        manager.setCinemaId(1);
        return manager;
    }

    private static boolean isLocked(int userId) throws SQLException {
        return scalar("SELECT CONVERT(INT,IsLocked) FROM Users WHERE Id=?", userId) == 1;
    }

    private static void cleanupByPrefix() throws SQLException {
        execute("""
                DELETE o FROM Orders o
                JOIN Users u ON u.Id=o.UserId
                WHERE u.Email LIKE 'comment-http-scope-%';
                DELETE c FROM Comments c
                JOIN Users u ON u.Id=c.UserId
                WHERE u.Email LIKE 'comment-http-scope-%';
                DELETE FROM Users WHERE Email LIKE 'comment-http-scope-%';
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

    private static void assertTestDatabase() throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT DB_NAME()");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(System.getProperty("cinebook.it.database", "CineBookIT_REQUIRED"), rs.getString(1));
        }
    }

    private static final class Exchange {
        private final HttpServletRequest request;
        private final HttpServletResponse response;
        private final Map<String, Object> sessionAttributes = new HashMap<>();
        private int status = HttpServletResponse.SC_OK;
        private String redirect;

        private Exchange(User actor, Map<String, String> parameters) {
            sessionAttributes.put(AppConstants.SESSION_USER, actor);
            HttpSession session = (HttpSession) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{HttpSession.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getAttribute" -> sessionAttributes.get((String) args[0]);
                        case "setAttribute" -> sessionAttributes.put((String) args[0], args[1]);
                        case "removeAttribute" -> sessionAttributes.remove((String) args[0]);
                        default -> defaultValue(method.getReturnType());
                    });
            request = (HttpServletRequest) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{HttpServletRequest.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getServletPath" -> "/admin/comments";
                        case "getContextPath" -> "/Website-ban-ve-xem-phim";
                        case "getParameter" -> parameters.get((String) args[0]);
                        case "getSession" -> session;
                        default -> defaultValue(method.getReturnType());
                    });
            response = (HttpServletResponse) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{HttpServletResponse.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "sendError", "setStatus" -> status = (Integer) args[0];
                            case "sendRedirect" -> redirect = (String) args[0];
                            case "getStatus" -> {
                                return status;
                            }
                            default -> {
                                return defaultValue(method.getReturnType());
                            }
                        }
                        return null;
                    });
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        return 0D;
    }
}
