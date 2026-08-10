package com.mycompany.website.ban.ve.xem.phim.controller.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.website.ban.ve.xem.phim.config.AppConstants;
import com.mycompany.website.ban.ve.xem.phim.controller.system.SystemPortalServlet;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
import com.mycompany.website.ban.ve.xem.phim.service.CinemaCapabilityPolicy;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;

class AdminMappedGetRouteContractTest {
    private static final String CONTEXT_PATH = "/Website-ban-ve-xem-phim";

    @Test
    void mappedCustomContentGetRedirectsToCanonicalEditor() throws Exception {
        Exchange exchange = new Exchange("/admin/custom-content");

        servlet().doGet(exchange.request, exchange.response);

        assertEquals(
                CONTEXT_PATH + "/admin/films?tab=custom",
                exchange.redirect,
                "Mapped admin custom-content route must not return the servlet's default 404");
    }

    @Test
    void managerMappedCustomContentGetRedirectsToCinemaScopedEditor() throws Exception {
        Exchange exchange = new Exchange("/admin/custom-content", "manager", 1, "GET");

        servlet().doGet(exchange.request, exchange.response);

        assertEquals(CONTEXT_PATH + "/admin/films?tab=custom", exchange.redirect);
    }

    @Test
    void managerCanonicalCustomContentGetUsesCinemaScopedStorage() throws Exception {
        String source = managerPortalSource();
        assertTrue(source.contains("cinemaContentService.getContent(cinemaContextId, \"cinetags_data\")"));
        assertTrue(source.contains("contentCinemaRequired"));
    }

    @Test
    void managerCanonicalCustomContentPostUsesCinemaScopedHandler() throws Exception {
        String source = managerPortalSource();
        assertTrue(source.contains("handleCustomContentPost(request, response, actor)"));
        assertTrue(source.contains("CinemaCapabilityPolicy.requireManagerCinema(actor)"));
        assertTrue(source.contains("cinemaContentService.saveContent(contentCinemaId"));
    }

    @Test
    void mappedBackupGetRedirectsToPageContainingBackupAction() throws Exception {
        Exchange exchange = new Exchange("/system/backup");

        new TestableSystemPortalServlet().invokeGet(exchange.request, exchange.response);

        assertEquals(
                CONTEXT_PATH + "/system/config",
                exchange.redirect,
                "GET on the mapped backup route must lead to its safe POST form, not return 404");
    }

    @Test
    void roomImpactSubpathReturnsJsonForDeleteConfirmationModal() throws Exception {
        Exchange exchange = new Exchange(
                "/admin/rooms",
                "/impact",
                Map.of("roomId", "1"));

        servlet().doGet(exchange.request, exchange.response);

        assertEquals(
                "application/json;charset=UTF-8",
                exchange.contentType,
                "The room-impact fetch endpoint must not render the HTML rooms page");
        assertTrue(exchange.body.toString().contains("\"roomName\""), exchange.body::toString);
    }

    @Test
    void managerPromotionCapabilityIsEnabled() {
        User manager = new User();
        manager.setRole(AppConstants.ROLE_MANAGER);
        manager.setCinemaId(1);
        assertTrue(CinemaCapabilityPolicy.canCreatePromotion(manager));
    }

    /**
     * Bon route noi dung toan he thong (gioi thieu + dieu khoan) khong co dong nao trong
     * {@code enterprise-flow-coverage.tsv} truoc dot nay, nen khong test nao giu hop dong
     * "chi admin" cua chung. Chung phai 403 voi manager <b>truoc</b> khi cham vao
     * {@code PolicyDocumentService} — day cung la ly do cac ca duoi day khong can DB.
     */
    @Test
    void managerGlobalContentGetIsForbidden() throws Exception {
        for (String path : GLOBAL_CONTENT_ROUTES) {
            Exchange exchange = new Exchange(path, "manager", 1, "GET");

            servlet().doGet(exchange.request, exchange.response);

            assertEquals(HttpServletResponse.SC_FORBIDDEN, exchange.status,
                    "GET " + path + " phai 403 voi manager");
        }
    }

    @Test
    void managerGlobalContentPostIsForbiddenBeforeMutation() throws Exception {
        for (String path : GLOBAL_CONTENT_ROUTES) {
            Exchange exchange = new Exchange(path, "manager", 1, "POST");

            servlet().doPost(exchange.request, exchange.response);

            assertEquals(HttpServletResponse.SC_FORBIDDEN, exchange.status,
                    "POST " + path + " phai 403 voi manager");
        }
    }

    private static final String[] GLOBAL_CONTENT_ROUTES = {
        "/admin/content/about-us", "/admin/about-us",
        "/admin/content/terms-of-use", "/admin/terms-of-use",
    };

    private static ManagerPortalServlet servlet() {
        return new ManagerPortalServlet(new StubAdminService());
    }

    private static final class StubAdminService extends AdminService {
        @Override
        public List<com.mycompany.website.ban.ve.xem.phim.model.Cinema> listCinemas() {
            return List.of();
        }

        @Override
        public int getUnreadNotificationCount(User actor) {
            return 0;
        }

        @Override
        public Map<String, Object> getRoomDeleteImpactInfo(int roomId, User actor) {
            return Map.of(
                    "roomName", "Contract room",
                    "status", "active",
                    "cinemaName", "Contract cinema",
                    "showtimeCount", 0,
                    "activeShowtimeCount", 0,
                    "totalTicketCount", 0,
                    "pendingTicketCount", 0);
        }
    }

    private static String managerPortalSource() throws IOException {
        return Files.readString(Path.of("src", "main", "java", "com", "mycompany", "website",
                "ban", "ve", "xem", "phim", "controller", "admin", "ManagerPortalServlet.java"),
                StandardCharsets.UTF_8);
    }

    private static final class TestableSystemPortalServlet extends SystemPortalServlet {
        private void invokeGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
            super.doGet(request, response);
        }
    }

    private static final class Exchange {
        private final HttpServletRequest request;
        private final HttpServletResponse response;
        private final StringWriter body = new StringWriter();
        private String redirect;
        private String contentType;
        private int status = HttpServletResponse.SC_OK;

        private Exchange(String servletPath) {
            this(servletPath, null, Map.of());
        }

        private Exchange(String servletPath, String pathInfo, Map<String, String> parameters) {
            this(servletPath, pathInfo, parameters, "admin", null, "GET");
        }

        private Exchange(String servletPath, String role, Integer cinemaId, String method) {
            this(servletPath, null, Map.of(), role, cinemaId, method);
        }

        private Exchange(
                String servletPath,
                String pathInfo,
                Map<String, String> parameters,
                String role,
                Integer cinemaId,
                String httpMethod) {
            User admin = new User();
            admin.setId(AppConstants.ROLE_ADMIN.equals(role) ? 5 : 4);
            admin.setRole(role);
            admin.setCinemaId(cinemaId);

            HttpSession session = (HttpSession) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[]{HttpSession.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getAttribute" -> AppConstants.SESSION_USER.equals(args[0]) ? admin : null;
                        default -> defaultValue(method.getReturnType());
                    });

            request = (HttpServletRequest) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[]{HttpServletRequest.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getServletPath" -> servletPath;
                        case "getPathInfo" -> pathInfo;
                        case "getContextPath" -> CONTEXT_PATH;
                        case "getMethod" -> httpMethod;
                        case "getSession" -> session;
                        case "getParameter" -> parameters.get((String) args[0]);
                        case "getRequestDispatcher" -> requestDispatcher();
                        default -> defaultValue(method.getReturnType());
                    });

            response = (HttpServletResponse) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[]{HttpServletResponse.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "sendRedirect" -> redirect = (String) args[0];
                            case "sendError", "setStatus" -> status = (Integer) args[0];
                            case "setContentType" -> contentType = (String) args[0];
                            case "getWriter" -> {
                                return new PrintWriter(body);
                            }
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

        private RequestDispatcher requestDispatcher() {
            return (RequestDispatcher) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[]{RequestDispatcher.class},
                    (proxy, method, args) -> null);
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
