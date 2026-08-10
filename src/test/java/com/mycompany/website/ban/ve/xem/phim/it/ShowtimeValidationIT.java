package com.mycompany.website.ban.ve.xem.phim.it;

import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.model.Showtime;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiem tra quan he phim–rap–phong va vong doi phim khi xep suat chieu (ST-01, ST-02).
 *
 * <p>Moi ca deu goi thang tang service — day la diem mau chot: JavaScript o form co the loc
 * dropdown dep den may thi request van sua tay duoc, nen chot that phai nam o backend.</p>
 */
@Tag("it")
@DisplayName("Xep suat chieu — rang buoc phim/rap/phong va vong doi")
public class ShowtimeValidationIT {

    static {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
    }

    /** Rap 1 co Phong 1; Rap 2 co Phong 2 — theo seed_test_fixtures.sql. */
    private static final int CINEMA_A = 1;
    private static final int ROOM_IN_A = 1;
    private static final int ROOM_IN_B = 2;

    private static AdminService adminService;
    private static User admin;
    private static int filmId;

    @BeforeAll
    public static void setUp() throws SQLException {
        System.setProperty("cinebook.db.config", System.getProperty("cinebook.it.config", "target/db.it.properties"));
        DBConnection.shutdown();
        adminService = new AdminService();

        admin = new User();
        admin.setId(5);
        admin.setRole("admin");
        admin.setCinemaId(null);

        filmId = createFilm("Phim kiem thu xep suat",
                LocalDate.now().minusDays(10), LocalDate.now().plusDays(30));
        assignFilmToCinema(filmId, CINEMA_A);
    }

    @AfterAll
    public static void tearDown() throws SQLException {
        deleteFilmCascade(filmId);
        DBConnection.shutdown();
    }

    @AfterEach
    public void clearShowtimes() throws SQLException {
        exec("DELETE FROM ShowtimeSeats WHERE ShowtimeId IN (SELECT Id FROM Showtimes WHERE FilmId = " + filmId + ")");
        exec("DELETE FROM Showtimes WHERE FilmId = " + filmId);
    }

    private Showtime showtime(int cinemaId, int roomId, LocalDateTime start) {
        Showtime showtime = new Showtime();
        showtime.setFilmId(filmId);
        showtime.setCinemaId(cinemaId);
        showtime.setRoomId(roomId);
        showtime.setStartTime(start);
        showtime.setEndTime(start.plusMinutes(120));
        showtime.setBasePrice(new BigDecimal("90000"));
        showtime.setFormat("2D");
        showtime.setVersion("Phụ đề");
        showtime.setLanguage("Tiếng Việt");
        return showtime;
    }

    /** Moc gio an toan: xa hien tai, gio tron, de khong dinh buffer don phong cua ca khac. */
    private LocalDateTime slot(int daysAhead, int hour) {
        return LocalDate.now().plusDays(daysAhead).atTime(hour, 0);
    }

    @Test
    @DisplayName("ST-01: xep suat vao phong cua rap KHAC bi chan")
    public void roomFromAnotherCinemaIsRejected() {
        // Rap A nhung chon phong cua rap B — truoc day loi nay lot xuong tan DB.
        Showtime crossCinema = showtime(CINEMA_A, ROOM_IN_B, slot(3, 10));
        BookingException ex = assertThrows(BookingException.class,
                () -> adminService.saveShowtime(crossCinema, admin));
        assertEquals(400, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("không thuộc cụm rạp"),
                "Thong bao phai noi ro phong thuoc rap nao: " + ex.getMessage());
    }

    @Test
    @DisplayName("ST-01: phim chua duoc gan cho rap thi khong xep suat duoc, va thong bao chi ro cach sua")
    public void filmNotAssignedToCinemaIsRejected() throws SQLException {
        int orphanFilm = createFilm("Phim chua gan rap",
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(30));
        try {
            Showtime unassigned = showtime(CINEMA_A, ROOM_IN_A, slot(4, 10));
            unassigned.setFilmId(orphanFilm);

            BookingException ex = assertThrows(BookingException.class,
                    () -> adminService.saveShowtime(unassigned, admin));
            assertEquals(400, ex.getStatusCode());
            assertTrue(ex.getMessage().contains("chưa được gán cho cụm rạp"), ex.getMessage());
            // Day la trieu chung nguoi dung bao: "phim moi khong them duoc suat chieu".
            // Thong bao phai chi duong, khong chi bao that bai.
            assertTrue(ex.getMessage().contains("Phim chiếu"), ex.getMessage());
        } finally {
            deleteFilmCascade(orphanFilm);
        }
    }

    // ------------------------------------------------------------------------ N-07

    /**
     * ST-02 yeu cau "Validate start khong o qua khu theo DB time", nhung khong duong dan nao
     * so {@code showtime.getStartTime()} voi gio DB — {@code BusinessClock} chi xuat hien o
     * hai ham bao cao. Hau qua: admin xep suat cho HOM QUA thi suat luu thanh cong, sinh du
     * {@code ShowtimeSeats}, hien trong danh sach va lam sai ti le lap ghe cua bao cao.
     */
    @Test
    @DisplayName("N-07: xep suat cho HOM QUA bi chan va khong sinh dong nao")
    public void showtimeStartingInThePastIsRejected() throws SQLException {
        Showtime yesterday = showtime(CINEMA_A, ROOM_IN_A,
                LocalDate.now().minusDays(1).atTime(19, 0));

        BookingException ex = assertThrows(BookingException.class,
                () -> adminService.saveShowtime(yesterday, admin));
        assertEquals(400, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("quá khứ"), ex.getMessage());
        assertEquals(0, scalar("SELECT COUNT(*) FROM Showtimes WHERE FilmId = " + filmId),
                "suat qua khu khong duoc ghi vao DB");
    }

    @Test
    @DisplayName("N-07: moc qua khu duoc do bang GIO DB, khong phai dong ho JVM")
    public void pastCheckUsesDatabaseClockNotJvmClock() throws SQLException {
        // Day lui BusinessClock 10 ngay: mot suat "hom qua" theo lich JVM tro thanh tuong lai
        // theo gio nghiep vu, va phai duoc chap nhan. Neu kiem tra dung LocalDateTime.now()
        // thi ca nay se that bai — dung lop loi ma CLAUDE.md cam.
        // The shared seed contains relative showtimes around DB now. Select an
        // actually free slot instead of guessing an hour: the old now-12h
        // value overlapped the seeded "present" showtime for part of each day.
        LocalDateTime fixedBusinessNow = LocalDateTime.now().minusDays(10);
        LocalDateTime yesterdayByJvm = findFreePastJvmSlot(fixedBusinessNow);
        assertTrue(yesterdayByJvm.isBefore(LocalDateTime.now()));
        assertTrue(yesterdayByJvm.isAfter(fixedBusinessNow));
        com.mycompany.website.ban.ve.xem.phim.util.BusinessClock
                .useFixedTimeForTesting(fixedBusinessNow);
        try {
            Showtime showtime = showtime(CINEMA_A, ROOM_IN_A, yesterdayByJvm);
            int saved = adminService.saveShowtimes(List.of(showtime), admin);
            assertEquals(1, saved, "theo gio DB day la suat tuong lai nen phai luu duoc");
        } finally {
            com.mycompany.website.ban.ve.xem.phim.util.BusinessClock.resetForTesting();
        }
    }

    private LocalDateTime findFreePastJvmSlot(LocalDateTime businessNow) throws SQLException {
        LocalDateTime latestStart = LocalDateTime.now().minusHours(3)
                .withMinute(0).withSecond(0).withNano(0);
        LocalDateTime candidate = businessNow.plusDays(1)
                .withMinute(0).withSecond(0).withNano(0);
        String sql = """
                SELECT COUNT(*)
                FROM Showtimes
                WHERE RoomId=?
                  AND StartTime < DATEADD(MINUTE, 15, ?)
                  AND EndTime > DATEADD(MINUTE, -15, ?)
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            while (!candidate.isAfter(latestStart)) {
                ps.setInt(1, ROOM_IN_A);
                ps.setTimestamp(2, java.sql.Timestamp.valueOf(candidate.plusMinutes(120)));
                ps.setTimestamp(3, java.sql.Timestamp.valueOf(candidate));
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    if (rs.getInt(1) == 0) {
                        return candidate;
                    }
                }
                candidate = candidate.plusHours(3);
            }
        }
        throw new IllegalStateException("Khong tim thay moc qua khu trong de kiem tra BusinessClock");
    }

    // ------------------------------------------------------------------------ N-08

    /**
     * {@code repeatDays} truoc day chi bi chan phia duoi bang {@code Math.max(1, ...)}. Mot
     * POST {@code repeatDays=100000} tu tai khoan manager hop le sinh 100.000 suat va ~3,6
     * trieu dong {@code ShowtimeSeats} trong MOT transaction.
     */
    @Test
    @DisplayName("N-08: loat suat vuot tran bi chan o tang service, khong sinh dong nao")
    public void oversizedRepeatBatchIsRejectedByTheService() throws SQLException {
        int longRunFilm = createLongRunFilm("Phim lich lap vuot tran");
        try {
            List<Showtime> huge = repeatBatch(longRunFilm, AdminService.MAX_SHOWTIME_BATCH_SIZE + 1);

            BookingException ex = assertThrows(BookingException.class,
                    () -> adminService.saveShowtimes(huge, admin));
            assertEquals(400, ex.getStatusCode());
            assertTrue(ex.getMessage().contains(String.valueOf(AdminService.MAX_SHOWTIME_BATCH_SIZE)),
                    "thong bao phai noi ro tran: " + ex.getMessage());
            assertEquals(0, scalar("SELECT COUNT(*) FROM Showtimes WHERE FilmId = " + longRunFilm),
                    "vuot tran thi khong duoc tao suat nao");
        } finally {
            deleteFilmCascade(longRunFilm);
        }
    }

    @Test
    @DisplayName("N-08: loat dung bang tran van luu duoc binh thuong")
    public void batchExactlyAtTheLimitStillSaves() throws SQLException {
        int longRunFilm = createLongRunFilm("Phim lich lap dung tran");
        try {
            List<Showtime> atLimit = repeatBatch(longRunFilm, AdminService.MAX_SHOWTIME_BATCH_SIZE);

            assertEquals(AdminService.MAX_SHOWTIME_BATCH_SIZE, adminService.saveShowtimes(atLimit, admin));
            assertEquals(AdminService.MAX_SHOWTIME_BATCH_SIZE,
                    scalar("SELECT COUNT(*) FROM Showtimes WHERE FilmId = " + longRunFilm));
        } finally {
            deleteFilmCascade(longRunFilm);
        }
    }

    /** Phim co khoang chieu du dai de chua ca loat lap toi da. */
    private static int createLongRunFilm(String title) throws SQLException {
        int id = createFilm(title, LocalDate.now().minusDays(1), LocalDate.now().plusDays(120));
        assignFilmToCinema(id, CINEMA_A);
        return id;
    }

    /**
     * Loat suat lap theo ngay. Seed tao mot suat o ngay +30 tai chinh gio suite bat dau, nen
     * khong duoc dung mot gio co dinh: 22:00 se xung dot neu gate chay khoang 19:45-21:59.
     * Dich 12 gio so voi luc tao batch de luon cach xa suat seed va buffer don phong.
     */
    private List<Showtime> repeatBatch(int film, int days) throws SQLException {
        List<Showtime> batch = new java.util.ArrayList<>(days);
        LocalDateTime firstStart = findFreeBatchStart(Math.min(days, AdminService.MAX_SHOWTIME_BATCH_SIZE));
        for (int day = 1; day <= days; day++) {
            Showtime showtime = showtime(CINEMA_A, ROOM_IN_A, firstStart.plusDays(day - 1L));
            showtime.setFilmId(film);
            batch.add(showtime);
        }
        return batch;
    }

    private LocalDateTime findFreeBatchStart(int daysToCheck) throws SQLException {
        LocalDateTime candidate = LocalDate.now().plusDays(1).atStartOfDay();
        String sql = """
                SELECT COUNT(*)
                FROM Showtimes
                WHERE RoomId=?
                  AND StartTime < DATEADD(MINUTE, 15, ?)
                  AND EndTime > DATEADD(MINUTE, -15, ?)
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            // Eight three-hour windows cover a full day; allow a second day
            // as well so the helper remains stable with denser future fixtures.
            for (int attempt = 0; attempt < 16; attempt++) {
                boolean free = true;
                for (int day = 0; day < daysToCheck; day++) {
                    LocalDateTime start = candidate.plusDays(day);
                    ps.setInt(1, ROOM_IN_A);
                    ps.setTimestamp(2, java.sql.Timestamp.valueOf(start.plusMinutes(120)));
                    ps.setTimestamp(3, java.sql.Timestamp.valueOf(start));
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        if (rs.getInt(1) != 0) {
                            free = false;
                            break;
                        }
                    }
                }
                if (free) {
                    return candidate;
                }
                candidate = candidate.plusHours(3);
            }
        }
        throw new IllegalStateException("Khong tim thay khung gio trong cho loat suat kiem thu");
    }

    @Test
    @DisplayName("ST-01: gia ve phai duong")
    public void nonPositivePriceIsRejected() {
        Showtime freeShow = showtime(CINEMA_A, ROOM_IN_A, slot(5, 10));
        freeShow.setBasePrice(BigDecimal.ZERO);
        BookingException ex = assertThrows(BookingException.class,
                () -> adminService.saveShowtime(freeShow, admin));
        assertEquals(400, ex.getStatusCode());
    }

    @Test
    @DisplayName("ST-01: dinh dang chieu ngoai danh sach cho phep bi chan")
    public void unknownFormatIsRejected() {
        Showtime weird = showtime(CINEMA_A, ROOM_IN_A, slot(6, 10));
        weird.setFormat("HOLOGRAM");
        BookingException ex = assertThrows(BookingException.class,
                () -> adminService.saveShowtime(weird, admin));
        assertEquals(400, ex.getStatusCode());
    }

    @Test
    @DisplayName("EX-01: khong xep duoc suat sau ngay ket thuc chieu cua phim")
    public void showtimeAfterFilmEndDateIsRejected() throws SQLException {
        int shortRunFilm = createFilm("Phim chieu ngan",
                LocalDate.now().minusDays(2), LocalDate.now().plusDays(1));
        assignFilmToCinema(shortRunFilm, CINEMA_A);
        try {
            Showtime tooLate = showtime(CINEMA_A, ROOM_IN_A, slot(10, 10));
            tooLate.setFilmId(shortRunFilm);
            BookingException ex = assertThrows(BookingException.class,
                    () -> adminService.saveShowtime(tooLate, admin));
            assertEquals(400, ex.getStatusCode());
            assertTrue(ex.getMessage().contains("ngày kết thúc chiếu"), ex.getMessage());
        } finally {
            deleteFilmCascade(shortRunFilm);
        }
    }

    @Test
    @DisplayName("EX-01: khong xep duoc suat truoc ngay khoi chieu")
    public void showtimeBeforeReleaseDateIsRejected() throws SQLException {
        int futureFilm = createFilm("Phim chua khoi chieu",
                LocalDate.now().plusDays(20), LocalDate.now().plusDays(60));
        assignFilmToCinema(futureFilm, CINEMA_A);
        try {
            Showtime tooEarly = showtime(CINEMA_A, ROOM_IN_A, slot(3, 10));
            tooEarly.setFilmId(futureFilm);
            BookingException ex = assertThrows(BookingException.class,
                    () -> adminService.saveShowtime(tooEarly, admin));
            assertEquals(400, ex.getStatusCode());
            assertTrue(ex.getMessage().contains("ngày khởi chiếu"), ex.getMessage());
        } finally {
            deleteFilmCascade(futureFilm);
        }
    }

    @Test
    @DisplayName("Suat chieu hop le luu duoc va sinh du ghe")
    public void validShowtimeIsSaved() throws SQLException {
        Showtime valid = showtime(CINEMA_A, ROOM_IN_A, slot(7, 10));
        adminService.saveShowtime(valid, admin);
        assertTrue(valid.getId() > 0, "Phai co Id sau khi luu");
        assertEquals(1, countShowtimes(), "Phai co dung mot suat chieu");
    }

    @Test
    @DisplayName("ST-02: lap lich N ngay chay ATOMIC — mot ngay vuong thi khong ngay nao duoc tao")
    public void repeatScheduleIsAtomic() throws SQLException {
        // Ngay 1 hop le. Chen san mot suat trung gio o ngay 2 de ca loat that bai.
        LocalDateTime day1 = slot(8, 14);
        Showtime blocker = showtime(CINEMA_A, ROOM_IN_A, day1.plusDays(1));
        adminService.saveShowtime(blocker, admin);
        int afterBlocker = countShowtimes();
        assertEquals(1, afterBlocker);

        List<Showtime> batch = List.of(
                showtime(CINEMA_A, ROOM_IN_A, day1),
                showtime(CINEMA_A, ROOM_IN_A, day1.plusDays(1)),   // trung voi blocker
                showtime(CINEMA_A, ROOM_IN_A, day1.plusDays(2)));

        assertThrows(BookingException.class, () -> adminService.saveShowtimes(batch, admin));

        // Diem mau chot: ngay 1 KHONG duoc nam lai trong DB.
        // Ban cu goi saveShowtime() N lan nen ngay 1 da commit truoc khi ngay 2 that bai.
        assertEquals(afterBlocker, countShowtimes(),
                "Lap lich that bai phai rollback toan bo, khong duoc de lai suat da tao mot phan");
    }

    @Test
    @DisplayName("ST-02: lap lich N ngay thanh cong tao dung N suat")
    public void repeatScheduleCreatesAllDays() throws SQLException {
        LocalDateTime start = slot(15, 9);
        List<Showtime> batch = List.of(
                showtime(CINEMA_A, ROOM_IN_A, start),
                showtime(CINEMA_A, ROOM_IN_A, start.plusDays(1)),
                showtime(CINEMA_A, ROOM_IN_A, start.plusDays(2)));

        assertEquals(3, adminService.saveShowtimes(batch, admin));
        assertEquals(3, countShowtimes());
    }

    @Test
    @DisplayName("ST-02: ca qua nua dem co EndTime sang ngay hom sau, khong bi truoc StartTime")
    public void overnightShowtimeEndsNextDay() throws SQLException {
        // 23:30 + 120 phut = 01:30 ngay hom sau. Ban cu tinh modulo 24 gio nhung giu nguyen
        // ngay, nen EndTime roi ve 01:30 CUNG ngay — tuc la truoc StartTime.
        LocalDateTime lateNight = slot(9, 23).plusMinutes(30);
        Showtime overnight = showtime(CINEMA_A, ROOM_IN_A, lateNight);
        overnight.setEndTime(lateNight.plusMinutes(120));
        adminService.saveShowtime(overnight, admin);

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT StartTime, EndTime FROM Showtimes WHERE Id = ?")) {
            ps.setInt(1, overnight.getId());
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                LocalDateTime start = rs.getTimestamp("StartTime").toLocalDateTime();
                LocalDateTime end = rs.getTimestamp("EndTime").toLocalDateTime();
                assertTrue(end.isAfter(start), "EndTime (" + end + ") phai sau StartTime (" + start + ")");
                assertEquals(start.toLocalDate().plusDays(1), end.toLocalDate(),
                        "Ca qua nua dem phai ket thuc vao ngay hom sau");
            }
        }
    }

    // ---------------------------------------------------------------- fixtures

    private static int createFilm(String title, LocalDate release, LocalDate end) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO Films (Title, ReleaseDate, EndDate, DurationMinutes, Status) "
                     + "VALUES (?, ?, ?, 120, 'showing')",
                     PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, title);
            ps.setDate(2, Date.valueOf(release));
            ps.setDate(3, end == null ? null : Date.valueOf(end));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    private static void assignFilmToCinema(int film, int cinema) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "IF NOT EXISTS (SELECT 1 FROM CinemaFilms WHERE CinemaId=? AND FilmId=?) "
                     + "INSERT INTO CinemaFilms (CinemaId, FilmId) VALUES (?, ?)")) {
            ps.setInt(1, cinema);
            ps.setInt(2, film);
            ps.setInt(3, cinema);
            ps.setInt(4, film);
            ps.executeUpdate();
        }
    }

    private static void deleteFilmCascade(int film) throws SQLException {
        exec("DELETE FROM ShowtimeSeats WHERE ShowtimeId IN (SELECT Id FROM Showtimes WHERE FilmId = " + film + ")");
        exec("DELETE FROM Showtimes WHERE FilmId = " + film);
        exec("DELETE FROM CinemaFilms WHERE FilmId = " + film);
        exec("DELETE FROM Films WHERE Id = " + film);
    }

    private static void exec(String sql) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.executeUpdate();
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

    private int countShowtimes() throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT COUNT(*) FROM Showtimes WHERE FilmId = ?")) {
            ps.setInt(1, filmId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
