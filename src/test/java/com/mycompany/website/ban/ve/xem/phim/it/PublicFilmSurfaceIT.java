package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcFilmDAO;
import com.mycompany.website.ban.ve.xem.phim.model.Film;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * N-04 · N-13 — "Phim het han an khoi MOI be mat public".
 *
 * <p>DoD cua EX-01 la mot cau ve <i>moi</i> be mat, nen phan tinh cua test nay quet toan bo
 * servlet public thay vi liet ke tay ba trang da biet la hong. Ba trang {@code /su-kien},
 * {@code /cinetags}, {@code /goc-dien-anh} chi la nhung trang bi bo sot lan nay; cach duy nhat
 * de lan sau khong bo sot nua la khong cho phep mau code do ton tai.</p>
 */
@Tag("it")
@DisplayName("N-04/N-13 — phim het han khong lot ra be mat public nao")
public class PublicFilmSurfaceIT {

    /** Truy van phim KHONG loc vong doi — ket qua cua chung khong duoc di thang ra view. */
    private static final Pattern RAW_FILM_QUERY_INTO_VIEW = Pattern.compile(
            "setAttribute\\(\\s*\"[^\"]*\"\\s*,\\s*(?:[A-Za-z_$][\\w$]*\\.)*"
                    + "filmDAO\\.(?:search|findFeatured|findByCinemaId)\\s*\\(");

    private static final Pattern UNFILTERED_FILM_QUERY = Pattern.compile(
            "filmDAO\\.(?:search|findFeatured|findByCinemaId)\\s*\\(");

    private static JdbcFilmDAO filmDAO;

    @BeforeAll
    public static void setUp() {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
        DBConnection.shutdown();
        filmDAO = new JdbcFilmDAO();
    }

    @AfterAll
    public static void tearDown() {
        DBConnection.shutdown();
    }

    // ------------------------------------------------------------------ N-04 (tinh)

    /**
     * Ban cu ba trang deu viet
     * {@code request.setAttribute("featuredFilms", filmDAO.search(null, 0, 4))} — ket qua tho
     * cua mot cau khong co {@code PUBLIC_LIFECYCLE_PREDICATE} di thang ra JSP.
     */
    @Test
    @DisplayName("N-04: khong servlet public nao day ket qua truy van phim THO ra view")
    public void noPublicServletPutsAnUnfilteredFilmListIntoTheView() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path source : publicSurfaces()) {
            String code = Files.readString(source, StandardCharsets.UTF_8);
            Matcher matcher = RAW_FILM_QUERY_INTO_VIEW.matcher(code);
            while (matcher.find()) {
                violations.add(source + ":" + lineOf(code, matcher.start()) + ": " + matcher.group());
            }
        }
        if (!violations.isEmpty()) {
            fail("Be mat public dang day danh sach phim chua loc vong doi ra view — phim da qua "
                    + "EndDate se hien va bam vao ra 410:\n" + String.join("\n", violations)
                    + "\nDung filmDAO.searchPublic(...) hoac boc trong FilmAvailabilityPolicy.publicOnly(...).");
        }
    }

    /**
     * Chan ca truong hop ket qua khong di thang ra view ma di vong qua bien trung gian:
     * file nao cham vao truy van phim khong loc thi phai co dau vet cua chinh sach vong doi.
     */
    @Test
    @DisplayName("N-04: file public nao dung truy van phim khong loc deu phai ap FilmAvailabilityPolicy")
    public void everyPublicSurfaceTouchingFilmsAppliesTheLifecyclePolicy() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path source : publicSurfaces()) {
            String code = Files.readString(source, StandardCharsets.UTF_8);
            if (UNFILTERED_FILM_QUERY.matcher(code).find()
                    && !code.contains("FilmAvailabilityPolicy")) {
                violations.add(source.toString());
            }
        }
        if (!violations.isEmpty()) {
            fail("Cac file public duoi day doc phim bang truy van khong loc vong doi va khong "
                    + "nhac den FilmAvailabilityPolicy o bat ky dau:\n" + String.join("\n", violations));
        }
    }

    // --------------------------------------------------------------- N-04 (hanh vi)

    @Test
    @DisplayName("N-04: phim co EndDate hom qua khong nam trong searchPublic, nhung CO trong search")
    public void expiredFilmIsExcludedBySearchPublicAndThatIsWhyTheSwapMatters() throws SQLException {
        int filmId = insertExpiredFilm("IT N-04 phim het han");
        try {
            assertFalse(containsFilm(filmDAO.searchPublic(null, null, 0, 100), filmId),
                    "searchPublic() phai loai phim da qua EndDate");
            assertTrue(containsFilm(filmDAO.search(null, 0, 100), filmId),
                    "search() KHONG loc vong doi — day chinh la ly do ba trang public bi lot phim");
        } finally {
            exec("DELETE FROM Films WHERE Id = " + filmId);
        }
    }

    // --------------------------------------------------------------- N-13 (hanh vi)

    /**
     * Ban cu {@code publicOnly(search(keyword, offset, pageSize))} lay dung {@code pageSize}
     * dong roi moi bo phim het han, nen mot trang co the tra ve it hon {@code pageSize} muc —
     * va nhung phim con han bi day sang trang sau khong bao gio duoc bu lai.
     */
    @Test
    @DisplayName("N-13: loc trong SQL nen trang van du so muc du co phim het han xen giua")
    public void paginationFillsEveryPageBecauseFilteringHappensInSql() throws SQLException {
        // CreatedAt dat tuong minh: ca hai duong deu ORDER BY CreatedAt DESC, nen phim het han
        // phai la phim MOI NHAT thi no moi roi vao trang 1 va tai hien duoc trieu chung.
        int expiredNewest = insertExpiredFilm("IT N-13 phim het han moi nhat", 0);
        int valid1 = insertValidFilm("IT N-13 phim con han A", 10);
        int valid2 = insertValidFilm("IT N-13 phim con han B", 20);
        try {
            long totalPublic = filmDAO.countSearchPublic("IT N-13", null);
            assertEquals(2, totalPublic, "chi 2 phim con han duoc tinh vao tong");

            List<Film> page1 = filmDAO.searchPublic("IT N-13", null, 0, 1);
            List<Film> page2 = filmDAO.searchPublic("IT N-13", null, 1, 1);

            assertEquals(1, page1.size(), "trang 1 phai du 1 muc, khong duoc rong vi phim het han");
            assertEquals(1, page2.size(), "trang 2 phai du 1 muc");
            assertFalse(containsFilm(page1, expiredNewest), "phim het han khong duoc chiem cho");
            assertFalse(containsFilm(page2, expiredNewest), "phim het han khong duoc chiem cho");

            // Doi chieu voi duong cu: loc SAU khi phan trang lam trang 1 rong hoan toan.
            List<Film> oldWayPage1 = com.mycompany.website.ban.ve.xem.phim.service
                    .FilmAvailabilityPolicy.publicOnly(filmDAO.search("IT N-13", 0, 1));
            assertEquals(0, oldWayPage1.size(),
                    "day la trieu chung N-13: phan trang truoc, loc sau -> trang 1 mat trang");
        } finally {
            exec("DELETE FROM Films WHERE Id IN (" + expiredNewest + ", " + valid1 + ", " + valid2 + ")");
        }
    }

    // ---------------------------------------------------------------- fixtures

    /** Servlet public: {@code controller/*.java} (tru {@code controller/admin}) va {@code api/v1}. */
    private static List<Path> publicSurfaces() throws IOException {
        Path base = Path.of("src/main/java/com/mycompany/website/ban/ve/xem/phim");
        List<Path> sources = new ArrayList<>();
        for (Path root : List.of(base.resolve("controller"), base.resolve("api/v1"))) {
            try (var paths = Files.walk(root)) {
                paths.filter(path -> path.toString().endsWith(".java"))
                        .filter(path -> !path.toString().replace('\\', '/').contains("/controller/admin/"))
                        .filter(path -> !path.toString().replace('\\', '/').contains("/controller/staff/"))
                        .forEach(sources::add);
            }
        }
        assertFalse(sources.isEmpty(), "khong tim thay file nguon nao de quet");
        return sources;
    }

    private static int lineOf(String code, int index) {
        return (int) code.substring(0, index).chars().filter(ch -> ch == '\n').count() + 1;
    }

    private static boolean containsFilm(List<Film> films, int filmId) {
        return films.stream().anyMatch(film -> film.getId() == filmId);
    }

    private static int insertExpiredFilm(String title) throws SQLException {
        return insertExpiredFilm(title, 0);
    }

    private static int insertExpiredFilm(String title, int createdMinutesAgo) throws SQLException {
        return insertReturningId(
                "INSERT INTO Films (Title, ReleaseDate, EndDate, DurationMinutes, Status, CreatedAt) "
                + "VALUES (N'" + title + "', DATEADD(DAY, -30, CAST(GETDATE() AS DATE)), "
                + "DATEADD(DAY, -1, CAST(GETDATE() AS DATE)), 120, 'showing', "
                + "DATEADD(MINUTE, -" + createdMinutesAgo + ", GETDATE()))");
    }

    private static int insertValidFilm(String title, int createdMinutesAgo) throws SQLException {
        return insertReturningId(
                "INSERT INTO Films (Title, ReleaseDate, EndDate, DurationMinutes, Status, CreatedAt) "
                + "VALUES (N'" + title + "', DATEADD(DAY, -5, CAST(GETDATE() AS DATE)), "
                + "DATEADD(DAY, 30, CAST(GETDATE() AS DATE)), 120, 'showing', "
                + "DATEADD(MINUTE, -" + createdMinutesAgo + ", GETDATE()))");
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

    private static void exec(String sql) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.executeUpdate();
        }
    }
}
