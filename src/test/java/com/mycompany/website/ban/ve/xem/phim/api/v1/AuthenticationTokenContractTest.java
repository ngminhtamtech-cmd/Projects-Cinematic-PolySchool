package com.mycompany.website.ban.ve.xem.phim.api.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Truc tiep goi router cua {@link AuthApiServlet}; khong doc source de suy dien endpoint.
 */
@DisplayName("Authentication API - access token and rotating refresh token contract")
@Tag("it")
public class AuthenticationTokenContractTest {

    static {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    @BeforeAll
    static void configureTestDatabase() {
        DBConnection.shutdown();
    }

    @BeforeEach
    void removePersistedTokenFixturesWhenSchemaExists() throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement table = connection.prepareStatement(
                     "SELECT CASE WHEN OBJECT_ID('dbo.RefreshTokens','U') IS NULL THEN 0 ELSE 1 END");
             ResultSet result = table.executeQuery()) {
            if (result.next() && result.getInt(1) == 1) {
                try (PreparedStatement cleanup = connection.prepareStatement(
                        "DELETE FROM RefreshTokens WHERE UserId=1")) {
                    cleanup.executeUpdate();
                }
            }
        }
    }

    @AfterAll
    static void shutdown() {
        DBConnection.shutdown();
    }

    /**
     * BUG-15 — he thong khong con phat JWT access token.
     *
     * <p>Truoc day login tra ve mot JWT HS256 TTL 600s, nhung {@code AuthFilter} khong bao gio doc
     * header {@code Authorization} nen token do khong mo duoc route nao. Phat mot credential khong
     * ai dung chi tao them be mat tan cong. Bai test dao chieu de ai do "khoi phuc lai cho du"
     * se lam do ngay.</p>
     */
    @Test
    @DisplayName("BUG-15: login KHONG con phat access token")
    void loginNoLongerIssuesAccessToken() throws Exception {
        Exchange exchange = invoke("/login",
                "{\"email\":\"member_bronze@test.com\",\"password\":\"password123\"}");

        assertEquals(200, exchange.status,
                "login fixture must authenticate before token assertions");
        assertFalse(exchange.body().contains("\"accessToken\""),
                "Khong duoc phat lai access token khi khong route nao chap nhan no: " + exchange.body());
        assertFalse(exchange.body().contains("\"expiresIn\""),
                "Khong con vong doi access token de cong bo: " + exchange.body());
        assertTrue(exchange.body().contains("\"csrfToken\""),
                "Phan con lai cua SessionDto phai giu nguyen: " + exchange.body());
    }

    @Test
    @DisplayName("FLOW-AUTH-REF-004: login sets an opaque HttpOnly refresh cookie")
    void loginSetsHardenedRefreshCookie() throws Exception {
        Exchange exchange = invoke("/login",
                "{\"email\":\"member_bronze@test.com\",\"password\":\"password123\"}");

        String cookies = String.join("\n", exchange.headers.getOrDefault("Set-Cookie", List.of()));
        assertTrue(cookies.contains("refresh_token="),
                "FLOW-AUTH-REF-004 login did not issue a refresh_token cookie. cookies=" + cookies);
        assertTrue(cookies.contains("HttpOnly"), "refresh cookie must be HttpOnly: " + cookies);
        assertTrue(cookies.contains("Secure"), "refresh cookie must be Secure: " + cookies);
        assertTrue(cookies.contains("SameSite=Strict"), "refresh cookie must be SameSite=Strict: " + cookies);
        assertTrue(cookies.contains("Path=/api/v1/auth"), "refresh cookie path is too broad: " + cookies);
    }

    @Test
    @DisplayName("FLOW-AUTH-REF-005: POST /refresh is a real API endpoint")
    void refreshEndpointExists() throws Exception {
        Exchange exchange = invoke("/refresh", "");
        assertTrue(exchange.status != 404,
                "FLOW-AUTH-REF-005 POST /api/v1/auth/refresh is not implemented. body=" + exchange.body());
    }

    @Test
    @DisplayName("FLOW-AUTH-REF-006: POST /logout-all is a real revocation endpoint")
    void logoutAllEndpointExists() throws Exception {
        Exchange exchange = invoke("/logout-all", "");
        assertTrue(exchange.status != 404,
                "FLOW-AUTH-REF-006 POST /api/v1/auth/logout-all is not implemented. body=" + exchange.body());
    }

    /**
     * BUG-15 — refresh cung khong duoc phat lai access token.
     *
     * <p>Kiem ca hai duong phat (login o tren, refresh o day) vi bo mot noi ma quen noi kia thi
     * credential van ro ri ra ngoai qua duong con lai.</p>
     */
    @Test
    @DisplayName("BUG-15: refresh cung KHONG con phat access token")
    void refreshNoLongerIssuesAccessToken() throws Exception {
        Exchange login = invoke("/login",
                "{\"email\":\"member_bronze@test.com\",\"password\":\"password123\"}");
        String refreshCookie = login.cookieValue("refresh_token");
        assertNotNull(refreshCookie, "login fixture did not issue a refresh token");

        Exchange refreshed = invoke("/refresh", "", cookieHeader(refreshCookie), null);

        assertEquals(200, refreshed.status, "refresh fixture failed: " + refreshed.body());
        assertFalse(refreshed.body().contains("\"accessToken\""),
                "refresh khong duoc phat lai access token: " + refreshed.body());
    }

    @Test
    @DisplayName("FLOW-AUTH-REF-007: refresh token is an opaque 256-bit random value")
    void refreshTokenIsOpaqueAndHasAtLeast256Bits() throws Exception {
        Exchange login = invoke("/login",
                "{\"email\":\"member_bronze@test.com\",\"password\":\"password123\"}");
        String token = login.cookieValue("refresh_token");
        assertNotNull(token, "FLOW-AUTH-REF-007 login did not issue refresh_token");
        assertFalse(token.contains("."), "refresh token must be opaque, not a JWT: " + token);
        assertEquals(32, Base64.getUrlDecoder().decode(token).length,
                "refresh token must contain exactly 256 random bits");
    }

    @Test
    @DisplayName("FLOW-AUTH-REF-008: database stores SHA-256 hash, never raw refresh token")
    void databaseStoresOnlyRefreshTokenHash() throws Exception {
        Exchange login = invoke("/login",
                "{\"email\":\"member_bronze@test.com\",\"password\":\"password123\"}");
        String token = login.cookieValue("refresh_token");
        assertNotNull(token, "FLOW-AUTH-REF-008 login did not issue refresh_token");

        String hash = sha256Hex(token);
        assertEquals(1, scalar("""
                SELECT COUNT(*) FROM RefreshTokens
                WHERE UserId=1 AND TokenHash=?
                """, hash), "the issued refresh token hash is missing from DB");
        assertEquals(0, scalar("""
                SELECT COUNT(*) FROM RefreshTokens
                WHERE TokenHash=?
                """, token), "raw bearer credential was persisted in TokenHash");
    }

    @Test
    @DisplayName("FLOW-AUTH-REF-009: refresh rotates once; replay revokes the whole family")
    void refreshRotationDetectsReplayAndRevokesFamily() throws Exception {
        Exchange login = invoke("/login",
                "{\"email\":\"member_bronze@test.com\",\"password\":\"password123\"}");
        String original = login.cookieValue("refresh_token");
        assertNotNull(original, "FLOW-AUTH-REF-009 login did not issue refresh_token");

        Exchange rotated = invoke("/refresh", "", cookieHeader(original), null);
        assertEquals(200, rotated.status, "first refresh must succeed: " + rotated.body());
        String replacement = rotated.cookieValue("refresh_token");
        assertNotNull(replacement, "successful refresh did not rotate the cookie");
        assertNotEquals(original, replacement, "refresh token was reused instead of rotated");

        Exchange replay = invoke("/refresh", "", cookieHeader(original), null);
        assertEquals(401, replay.status, "reusing a consumed refresh token must be rejected");
        Exchange familyMember = invoke("/refresh", "", cookieHeader(replacement), null);
        assertEquals(401, familyMember.status,
                "replay must revoke even the latest token in the compromised family");

        assertEquals(0, activeTokensInFamily(sha256Hex(original)),
                "a replayed token family still contains active refresh tokens");
    }

    @Test
    @DisplayName("FLOW-AUTH-REF-010: concurrent refresh permits exactly one rotation")
    void concurrentRefreshAllowsExactlyOneWinner() throws Exception {
        Exchange login = invoke("/login",
                "{\"email\":\"member_bronze@test.com\",\"password\":\"password123\"}");
        String original = login.cookieValue("refresh_token");
        assertNotNull(original, "FLOW-AUTH-REF-010 login did not issue refresh_token");

        var executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Integer>> attempts = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                attempts.add(() -> invoke("/refresh", "", cookieHeader(original), null).status);
            }
            List<Integer> statuses = executor.invokeAll(attempts).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .toList();
            assertEquals(1, statuses.stream().filter(status -> status == 200).count(),
                    "exactly one concurrent request may consume the token; statuses=" + statuses);
            assertTrue(statuses.stream().allMatch(status -> status == 200 || status == 401),
                    "refresh race returned an unexpected status: " + statuses);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("FLOW-AUTH-REF-011: logout-all revokes every refresh family for the user")
    void logoutAllRevokesEveryRefreshFamily() throws Exception {
        Exchange firstLogin = invoke("/login",
                "{\"email\":\"member_bronze@test.com\",\"password\":\"password123\"}");
        Exchange secondLogin = invoke("/login",
                "{\"email\":\"member_bronze@test.com\",\"password\":\"password123\"}");
        String firstRefresh = firstLogin.cookieValue("refresh_token");
        String secondRefresh = secondLogin.cookieValue("refresh_token");
        assertNotNull(firstRefresh, "first login did not issue refresh token");
        assertNotNull(secondRefresh, "second login did not issue refresh token");

        // BUG-15: khong con Bearer de nhan dien nguoi goi. Chinh cookie refresh dang cam la du —
        // client REST thuan tuy van dung duoc endpoint nay.
        Exchange logout = invoke("/logout-all", "", cookieHeader(firstRefresh), null);
        assertTrue(logout.status == 200 || logout.status == 204,
                "logout-all failed: status=" + logout.status + ", body=" + logout.body());
        assertEquals(401, invoke("/refresh", "", cookieHeader(firstRefresh), null).status);
        assertEquals(401, invoke("/refresh", "", cookieHeader(secondRefresh), null).status);
    }

    private Exchange invoke(String pathInfo, String jsonBody) throws Exception {
        return invoke(pathInfo, jsonBody, null, null);
    }

    private Exchange invoke(String pathInfo, String jsonBody, String cookieHeader,
                            String authorization) throws Exception {
        Exchange exchange = new Exchange();
        RequestFixture requestFixture = new RequestFixture(
                pathInfo, jsonBody, cookieHeader, authorization);
        new InspectableAuthApiServlet().invoke(requestFixture.request(), exchange.response());
        return exchange;
    }

    private static final class InspectableAuthApiServlet extends AuthApiServlet {
        void invoke(HttpServletRequest request, HttpServletResponse response) throws Exception {
            super.service(request, response);
        }
    }

    private static final class RequestFixture {
        private final String pathInfo;
        private final byte[] body;
        private final String cookieHeader;
        private final String authorization;
        private HttpSession session;

        RequestFixture(String pathInfo, String body, String cookieHeader, String authorization) {
            this.pathInfo = pathInfo;
            this.body = body.getBytes(StandardCharsets.UTF_8);
            this.cookieHeader = cookieHeader;
            this.authorization = authorization;
        }

        HttpServletRequest request() {
            return (HttpServletRequest) Proxy.newProxyInstance(
                    HttpServletRequest.class.getClassLoader(),
                    new Class<?>[]{HttpServletRequest.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getMethod" -> "POST";
                        case "getPathInfo" -> pathInfo;
                        case "getServletPath" -> "/api/v1/auth";
                        case "getRequestURI" -> "/Website-ban-ve-xem-phim/api/v1/auth" + pathInfo;
                        case "getContextPath" -> "/Website-ban-ve-xem-phim";
                        case "getRequestURL" -> new StringBuffer(
                                "https://localhost/Website-ban-ve-xem-phim/api/v1/auth" + pathInfo);
                        case "getRemoteAddr" -> "127.0.0.1";
                        case "isSecure" -> true;
                        case "getContentType" -> "application/json";
                        case "getInputStream" -> inputStream(body);
                        case "getCookies" -> requestCookies(cookieHeader);
                        case "getSession" -> {
                            boolean create = args == null || args.length == 0 || Boolean.TRUE.equals(args[0]);
                            if (session == null && create) {
                                session = session();
                            }
                            yield session;
                        }
                        case "getHeader" -> {
                            String name = (String) args[0];
                            if ("Cookie".equalsIgnoreCase(name)) {
                                yield cookieHeader;
                            }
                            if ("Authorization".equalsIgnoreCase(name)) {
                                yield authorization;
                            }
                            yield null;
                        }
                        case "getParameter", "getQueryString" -> null;
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private HttpSession session() {
            Map<String, Object> attributes = new HashMap<>();
            return (HttpSession) Proxy.newProxyInstance(
                    HttpSession.class.getClassLoader(),
                    new Class<?>[]{HttpSession.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getAttribute" -> attributes.get((String) args[0]);
                        case "setAttribute" -> {
                            attributes.put((String) args[0], args[1]);
                            yield null;
                        }
                        case "removeAttribute" -> {
                            attributes.remove((String) args[0]);
                            yield null;
                        }
                        case "invalidate" -> {
                            attributes.clear();
                            yield null;
                        }
                        case "getId" -> "enterprise-auth-session";
                        default -> defaultValue(method.getReturnType());
                    });
        }
    }

    private static final class Exchange {
        private int status = 200;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private final Map<String, List<String>> headers = new HashMap<>();

        HttpServletResponse response() {
            return (HttpServletResponse) Proxy.newProxyInstance(
                    HttpServletResponse.class.getClassLoader(),
                    new Class<?>[]{HttpServletResponse.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "setStatus" -> {
                            status = (Integer) args[0];
                            yield null;
                        }
                        case "getStatus" -> status;
                        case "addHeader" -> {
                            headers.computeIfAbsent((String) args[0], ignored -> new ArrayList<>())
                                    .add((String) args[1]);
                            yield null;
                        }
                        case "setHeader" -> {
                            headers.put((String) args[0], new ArrayList<>(List.of((String) args[1])));
                            yield null;
                        }
                        case "getOutputStream" -> outputStream(output);
                        case "reset" -> {
                            output.reset();
                            headers.clear();
                            status = 200;
                            yield null;
                        }
                        case "isCommitted" -> false;
                        case "setCharacterEncoding", "setContentType" -> null;
                        default -> defaultValue(method.getReturnType());
                    });
        }

        String body() {
            return output.toString(StandardCharsets.UTF_8);
        }

        String cookieValue(String name) {
            for (String header : headers.getOrDefault("Set-Cookie", List.of())) {
                if (header.startsWith(name + "=")) {
                    String value = header.substring(name.length() + 1);
                    int delimiter = value.indexOf(';');
                    return delimiter < 0 ? value : value.substring(0, delimiter);
                }
            }
            return null;
        }
    }

    private String cookieHeader(String refreshToken) {
        return "refresh_token=" + refreshToken;
    }

    private static Cookie[] requestCookies(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        return java.util.Arrays.stream(header.split(";"))
                .map(String::trim)
                .map(part -> part.split("=", 2))
                .filter(pair -> pair.length == 2)
                .map(pair -> new Cookie(pair[0], pair[1]))
                .toArray(Cookie[]::new);
    }

    private String sha256Hex(String token) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8));
        return java.util.HexFormat.of().formatHex(digest);
    }

    private int scalar(String sql, String value) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private int activeTokensInFamily(String originalHash) throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM RefreshTokens
                WHERE FamilyId=(SELECT TOP(1) FamilyId FROM RefreshTokens WHERE TokenHash=?)
                  AND RevokedAt IS NULL AND ConsumedAt IS NULL
                """;
        return scalar(sql, originalHash);
    }

    private static ServletInputStream inputStream(byte[] bytes) {
        ByteArrayInputStream input = new ByteArrayInputStream(bytes);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return input.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
            }

            @Override
            public int read() {
                return input.read();
            }
        };
    }

    private static ServletOutputStream outputStream(ByteArrayOutputStream output) {
        return new ServletOutputStream() {
            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {
            }

            @Override
            public void write(int value) throws IOException {
                output.write(value);
            }
        };
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class || type == short.class || type == byte.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        if (type == char.class) {
            return '\0';
        }
        return null;
    }
}
