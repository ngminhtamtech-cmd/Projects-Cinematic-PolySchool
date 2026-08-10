package com.mycompany.website.ban.ve.xem.phim.api;

import com.mycompany.website.ban.ve.xem.phim.filter.SecurityHeaderFilter;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Bao ve TOAN BO ung dung khoi CSRF theo mo hinh synchronizer token.
 *
 * <p>Tu P05 filter nay map {@code /*} chu khong chi {@code /api/v1/*} nua, nen no phai nhan
 * token o ca hai dang:</p>
 * <ul>
 *   <li>Header {@code X-CSRF-Token} - tang REST/AJAX va BFF Next.js.</li>
 *   <li>Tham so form {@code _csrf} - 67 form POST cua tang JSP. Chen bang tag
 *       {@code <cb:csrf/>} (xem {@code /WEB-INF/tags/csrf.tag}).</li>
 * </ul>
 *
 * <p>Luong hoat dong:</p>
 * <ol>
 *   <li>Request an toan (GET/HEAD/OPTIONS) sinh token neu session chua co, roi phat ra cookie
 *       {@code XSRF-TOKEN} (khong HttpOnly de JS doc duoc, {@code SameSite=Strict}).</li>
 *   <li>Request lam thay doi du lieu bat buoc gui lai token khop voi token trong session.</li>
 * </ol>
 *
 * <p>Khong endpoint nao duoc mien tru - ke ca login - de tranh lo hong "login CSRF".
 * Rieng tai nguyen tinh {@code /assets/*} duoc bo qua hoan toan: chung khong bao gio la thao tac
 * ghi, va cham vao chung se tao session cho moi khach vang lai.</p>
 */
public class CsrfFilter implements Filter {
    public static final String SESSION_ATTR = "csrfToken";
    public static final String HEADER = "X-CSRF-Token";
    /** Ten tham so form cho tang JSP. Hop dong dat ten chot o P05 - dung doi. */
    public static final String PARAM = "_csrf";
    public static final String COOKIE = "XSRF-TOKEN";

    private static final Logger LOG = Logger.getLogger(CsrfFilter.class.getName());
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        if (isStaticAsset(pathWithinApp(request))) {
            chain.doFilter(request, response);
            return;
        }

        if (SAFE_METHODS.contains(request.getMethod().toUpperCase())) {
            publishCookie(request, response, currentToken(request));
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);
        String expected = session == null ? null : (String) session.getAttribute(SESSION_ATTR);
        String provided = providedToken(request);

        if (expected == null || provided == null || !constantTimeEquals(expected, provided)) {
            LOG.log(Level.WARNING, "CSRF tu choi {0} {1} (co session={2}, co token gui len={3})",
                    new Object[] {request.getMethod(), pathWithinApp(request), session != null, provided != null});
            reject(request, response);
            return;
        }
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }

    /** Sinh lai token cho session hien tai. Goi sau khi dang nhap/dang xuat de token khong bi tai su dung. */
    public static String rotate(HttpServletRequest request) {
        String token = newToken();
        request.getSession(true).setAttribute(SESSION_ATTR, token);
        return token;
    }

    public static String currentToken(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        String token = (String) session.getAttribute(SESSION_ATTR);
        if (token == null) {
            token = newToken();
            session.setAttribute(SESSION_ATTR, token);
        }
        return token;
    }

    /**
     * Doc token do client gui len: uu tien header (REST/AJAX), sau do den tham so form {@code _csrf}.
     *
     * <p>Chi goi {@link HttpServletRequest#getParameter} khi header vang mat. Voi request JSON cua
     * {@code /api/v1/*} header luon co, nen than request khong bao gio bi doc truoc servlet.</p>
     */
    private static String providedToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header != null && !header.isBlank()) {
            return header;
        }
        String param = request.getParameter(PARAM);
        return param == null || param.isBlank() ? null : param;
    }

    /**
     * Phat token ra cookie {@code XSRF-TOKEN}.
     *
     * <p>Phai goi sau moi lan {@link #rotate} o cac endpoint POST (login/logout): filter chi
     * tu phat cookie tren request an toan, nen neu khong goi ham nay thi cookie se giu token
     * cu trong khi session da doi - moi thao tac ghi tiep theo se bi tu choi 403.</p>
     */
    public static void writeCookie(HttpServletRequest request, HttpServletResponse response, String token) {
        response.addHeader("Set-Cookie", buildCookieHeader(request, token));
    }

    /** Chi ghi lai cookie khi gia tri thuc su doi, de khoi lap header tren moi request tinh. */
    private static void publishCookie(HttpServletRequest request, HttpServletResponse response, String token) {
        if (token.equals(readCookie(request, COOKIE))) {
            return;
        }
        response.addHeader("Set-Cookie", buildCookieHeader(request, token));
    }

    /**
     * Dung chuoi {@code Set-Cookie} thu cong.
     *
     * <p>Servlet 4.0 khong co API {@code SameSite} cho {@link Cookie}; Tomcat chi ap
     * {@code sameSiteCookies} cua {@code CookieProcessor} cho cookie tao bang API do (JSESSIONID).
     * Cookie CSRF can {@code Strict} chat hon nen phai tu viet header.</p>
     *
     * <p>Co y KHONG dat {@code HttpOnly}: tang JS/BFF phai doc duoc de gan vao header.</p>
     */
    private static String buildCookieHeader(HttpServletRequest request, String token) {
        String path = request.getContextPath();
        StringBuilder sb = new StringBuilder(96);
        sb.append(COOKIE).append('=').append(token);
        sb.append("; Path=").append(path == null || path.isEmpty() ? "/" : path);
        sb.append("; SameSite=Strict");
        if (request.isSecure()) {
            sb.append("; Secure");
        }
        return sb.toString();
    }

    private static String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    static String pathWithinApp(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri.startsWith(context)) {
            return uri.substring(context.length());
        }
        return uri;
    }

    /** Tai nguyen tinh: khong bao gio la thao tac ghi, va khong duoc phep tao session. */
    static boolean isStaticAsset(String path) {
        return path.startsWith("/assets/") || path.equals("/favicon.ico");
    }

    /**
     * Tra 403. Tang REST nhan JSON dung envelope cu; trinh duyet nhan trang loi 403 cua ung dung
     * de nguoi dung doc duoc thay vi thay mot cuc JSON.
     */
    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.reset();
        SecurityHeaderFilter.apply(response);
        String message = "CSRF token khong hop le hoac da het han. Vui long tai lai trang roi thu lai.";
        if (wantsJson(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json;charset=UTF-8");
            Json.mapper().writeValue(response.getOutputStream(), ApiEnvelope.fail("CSRF_INVALID", message));
            return;
        }
        response.sendError(HttpServletResponse.SC_FORBIDDEN, message);
    }

    private boolean wantsJson(HttpServletRequest request) {
        if (pathWithinApp(request).startsWith("/api/")) {
            return true;
        }
        String accept = request.getHeader("Accept");
        String requestedWith = request.getHeader("X-Requested-With");
        return (accept != null && accept.contains("application/json"))
                || "XMLHttpRequest".equalsIgnoreCase(requestedWith);
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** So sanh khong phu thuoc thoi gian de tranh timing attack. */
    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
