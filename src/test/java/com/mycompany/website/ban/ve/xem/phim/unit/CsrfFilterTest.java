package com.mycompany.website.ban.ve.xem.phim.unit;

import com.mycompany.website.ban.ve.xem.phim.api.CsrfFilter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.FilterChain;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.WriteListener;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P05 / D1 - kiem tra lop bao ve CSRF sau khi no duoc map ra {@code /*}.
 *
 * <p>Khong dung thu vien mock: {@code HttpServletRequest}/{@code HttpServletResponse} duoc gia lap
 * bang {@link Proxy}, du de chay that qua {@code doFilter}.</p>
 */
public class CsrfFilterTest {

    private static final String CONTEXT = "/Website-ban-ve-xem-phim";

    // ---------------------------------------------------------------- request an toan

    @Test
    @DisplayName("GET sinh token trong session va phat cookie XSRF-TOKEN khong HttpOnly, SameSite=Strict")
    public void safeRequestIssuesTokenAndCookie() throws Exception {
        FakeRequest req = new FakeRequest("GET", CONTEXT + "/home");
        FakeResponse res = new FakeResponse();
        FakeChain chain = new FakeChain();

        new CsrfFilter().doFilter(req.proxy(), res.proxy(), chain.proxy());

        assertTrue(chain.called, "request an toan phai di tiep");
        String token = (String) req.session.attrs.get(CsrfFilter.SESSION_ATTR);
        assertNotNull(token, "GET phai sinh token neu session chua co");

        String setCookie = res.firstSetCookie();
        assertNotNull(setCookie, "phai phat cookie XSRF-TOKEN");
        assertTrue(setCookie.startsWith(CsrfFilter.COOKIE + "=" + token), setCookie);
        assertTrue(setCookie.contains("Path=" + CONTEXT), setCookie);
        assertTrue(setCookie.contains("SameSite=Strict"), setCookie);
        assertFalse(setCookie.contains("HttpOnly"),
                "tang JS/BFF phai doc duoc cookie nay de gan vao header");
        assertFalse(setCookie.contains("Secure"), "request HTTP thi khong duoc dat co Secure");
    }

    @Test
    @DisplayName("Tren HTTPS cookie CSRF co them co Secure")
    public void secureRequestMarksCookieSecure() throws Exception {
        FakeRequest req = new FakeRequest("GET", CONTEXT + "/home");
        req.secure = true;
        FakeResponse res = new FakeResponse();

        new CsrfFilter().doFilter(req.proxy(), res.proxy(), new FakeChain().proxy());

        assertTrue(res.firstSetCookie().contains("; Secure"), res.firstSetCookie());
    }

    @Test
    @DisplayName("Cookie khong bi phat lai khi gia tri van con dung")
    public void cookieIsNotRepublishedWhenUnchanged() throws Exception {
        FakeRequest req = new FakeRequest("GET", CONTEXT + "/home");
        String token = CsrfFilter.currentToken(req.proxy());
        req.cookies.add(new Cookie(CsrfFilter.COOKIE, token));
        FakeResponse res = new FakeResponse();

        new CsrfFilter().doFilter(req.proxy(), res.proxy(), new FakeChain().proxy());

        assertNull(res.firstSetCookie(), "khong duoc lap header Set-Cookie khi token khong doi");
    }

    @Test
    @DisplayName("Tai nguyen tinh khong tao session va khong phat cookie")
    public void staticAssetIsSkippedEntirely() throws Exception {
        FakeRequest req = new FakeRequest("GET", CONTEXT + "/assets/css/app.css");
        FakeResponse res = new FakeResponse();
        FakeChain chain = new FakeChain();

        new CsrfFilter().doFilter(req.proxy(), res.proxy(), chain.proxy());

        assertTrue(chain.called);
        assertNull(req.session, "cham vao file tinh khong duoc tao session cho khach vang lai");
        assertNull(res.firstSetCookie());
    }

    // ---------------------------------------------------------------- request ghi

    @Test
    @DisplayName("POST thieu token bi tu choi 403 va khong den duoc servlet")
    public void postWithoutTokenIsRejected() throws Exception {
        FakeRequest req = new FakeRequest("POST", CONTEXT + "/login");
        req.newSession().attrs.put(CsrfFilter.SESSION_ATTR, "token-that");
        FakeResponse res = new FakeResponse();
        FakeChain chain = new FakeChain();

        new CsrfFilter().doFilter(req.proxy(), res.proxy(), chain.proxy());

        assertFalse(chain.called, "request thieu token khong duoc di tiep");
        assertEquals(HttpServletResponse.SC_FORBIDDEN, res.sendErrorStatus);
    }

    @Test
    @DisplayName("POST khong co session nao cung bi tu choi")
    public void postWithoutSessionIsRejected() throws Exception {
        FakeRequest req = new FakeRequest("POST", CONTEXT + "/login");
        req.params.put(CsrfFilter.PARAM, "token-tu-che");
        FakeResponse res = new FakeResponse();
        FakeChain chain = new FakeChain();

        new CsrfFilter().doFilter(req.proxy(), res.proxy(), chain.proxy());

        assertFalse(chain.called);
        assertEquals(HttpServletResponse.SC_FORBIDDEN, res.sendErrorStatus);
    }

    @Test
    @DisplayName("POST sai token bi tu choi")
    public void postWithWrongTokenIsRejected() throws Exception {
        FakeRequest req = new FakeRequest("POST", CONTEXT + "/admin/films");
        req.newSession().attrs.put(CsrfFilter.SESSION_ATTR, "token-that");
        req.params.put(CsrfFilter.PARAM, "token-gia");
        FakeResponse res = new FakeResponse();
        FakeChain chain = new FakeChain();

        new CsrfFilter().doFilter(req.proxy(), res.proxy(), chain.proxy());

        assertFalse(chain.called);
        assertEquals(HttpServletResponse.SC_FORBIDDEN, res.sendErrorStatus);
    }

    @Test
    @DisplayName("POST kem tham so form _csrf dung thi di tiep - day la duong cua 67 form JSP")
    public void postWithFormParameterPasses() throws Exception {
        FakeRequest req = new FakeRequest("POST", CONTEXT + "/admin/films");
        req.newSession().attrs.put(CsrfFilter.SESSION_ATTR, "token-that");
        req.params.put(CsrfFilter.PARAM, "token-that");
        FakeResponse res = new FakeResponse();
        FakeChain chain = new FakeChain();

        new CsrfFilter().doFilter(req.proxy(), res.proxy(), chain.proxy());

        assertTrue(chain.called, "form JSP gui dung _csrf phai duoc di tiep");
        assertEquals(-1, res.sendErrorStatus);
    }

    @Test
    @DisplayName("POST kem header X-CSRF-Token dung thi di tiep - duong cua REST/AJAX")
    public void postWithHeaderPasses() throws Exception {
        FakeRequest req = new FakeRequest("POST", CONTEXT + "/api/v1/staff/checkin");
        req.newSession().attrs.put(CsrfFilter.SESSION_ATTR, "token-that");
        req.headers.put(CsrfFilter.HEADER, "token-that");
        FakeResponse res = new FakeResponse();
        FakeChain chain = new FakeChain();

        new CsrfFilter().doFilter(req.proxy(), res.proxy(), chain.proxy());

        assertTrue(chain.called);
        assertFalse(req.getParameterCalled, "co header roi thi khong duoc dong vao than request");
    }

    @Test
    @DisplayName("Tang REST bi tu choi thi nhan JSON dung envelope cu, khong phai trang HTML")
    public void apiRejectionStaysJson() throws Exception {
        FakeRequest req = new FakeRequest("POST", CONTEXT + "/api/v1/orders/9/pay");
        req.newSession().attrs.put(CsrfFilter.SESSION_ATTR, "token-that");
        FakeResponse res = new FakeResponse();

        new CsrfFilter().doFilter(req.proxy(), res.proxy(), new FakeChain().proxy());

        assertEquals(HttpServletResponse.SC_FORBIDDEN, res.status);
        assertEquals(-1, res.sendErrorStatus, "endpoint API khong duoc tra trang loi HTML");
        String body = res.bodyAsString();
        assertTrue(body.contains("CSRF_INVALID"), body);
    }

    @Test
    @DisplayName("Header bao mat duoc dat lai sau khi response bi reset o duong tu choi")
    public void rejectionKeepsSecurityHeaders() throws Exception {
        FakeRequest req = new FakeRequest("POST", CONTEXT + "/login");
        req.newSession().attrs.put(CsrfFilter.SESSION_ATTR, "token-that");
        FakeResponse res = new FakeResponse();

        new CsrfFilter().doFilter(req.proxy(), res.proxy(), new FakeChain().proxy());

        assertEquals("nosniff", res.headers.get("X-Content-Type-Options"));
        assertEquals("DENY", res.headers.get("X-Frame-Options"));
        assertNotNull(res.headers.get("Content-Security-Policy"));
    }

    // ---------------------------------------------------------------- token

    @Test
    @DisplayName("rotate() doi token va currentToken() giu nguyen token da co")
    public void rotateReplacesToken() {
        FakeRequest req = new FakeRequest("GET", CONTEXT + "/home");
        String first = CsrfFilter.currentToken(req.proxy());
        assertEquals(first, CsrfFilter.currentToken(req.proxy()), "goi lai khong duoc doi token");

        String rotated = CsrfFilter.rotate(req.proxy());
        assertNotEquals(first, rotated);
        assertEquals(rotated, CsrfFilter.currentToken(req.proxy()));
    }

    @Test
    @DisplayName("Token la 256 bit ngau nhien, an toan de dat trong thuoc tinh HTML")
    public void tokenIsRandomAndHtmlSafe() {
        String a = CsrfFilter.rotate(new FakeRequest("GET", CONTEXT + "/home").proxy());
        String b = CsrfFilter.rotate(new FakeRequest("GET", CONTEXT + "/home").proxy());
        assertNotEquals(a, b);
        assertEquals(43, a.length(), "32 byte ma hoa Base64-URL khong padding");
        assertTrue(a.matches("[A-Za-z0-9_-]+"), a);
    }

    // ================================================================ gia lap servlet API

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return Boolean.FALSE;
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

    private static final class FakeSession implements InvocationHandler {
        final Map<String, Object> attrs = new HashMap<>();
        final String id;
        private HttpSession cached;

        FakeSession(String id) {
            this.id = id;
        }

        HttpSession proxy() {
            if (cached == null) {
                cached = (HttpSession) Proxy.newProxyInstance(getClass().getClassLoader(),
                        new Class<?>[] {HttpSession.class}, this);
            }
            return cached;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "getAttribute":
                    return attrs.get((String) args[0]);
                case "setAttribute":
                    attrs.put((String) args[0], args[1]);
                    return null;
                case "removeAttribute":
                    attrs.remove((String) args[0]);
                    return null;
                case "invalidate":
                    attrs.clear();
                    return null;
                case "getId":
                    return id;
                case "toString":
                    return "FakeSession[" + id + "]";
                case "hashCode":
                    return System.identityHashCode(this);
                case "equals":
                    return proxy == args[0];
                default:
                    return defaultValue(method.getReturnType());
            }
        }
    }

    private static final class FakeRequest implements InvocationHandler {
        final Map<String, String> headers = new HashMap<>();
        final Map<String, String> params = new HashMap<>();
        final List<Cookie> cookies = new ArrayList<>();
        final String method;
        final String uri;
        boolean secure;
        boolean getParameterCalled;
        FakeSession session;
        private HttpServletRequest cached;

        FakeRequest(String method, String uri) {
            this.method = method;
            this.uri = uri;
        }

        FakeSession newSession() {
            session = new FakeSession("S1");
            return session;
        }

        HttpServletRequest proxy() {
            if (cached == null) {
                cached = (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                        new Class<?>[] {HttpServletRequest.class}, this);
            }
            return cached;
        }

        @Override
        public Object invoke(Object proxy, Method m, Object[] args) {
            switch (m.getName()) {
                case "getMethod":
                    return method;
                case "getRequestURI":
                    return uri;
                case "getContextPath":
                    return CONTEXT;
                case "getHeader":
                    return headers.get((String) args[0]);
                case "getParameter":
                    getParameterCalled = true;
                    return params.get((String) args[0]);
                case "getCookies":
                    return cookies.isEmpty() ? null : cookies.toArray(new Cookie[0]);
                case "isSecure":
                    return secure;
                case "getSession": {
                    boolean create = args == null || args.length == 0 || Boolean.TRUE.equals(args[0]);
                    if (session == null && create) {
                        session = new FakeSession("S1");
                    }
                    return session == null ? null : session.proxy();
                }
                case "toString":
                    return "FakeRequest[" + method + " " + uri + "]";
                case "hashCode":
                    return System.identityHashCode(this);
                case "equals":
                    return proxy == args[0];
                default:
                    return defaultValue(m.getReturnType());
            }
        }
    }

    private static final class FakeResponse implements InvocationHandler {
        final Map<String, String> headers = new HashMap<>();
        final List<String> setCookies = new ArrayList<>();
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        int status = 200;
        int sendErrorStatus = -1;
        private HttpServletResponse cached;

        HttpServletResponse proxy() {
            if (cached == null) {
                cached = (HttpServletResponse) Proxy.newProxyInstance(getClass().getClassLoader(),
                        new Class<?>[] {HttpServletResponse.class}, this);
            }
            return cached;
        }

        String firstSetCookie() {
            return setCookies.isEmpty() ? null : setCookies.get(0);
        }

        String bodyAsString() {
            return new String(body.toByteArray(), StandardCharsets.UTF_8);
        }

        @Override
        public Object invoke(Object proxy, Method m, Object[] args) throws IOException {
            switch (m.getName()) {
                case "addHeader":
                case "setHeader": {
                    String name = (String) args[0];
                    String value = (String) args[1];
                    if ("Set-Cookie".equals(name)) {
                        setCookies.add(value);
                    } else {
                        headers.put(name, value);
                    }
                    return null;
                }
                case "setStatus":
                    status = (Integer) args[0];
                    return null;
                case "getStatus":
                    return status;
                case "sendError":
                    sendErrorStatus = (Integer) args[0];
                    status = sendErrorStatus;
                    return null;
                case "reset":
                    headers.clear();
                    setCookies.clear();
                    body.reset();
                    status = 200;
                    return null;
                case "getOutputStream":
                    return new ServletOutputStream() {
                        @Override
                        public boolean isReady() {
                            return true;
                        }

                        @Override
                        public void setWriteListener(WriteListener listener) {
                        }

                        @Override
                        public void write(int b) {
                            body.write(b);
                        }
                    };
                case "toString":
                    return "FakeResponse";
                case "hashCode":
                    return System.identityHashCode(this);
                case "equals":
                    return proxy == args[0];
                default:
                    return defaultValue(m.getReturnType());
            }
        }
    }

    private static final class FakeChain implements InvocationHandler {
        boolean called;

        FilterChain proxy() {
            return (FilterChain) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] {FilterChain.class}, this);
        }

        @Override
        public Object invoke(Object proxy, Method m, Object[] args) {
            if ("doFilter".equals(m.getName())) {
                assertTrue(args[0] instanceof ServletRequest);
                assertTrue(args[1] instanceof ServletResponse);
                called = true;
                return null;
            }
            return defaultValue(m.getReturnType());
        }
    }
}
