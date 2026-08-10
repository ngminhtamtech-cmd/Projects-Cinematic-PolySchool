package com.mycompany.website.ban.ve.xem.phim.filter;

import com.mycompany.website.ban.ve.xem.phim.config.AppConstants;
import com.mycompany.website.ban.ve.xem.phim.dao.DaoException;
import com.mycompany.website.ban.ve.xem.phim.dao.UserDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcUserDAO;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.model.UserAuthState;
import com.mycompany.website.ban.ve.xem.phim.service.AccountStateGuard;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-002 — khi khong doc duoc live account state, route bao ve phai dung voi 503.
 */
public class AuthFilterAvailabilityTest {
    private static final int ROUTE_USER_ID = 777;
    private AtomicInteger daoCalls;
    private AuthFilter filter;

    @BeforeEach
    public void setUp() {
        AccountStateGuard.clearCache();
        daoCalls = new AtomicInteger();
        UserDAO broken = new JdbcUserDAO() {
            @Override
            public Optional<UserAuthState> findAuthState(int id) {
                daoCalls.incrementAndGet();
                throw new DaoException("sensitive simulated database failure", null);
            }
        };
        filter = new AuthFilter(new AccountStateGuard(broken));
    }

    @AfterEach
    public void tearDown() {
        AccountStateGuard.clearCache();
    }

    @Test
    @DisplayName("Member/staff/manager/admin mutation dung 503 khi live-state DB loi")
    public void testProtectedMutationRoutesFailClosedForEveryRole() throws Exception {
        assertUnavailable("/orders", AppConstants.ROLE_MEMBER);
        assertUnavailable("/ticket-refund-appeal", AppConstants.ROLE_MEMBER);
        assertUnavailable("/appeals", AppConstants.ROLE_MEMBER);
        assertUnavailable("/ticket-refund", AppConstants.ROLE_MEMBER);
        assertUnavailable("/staff/checkin", AppConstants.ROLE_STAFF);
        assertUnavailable("/admin/orders", AppConstants.ROLE_MANAGER);
        assertUnavailable("/system/config", AppConstants.ROLE_ADMIN);
        assertUnavailable("/api/v1/orders/1/hold", AppConstants.ROLE_MEMBER);
        assertUnavailable("/api/v1/staff/tickets/T-1", AppConstants.ROLE_STAFF);
        assertUnavailable("/api/v1/auth/me", AppConstants.ROLE_MEMBER);
        assertUnavailable("/api/v1/auth/me/", AppConstants.ROLE_MEMBER);
        assertUnavailable("/api/v1/auth/me/extra", AppConstants.ROLE_MEMBER);
        assertUnavailable("/invoices/1", AppConstants.ROLE_MEMBER);
        assertUnavailable("/api/v1/films/1/comments", AppConstants.ROLE_MEMBER, "POST");
        assertUnavailable("/api/v1/films/1/comments/", AppConstants.ROLE_MEMBER, "POST");
        assertUnavailable("/api/v1/films/1/comments/extra", AppConstants.ROLE_MEMBER, "POST");
        assertUnavailable("/api/v1/films/1/comments/2/report/extra", AppConstants.ROLE_MEMBER, "POST");
        assertUnavailable("/films/1/comments/", AppConstants.ROLE_MEMBER, "POST");
        assertEquals(18, daoCalls.get(), "Moi request loi phai thu doc DB, khong cache UNAVAILABLE");
    }

    @Test
    @DisplayName("JSON protected route tra 503 generic, khong chay chain hay lo chi tiet DAO")
    public void testProtectedJsonRouteReturnsGeneric503() throws Exception {
        Exchange exchange = new Exchange(user(301, AppConstants.ROLE_MEMBER), "/orders");
        exchange.headers.put("Accept", "application/json");

        filter.doFilter(exchange.request(), exchange.response(), exchange.chain());

        assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, exchange.status);
        assertFalse(exchange.chainCalled);
        assertFalse(exchange.invalidated);
        assertEquals("application/json;charset=UTF-8", exchange.contentType);
        assertTrue(exchange.body.toString().contains("\"error\":true"));
        assertFalse(exchange.body.toString().contains("sensitive simulated database failure"));
    }

    @Test
    @DisplayName("API route giu envelope error.code khi unavailable va khi chua dang nhap")
    public void testApiErrorEnvelopeContract() throws Exception {
        Exchange unavailable = new Exchange(user(302, AppConstants.ROLE_MEMBER),
                "/api/v1/orders/1/hold");
        filter.doFilter(unavailable.request(), unavailable.response(), unavailable.chain());

        assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, unavailable.status);
        assertFalse(unavailable.chainCalled);
        assertTrue(unavailable.body.toString().contains("\"code\":\"SERVICE_UNAVAILABLE\""));
        assertFalse(unavailable.body.toString().contains("\"error\":true"));

        Exchange anonymous = new Exchange(null, "/api/v1/orders/1/hold");
        filter.doFilter(anonymous.request(), anonymous.response(), anonymous.chain());

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, anonymous.status);
        assertFalse(anonymous.chainCalled);
        assertTrue(anonymous.body.toString().contains("\"code\":\"UNAUTHORIZED\""));
        assertEquals(1, daoCalls.get(), "Anonymous API request khong can doc live account state");
    }

    @Test
    @DisplayName("Public read route van graceful degradation va khong goi live-state DAO")
    public void testPublicReadRouteStillPassesWithoutDatabaseCheck() throws Exception {
        User currentUser = user(401, AppConstants.ROLE_MEMBER);
        Exchange exchange = new Exchange(currentUser, "/home");

        filter.doFilter(exchange.request(), exchange.response(), exchange.chain());

        assertTrue(exchange.chainCalled);
        assertEquals(HttpServletResponse.SC_OK, exchange.status);
        assertFalse(exchange.invalidated);
        assertSame(currentUser, exchange.sessionAttributes.get(AppConstants.SESSION_USER));
        assertEquals(0, daoCalls.get());
    }

    @Test
    @DisplayName("Report comment cong khai giu nguyen hop dong public")
    public void testCommentReportContractRemainsPublic() throws Exception {
        Exchange exchange = new Exchange(user(402, AppConstants.ROLE_MEMBER),
                "/api/v1/films/1/comments/2/report", "POST");

        filter.doFilter(exchange.request(), exchange.response(), exchange.chain());

        assertTrue(exchange.chainCalled);
        assertEquals(HttpServletResponse.SC_OK, exchange.status);
        assertEquals(0, daoCalls.get());
    }

    private void assertUnavailable(String path, String role) throws Exception {
        assertUnavailable(path, role, "GET");
    }

    private void assertUnavailable(String path, String role, String method) throws Exception {
        User currentUser = user(ROUTE_USER_ID, role);
        Exchange exchange = new Exchange(currentUser, path, method);

        filter.doFilter(exchange.request(), exchange.response(), exchange.chain());

        assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, exchange.status, path);
        assertFalse(exchange.chainCalled, path);
        assertFalse(exchange.invalidated, path + " khong duoc logout user khi DB loi");
        assertSame(currentUser, exchange.sessionAttributes.get(AppConstants.SESSION_USER), path);
    }

    private static User user(int id, String role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setEmail("user-" + id + "@test.local");
        return user;
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
        if (type == double.class) {
            return 0d;
        }
        if (type == float.class) {
            return 0f;
        }
        return 0;
    }

    private static final class Exchange {
        private final Map<String, String> headers = new HashMap<>();
        private final Map<String, Object> sessionAttributes = new HashMap<>();
        private final String path;
        private final String method;
        private final StringWriter body = new StringWriter();
        private final PrintWriter writer = new PrintWriter(body, true);
        private HttpServletRequest request;
        private HttpServletResponse response;
        private int status = HttpServletResponse.SC_OK;
        private String contentType;
        private boolean chainCalled;
        private boolean invalidated;

        private Exchange(User currentUser, String path) {
            this(currentUser, path, "GET");
        }

        private Exchange(User currentUser, String path, String method) {
            this.path = path;
            this.method = method;
            sessionAttributes.put(AppConstants.SESSION_USER, currentUser);
        }

        private HttpSession session() {
            InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
                case "getAttribute" -> sessionAttributes.get((String) args[0]);
                case "setAttribute" -> {
                    sessionAttributes.put((String) args[0], args[1]);
                    yield null;
                }
                case "invalidate" -> {
                    invalidated = true;
                    sessionAttributes.clear();
                    yield null;
                }
                default -> defaultValue(method.getReturnType());
            };
            return (HttpSession) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[] {HttpSession.class}, handler);
        }

        private HttpServletRequest request() {
            if (request == null) {
                HttpSession session = session();
                InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
                    case "getRequestURI" -> "/cinebook" + path;
                    case "getContextPath" -> "/cinebook";
                    case "getMethod" -> Exchange.this.method;
                    case "getQueryString" -> null;
                    case "getHeader" -> headers.get((String) args[0]);
                    case "getSession" -> session;
                    default -> defaultValue(method.getReturnType());
                };
                request = (HttpServletRequest) Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[] {HttpServletRequest.class}, handler);
            }
            return request;
        }

        private HttpServletResponse response() {
            if (response == null) {
                InvocationHandler handler = (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "setStatus":
                        case "sendError":
                            status = (Integer) args[0];
                            return null;
                        case "getStatus":
                            return status;
                        case "getWriter":
                            return writer;
                        case "setContentType":
                            contentType = (String) args[0];
                            return null;
                        default:
                            return defaultValue(method.getReturnType());
                    }
                };
                response = (HttpServletResponse) Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[] {HttpServletResponse.class}, handler);
            }
            return response;
        }

        private FilterChain chain() {
            return (FilterChain) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[] {FilterChain.class},
                    (proxy, method, args) -> {
                        if ("doFilter".equals(method.getName())) {
                            chainCalled = true;
                        }
                        return null;
                    });
        }
    }
}
