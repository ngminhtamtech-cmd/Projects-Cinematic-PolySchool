package com.mycompany.website.ban.ve.xem.phim.it;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * P05 / D1 + D2 + D13 - kiem chung tren Tomcat that, khong phai tren DAO.
 *
 * <p>Khac moi IT khac cua du an (chung noi thang xuong {@code CineBookDB_Test}), test nay di bang
 * HTTP nen no kiem tra BAN DEPLOY chu khong kiem tra ma nguon vua bien dich.</p>
 *
 * <p><b>Mac dinh bo qua.</b> Phai tu truyen {@code -Dcinebook.it.baseUrl} moi chay. Neu de no tu
 * chay theo mac dinh thi {@code mvn package} se do moi khi Tomcat con giu ban deploy cu - dung ngay
 * luc can build ra WAR moi de deploy.</p>
 *
 * <pre>
 *   mvn -Dit.test=SessionFixationIT failsafe:integration-test failsafe:verify ^
 *     -Dcinebook.it.baseUrl=http://localhost:8080/Website-ban-ve-xem-phim ^
 *     -Dcinebook.it.email=... -Dcinebook.it.password=...
 * </pre>
 *
 * <p>Tai khoan truyen qua tham so dong lenh, khong bao gio nam trong ma nguon.</p>
 */
@Tag("it")
public class SessionFixationIT {

    private static final String BASE = System.getProperty("cinebook.it.baseUrl", "");
    private static final String EMAIL = System.getProperty("cinebook.it.email", "");
    private static final String PASSWORD = System.getProperty("cinebook.it.password", "");
    private static final String SKIP =
            "Bo qua: can -Dcinebook.it.baseUrl tro toi mot ban deploy dang chay (hien tai: '"
            + BASE + "')";

    @Test
    @DisplayName("D13 - GET /login phat XSRF-TOKEN (SameSite=Strict, doc duoc) va JSESSIONID (HttpOnly)")
    public void loginPageIssuesHardenedCookies() throws Exception {
        Response res = request("GET", "/login", null, null);
        assumeTrue(res != null, SKIP);
        assertEquals(200, res.status);

        String csrfCookie = res.setCookie("XSRF-TOKEN");
        assertNotNull(csrfCookie, "GET /login phai phat cookie XSRF-TOKEN");
        assertTrue(csrfCookie.contains("SameSite=Strict"), csrfCookie);
        assertFalse(csrfCookie.toLowerCase().contains("httponly"),
                "XSRF-TOKEN phai doc duoc tu JS/BFF: " + csrfCookie);

        String sessionCookie = res.setCookie("JSESSIONID");
        assertNotNull(sessionCookie, "GET /login phai phat JSESSIONID");
        assertTrue(sessionCookie.toLowerCase().contains("httponly"),
                "JSESSIONID phai co HttpOnly: " + sessionCookie);
        assertTrue(sessionCookie.toLowerCase().contains("samesite=lax"),
                "JSESSIONID phai co SameSite: " + sessionCookie);
    }

    @Test
    @DisplayName("D1 - POST /login thieu _csrf bi tu choi 403 (form JSP khong con mien tru)")
    public void postLoginWithoutCsrfTokenIsForbidden() throws Exception {
        Response page = request("GET", "/login", null, null);
        assumeTrue(page != null, SKIP);

        Response res = request("POST", "/login", page.cookieHeader(),
                "email=khong-ton-tai@test.local&password=sai");
        assertEquals(403, res.status, "POST khong kem token phai bi CsrfFilter chan");
    }

    @Test
    @DisplayName("D1 - POST /login kem _csrf dung thi qua duoc CsrfFilter")
    public void postLoginWithCsrfTokenPassesTheFilter() throws Exception {
        Response page = request("GET", "/login", null, null);
        assumeTrue(page != null, SKIP);
        String token = page.cookieValue("XSRF-TOKEN");

        Response res = request("POST", "/login", page.cookieHeader(),
                "_csrf=" + enc(token) + "&email=khong-ton-tai@test.local&password=sai");
        assertNotEquals(403, res.status,
                "Co token hop le ma van 403 nghia la CsrfFilter dang chan nham");
        assertEquals(200, res.status, "Sai mat khau thi o lai trang login");
    }

    @Test
    @DisplayName("D2 - JSESSIONID phai doi sau khi dang nhap thanh cong (chong session fixation)")
    public void sessionIdChangesAfterSuccessfulLogin() throws Exception {
        assumeTrue(!EMAIL.isBlank(),
                "Bo qua: chua truyen -Dcinebook.it.email / -Dcinebook.it.password");
        Response page = request("GET", "/login", null, null);
        assumeTrue(page != null, SKIP);

        String sessionBefore = page.cookieValue("JSESSIONID");
        String tokenBefore = page.cookieValue("XSRF-TOKEN");
        assertNotNull(sessionBefore);

        Response res = request("POST", "/login", page.cookieHeader(),
                "_csrf=" + enc(tokenBefore) + "&email=" + enc(EMAIL) + "&password=" + enc(PASSWORD));
        assertEquals(302, res.status, "Dang nhap dung phai chuyen huong, khong quay lai trang login");

        String sessionAfter = res.cookieValue("JSESSIONID");
        assertNotNull(sessionAfter, "Dang nhap phai cap JSESSIONID moi");
        assertNotEquals(sessionBefore, sessionAfter,
                "Session id khong doi sau dang nhap = van con lo session fixation");

        String tokenAfter = res.cookieValue("XSRF-TOKEN");
        assertNotNull(tokenAfter, "Phai phat lai cookie CSRF sau khi xoay token");
        assertNotEquals(tokenBefore, tokenAfter, "Token CSRF phai duoc xoay sau dang nhap");
    }

    // ------------------------------------------------------------------ ha tang HTTP

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    /**
     * Tra null khi chua chi dinh {@code -Dcinebook.it.baseUrl} hoac khong ket noi duoc,
     * de test tu bo qua thay vi bao do.
     */
    private static Response request(String method, String path, String cookieHeader, String body)
            throws Exception {
        if (BASE.isBlank()) {
            return null;
        }
        HttpURLConnection conn;
        try {
            conn = (HttpURLConnection) java.net.URI.create(BASE + path).toURL().openConnection();
        } catch (Exception ex) {
            return null;
        }
        try {
            conn.setRequestMethod(method);
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(10000);
            if (cookieHeader != null && !cookieHeader.isBlank()) {
                conn.setRequestProperty("Cookie", cookieHeader);
            }
            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            Response res = new Response();
            res.status = conn.getResponseCode();
            List<String> cookies = conn.getHeaderFields().get("Set-Cookie");
            if (cookies != null) {
                res.setCookies.addAll(cookies);
            }
            if (cookieHeader != null) {
                res.inherited = cookieHeader;
            }
            return res;
        } catch (java.net.ConnectException | java.net.SocketTimeoutException ex) {
            return null;
        } finally {
            conn.disconnect();
        }
    }

    private static final class Response {
        int status;
        final List<String> setCookies = new ArrayList<>();
        String inherited;

        String setCookie(String name) {
            for (String raw : setCookies) {
                if (raw.startsWith(name + "=")) {
                    return raw;
                }
            }
            return null;
        }

        String cookieValue(String name) {
            String raw = setCookie(name);
            if (raw == null) {
                return inherited == null ? null : fromHeader(inherited, name);
            }
            String value = raw.substring(name.length() + 1);
            int semi = value.indexOf(';');
            return semi < 0 ? value : value.substring(0, semi);
        }

        /** Cookie de gui cho request sau: gop cookie moi nhan voi cookie da mang san. */
        String cookieHeader() {
            StringBuilder sb = new StringBuilder();
            for (String name : new String[] {"JSESSIONID", "XSRF-TOKEN"}) {
                String value = cookieValue(name);
                if (value != null) {
                    if (sb.length() > 0) {
                        sb.append("; ");
                    }
                    sb.append(name).append('=').append(value);
                }
            }
            return sb.toString();
        }

        private static String fromHeader(String header, String name) {
            for (String part : header.split(";")) {
                String trimmed = part.trim();
                if (trimmed.startsWith(name + "=")) {
                    return trimmed.substring(name.length() + 1);
                }
            }
            return null;
        }
    }
}
