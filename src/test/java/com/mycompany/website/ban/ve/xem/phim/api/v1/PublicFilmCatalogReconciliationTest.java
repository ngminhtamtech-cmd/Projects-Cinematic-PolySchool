package com.mycompany.website.ban.ve.xem.phim.api.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.mycompany.website.ban.ve.xem.phim.api.Json;
import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcFilmDAO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Exercises the real public FilmApiServlet against the same row visible to
 * admin. This catches count/list drift that DAO-only tests cannot see.
 */
@DisplayName("Public film catalog reconciles with admin lifecycle data")
@Tag("it")
public class PublicFilmCatalogReconciliationTest {
    private static final String TITLE = "FLOW-CATALOG-EXPIRED-UNIQUE";

    static {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    private int expiredFilmId;

    @BeforeAll
    static void configureDatabase() {
        DBConnection.shutdown();
    }

    @BeforeEach
    void createExpiredAdminFilm() throws SQLException {
        assertTestDatabase();
        cleanupFixture();
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO Films (Title,ReleaseDate,EndDate,DurationMinutes,Status)
                     VALUES (?,DATEADD(DAY,-30,CAST(GETDATE() AS DATE)),
                             DATEADD(DAY,-1,CAST(GETDATE() AS DATE)),120,'showing')
                     """, PreparedStatement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, TITLE);
            assertEquals(1, statement.executeUpdate());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                expiredFilmId = keys.getInt(1);
            }
        }
    }

    @AfterEach
    void cleanup() throws SQLException {
        cleanupFixture();
    }

    @AfterAll
    static void shutdown() {
        DBConnection.shutdown();
    }

    @Test
    @DisplayName("FLOW-CATALOG-002: public page total counts only publicly visible films")
    void expiredFilmIsExcludedFromItemsAndPaginationTotal() throws Exception {
        Exchange exchange = invoke(null, Map.of("q", TITLE, "page", "1", "size", "20"));
        JsonNode response = Json.mapper().readTree(exchange.body());

        assertEquals(200, exchange.status);
        assertEquals(0, response.path("data").size(),
                "the expired film correctly must not appear in public items");
        assertEquals(0, response.path("meta").path("total").asLong(),
                "FLOW-CATALOG-002 pagination total must use the same lifecycle filter as data; body="
                        + exchange.body());
        assertEquals(0, response.path("meta").path("totalPages").asInt());
    }

    @Test
    @DisplayName("FLOW-CATALOG-003: expired admin film cannot be opened by public direct URL")
    void expiredFilmDetailReturnsNotFound() throws Exception {
        Exchange exchange = invoke("/" + expiredFilmId, Map.of());
        JsonNode response = Json.mapper().readTree(exchange.body());

        assertEquals(404, exchange.status);
        assertEquals("NOT_FOUND", response.path("error").path("code").asText());
        assertFalse(exchange.body().contains(TITLE),
                "the hidden admin film must not leak through the public detail response");
    }

    @Test
    @DisplayName("FLOW-CATALOG-004: cinema join keeps film and assignment statuses unambiguous")
    void cinemaScopedFilmQueryUsesQualifiedLifecycleColumns() throws Exception {
        int cinemaId;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement cinema = connection.prepareStatement(
                     "SELECT TOP (1) Id FROM Cinemas ORDER BY Id");
             ResultSet cinemas = cinema.executeQuery()) {
            assertTrue(cinemas.next(), "integration database must contain an active cinema");
            cinemaId = cinemas.getInt(1);
        }
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement assignment = connection.prepareStatement(
                     "INSERT INTO CinemaFilms (CinemaId,FilmId,Status) VALUES (?,?,'active')")) {
            assignment.setInt(1, cinemaId);
            assignment.setInt(2, expiredFilmId);
            assertEquals(1, assignment.executeUpdate());
        }

        assertFalse(new JdbcFilmDAO().findByCinemaId(cinemaId).stream()
                        .anyMatch(film -> TITLE.equals(film.getTitle())),
                "an expired film stays hidden even when its cinema assignment is active");
    }

    private Exchange invoke(String pathInfo, Map<String, String> parameters) throws Exception {
        Exchange exchange = new Exchange();
        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getMethod" -> "GET";
                    case "getPathInfo" -> pathInfo;
                    case "getServletPath" -> "/api/v1/films";
                    case "getRequestURI" -> "/Website-ban-ve-xem-phim/api/v1/films"
                            + (pathInfo == null ? "" : pathInfo);
                    case "getParameter" -> parameters.get((String) args[0]);
                    case "getSession" -> null;
                    default -> defaultValue(method.getReturnType());
                });
        new InspectableFilmApiServlet().invoke(request, exchange.response());
        return exchange;
    }

    private void cleanupFixture() throws SQLException {
        assertTestDatabase();
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM CinemaFilms WHERE FilmId IN (SELECT Id FROM Films WHERE Title=?);"
                             + " DELETE FROM Films WHERE Title=?")) {
            statement.setString(1, TITLE);
            statement.setString(2, TITLE);
            statement.executeUpdate();
        }
    }

    private void assertTestDatabase() throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT DB_NAME()");
             ResultSet result = statement.executeQuery()) {
            assertTrue(result.next());
            assertEquals(System.getProperty("cinebook.it.database", "CineBookIT_REQUIRED"), result.getString(1));
        }
    }

    private static final class InspectableFilmApiServlet extends FilmApiServlet {
        void invoke(HttpServletRequest request, HttpServletResponse response) throws Exception {
            super.service(request, response);
        }
    }

    private static final class Exchange {
        private int status = 200;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private final Map<String, String> headers = new HashMap<>();

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
                        case "setHeader" -> {
                            headers.put((String) args[0], (String) args[1]);
                            yield null;
                        }
                        case "getOutputStream" -> outputStream(output);
                        case "reset" -> {
                            status = 200;
                            output.reset();
                            headers.clear();
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
        if (type == double.class) {
            return 0d;
        }
        if (type == float.class) {
            return 0f;
        }
        return '\0';
    }
}
