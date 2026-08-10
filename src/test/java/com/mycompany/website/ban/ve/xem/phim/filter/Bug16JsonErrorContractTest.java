package com.mycompany.website.ban.ve.xem.phim.filter;

import com.mycompany.website.ban.ve.xem.phim.service.AccountStateGuard;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BUG-16 — hop dong HTTP cho client <b>xin JSON</b>.
 *
 * <p>Quyet dinh da chot: chi sua cho client xin JSON; form POST thuong giu nguyen
 * POST-redirect-GET. Bai test chot ca hai ve cua quyet dinh do, vi ve thu hai de bi pha nham khi
 * ai do "sua cho nhat quan".</p>
 *
 * <p>Ca cu the phai dong: trang dat ve goi {@code POST /orders/&#123;id&#125;/pay} bang
 * {@code fetch()} va parse {@code response.json()}. Neu phien bi thu hoi ma server tra ve HTML
 * trang login thi {@code res.json()} nem loi parse, va khach thay mot man hinh treo thay vi mot
 * thong bao "vui long dang nhap lai".</p>
 */
public class Bug16JsonErrorContractTest {

    private final AuthFilter filter = new AuthFilter(new AccountStateGuard());

    @AfterEach
    public void tearDown() {
        AccountStateGuard.clearCache();
    }

    @Test
    @DisplayName("BUG-16: POST /orders/{id}/pay xin JSON khi chua dang nhap -> 401 JSON, khong phai HTML")
    public void payEndpointReturnsJsonWhenClientAsksForJson() throws Exception {
        Exchange exchange = new Exchange("/orders/55/pay", "POST");
        exchange.headers.put("Accept", "application/json");

        filter.doFilter(exchange.request(), exchange.response(), exchange.chain());

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, exchange.status);
        assertNull(exchange.redirectedTo, "Client xin JSON thi khong duoc day sang trang login");
        assertTrue(exchange.contentType != null && exchange.contentType.contains("application/json"),
                "Content-Type phai la JSON, thuc te " + exchange.contentType);
        assertFalse(exchange.chainCalled);
    }

    @Test
    @DisplayName("BUG-16: X-Requested-With cung duoc coi la xin JSON")
    public void xmlHttpRequestHeaderAlsoCountsAsJsonClient() throws Exception {
        Exchange exchange = new Exchange("/orders/55/pay", "POST");
        exchange.headers.put("X-Requested-With", "XMLHttpRequest");

        filter.doFilter(exchange.request(), exchange.response(), exchange.chain());

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, exchange.status);
        assertNull(exchange.redirectedTo);
    }

    @Test
    @DisplayName("BUG-16: form POST thuong VAN giu POST-redirect-GET, khong bi doi sang JSON")
    public void plainFormPostKeepsRedirectContract() throws Exception {
        Exchange exchange = new Exchange("/orders/55/cancel", "POST");

        filter.doFilter(exchange.request(), exchange.response(), exchange.chain());

        assertTrue(exchange.redirectedTo != null && exchange.redirectedTo.contains("/login"),
                "Form POST khong xin JSON thi phai duoc day sang trang login nhu cu, thuc te "
                        + exchange.redirectedTo);
        assertFalse(exchange.chainCalled);
    }

    // ---------------------------------------------------------------- fake servlet API

    private static final class Exchange {
        private final Map<String, String> headers = new HashMap<>();
        private final String path;
        private final String method;
        private final StringWriter body = new StringWriter();
        private final PrintWriter writer = new PrintWriter(body, true);
        private int status = HttpServletResponse.SC_OK;
        private String contentType;
        private String redirectedTo;
        private boolean chainCalled;

        Exchange(String path, String method) {
            this.path = path;
            this.method = method;
        }

        HttpServletRequest request() {
            return (HttpServletRequest) Proxy.newProxyInstance(
                    Exchange.class.getClassLoader(),
                    new Class<?>[] {HttpServletRequest.class},
                    (proxy, method2, args) -> switch (method2.getName()) {
                        case "getRequestURI" -> "/cinebook" + path;
                        case "getContextPath" -> "/cinebook";
                        case "getServletPath" -> path;
                        case "getPathInfo" -> null;
                        case "getMethod" -> method;
                        case "getQueryString" -> null;
                        case "getHeader" -> headers.get((String) args[0]);
                        case "getSession" -> null;
                        case "toString" -> "FakeRequest";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> defaultValue(method2.getReturnType());
                    });
        }

        HttpServletResponse response() {
            return (HttpServletResponse) Proxy.newProxyInstance(
                    Exchange.class.getClassLoader(),
                    new Class<?>[] {HttpServletResponse.class},
                    (proxy, method2, args) -> {
                        switch (method2.getName()) {
                            case "setStatus" -> status = (int) args[0];
                            case "sendError" -> status = (int) args[0];
                            case "sendRedirect" -> redirectedTo = (String) args[0];
                            case "setContentType" -> contentType = (String) args[0];
                            case "getWriter" -> {
                                return writer;
                            }
                            case "toString" -> {
                                return "FakeResponse";
                            }
                            case "hashCode" -> {
                                return System.identityHashCode(proxy);
                            }
                            case "equals" -> {
                                return proxy == args[0];
                            }
                            default -> {
                                return defaultValue(method2.getReturnType());
                            }
                        }
                        return null;
                    });
        }

        FilterChain chain() {
            return (request, response) -> chainCalled = true;
        }
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
}
