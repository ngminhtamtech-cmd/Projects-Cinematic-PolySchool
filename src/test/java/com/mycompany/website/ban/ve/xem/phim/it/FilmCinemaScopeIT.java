package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.Film;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * N-01 · N-14 — pham vi rap cua manager khi sua phim, va viec go phim khoi MOI rap.
 */
@Tag("it")
@DisplayName("N-01/N-14 — mapping phim–rap khi manager va admin luu phim")
public class FilmCinemaScopeIT {

    private static final int CINEMA_OF_MANAGER = 1;
    private static final int OTHER_CINEMA = 2;

    private static AdminService adminService;
    private static User manager;
    private static User admin;

    @BeforeAll
    public static void setUp() {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
        DBConnection.shutdown();
        adminService = new AdminService();
        manager = new User();
        manager.setId(4);
        manager.setRole("manager");
        manager.setCinemaId(CINEMA_OF_MANAGER);
        admin = new User();
        admin.setId(5);
        admin.setRole("admin");
    }

    @AfterAll
    public static void tearDown() {
        DBConnection.shutdown();
    }

    // ------------------------------------------------------------------------ N-01

    /**
     * "Done khi" cua N-01: manager sua phim thuoc 2 rap thi CinemaFilms van con 2 dong.
     *
     * <p>Ban cu: {@code resolveFilmCinemaTargets} tra ve dung mot phan tu la rap cua
     * manager, roi {@code replaceFilmCinemas} chay {@code DELETE FROM CinemaFilms WHERE
     * FilmId = ?} va chen lai mot dong — rap kia mat phim.</p>
     */
    @Test
    @DisplayName("N-01: manager khong duoc sua truc tiep phim thuoc 2 rap")
    public void managerCannotDirectlyEditSharedFilm() throws SQLException {
        int filmId = insertFilm("IT N-01 phim dung chung");
        try {
            link(filmId, CINEMA_OF_MANAGER);
            link(filmId, OTHER_CINEMA);
            assertEquals(2, countLinks(filmId), "tien de: phim phai thuoc 2 rap");

            Film film = loadFilmForEdit(filmId, "IT N-01 phim dung chung (da sua mo ta)");
            BookingException forbidden = assertThrows(BookingException.class,
                    () -> adminService.saveFilm(film, manager, List.of(CINEMA_OF_MANAGER)));

            assertEquals(403, forbidden.getStatusCode());
            assertEquals(2, countLinks(filmId),
                    "POST truc tiep cua manager khong duoc doi mapping");
            assertTrue(linkedCinemas(filmId).contains(OTHER_CINEMA),
                    "Rap " + OTHER_CINEMA + " phai con giu phim");
        } finally {
            cleanUpFilm(filmId);
        }
    }

    @Test
    @DisplayName("N-01: manager khong duoc go phim truc tiep khoi rap minh")
    public void managerCannotDirectlyUnassignOwnCinema() throws SQLException {
        int filmId = insertFilm("IT N-01 phim go lien ket");
        try {
            link(filmId, CINEMA_OF_MANAGER);
            link(filmId, OTHER_CINEMA);

            Film film = loadFilmForEdit(filmId, "IT N-01 phim go lien ket");
            BookingException forbidden = assertThrows(BookingException.class,
                    () -> adminService.saveFilm(film, manager, List.of()));

            assertEquals(403, forbidden.getStatusCode());
            List<Integer> remaining = linkedCinemas(filmId);
            assertEquals(List.of(CINEMA_OF_MANAGER, OTHER_CINEMA), remaining,
                    "mapping chi duoc doi sau khi admin duyet request");
        } finally {
            cleanUpFilm(filmId);
        }
    }

    @Test
    @DisplayName("N-01: manager gui id rap khac -> khong tao duoc lien ket cho rap do")
    public void managerCannotLinkFilmToAnotherCinema() throws SQLException {
        int filmId = insertFilm("IT N-01 phim vuot rap");
        try {
            link(filmId, CINEMA_OF_MANAGER);

            Film film = loadFilmForEdit(filmId, "IT N-01 phim vuot rap");
            BookingException forbidden = assertThrows(BookingException.class,
                    () -> adminService.saveFilm(film, manager,
                            List.of(CINEMA_OF_MANAGER, OTHER_CINEMA)));

            assertEquals(403, forbidden.getStatusCode());
            assertEquals(List.of(CINEMA_OF_MANAGER), linkedCinemas(filmId),
                    "manager khong duoc gan phim cho rap khac du form gui len id do");
        } finally {
            cleanUpFilm(filmId);
        }
    }

    /**
     * Cung lop loi voi tren: {@code assertFilmScope()} chi doi hoi phim CO lien ket toi
     * rap cua manager, nen manager hard-delete duoc mot phim dung chung — ke ca toan bo
     * Comment cua phim — va cac rap khac mat phim ma khong biet vi sao.
     */
    @Test
    @DisplayName("N-01: manager KHONG xoa duoc phim dang chieu o nhieu rap")
    public void managerCannotHardDeleteFilmSharedWithAnotherCinema() throws SQLException {
        int filmId = insertFilm("IT N-01 phim khong duoc xoa");
        try {
            link(filmId, CINEMA_OF_MANAGER);
            link(filmId, OTHER_CINEMA);

            BookingException ex = assertThrows(BookingException.class,
                    () -> adminService.deleteFilm(filmId, manager));
            assertEquals(403, ex.getStatusCode(), ex.getMessage());
            assertEquals(1, scalar("SELECT COUNT(*) FROM Films WHERE Id = " + filmId),
                    "phim phai con nguyen");
            assertEquals(2, countLinks(filmId), "mapping phai con nguyen");
        } finally {
            cleanUpFilm(filmId);
        }
    }

    @Test
    @DisplayName("N-01: chỉ admin hệ thống được xóa phim kể cả phim của một rạp")
    public void managerStillDeletesFilmThatBelongsOnlyToOwnCinema() throws SQLException {
        int filmId = insertFilm("IT N-01 phim rieng cua rap");
        link(filmId, CINEMA_OF_MANAGER);

        BookingException forbidden = assertThrows(BookingException.class,
                () -> adminService.deleteFilm(filmId, manager));
        assertEquals(403, forbidden.getStatusCode());
        assertEquals(1, scalar("SELECT COUNT(*) FROM Films WHERE Id = " + filmId));
        cleanUpFilm(filmId);
    }

    // ------------------------------------------------------------------------ N-14

    /**
     * "Bo chon het" phai thuc su go het. Tang service da phan biet {@code null} (khong
     * dong toi) voi tap rong (go het) — day la khang dinh cho nua duoi cua N-14.
     */
    @Test
    @DisplayName("N-14: admin gui tap rong -> go phim khoi MOI rap")
    public void adminSubmittingEmptyCinemaSetUnlinksFilmEverywhere() throws SQLException {
        int filmId = insertFilm("IT N-14 phim go het rap");
        try {
            link(filmId, CINEMA_OF_MANAGER);
            link(filmId, OTHER_CINEMA);

            Film film = loadFilmForEdit(filmId, "IT N-14 phim go het rap");
            adminService.saveFilm(film, admin, List.of());

            assertEquals(0, countActiveLinks(filmId),
                    "bo chon het thi khong con mapping hoat dong");
            assertEquals(2, countLinks(filmId),
                    "mapping lich su phai duoc giu o trang thai inactive");
        } finally {
            cleanUpFilm(filmId);
        }
    }

    @Test
    @DisplayName("N-14: null (form khong gui truong rap) -> mapping giu nguyen")
    public void adminSubmittingNoCinemaFieldLeavesMappingUntouched() throws SQLException {
        int filmId = insertFilm("IT N-14 phim giu mapping");
        try {
            link(filmId, CINEMA_OF_MANAGER);
            link(filmId, OTHER_CINEMA);

            Film film = loadFilmForEdit(filmId, "IT N-14 phim giu mapping");
            adminService.saveFilm(film, admin, null);

            assertEquals(2, countLinks(filmId), "khong gui truong rap thi khong duoc dong toi mapping");
        } finally {
            cleanUpFilm(filmId);
        }
    }

    /**
     * Nua tren cua N-14 nam o form: khong co hidden sentinel thi servlet khong the biet
     * admin da bo chon het, va tap rong khong bao gio den duoc tang service.
     */
    @Test
    @DisplayName("N-14: film-form.jsp phai co hidden sentinel cinemaIdsPresent")
    public void filmFormCarriesTheCinemaSentinelSoEmptySelectionReachesTheServer() throws Exception {
        String jsp = Files.readString(
                Path.of("src/main/webapp/WEB-INF/views/admin/film-form.jsp"), StandardCharsets.UTF_8);
        assertTrue(jsp.contains("name=\"cinemaIdsPresent\""),
                "form phim phai gui sentinel de phan biet 'bo chon het' voi 'khong gui truong nay'");
        assertTrue(jsp.indexOf("name=\"cinemaIdsPresent\"") < jsp.indexOf("name=\"cinemaIds\""),
                "sentinel phai nam trong cung form va truoc cac checkbox");
    }

    // ---------------------------------------------------------------- fixtures

    private static Film loadFilmForEdit(int filmId, String title) {
        Film film = new Film();
        film.setId(filmId);
        film.setTitle(title);
        film.setReleaseDate(LocalDate.of(2026, 1, 1));
        film.setEndDate(LocalDate.of(2026, 12, 31));
        film.setDurationMinutes(120);
        film.setStatus("showing");
        film.setActors("IT actor");
        film.setDirectors("IT director");
        film.setCategories("IT");
        film.setCountry("VN");
        film.setDescription("IT description");
        film.setLanguage("vi");
        film.setFormat("2D");
        film.setAgeRating("P");
        film.setThumbnail("/it-poster.jpg");
        film.setBanner("/it-banner.jpg");
        film.setTrailerUrl("https://example.test/trailer");
        return film;
    }

    private static int insertFilm(String title) throws SQLException {
        return insertReturningId("INSERT INTO Films (Title, ReleaseDate, EndDate, DurationMinutes, Status) "
                + "VALUES (N'" + title + "', '2026-01-01', '2026-12-31', 120, 'showing')");
    }

    private static void link(int filmId, int cinemaId) throws SQLException {
        exec("INSERT INTO CinemaFilms (CinemaId, FilmId) VALUES (" + cinemaId + ", " + filmId + ")");
    }

    private static int countLinks(int filmId) throws SQLException {
        return scalar("SELECT COUNT(*) FROM CinemaFilms WHERE FilmId = " + filmId);
    }

    private static int countActiveLinks(int filmId) throws SQLException {
        return scalar("SELECT COUNT(*) FROM CinemaFilms WHERE Status='active' AND FilmId = " + filmId);
    }

    private static List<Integer> linkedCinemas(int filmId) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT CinemaId FROM CinemaFilms WHERE FilmId = ? ORDER BY CinemaId")) {
            ps.setInt(1, filmId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
            }
        }
        return ids;
    }

    private static void cleanUpFilm(int filmId) throws SQLException {
        exec("DELETE FROM CinemaFilms WHERE FilmId = " + filmId);
        exec("DELETE FROM FilmCategories WHERE FilmId = " + filmId);
        exec("DELETE FROM Films WHERE Id = " + filmId);
    }

    private static int insertReturningId(String sql) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    private static int scalar(String sql) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static void exec(String sql) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.executeUpdate();
        }
    }
}
