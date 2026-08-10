package com.mycompany.website.ban.ve.xem.phim.controller.member;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.HashMap;
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

/** HTTP-facing proof that forged loyalty POSTs are 4xx and replays are harmless. */
@Tag("it")
@DisplayName("Loyalty servlet redemption HTTP contract")
class LoyaltyServletRedemptionIT {
    private static final String EMAIL_PREFIX = "loyalty-http-";
    private static final String PROMO_PREFIX = "LOYHT";

    static {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    @BeforeAll
    static void configure() {
        DBConnection.shutdown();
    }

    @BeforeEach
    @AfterEach
    void cleanup() throws SQLException {
        assertTestDatabase();
        execute("""
                DELETE uv FROM UserVouchers uv
                JOIN Promotions p ON p.Id=uv.PromotionId
                WHERE p.Code LIKE 'LOYHT%';
                DELETE pt FROM PointTransactions pt
                JOIN Users u ON u.Id=pt.UserId
                WHERE u.Email LIKE 'loyalty-http-%';
                DELETE FROM Promotions WHERE Code LIKE 'LOYHT%';
                DELETE FROM Users WHERE Email LIKE 'loyalty-http-%';
                """);
    }

    @AfterAll
    static void shutdown() {
        DBConnection.shutdown();
    }

    @Test
    void forgedPublicPromotionReturns4xxAndChangesNothing() throws Exception {
        User user = createUser("forged", 200);
        int promotionId = createPromotion("PUBLIC", "PUBLIC");
        Exchange exchange = new Exchange(user, Map.of(
                "action", "redeem",
                "promotionId", String.valueOf(promotionId),
                "redemptionKey", "http-forged-public"));

        new LoyaltyServlet().doPost(exchange.request, exchange.response);

        assertEquals(HttpServletResponse.SC_BAD_REQUEST, exchange.status);
        assertNull(exchange.redirect);
        assertEquals(200, scalar("SELECT LoyaltyPoints FROM Users WHERE Id=?", user.getId()));
        assertEquals(0, scalar("SELECT COUNT(*) FROM UserVouchers WHERE UserId=?", user.getId()));
        assertEquals(0, scalar("SELECT COUNT(*) FROM PointTransactions "
                + "WHERE UserId=? AND Type='REDEEM_VOUCHER'", user.getId()));
    }

    @Test
    void malformedPromotionIdReturns400() throws Exception {
        User user = createUser("malformed", 200);
        Exchange exchange = new Exchange(user, Map.of(
                "action", "redeem",
                "promotionId", "not-a-number",
                "redemptionKey", "http-malformed"));

        new LoyaltyServlet().doPost(exchange.request, exchange.response);

        assertEquals(HttpServletResponse.SC_BAD_REQUEST, exchange.status);
        assertNull(exchange.redirect);
        assertEquals(200, scalar("SELECT LoyaltyPoints FROM Users WHERE Id=?", user.getId()));
    }

    @Test
    void repeatedEligiblePostRedirectsButCommitsOnlyOnce() throws Exception {
        User user = createUser("replay", 200);
        int promotionId = createPromotion("REPLAY", "REDEEMABLE");
        Map<String, String> parameters = Map.of(
                "action", "redeem",
                "promotionId", String.valueOf(promotionId),
                "redemptionKey", "http-replay-key");

        Exchange first = new Exchange(user, parameters);
        new LoyaltyServlet().doPost(first.request, first.response);
        Exchange replay = new Exchange(user, parameters);
        new LoyaltyServlet().doPost(replay.request, replay.response);

        assertEquals("/Website-ban-ve-xem-phim/member/loyalty", first.redirect);
        assertEquals(first.redirect, replay.redirect);
        assertEquals(100, scalar("SELECT LoyaltyPoints FROM Users WHERE Id=?", user.getId()));
        assertEquals(1, scalar("SELECT COUNT(*) FROM UserVouchers "
                + "WHERE UserId=? AND PromotionId=" + promotionId, user.getId()));
        assertEquals(1, scalar("SELECT COUNT(*) FROM PointTransactions "
                + "WHERE UserId=? AND Type='REDEEM_VOUCHER'", user.getId()));
    }

    private static User createUser(String suffix, int points) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     INSERT INTO Users
                         (FullName, Email, PasswordHash, Role, LoyaltyPoints,
                          LifetimeEarnedPoints, TotalSpent, MembershipTier)
                     VALUES (?, ?, ?, 'member', ?, ?, 0, 'BRONZE')
                     """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, "Loyalty HTTP " + suffix);
            ps.setString(2, EMAIL_PREFIX + suffix + "@test.local");
            ps.setString(3, "$2a$12$Cja7B.jV5kPjNnjZfPWAR.5lZPcgJ9Z/mTRrIUTaSBxpO6iTyfoBm");
            ps.setInt(4, points);
            ps.setInt(5, points);
            assertEquals(1, ps.executeUpdate());
            try (ResultSet keys = ps.getGeneratedKeys()) {
                assertTrue(keys.next());
                User user = new User();
                user.setId(keys.getInt(1));
                user.setRole("member");
                return user;
            }
        }
    }

    private static int createPromotion(String suffix, String voucherType) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     INSERT INTO Promotions
                         (Code, Description, DiscountPercent, MaxDiscount, StartDate, EndDate,
                          Status, VoucherType, TargetTier, PointsRequired, PerUserLimit)
                     VALUES (?, 'Loyalty servlet regression', 10, 50000,
                             DATEADD(DAY,-1,CAST(GETDATE() AS DATE)),
                             DATEADD(DAY,7,CAST(GETDATE() AS DATE)),
                             'active', ?, 'ALL', 100, 1)
                     """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, PROMO_PREFIX + suffix);
            ps.setString(2, voucherType);
            assertEquals(1, ps.executeUpdate());
            try (ResultSet keys = ps.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getInt(1);
            }
        }
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

        private Exchange(User user, Map<String, String> parameters) {
            sessionAttributes.put(AppConstants.SESSION_USER, user);
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
