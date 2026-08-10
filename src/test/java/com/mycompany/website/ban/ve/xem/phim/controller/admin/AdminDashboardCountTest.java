package com.mycompany.website.ban.ve.xem.phim.controller.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.website.ban.ve.xem.phim.config.AppConstants;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F-003 — dashboard khong duoc bien loi doc so lieu thanh so 0.
 */
public class AdminDashboardCountTest {

    private static User admin() {
        User user = new User();
        user.setId(5);
        user.setRole(AppConstants.ROLE_ADMIN);
        return user;
    }

    @Test
    @DisplayName("Doc duoc so lieu: dat dung gia tri that, khong bat co unavailable")
    public void testCountsArePublishedWhenReadable() throws Exception {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("filmCount", 217L);
        counts.put("orderCount", 12345L);
        Exchange exchange = new Exchange(admin());

        servletReturning(counts).doGet(exchange.request(), exchange.response());

        assertEquals(217L, exchange.requestAttributes.get("filmCount"));
        assertEquals(12345L, exchange.requestAttributes.get("orderCount"));
        assertEquals(Boolean.FALSE, exchange.requestAttributes.get("countsUnavailable"));
        assertEquals(HttpServletResponse.SC_OK, exchange.status);
        assertTrue(exchange.forwarded);
    }

    @Test
    @DisplayName("DB loi: khong hien 0, bat co unavailable va tra 503")
    public void testReadFailureIsNotReportedAsZero() throws Exception {
        Exchange exchange = new Exchange(admin());

        servletFailing().doGet(exchange.request(), exchange.response());

        assertEquals(Boolean.TRUE, exchange.requestAttributes.get("countsUnavailable"));
        assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, exchange.status);
        for (String key : new String[] {"filmCount", "cinemaCount", "roomCount", "showtimeCount",
            "memberCount", "promotionCount", "orderCount", "managerCount", "settingCount", "auditCount"}) {
            Object value = exchange.requestAttributes.get(key);
            assertNotEquals(0, value, key + " khong duoc la 0 gia khi doc loi");
            assertNotEquals(0L, value, key + " khong duoc la 0 gia khi doc loi");
            assertEquals("—", value, key + " phai hien dau gach ngang");
        }
        assertTrue(exchange.forwarded, "Van phai render trang de nguoi dung doc duoc canh bao");
    }

    private static AdminDashboardServlet servletReturning(Map<String, Long> counts) {
        return new AdminDashboardServlet(new AdminService() {
            @Override
            public Map<String, Long> dashboardCounts(User actor) {
                return counts;
            }
        });
    }

    private static AdminDashboardServlet servletFailing() {
        return new AdminDashboardServlet(new AdminService() {
            @Override
            public Map<String, Long> dashboardCounts(User actor) {
                throw new BookingException(500, "Không thể tải số liệu tổng quan.");
            }
        });
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
        return 0;
    }

    private static final class Exchange {
        private final Map<String, Object> requestAttributes = new HashMap<>();
        private final Map<String, Object> sessionAttributes = new HashMap<>();
        private HttpServletRequest request;
        private HttpServletResponse response;
        private int status = HttpServletResponse.SC_OK;
        private boolean forwarded;

        private Exchange(User currentUser) {
            sessionAttributes.put(AppConstants.SESSION_USER, currentUser);
        }

        private HttpServletRequest request() {
            if (request == null) {
                HttpSession session = session();
                RequestDispatcher dispatcher = dispatcher();
                InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
                    case "getContextPath" -> "/cinebook";
                    case "getSession" -> session;
                    case "setAttribute" -> {
                        requestAttributes.put((String) args[0], args[1]);
                        yield null;
                    }
                    case "getAttribute" -> requestAttributes.get((String) args[0]);
                    case "removeAttribute" -> {
                        requestAttributes.remove((String) args[0]);
                        yield null;
                    }
                    case "getRequestDispatcher" -> dispatcher;
                    default -> defaultValue(method.getReturnType());
                };
                request = (HttpServletRequest) Proxy.newProxyInstance(
                        getClass().getClassLoader(), new Class<?>[] {HttpServletRequest.class}, handler);
            }
            return request;
        }

        private HttpSession session() {
            InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
                case "getAttribute" -> sessionAttributes.get((String) args[0]);
                case "setAttribute" -> {
                    sessionAttributes.put((String) args[0], args[1]);
                    yield null;
                }
                case "removeAttribute" -> {
                    sessionAttributes.remove((String) args[0]);
                    yield null;
                }
                default -> defaultValue(method.getReturnType());
            };
            return (HttpSession) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[] {HttpSession.class}, handler);
        }

        private RequestDispatcher dispatcher() {
            return (RequestDispatcher) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[] {RequestDispatcher.class},
                    (proxy, method, args) -> {
                        if ("forward".equals(method.getName())) {
                            forwarded = true;
                        }
                        return null;
                    });
        }

        private HttpServletResponse response() {
            if (response == null) {
                InvocationHandler handler = (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "sendError":
                        case "setStatus":
                            status = (Integer) args[0];
                            return null;
                        case "getStatus":
                            return status;
                        default:
                            return defaultValue(method.getReturnType());
                    }
                };
                response = (HttpServletResponse) Proxy.newProxyInstance(
                        getClass().getClassLoader(), new Class<?>[] {HttpServletResponse.class}, handler);
            }
            return response;
        }
    }
}
