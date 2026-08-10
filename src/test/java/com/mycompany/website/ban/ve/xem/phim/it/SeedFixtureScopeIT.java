package com.mycompany.website.ban.ve.xem.phim.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("it")
@DisplayName("Clean seed fixture cinema-scope invariant")
class SeedFixtureScopeIT {
    private static final int SEEDED_CINEMA_ID = 1;
    private static final int SEEDED_FILM_ID = 1;
    private static final int SEEDED_SHOWTIME_COUNT = 3;

    static {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    private final AdminService adminService = new AdminService();

    @BeforeAll
    static void configureDatabase() {
        DBConnection.shutdown();
    }

    @AfterAll
    static void shutdown() {
        DBConnection.shutdown();
    }

    @Test
    @DisplayName("D.4: seeded manager sees one assigned film and its three showtimes")
    void managerCatalogMatchesSeededShowtimes() throws SQLException {
        assertTestDatabase();
        User manager = actor(4, "manager", SEEDED_CINEMA_ID);

        assertEquals(1, adminService.listFilms(manager).size());
        assertEquals(SEEDED_FILM_ID, adminService.listFilms(manager).get(0).getId());
        assertEquals(SEEDED_SHOWTIME_COUNT, adminService.listShowtimes(manager).size());

        Map<String, Long> counts = adminService.dashboardCounts(manager);
        assertEquals(1L, counts.get("filmCount"));
        assertEquals((long) SEEDED_SHOWTIME_COUNT, counts.get("showtimeCount"));
        assertEquals(1, scalar("""
                SELECT COUNT(*) FROM CinemaFilms WHERE CinemaId=? AND FilmId=?
                """, SEEDED_CINEMA_ID, SEEDED_FILM_ID));
    }

    private int scalar(String sql, Object... values) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private void assertTestDatabase() throws SQLException {
        assertEquals(System.getProperty("cinebook.it.database", "CineBookIT_REQUIRED"), databaseName());
    }

    private String databaseName() throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT DB_NAME()");
             ResultSet result = statement.executeQuery()) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }

    private static User actor(int id, String role, Integer cinemaId) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setCinemaId(cinemaId);
        return user;
    }
}
