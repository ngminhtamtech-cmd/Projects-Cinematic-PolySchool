package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.config.AppConstants;
import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcUserDAO;
import com.mycompany.website.ban.ve.xem.phim.filter.AuthFilter;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AccountStateGuard;
import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D14 — {@code /system/*} la khu vuc cua rieng admin, ke ca voi POST.
 *
 * <p>Kiem o tang {@code AuthFilter} chu khong phai o giao dien: an nut tren UI khong phai la
 * phan quyen. Servlet API duoc gia lap bang {@link Proxy} — cung cach P05 da dung o
 * {@code CsrfFilterTest}, khong can them thu vien mock.</p>
 */
@Tag("it")
public class SystemScopeIT {

    static {
        System.setProperty("cinebook.db.config", new File(System.getProperty("cinebook.it.config", "target/db.it.properties")).getAbsolutePath());
        DBConnection.shutdown();
    }

    private AuthFilter filter;
    private User manager;
    private User admin;
    private User staff;
    private User member;

    @BeforeAll
    public static void initConnection() {
        System.setProperty("cinebook.db.config", new File(System.getProperty("cinebook.it.config", "target/db.it.properties")).getAbsolutePath());
        DBConnection.shutdown();
    }

    @AfterAll
    public static void cleanUp() {
        DBConnection.shutdown();
    }

    @BeforeEach
    public void setUp() {
        AccountStateGuard.clearCache();
        filter = new AuthFilter();
        JdbcUserDAO userDAO = new JdbcUserDAO();
        manager = userDAO.findByEmail("manager@test.com").orElseThrow();
        admin = userDAO.findByEmail("admin@test.com").orElseThrow();
        staff = userDAO.findByEmail("staff@test.com").orElseThrow();
        member = userDAO.findByEmail("member_bronze@test.com").orElseThrow();
    }

    @Test
    @DisplayName("Manager POST /system/config -> 403")
    public void testManagerCannotPostToSystem() throws Exception {
        Result result = run(manager, "POST", "/system/config");
        assertEquals(403, result.errorStatus, "Manager phai bi tu choi o /system/*");
        assertFalse(result.chainCalled, "Request khong duoc di tiep toi servlet");
    }

    @Test
    @DisplayName("Manager POST cac endpoint /system/* con lai cung 403")
    public void testManagerCannotPostToAnySystemEndpoint() throws Exception {
        for (String path : new String[] {"/system/managers", "/system/audit-logs", "/system/backup", "/system/dashboard"}) {
            Result result = run(manager, "POST", path);
            assertEquals(403, result.errorStatus, "Manager POST " + path + " phai 403");
            assertFalse(result.chainCalled, "Manager khong duoc vao " + path);
        }
    }

    @Test
    @DisplayName("Manager GET /system/* cung 403 — khong chi chan moi POST")
    public void testManagerCannotGetSystem() throws Exception {
        Result result = run(manager, "GET", "/system/config");
        assertEquals(403, result.errorStatus);
        assertFalse(result.chainCalled);
    }

    @Test
    @DisplayName("Staff va member cung bi chan khoi /system/*")
    public void testLowerRolesCannotReachSystem() throws Exception {
        assertEquals(403, run(staff, "POST", "/system/config").errorStatus);
        assertEquals(403, run(member, "POST", "/system/config").errorStatus);
    }

    @Test
    @DisplayName("Admin van vao duoc /system/* — khong hoi quy")
    public void testAdminStillAllowed() throws Exception {
        Result result = run(admin, "POST", "/system/config");
        assertTrue(result.chainCalled, "Admin phai di tiep duoc");
        assertEquals(0, result.errorStatus);
    }

    @Test
    @DisplayName("Manager van lam viec binh thuong o /admin/* — chi /system/* bi chan")
    public void testManagerStillOwnsAdminArea() throws Exception {
        Result result = run(manager, "POST", "/admin/films");
        assertTrue(result.chainCalled, "Manager phai vao duoc /admin/*");
        assertEquals(0, result.errorStatus);
    }

    @Test
    @DisplayName("Chua dang nhap thi bi day ve /login, khong phai 403")
    public void testAnonymousRedirectedToLogin() throws Exception {
        Result result = run(null, "GET", "/system/config");
        assertFalse(result.chainCalled);
        assertNotNull(result.redirect, "Phai chuyen huong toi trang dang nhap");
        assertTrue(result.redirect.contains("/login"), "Chuyen huong toi: " + result.redirect);
    }

    // ------------------------------------------------------------------ ha tang gia lap

    private Result run(User sessionUser, String method, String path) throws Exception {
        Result result = new Result();
        HttpServletRequest request = fakeRequest(sessionUser, method, path);
        HttpServletResponse response = fakeResponse(result);
        FilterChain chain = fakeChain(result);
        filter.doFilter(request, response, chain);
        return result;
    }

    private static final class Result {
        private boolean chainCalled;
        private int errorStatus;
        private String redirect;
    }

    private HttpServletRequest fakeRequest(User sessionUser, String method, String path) {
        Map<String, Object> sessionAttributes = new HashMap<>();
        if (sessionUser != null) {
            sessionAttributes.put(AppConstants.SESSION_USER, sessionUser);
        }
        HttpSession session = (HttpSession) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {HttpSession.class},
                (proxy, m, args) -> switch (m.getName()) {
                    case "getAttribute" -> sessionAttributes.get((String) args[0]);
                    case "setAttribute" -> {
                        sessionAttributes.put((String) args[0], args[1]);
                        yield null;
                    }
                    case "invalidate" -> {
                        sessionAttributes.clear();
                        yield null;
                    }
                    default -> defaultValue(m.getReturnType());
                });

        InvocationHandler handler = (proxy, m, args) -> switch (m.getName()) {
            case "getRequestURI" -> "/cinebook" + path;
            case "getContextPath" -> "/cinebook";
            case "getMethod" -> method;
            case "getQueryString" -> null;
            case "getHeader" -> null;
            case "getSession" -> sessionUser == null && args != null && args.length == 1
                    && Boolean.FALSE.equals(args[0]) ? null : session;
            default -> defaultValue(m.getReturnType());
        };
        return (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {HttpServletRequest.class}, handler);
    }

    private HttpServletResponse fakeResponse(Result result) {
        InvocationHandler handler = (proxy, m, args) -> {
            switch (m.getName()) {
                case "sendError" -> result.errorStatus = (int) args[0];
                case "sendRedirect" -> result.redirect = (String) args[0];
                default -> { }
            }
            return defaultValue(m.getReturnType());
        };
        return (HttpServletResponse) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {HttpServletResponse.class}, handler);
    }

    private FilterChain fakeChain(Result result) {
        return (FilterChain) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {FilterChain.class},
                (proxy, m, args) -> {
                    if ("doFilter".equals(m.getName())) {
                        result.chainCalled = true;
                    }
                    return null;
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
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
        if (type == void.class) {
            return null;
        }
        return 0;
    }
}
