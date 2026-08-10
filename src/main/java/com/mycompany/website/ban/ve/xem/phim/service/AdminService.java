package com.mycompany.website.ban.ve.xem.phim.service;

import com.mycompany.website.ban.ve.xem.phim.config.AppConstants;
import com.mycompany.website.ban.ve.xem.phim.config.DBConnection;
import com.mycompany.website.ban.ve.xem.phim.config.SecuritySettings;
import com.mycompany.website.ban.ve.xem.phim.filter.HeaderDataFilter;
import com.mycompany.website.ban.ve.xem.phim.dao.OrderDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcOrderDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.AdminNotificationDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.NotificationRecipientDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.UserNotificationDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcAdminNotificationDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcNotificationRecipientDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcUserNotificationDAO;
import com.mycompany.website.ban.ve.xem.phim.model.AdminNotification;
import com.mycompany.website.ban.ve.xem.phim.model.AuditLogEntry;
import com.mycompany.website.ban.ve.xem.phim.model.Cinema;
import com.mycompany.website.ban.ve.xem.phim.model.ComboFood;
import com.mycompany.website.ban.ve.xem.phim.model.Film;
import com.mycompany.website.ban.ve.xem.phim.model.FilmComment;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.model.OrderSeatItem;
import com.mycompany.website.ban.ve.xem.phim.model.OrderComboItem;
import com.mycompany.website.ban.ve.xem.phim.model.PageResult;
import com.mycompany.website.ban.ve.xem.phim.model.MembershipTier;
import com.mycompany.website.ban.ve.xem.phim.model.Promotion;
import com.mycompany.website.ban.ve.xem.phim.model.ReportSummaryDto;
import com.mycompany.website.ban.ve.xem.phim.model.RevenueRow;
import com.mycompany.website.ban.ve.xem.phim.model.Room;
import com.mycompany.website.ban.ve.xem.phim.model.Seat;
import com.mycompany.website.ban.ve.xem.phim.model.Showtime;
import com.mycompany.website.ban.ve.xem.phim.model.SystemSetting;
import com.mycompany.website.ban.ve.xem.phim.model.TopFilmRow;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.dao.UserDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcUserDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.UserAppealDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcUserAppealDAO;
import com.mycompany.website.ban.ve.xem.phim.model.UserAppeal;
import com.mycompany.website.ban.ve.xem.phim.model.UserNotification;
import com.mycompany.website.ban.ve.xem.phim.util.BusinessClock;
import com.mycompany.website.ban.ve.xem.phim.util.PasswordPolicy;
import com.mycompany.website.ban.ve.xem.phim.util.QrCodeUtil;
import com.mycompany.website.ban.ve.xem.phim.util.RequestContext;
import com.mycompany.website.ban.ve.xem.phim.util.PasswordUtil;
import com.mycompany.website.ban.ve.xem.phim.util.ScopeUtil;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AdminService {
    public static final int ORDER_PAGE_QUERY_COUNT = 4;
    public static final int AUDIT_PAGE_QUERY_COUNT = 2;
    private static final Logger LOGGER = Logger.getLogger(AdminService.class.getName());
    private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    /** Dinh dang suat chieu trong thong bao loi gui cho manager. */
    private static final DateTimeFormatter SHOWTIME_STAMP = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
    /** Tab vong doi mac dinh cua phong/rap trong trang quan tri. */
    public static final String LIFECYCLE_ACTIVE = "active";
    /** Tab "Đã bị xóa" — phan da xoa mem, giu lai de doi chieu doanh thu (D-03). */
    public static final String LIFECYCLE_DELETED = "deleted";

    private final OrderDAO orderDAO = new JdbcOrderDAO();
    private final AdminNotificationDAO notificationDAO = new JdbcAdminNotificationDAO();
    private final NotificationRecipientDAO recipientDAO = new JdbcNotificationRecipientDAO();
    private final UserNotificationDAO userNotificationDAO = new JdbcUserNotificationDAO();
    private final UserDAO userDAO = new JdbcUserDAO();
    private final UserAppealDAO userAppealDAO;

    public AdminService() {
        this(new JdbcUserAppealDAO());
    }

    /**
     * Chi dung cho test: thay DAO khang cao de kiem tra hanh vi khi hang doi khong
     * doc duoc.
     */
    AdminService(UserAppealDAO userAppealDAO) {
        this.userAppealDAO = userAppealDAO;
    }

    /**
     * Chuan hoa tab vong doi thanh mot dieu kien SQL cho bang co cot {@code Status}.
     *
     * <p>Gia tri la nhan bang trong ma nguon chu khong phai tham so nguoi dung ghep thang vao SQL:
     * moi dau vao khong khop {@code deleted} deu roi ve tab {@code active}.</p>
     */
    static String lifecyclePredicate(String alias, String lifecycleTab) {
        String column = "ISNULL(" + alias + ".Status, 'active')";
        return LIFECYCLE_DELETED.equalsIgnoreCase(lifecycleTab == null ? "" : lifecycleTab.trim())
                ? column + " = 'deleted'"
                : column + " <> 'deleted'";
    }

    /** Chuan hoa tham so {@code lifecycle} tu request ve mot trong hai tab hop le. */
    public static String normalizeLifecycleTab(String raw) {
        return LIFECYCLE_DELETED.equalsIgnoreCase(raw == null ? "" : raw.trim())
                ? LIFECYCLE_DELETED : LIFECYCLE_ACTIVE;
    }

    public List<Film> listFilms() {
        String sql = "SELECT TOP (200) * FROM Films WHERE DeletedAt IS NULL ORDER BY CreatedAt DESC";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            List<Film> films = new ArrayList<>();
            while (rs.next()) {
                films.add(mapFilm(rs, connection));
            }
            return films;
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the tai danh sach phim.", ex);
        }
    }

    public List<Film> listFilms(User actor) {
        if (!ScopeUtil.isManager(actor))
            return listFilms();
        String sql = """
                SELECT f.*
                FROM Films f
                WHERE f.DeletedAt IS NULL AND EXISTS (
                  SELECT 1 FROM CinemaFilms cf
                  WHERE cf.FilmId = f.Id AND cf.CinemaId = ? AND cf.Status=N'active'
                )
                ORDER BY f.CreatedAt DESC
                """;
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, requireActorCinema(actor));
            try (ResultSet rs = ps.executeQuery()) {
                List<Film> films = new ArrayList<>();
                while (rs.next()) {
                    films.add(mapFilm(rs, connection));
                }
                return films;
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the tai danh sach phim.", ex);
        }
    }

    /** Admin film tabs use lifecycle predicates instead of mixing expired rows into active work. */
    public List<Film> listFilms(User actor, String lifecycleTab) {
        String tab = lifecycleTab == null ? "active" : lifecycleTab.trim().toLowerCase();
        if (!Set.of("active", "archive", "deleted").contains(tab)) {
            tab = "active";
        }
        if ("deleted".equals(tab) && ScopeUtil.isManager(actor)) {
            return List.of();
        }
        String lifecycle = switch (tab) {
            case "archive" -> "f.DeletedAt IS NULL AND (LOWER(ISNULL(f.Status,'showing'))='ended' "
                    + "OR f.EndDate < CAST(GETDATE() AS date))";
            case "deleted" -> "f.DeletedAt IS NOT NULL";
            default -> "f.DeletedAt IS NULL AND LOWER(ISNULL(f.Status,'showing'))<>'ended' "
                    + "AND f.EndDate >= CAST(GETDATE() AS date)";
        };
        StringBuilder sql = new StringBuilder("SELECT TOP (500) f.* FROM Films f WHERE ")
                .append(lifecycle);
        boolean manager = ScopeUtil.isManager(actor);
        if (manager) {
            sql.append(" AND EXISTS (SELECT 1 FROM CinemaFilms cf WHERE cf.FilmId=f.Id AND cf.CinemaId=? AND cf.Status=N'active')");
        }
        sql.append(" ORDER BY f.CreatedAt DESC");
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            if (manager) {
                ps.setInt(1, requireActorCinema(actor));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Film> films = new ArrayList<>();
                while (rs.next()) {
                    films.add(mapFilm(rs, connection));
                }
                return films;
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể tải danh sách phim theo vòng đời.", ex);
        }
    }

    public Optional<Film> findFilmById(int filmId) {
        String sql = "SELECT * FROM Films WHERE Id = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, filmId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapFilm(rs, connection));
                }
            }
            return Optional.empty();
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the tai thong tin phim.", ex);
        }
    }

    public Optional<Film> findFilmById(int filmId, User actor) {
        if (ScopeUtil.isManager(actor))
            assertFilmScope(actor, filmId);
        return findFilmById(filmId);
    }

    public void saveFilm(Film film, User actor) {
        saveFilm(film, actor, null);
    }

    public void saveFilm(FilmFormCommand command, User actor) {
        if (command == null) {
            throw new BookingException(400, "Dữ liệu phim không hợp lệ.");
        }
        saveFilm(command.film(), actor, command.cinemaIds());
    }

    /**
     * Luu phim, dong thoi gan phim vao cac cum rap trong <b>cung mot
     * transaction</b> (ST-01).
     *
     * <p>
     * <b>Van de goc.</b> Khi manager tao phim, code tu gan phim vao rap cua
     * manager. Khi
     * <i>admin</i> tao phim thi khong tao dong {@code CinemaFilms} nao ca. Phim moi
     * vi vay khong
     * thuoc rap nao, nen o man hinh them suat chieu no khong xuat hien trong danh
     * sach chon —
     * dung trieu chung "phim moi khong them duoc suat chieu" ma nguoi dung bao.
     * Admin phai nho
     * sang mot man hinh khac gan rap thu cong, khong co gi nhac.
     * </p>
     *
     * <p>
     * <b>Cach sua.</b> Form them phim cua admin bay gio gui kem danh sach rap. Tao
     * phim va
     * gan rap nam trong mot transaction: hoac co ca hai, hoac khong co gi. Khong
     * con trang thai
     * lung chung "phim ton tai nhung khong thuoc rap nao" tru khi admin co y chon
     * nhu vay.
     * </p>
     *
     * @param cinemaIds rap duoc gan; {@code null} nghia la khong dong toi mapping
     *                  hien co
     *                  (dung khi sua cac truong khac cua phim)
     */
    public void saveFilm(Film film, User actor, List<Integer> cinemaIds) {
        CinemaCapabilityPolicy.requireAdmin(actor);
        if (film.getId() > 0)
            assertFilmScope(actor, film.getId());
        boolean creating = film.getId() <= 0;
        normalizeFilmFields(film);
        validateFilmInput(film, creating);
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (!creating) {
                    enforceFilmEditInvariants(connection, film);
                }
                if (!creating) {
                    String sql = """
                            UPDATE Films
                            SET Title = ?, OtherTitles = ?, Actors = ?, Directors = ?, Rating = ?, ReleaseDate = ?,
                                DurationMinutes = ?, AgeRating = ?, TrailerUrl = ?, Thumbnail = ?, Language = ?,
                                Subtitles = ?, Description = ?, Country = ?, Format = ?, Status = ?, Banner = ?,
                                EndDate = ?, UpdatedAt = GETDATE()
                            WHERE Id = ?
                            """;
                    try (PreparedStatement ps = connection.prepareStatement(sql)) {
                        bindFilm(ps, film, false);
                        ps.setInt(19, film.getId());
                        ps.executeUpdate();
                    }
                } else {
                    String sql = """
                            INSERT INTO Films (
                                Title, OtherTitles, Actors, Directors, Rating, ReleaseDate, DurationMinutes,
                                AgeRating, TrailerUrl, Thumbnail, Language, Subtitles, Description, Country,
                                Format, Status, Banner, EndDate
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """;
                    try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                        bindFilm(ps, film, true);
                        ps.executeUpdate();
                        try (ResultSet keys = ps.getGeneratedKeys()) {
                            if (keys.next()) {
                                film.setId(keys.getInt(1));
                            }
                        }
                    }
                }

                List<Integer> targetCinemas =
                        resolveFilmCinemaTargets(connection, actor, cinemaIds, creating, film.getId());
                if (targetCinemas != null) {
                    replaceFilmCinemas(connection, film.getId(), targetCinemas, actor.getId());
                }
                replaceFilmCategories(connection, film.getId(), film.getCategories());

                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể lưu phim.", ex);
        }
        auditAfterCommit(actor.getId(), creating ? "CREATE_FILM" : "UPDATE_FILM",
                "Film", String.valueOf(film.getId()), film.getTitle());
        invalidateCatalogCaches();
    }

    /**
     * Rap se duoc gan cho phim, hoac {@code null} khi khong dong toi mapping.
     *
     * <p>
     * Manager luon bi ep ve dung rap cua minh — khong the tao phim cho rap khac ke
     * ca khi
     * form gui len id khac.
     * </p>
     *
     * <p>
     * <b>Loi da sua (N-01).</b> Ban cu tra ve dung MOT phan tu la rap cua manager, roi
     * {@link #replaceFilmCinemas} chay {@code DELETE FROM CinemaFilms WHERE FilmId = ?}
     * va chen lai mot dong. Voi phim dung chung nhieu rap, manager cua rap 7 chi sua mo
     * ta phim la mapping cua rap 8 bi xoa sach: rap 8 mat phim khoi trang rap, khong the
     * them suat moi, va khong co audit nao ghi lai viec do. Nay manager chi TOGGLE lien
     * ket cua chinh rap minh; mapping cua rap khac duoc doc ra va giu nguyen.
     * </p>
     */
    private List<Integer> resolveFilmCinemaTargets(Connection connection, User actor, List<Integer> requested,
            boolean creating, int filmId) throws SQLException {
        if (ScopeUtil.isManager(actor)) {
            int ownCinema = requireActorCinema(actor);
            if (creating) {
                // Phim moi chua co mapping nao, va manager khong duoc tao phim cho rap khac.
                return List.of(ownCinema);
            }
            if (requested == null) {
                // Form khong gui truong rap: khong the hien y dinh nao, khong dong toi mapping.
                return null;
            }
            List<Integer> merged = new ArrayList<>(findFilmCinemaIds(connection, filmId));
            merged.removeIf(id -> id == ownCinema);
            if (requested.contains(ownCinema)) {
                merged.add(ownCinema);
            }
            return merged;
        }
        if (requested != null) {
            return requested.stream().filter(id -> id != null && id > 0).distinct().toList();
        }
        // Admin sua phim ma form khong gui danh sach rap: giu nguyen mapping dang co.
        return creating ? List.of() : null;
    }

    /** Cac rap dang duoc gan cho phim, doc trong chinh connection dang giu transaction. */
    private List<Integer> findFilmCinemaIds(Connection connection, int filmId) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT CinemaId FROM CinemaFilms WHERE FilmId = ? AND Status=N'active'")) {
            ps.setInt(1, filmId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
            }
        }
        return ids;
    }

    /** Ghi de mapping phim–rap trong cung connection dang mo transaction. */
    private void replaceFilmCinemas(Connection connection, int filmId, List<Integer> cinemaIds,
            Integer actorId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                UPDATE CinemaFilms SET Status=N'inactive',UnassignedAt=SYSDATETIME(),
                    UnassignedByUserId=?
                WHERE FilmId=? AND Status=N'active'
                """)) {
            if (actorId == null) {
                ps.setNull(1, Types.INTEGER);
            } else {
                ps.setInt(1, actorId);
            }
            ps.setInt(2, filmId);
            ps.executeUpdate();
        }
        if (cinemaIds.isEmpty()) return;
        try (PreparedStatement update = connection.prepareStatement("""
                UPDATE CinemaFilms SET Status=N'active',AssignedAt=SYSDATETIME(),
                    AssignedByUserId=?,UnassignedAt=NULL,UnassignedByUserId=NULL
                WHERE CinemaId=? AND FilmId=?
                """)) {
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO CinemaFilms(CinemaId,FilmId,Status,AssignedAt,AssignedByUserId)
                    VALUES(?,?,N'active',SYSDATETIME(),?)
                    """)) {
                for (Integer cinemaId : cinemaIds.stream().distinct().toList()) {
                    if (actorId == null) {
                        update.setNull(1, Types.INTEGER);
                    } else {
                        update.setInt(1, actorId);
                    }
                    update.setInt(2, cinemaId);
                    update.setInt(3, filmId);
                    if (update.executeUpdate() == 0) {
                        insert.setInt(1, cinemaId);
                        insert.setInt(2, filmId);
                        if (actorId == null) {
                            insert.setNull(3, Types.INTEGER);
                        } else {
                            insert.setInt(3, actorId);
                        }
                        insert.executeUpdate();
                    }
                }
            }
        }
    }

    /**
     * Kiem tra vong doi ngay chieu (EX-01).
     *
     * <p>
     * Rut ngan {@code EndDate} ve truoc mot suat chieu da xep se tao ra suat nam
     * ngoai thoi
     * gian chieu cua chinh phim do — chan tai day va noi ro suat nao, de manager
     * biet phai huy
     * cai gi truoc.
     * </p>
     */
    private void validateFilmInput(Film film, boolean creating) {
        if (film == null) {
            throw new BookingException(400, "Dữ liệu phim không hợp lệ.");
        }
        requireFilmText(film.getTitle(), "Tiêu đề phim", 255);
        requireFilmText(film.getActors(), "Diễn viên", 500);
        requireFilmText(film.getDirectors(), "Đạo diễn", 255);
        requireFilmText(film.getCategories(), "Thể loại", 500);
        requireFilmText(film.getCountry(), "Quốc gia", 100);
        requireFilmText(film.getDescription(), "Mô tả", 4000);
        requireFilmText(film.getLanguage(), "Ngôn ngữ", 50);
        requireFilmText(film.getFormat(), "Định dạng", 50);
        requireFilmText(film.getAgeRating(), "Phân loại độ tuổi", 10);
        requireFilmText(film.getRawStatus(), "Trạng thái", 20);
        requireFilmText(film.getThumbnail(), "Ảnh poster", 255);
        requireFilmText(film.getBanner(), "Ảnh banner", 255);
        requireFilmText(film.getTrailerUrl(), "Trailer", 255);
        if (film.getDurationMinutes() == null || film.getDurationMinutes() <= 0) {
            throw new BookingException(400, "Thời lượng phim phải lớn hơn 0 phút.");
        }
        if (!Set.of("P", "K", "T13", "T16", "T18").contains(film.getAgeRating())) {
            throw new BookingException(400, "Phân loại độ tuổi không hợp lệ.");
        }
        if (!Set.of("2D", "3D", "IMAX 2D", "IMAX 3D", "4DX").contains(film.getFormat())) {
            throw new BookingException(400, "Định dạng phim không hợp lệ.");
        }
        if (!Set.of("showing", "coming", "ended").contains(film.getRawStatus().toLowerCase())) {
            throw new BookingException(400, "Trạng thái phim không hợp lệ.");
        }
        validateFilmUrl(film.getTrailerUrl());
        if (!creating && film.getReleaseDate() == null) {
            throw new BookingException(400, "Ngày khởi chiếu không được để trống khi sửa phim.");
        }
        validateFilmDates(film, creating);
    }

    private void requireFilmText(String value, String label, int maxLength) {
        if (value == null || value.trim().isBlank()) {
            throw new BookingException(400, label + " là bắt buộc.");
        }
        if (value.trim().length() > maxLength) {
            throw new BookingException(400, label + " không được dài quá " + maxLength + " ký tự.");
        }
    }

    private void normalizeFilmFields(Film film) {
        if (film == null) {
            return;
        }
        film.setTitle(trim(film.getTitle()));
        film.setOtherTitles(trim(film.getOtherTitles()));
        film.setActors(trim(film.getActors()));
        film.setDirectors(trim(film.getDirectors()));
        film.setCategories(trim(film.getCategories()));
        film.setCountry(trim(film.getCountry()));
        film.setDescription(trim(film.getDescription()));
        film.setLanguage(trim(film.getLanguage()));
        film.setFormat(trim(film.getFormat()));
        String ageRating = trim(film.getAgeRating());
        String rawStatus = trim(film.getRawStatus());
        film.setAgeRating(ageRating == null ? null : ageRating.toUpperCase());
        film.setStatus(rawStatus == null ? null : rawStatus.toLowerCase());
        film.setTrailerUrl(trim(film.getTrailerUrl()));
        film.setThumbnail(trim(film.getThumbnail()));
        film.setBanner(trim(film.getBanner()));
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private void validateFilmUrl(String value) {
        try {
            java.net.URI uri = new java.net.URI(value);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null) {
                throw new IllegalArgumentException();
            }
        } catch (java.net.URISyntaxException | IllegalArgumentException ex) {
            throw new BookingException(400, "Trailer URL không hợp lệ.");
        }
    }

    private void validateFilmDates(Film film) {
        validateFilmDates(film, false);
    }

    private void validateFilmDates(Film film, boolean creating) {
        validateFilmDates(film, creating, null);
    }

    private void validateFilmDates(Film film, boolean creating, Connection transactionConnection) {
        LocalDate release = film.getReleaseDate();
        LocalDate end = film.getEndDate();
        if (release == null || end == null) {
            throw new BookingException(400, "Phim bắt buộc phải có ngày khởi chiếu và ngày kết thúc.");
        }
        if (creating && release.isBefore(BusinessClock.now().toLocalDate())) {
            throw new BookingException(400, "Ngày khởi chiếu của phim mới không được ở trong quá khứ.");
        }
        if (end.isBefore(release)) {
            throw new BookingException(400,
                    "Ngày kết thúc chiếu (" + end + ") không được trước ngày khởi chiếu (" + release + ").");
        }
        if (!creating && film.getId() > 0) {
            String conflict = transactionConnection == null
                    ? findShowtimeOutsideFilmWindow(film.getId(), release, end)
                    : findShowtimeOutsideFilmWindow(transactionConnection, film.getId(), release, end);
            if (conflict != null) {
                throw new BookingException(409,
                        "Không thể thu hẹp thời gian chiếu: vẫn còn suất chiếu nằm ngoài khoảng "
                                + release + " → " + end + " (" + conflict + "). "
                                + "Hãy hủy hoặc dời các suất đó trước.");
            }
        }
    }

    /**
     * Mo ta suat chieu dau tien nam ngoai khoang ngay chieu, hoac {@code null} neu
     * khong co.
     */
    private void enforceFilmEditInvariants(Connection connection, Film incoming) throws SQLException {
        String sql = "SELECT ReleaseDate, EndDate FROM Films WITH (UPDLOCK, HOLDLOCK) WHERE Id=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, incoming.getId());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new BookingException(404, "Không tìm thấy phim.");
                }
                Date oldRelease = rs.getDate("ReleaseDate");
                Date oldEnd = rs.getDate("EndDate");
                LocalDate oldReleaseDate = oldRelease == null ? null : oldRelease.toLocalDate();
                LocalDate oldEndDate = oldEnd == null ? null : oldEnd.toLocalDate();
                if (oldReleaseDate != null && !oldReleaseDate.equals(incoming.getReleaseDate())) {
                    throw new BookingException(409, "Ngày khởi chiếu không được thay đổi sau khi phim đã tạo.");
                }
                if (oldEndDate != null && incoming.getEndDate().isBefore(oldEndDate)) {
                    throw new BookingException(409, "Ngày kết thúc chỉ được giữ nguyên hoặc gia hạn, không được rút ngắn.");
                }
                validateFilmDates(incoming, false, connection);
            }
        }
    }

    private void replaceFilmCategories(Connection connection, int filmId, String categories) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM FilmCategories WHERE FilmId=?")) {
            delete.setInt(1, filmId);
            delete.executeUpdate();
        }
        if (categories == null || categories.isBlank()) {
            return;
        }
        Set<String> unique = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String token : categories.split(",")) {
            String title = token.trim();
            if (!title.isBlank()) {
                unique.add(title);
            }
        }
        for (String title : unique) {
            int categoryId = 0;
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT Id FROM Categories WHERE LOWER(LTRIM(RTRIM(Title)))=LOWER(LTRIM(RTRIM(?)))")) {
                ps.setString(1, title);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        categoryId = rs.getInt(1);
                    }
                }
            }
            if (categoryId == 0) {
                try (PreparedStatement ps = connection.prepareStatement("INSERT INTO Categories(Title) VALUES(?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, title);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("Không đọc được mã thể loại mới");
                        }
                        categoryId = keys.getInt(1);
                    }
                }
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO FilmCategories(FilmId,CategoryId) VALUES(?,?)")) {
                ps.setInt(1, filmId);
                ps.setInt(2, categoryId);
                ps.executeUpdate();
            }
        }
    }

    private String findShowtimeOutsideFilmWindow(Connection connection, int filmId, LocalDate release,
            LocalDate end) {
        String sql = """
                SELECT TOP (1) s.StartTime, r.Name AS RoomName, c.Name AS CinemaName
                FROM Showtimes s
                JOIN Rooms r ON r.Id = s.RoomId
                JOIN Cinemas c ON c.Id = s.CinemaId
                WHERE s.FilmId = ?
                  AND (CAST(s.StartTime AS DATE) < ? OR CAST(s.StartTime AS DATE) > ?)
                ORDER BY s.StartTime
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, filmId);
            ps.setDate(2, Date.valueOf(release));
            ps.setDate(3, Date.valueOf(end));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    LocalDateTime start = toLocalDateTime(rs.getTimestamp("StartTime"));
                    return "suáº¥t " + (start == null ? "?" : start.format(SHOWTIME_STAMP))
                            + " táº¡i " + rs.getString("RoomName") + " â€” " + rs.getString("CinemaName");
                }
            }
            return null;
        } catch (SQLException ex) {
            throw new BookingException(500, "KhÃ´ng thá»ƒ kiá»ƒm tra suáº¥t chiáº¿u cá»§a phim.", ex);
        }
    }

    private String findShowtimeOutsideFilmWindow(int filmId, LocalDate release, LocalDate end) {
        String sql = """
                SELECT TOP (1) s.StartTime, r.Name AS RoomName, c.Name AS CinemaName
                FROM Showtimes s
                JOIN Rooms r ON r.Id = s.RoomId
                JOIN Cinemas c ON c.Id = s.CinemaId
                WHERE s.FilmId = ?
                  AND (CAST(s.StartTime AS DATE) < ? OR CAST(s.StartTime AS DATE) > ?)
                ORDER BY s.StartTime
                """;
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, filmId);
            ps.setDate(2, Date.valueOf(release));
            ps.setDate(3, Date.valueOf(end));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    LocalDateTime start = toLocalDateTime(rs.getTimestamp("StartTime"));
                    return "suất " + (start == null ? "?" : start.format(SHOWTIME_STAMP))
                            + " tại " + rs.getString("RoomName") + " — " + rs.getString("CinemaName");
                }
            }
            return null;
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể kiểm tra suất chiếu của phim.", ex);
        }
    }

    /**
     * Anh huong cua viec xoa mot phim — dung de bao truoc cho quan tri vien
     * (FL-01).
     *
     * @param filmId phim can kiem tra
     * @return so luong tung loai phu thuoc; {@code title} la ten phim
     */
    public Map<String, Object> getFilmDeleteImpactInfo(int filmId) {
        try (Connection connection = DBConnection.getConnection()) {
            return loadFilmDeleteImpact(connection, filmId, false);
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể kiểm tra dữ liệu liên quan của phim.", ex);
        }
    }

    private Map<String, Object> loadFilmDeleteImpact(Connection connection, int filmId, boolean lock)
            throws SQLException {
        if (lock) {
            try (PreparedStatement rowLock = connection.prepareStatement(
                    "SELECT Id FROM Films WITH (UPDLOCK,HOLDLOCK) WHERE Id=?")) {
                rowLock.setInt(1, filmId);
                try (ResultSet result = rowLock.executeQuery()) {
                    if (!result.next()) throw new BookingException(404, "Không tìm thấy phim.");
                }
            }
        }
        String sql = """
                SELECT f.Title,f.Status,f.EndDate,f.DeletedAt,
                  (SELECT COUNT(*) FROM Showtimes s WHERE s.FilmId=f.Id) ShowtimeCount,
                  (SELECT COUNT(*) FROM Showtimes s WHERE s.FilmId=f.Id AND s.SaleStatus<>'DELETED'
                    AND s.EndTime>=GETDATE()) CurrentOrFutureShowtimeCount,
                  (SELECT COUNT(*) FROM Showtimes s WHERE s.FilmId=f.Id AND s.EndTime<GETDATE()) HistoricalShowtimeCount,
                  (SELECT COUNT(*) FROM ShowtimeSeats ss JOIN Showtimes s ON s.Id=ss.ShowtimeId
                    WHERE s.FilmId=f.Id AND s.EndTime>=GETDATE() AND s.SaleStatus<>'DELETED'
                      AND ss.Status='held' AND ss.HeldUntil>GETDATE()) ActiveHoldCount,
                  (SELECT COUNT(*) FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId
                    WHERE s.FilmId=f.Id AND s.EndTime>=GETDATE() AND s.SaleStatus<>'DELETED'
                      AND o.PaymentStatus='pending' AND o.OrderStatus IN ('created','pending')) ActiveDraftOrderCount,
                  (SELECT COUNT(*) FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId
                    WHERE s.FilmId=f.Id AND s.EndTime>=GETDATE() AND s.SaleStatus<>'DELETED'
                      AND ((o.PaymentStatus='paid' AND o.OrderStatus NOT IN ('cancelled','redeemed'))
                        OR (o.PaymentStatus='pending' AND o.OrderStatus='confirmed'))) CommittedOrderCount,
                  (SELECT COUNT(*) FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId
                    WHERE s.FilmId=f.Id AND s.EndTime<GETDATE()) HistoricalOrderCount,
                  (SELECT COUNT(*) FROM Comments WHERE FilmId=f.Id) CommentCount,
                  (SELECT COUNT(*) FROM CommentReports cr JOIN Comments cm ON cm.Id=cr.CommentId
                    WHERE cm.FilmId=f.Id) CommentReportCount,
                  (SELECT COUNT(*) FROM CinemaFilms WHERE FilmId=f.Id) CinemaCount
                FROM Films f WHERE f.Id=?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, filmId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new BookingException(404, "Không tìm thấy phim.");
                int currentOrFuture = rs.getInt("CurrentOrFutureShowtimeCount");
                int activeHolds = rs.getInt("ActiveHoldCount");
                int activeDrafts = rs.getInt("ActiveDraftOrderCount");
                int committed = rs.getInt("CommittedOrderCount");
                int showtimeCount = rs.getInt("ShowtimeCount");
                int commentCount = rs.getInt("CommentCount");
                boolean unreferenced = showtimeCount == 0 && commentCount == 0;
                boolean deleted = rs.getTimestamp("DeletedAt") != null;
                boolean expiredOrWithdrawn = "ended".equalsIgnoreCase(rs.getString("Status"))
                        || (rs.getDate("EndDate") != null && rs.getDate("EndDate").toLocalDate()
                                .isBefore(BusinessClock.now().toLocalDate()));
                String blocked = deleted ? "Phim đã được xóa trước đó."
                        : unreferenced ? ""
                        : !expiredOrWithdrawn ? "Phim chưa hết hạn hoặc chưa được ngừng chiếu."
                        : currentOrFuture > 0 ? "Phim vẫn còn suất đang diễn ra hoặc tương lai chưa xử lý."
                        : activeHolds + activeDrafts + committed > 0 ? "Phim vẫn còn hold/order hoạt động."
                        : "";
                Map<String, Object> impact = new LinkedHashMap<>();
                impact.put("title", rs.getString("Title"));
                impact.put("showtimeCount", showtimeCount);
                impact.put("currentOrFutureShowtimeCount", currentOrFuture);
                impact.put("futureShowtimeCount", currentOrFuture);
                impact.put("historicalShowtimeCount", rs.getInt("HistoricalShowtimeCount"));
                impact.put("activeHoldCount", activeHolds);
                impact.put("activeDraftOrderCount", activeDrafts);
                impact.put("committedOrderCount", committed);
                impact.put("activeOrderCount", committed);
                impact.put("historicalOrderCount", rs.getInt("HistoricalOrderCount"));
                impact.put("orderCount", rs.getInt("HistoricalOrderCount") + activeDrafts + committed);
                impact.put("commentCount", commentCount);
                impact.put("commentReportCount", rs.getInt("CommentReportCount"));
                impact.put("cinemaCount", rs.getInt("CinemaCount"));
                impact.put("expiredOrWithdrawn", expiredOrWithdrawn);
                impact.put("alreadyDeleted", deleted);
                impact.put("eligible", blocked.isEmpty());
                impact.put("blockedReason", blocked);
                return impact;
            }
        }
    }

    public FilmDeleteImpact previewFilmDeleteImpact(int filmId, User actor) {
        assertGlobalAdmin(actor);
        Map<String, Object> impact = getFilmDeleteImpactInfo(filmId);
        return new FilmDeleteImpact(filmId, String.valueOf(impact.get("title")),
                (int) impact.get("currentOrFutureShowtimeCount"),
                (int) impact.get("historicalShowtimeCount"),
                (int) impact.get("activeHoldCount"), (int) impact.get("activeDraftOrderCount"),
                (int) impact.get("committedOrderCount"), (int) impact.get("historicalOrderCount"),
                (int) impact.get("commentCount"), (int) impact.get("commentReportCount"),
                (int) impact.get("cinemaCount"), (boolean) impact.get("expiredOrWithdrawn"),
                (boolean) impact.get("eligible"), String.valueOf(impact.get("blockedReason")));
    }

    public FilmDeletionOutcome deleteFilm(int filmId, FilmDeletionMode mode,
            String confirmationTitle, User actor) {
        assertGlobalAdmin(actor);
        FilmDeletionMode effectiveMode = mode == null ? FilmDeletionMode.PRESERVE_COMMENTS : mode;
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Map<String, Object> impact = loadFilmDeleteImpact(connection, filmId, true);
                String title = String.valueOf(impact.get("title"));
                if (effectiveMode == FilmDeletionMode.PURGE_COMMENTS
                        && !title.equals(confirmationTitle == null ? "" : confirmationTitle.trim())) {
                    throw new BookingException(400,
                            "Hãy nhập đúng tên phim để xác nhận xóa bình luận vĩnh viễn.");
                }
                boolean unreferenced = (int) impact.get("showtimeCount") == 0
                        && (int) impact.get("commentCount") == 0;
                if (!unreferenced && !(boolean) impact.get("eligible")) {
                    throw new BookingException(409, String.valueOf(impact.get("blockedReason")));
                }
                if (unreferenced) {
                    deleteFilmAuxiliaryRows(connection, filmId);
                    try (PreparedStatement ps = connection.prepareStatement("DELETE FROM Films WHERE Id=?")) {
                        ps.setInt(1, filmId);
                        if (ps.executeUpdate() != 1) {
                            throw new BookingException(409, "Trạng thái phim đã thay đổi. Vui lòng tải lại.");
                        }
                    }
                    insertAudit(connection, actor.getId(), "HARD_DELETE_FILM", "Film", filmId,
                            "{\"title\":\"" + jsonText(title) + "\"}");
                    connection.commit();
                    invalidateCatalogCaches();
                    return FilmDeletionOutcome.HARD_DELETED;
                }

                int purgedComments = 0;
                if (effectiveMode == FilmDeletionMode.PURGE_COMMENTS) {
                    deleteFilmCommentReports(connection, filmId);
                    try (PreparedStatement ps = connection.prepareStatement("DELETE FROM Comments WHERE FilmId=?")) {
                        ps.setInt(1, filmId);
                        purgedComments = ps.executeUpdate();
                    }
                }
                try (PreparedStatement ps = connection.prepareStatement("""
                        UPDATE Films SET DeletedAt=SYSDATETIME(),DeletedByUserId=?,DeletionMode=?,
                          Status='ended',UpdatedAt=GETDATE() WHERE Id=? AND DeletedAt IS NULL
                        """)) {
                    ps.setInt(1, actor.getId());
                    ps.setString(2, effectiveMode.name());
                    ps.setInt(3, filmId);
                    if (ps.executeUpdate() != 1) {
                        throw new BookingException(409, "Phim đã được xóa bởi một yêu cầu khác.");
                    }
                }
                insertAudit(connection, actor.getId(), "TOMBSTONE_FILM", "Film", filmId,
                        "{\"title\":\"" + jsonText(title) + "\",\"mode\":\""
                                + effectiveMode.name() + "\",\"purgedComments\":" + purgedComments + "}");
                connection.commit();
                invalidateCatalogCaches();
                return FilmDeletionOutcome.TOMBSTONED;
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể xóa phim.", ex);
        }
    }

    public FilmDeletionOutcome deleteFilm(int filmId, User actor) {
        String title = findFilmById(filmId).map(Film::getTitle).orElse("");
        return deleteFilm(filmId, FilmDeletionMode.PRESERVE_COMMENTS, title, actor);
    }

    private void deleteFilmAuxiliaryRows(Connection connection, int filmId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM CinemaFilms WHERE FilmId=?")) {
            ps.setInt(1, filmId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM FilmCategories WHERE FilmId=?")) {
            ps.setInt(1, filmId);
            ps.executeUpdate();
        }
        notificationDAO.deleteByTarget(connection, "Film", String.valueOf(filmId));
    }

    private void deleteFilmCommentReports(Connection connection, int filmId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM CommentReports WHERE CommentId IN (SELECT Id FROM Comments WHERE FilmId=?)")) {
            ps.setInt(1, filmId);
            ps.executeUpdate();
        }
    }

    private void insertAudit(Connection connection, Integer actorId, String action,
            String targetType, int targetId, String detailJson) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO AuditLogs(ActorUserId,Action,TargetType,TargetId,DetailJson,AfterJson)
                VALUES(?,?,?,?,?,?)
                """)) {
            if (actorId == null) {
                ps.setNull(1, java.sql.Types.INTEGER);
            } else {
                ps.setInt(1, actorId);
            }
            ps.setString(2, action);
            ps.setString(3, targetType);
            ps.setString(4, String.valueOf(targetId));
            ps.setString(5, detailJson);
            ps.setString(6, detailJson);
            ps.executeUpdate();
        }
    }

    private void insertAudit(Connection connection, Integer actorId, String action,
            String targetType, String targetId, String beforeJson, String afterJson) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO AuditLogs(ActorUserId,Action,TargetType,TargetId,DetailJson,BeforeJson,AfterJson)
                VALUES(?,?,?,?,?,?,?)
                """)) {
            if (actorId == null) {
                ps.setNull(1, java.sql.Types.INTEGER);
            } else {
                ps.setInt(1, actorId);
            }
            ps.setString(2, action);
            ps.setString(3, targetType);
            ps.setString(4, targetId);
            ps.setString(5, afterJson);
            ps.setString(6, beforeJson);
            ps.setString(7, afterJson);
            ps.executeUpdate();
        }
    }

    private String jsonText(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public List<Cinema> listCinemas() {
        return listCinemas(LIFECYCLE_ACTIVE);
    }

    /**
     * Danh sach rap cho trang quan tri, tach theo tab vong doi.
     *
     * <p>Cung khuon voi {@link #listFilms(User, String)}: rap xoa mem khong biet mat khoi he thong
     * ma nam o tab "Đã bị xóa", dung yeu cau "phan bi xoa phai co cho de xem lai" (D-03).</p>
     */
    public List<Cinema> listCinemas(String lifecycleTab) {
        String sql = """
                SELECT c.*, ci.Name AS CityName, COUNT(r.Id) AS RoomCount
                FROM Cinemas c
                JOIN Cities ci ON ci.Id = c.CityId
                LEFT JOIN Rooms r ON r.CinemaId = c.Id AND ISNULL(r.Status, 'active') != 'deleted'
                WHERE %s
                GROUP BY c.Id, c.CityId, c.Name, c.Address, c.Avatar, c.BannerUrl, c.Description, c.Phone, c.Status, c.CinemaType, c.CreatedAt, c.UpdatedAt, ci.Name
                ORDER BY c.CreatedAt DESC
                """.formatted(lifecyclePredicate("c", lifecycleTab));
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            List<Cinema> cinemas = new ArrayList<>();
            while (rs.next()) {
                cinemas.add(mapCinema(rs));
            }
            return cinemas;
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the tai danh sach rap.", ex);
        }
    }

    public List<Cinema> listCinemas(User actor) {
        return listCinemas(actor, LIFECYCLE_ACTIVE);
    }

    public List<Cinema> listCinemas(User actor, String lifecycleTab) {
        List<Cinema> cinemas = listCinemas(lifecycleTab);
        if (!ScopeUtil.isManager(actor))
            return cinemas;
        int cinemaId = requireActorCinema(actor);
        return cinemas.stream().filter(cinema -> cinema.getId() == cinemaId).toList();
    }

    public Optional<Cinema> findCinemaById(int cinemaId) {
        String sql = """
                SELECT c.*, ci.Name AS CityName, COUNT(r.Id) AS RoomCount
                FROM Cinemas c
                JOIN Cities ci ON ci.Id = c.CityId
                LEFT JOIN Rooms r ON r.CinemaId = c.Id AND ISNULL(r.Status, 'active') != 'deleted'
                WHERE c.Id = ?
                GROUP BY c.Id, c.CityId, c.Name, c.Address, c.Avatar, c.BannerUrl, c.Description, c.Phone, c.Status, c.CinemaType, c.CreatedAt, c.UpdatedAt, ci.Name
                """;
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, cinemaId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapCinema(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the tai thong tin rap.", ex);
        }
    }

    public Optional<Cinema> findCinemaById(int cinemaId, User actor) {
        ScopeUtil.assertCinemaScope(actor, cinemaId);
        return findCinemaById(cinemaId);
    }

    public Map<Integer, String> cityOptions() {
        String sql = "SELECT Id, Name FROM Cities ORDER BY Name";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            Map<Integer, String> options = new LinkedHashMap<>();
            while (rs.next()) {
                options.put(rs.getInt("Id"), rs.getString("Name"));
            }
            return options;
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the tai danh sach thanh pho.", ex);
        }
    }

    public int findOrCreateCity(String cityName) {
        if (cityName == null || cityName.isBlank()) {
            throw new BookingException(400, "Tên Tỉnh / Thành phố không được để trống.");
        }
        String name = cityName.trim();
        String selectSql = "SELECT Id FROM Cities WHERE LOWER(Name) = LOWER(?)";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(selectSql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("Id");
                }
            }
            invalidateCatalogCaches();
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể kiểm tra thông tin thành phố.", ex);
        }

        String insertSql = "INSERT INTO Cities (Name) VALUES (?)";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể tạo thành phố mới.", ex);
        }
        throw new BookingException(500, "Tạo thành phố mới thất bại.");
    }

    public List<Integer> getFilmIdsByCinemaId(int cinemaId) {
        String sql = "SELECT FilmId FROM CinemaFilms WHERE CinemaId = ? AND Status=N'active'";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, cinemaId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Integer> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(rs.getInt("FilmId"));
                }
                return list;
            }
        } catch (SQLException ex) {
            // Khong duoc tra list rong: form /admin/cinemas/films dung ket qua nay lam
            // trang thai
            // tick san, nen "doc loi" bi hieu thanh "rap khong chieu phim nao" va luu lai
            // se xoa
            // sach lien ket phim-rap. Pham vi phim cua manager cung doc ham nay.
            throw new BookingException(500, "Khong the tai danh sach phim cua rap.", ex);
        }
    }

    /**
     * Gia han lich chieu cua mot phim (EX-01).
     *
     * <p>
     * Chi doi {@code EndDate}; khong dung toi truong nao khac cua phim va khong
     * dung toi
     * suat chieu hay don ve da co. Ghi audit kem gia tri cu/moi de doi soat duoc ve
     * sau.
     * </p>
     *
     * <p>
     * Van chay qua {@link #validateFilmDates} nen khong the gia han ve mot ngay
     * truoc
     * {@code ReleaseDate}, va khong the <i>rut ngan</i> xuong truoc mot suat chieu
     * da xep.
     * </p>
     */
    public void extendFilmEndDate(int filmId, LocalDate newEndDate, User actor) {
        assertFilmScope(actor, filmId);
        if (newEndDate == null) {
            throw new BookingException(400, "Vui lòng chọn ngày kết thúc chiếu mới.");
        }
        Film film = findFilmById(filmId, actor)
                .orElseThrow(() -> new BookingException(404, "Không tìm thấy phim."));
        LocalDate oldEndDate = film.getEndDate();
        film.setEndDate(newEndDate);
        validateFilmDates(film);

        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(
                        "UPDATE Films SET EndDate = ?, UpdatedAt = GETDATE() WHERE Id = ?")) {
            ps.setDate(1, Date.valueOf(newEndDate));
            ps.setInt(2, filmId);
            if (ps.executeUpdate() == 0) {
                throw new BookingException(404, "Không tìm thấy phim.");
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể gia hạn lịch chiếu.", ex);
        }

        logAction(actor.getId(), "EXTEND_FILM_ENDDATE", "Film", String.valueOf(filmId),
                String.format("{\"from\":%s,\"to\":\"%s\"}",
                        oldEndDate == null ? "null" : "\"" + oldEndDate + "\"", newEndDate));
        invalidateCatalogCaches();
    }

    /**
     * Cac cum rap dang chieu mot phim — dung de tich san lua chon o form sua phim
     * (ST-01).
     */
    public List<Integer> getCinemaIdsByFilmId(int filmId) {
        String sql = "SELECT CinemaId FROM CinemaFilms WHERE FilmId = ? AND Status=N'active' ORDER BY CinemaId";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, filmId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Integer> ids = new ArrayList<>();
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
                return ids;
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể đọc danh sách cụm rạp của phim.", ex);
        }
    }

    public Map<Integer, List<Integer>> getCinemaFilmMap() {
        return getCinemaFilmMap(null);
    }

    /** Return only mappings visible in the actor's current cinema context. */
    public Map<Integer, List<Integer>> getCinemaFilmMap(User actor) {
        boolean scoped = ScopeUtil.isManager(actor);
        String sql = "SELECT CinemaId, FilmId FROM CinemaFilms WHERE Status=N'active'"
                + (scoped ? " AND CinemaId=?" : "");
        Map<Integer, List<Integer>> map = new LinkedHashMap<>();
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            if (scoped) {
                ps.setInt(1, requireActorCinema(actor));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int cinemaId = rs.getInt("CinemaId");
                    int filmId = rs.getInt("FilmId");
                    map.computeIfAbsent(cinemaId, k -> new ArrayList<>()).add(filmId);
                }
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể tải ánh xạ phim theo rạp.", ex);
        }
        return map;
    }

    public void saveCinemaFilms(int cinemaId, List<Integer> filmIds) {
        saveCinemaFilms(cinemaId, filmIds, (Integer) null);
    }

    private void saveCinemaFilms(int cinemaId, List<Integer> filmIds, Integer actorId) {
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement ps = connection.prepareStatement("""
                        UPDATE CinemaFilms SET Status=N'inactive',UnassignedAt=SYSDATETIME(),
                            UnassignedByUserId=?
                        WHERE CinemaId=? AND Status=N'active'
                        """)) {
                    if (actorId == null) {
                        ps.setNull(1, Types.INTEGER);
                    } else {
                        ps.setInt(1, actorId);
                    }
                    ps.setInt(2, cinemaId);
                    ps.executeUpdate();
                }
                if (filmIds != null && !filmIds.isEmpty()) {
                    try (PreparedStatement update = connection.prepareStatement("""
                            UPDATE CinemaFilms SET Status=N'active',AssignedAt=SYSDATETIME(),
                                AssignedByUserId=?,UnassignedAt=NULL,UnassignedByUserId=NULL
                            WHERE CinemaId=? AND FilmId=?
                            """)) {
                        try (PreparedStatement insert = connection.prepareStatement("""
                                INSERT INTO CinemaFilms(CinemaId,FilmId,Status,AssignedAt,AssignedByUserId)
                                VALUES(?,?,N'active',SYSDATETIME(),?)
                                """)) {
                            for (Integer filmId : filmIds.stream().distinct().toList()) {
                                if (actorId == null) {
                                    update.setNull(1, Types.INTEGER);
                                } else {
                                    update.setInt(1, actorId);
                                }
                                update.setInt(2, cinemaId);
                                update.setInt(3, filmId);
                                if (update.executeUpdate() == 0) {
                                    insert.setInt(1, cinemaId);
                                    insert.setInt(2, filmId);
                                    if (actorId == null) {
                                        insert.setNull(3, Types.INTEGER);
                                    } else {
                                        insert.setInt(3, actorId);
                                    }
                                    insert.executeUpdate();
                                }
                            }
                        }
                    }
                }
                connection.commit();
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể cập nhật danh sách phim theo rạp.", ex);
        }
        // PU-01: gan phim cho rap doi noi dung trang rap va mega-menu — phai xoa cache,
        // neu khong nguoi dung con thay danh sach cu toi het TTL.
        invalidateCatalogCaches();
    }

    /**
     * Xoa ca hai cache danh muc sau khi CRUD phim/rap.
     *
     * <p>
     * Co <b>hai</b> cache doc lap: {@link HeaderDataFilter} phuc vu tang JSP va
     * cache trong
     * {@code CatalogApiServlet} phuc vu tang Next.js. Quen mot cai la hai tang
     * front-end hien
     * hai danh sach phim khac nhau.
     * </p>
     */
    private void invalidateCatalogCaches() {
        HeaderDataFilter.invalidate();
        com.mycompany.website.ban.ve.xem.phim.api.v1.CatalogApiServlet.invalidateHeaderCache();
    }

    public void saveCinemaFilms(int cinemaId, List<Integer> filmIds, User actor) {
        CinemaCapabilityPolicy.requireAdmin(actor);
        Set<Integer> previouslyActive = new LinkedHashSet<>(getFilmIdsByCinemaId(cinemaId));
        saveCinemaFilms(cinemaId, filmIds, actor.getId());
        if (filmIds != null) {
            ApprovalService notifications = new ApprovalService();
            for (Integer filmId : filmIds.stream().filter(id -> id != null && id > 0).distinct().toList()) {
                if (!previouslyActive.contains(filmId)) {
                    String title = findFilmById(filmId).map(Film::getTitle).orElse("#" + filmId);
                    notifications.notifyFilmAssigned(cinemaId, filmId, title, actor);
                }
            }
        }
    }

    public void saveCinema(Cinema cinema, List<Integer> filmIds, User actor) {
        saveCinema(cinema, actor);
        if (cinema.getId() > 0 && filmIds != null && CinemaCapabilityPolicy.isAdmin(actor)) {
            saveCinemaFilms(cinema.getId(), filmIds, actor);
        }
    }

    public void saveCinema(Cinema cinema, User actor) {
        if (cinema.getId() <= 0) {
            CinemaCapabilityPolicy.requireAdmin(actor);
        }
        if (cinema.getId() > 0)
            ScopeUtil.assertCinemaScope(actor, cinema.getId());
        try (Connection connection = DBConnection.getConnection()) {
            if (cinema.getId() > 0) {
                if (CinemaCapabilityPolicy.isManager(actor)) {
                    try (PreparedStatement ps = connection.prepareStatement("""
                            UPDATE Cinemas
                            SET Address=?,Avatar=?,BannerUrl=?,Description=?,Phone=?,UpdatedAt=GETDATE()
                            WHERE Id=?
                            """)) {
                        ps.setString(1, cinema.getAddress());
                        ps.setString(2, cinema.getAvatar());
                        ps.setString(3, cinema.getBannerUrl());
                        ps.setString(4, cinema.getDescription());
                        ps.setString(5, cinema.getPhone());
                        ps.setInt(6, cinema.getId());
                        if (ps.executeUpdate() != 1) {
                            throw new BookingException(404, "Không tìm thấy rạp được gán.");
                        }
                    }
                    logAction(actor.getId(), "UPDATE_CINEMA_OPERATIONS", "Cinema",
                            String.valueOf(cinema.getId()), cinema.getName());
                    invalidateCatalogCaches();
                    return;
                }
                String sql = """
                        UPDATE Cinemas
                        SET CityId = ?, Name = ?, Address = ?, Avatar = ?, BannerUrl = ?, Description = ?, Phone = ?, Status = ?, CinemaType = ?, UpdatedAt = GETDATE()
                        WHERE Id = ?
                        """;
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setInt(1, cinema.getCityId());
                    ps.setString(2, cinema.getName());
                    ps.setString(3, cinema.getAddress());
                    ps.setString(4, cinema.getAvatar());
                    ps.setString(5, cinema.getBannerUrl());
                    ps.setString(6, cinema.getDescription());
                    ps.setString(7, cinema.getPhone());
                    ps.setString(8,
                            cinema.getStatus() == null || cinema.getStatus().isBlank() ? "active" : cinema.getStatus());
                    ps.setString(9, normalizedRoomType(cinema.getCinemaType()));
                    ps.setInt(10, cinema.getId());
                    ps.executeUpdate();
                }
                logAction(actor.getId(), "UPDATE_CINEMA", "Cinema", String.valueOf(cinema.getId()), cinema.getName());
            } else {
                String sql = "INSERT INTO Cinemas (CityId, Name, Address, Avatar, BannerUrl, Description, Phone, Status, CinemaType) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, cinema.getCityId());
                    ps.setString(2, cinema.getName());
                    ps.setString(3, cinema.getAddress());
                    ps.setString(4, cinema.getAvatar());
                    ps.setString(5, cinema.getBannerUrl());
                    ps.setString(6, cinema.getDescription());
                    ps.setString(7, cinema.getPhone());
                    ps.setString(8,
                            cinema.getStatus() == null || cinema.getStatus().isBlank() ? "active" : cinema.getStatus());
                    ps.setString(9, normalizedRoomType(cinema.getCinemaType()));
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (keys.next()) {
                            cinema.setId(keys.getInt(1));
                        }
                    }
                }
                logAction(actor.getId(), "CREATE_CINEMA", "Cinema", String.valueOf(cinema.getId()), cinema.getName());
            }
            com.mycompany.website.ban.ve.xem.phim.filter.HeaderDataFilter.invalidate();
            invalidateCatalogCaches();
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the luu rap.", ex);
        }
    }

    /**
     * Xoa rap: <b>soft delete khi con lich su, hard delete khi that su sach</b> — cung khuon voi
     * {@link #deleteRoom(int, User, boolean)}.
     *
     * <p><b>Loi da sua (D-02).</b> Buoc 3 truoc day chay {@code UPDATE Combos SET CinemaId = NULL}
     * trong khi bang that ten la {@code ComboFoods}. Cau lenh nam <b>vo dieu kien</b> giua hai chot
     * kiem tra va {@code DELETE FROM Cinemas}, nen <b>moi</b> rap qua duoc hai chot deu chet o day
     * voi {@code Msg 208 Invalid object name 'Combos'} — tinh nang xoa rap chua bao gio chay duoc.
     * Te hon, {@code SQLException} bi doi thanh <i>"còn dữ liệu ràng buộc"</i>, do toi cho du lieu
     * va che mat mot loi lap trinh.</p>
     *
     * <p><b>Vi sao soft delete chu khong sua cho "xoa duoc".</b> Cac khoa ngoai tro toi
     * {@code Cinemas} ({@code Rooms}, {@code Showtimes}, {@code ComboFoods}, {@code Users},
     * {@code UserNotifications}) deu la {@code NO_ACTION}. Doi chung sang {@code ON DELETE CASCADE}
     * de "cho chay duoc" se xoa sach {@code Showtimes} — tuc la mat doanh thu. Soft delete giu
     * nguyen moi dong: bao cao chi doc bang {@code Orders}, va lich su don van {@code JOIN Cinemas}
     * duoc vi dong rap khong bi xoa.</p>
     */
    public void deleteCinema(int cinemaId, User actor) {
        CinemaCapabilityPolicy.requireAdmin(actor);
        Cinema cinema = findCinemaById(cinemaId)
                .orElseThrow(() -> new BookingException(404, "Không tìm thấy rạp chiếu."));

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Kiểm tra phòng chiếu thuộc rạp
                int roomCount = countForCinema(conn,
                        "SELECT COUNT(*) FROM Rooms WHERE CinemaId = ? AND ISNULL(Status, 'active') != 'deleted'",
                        cinemaId);
                if (roomCount > 0) {
                    throw new BookingException(400, "Không thể xóa rạp '" + cinema.getName()
                            + "' vì đang có " + roomCount + " phòng chiếu trực thuộc. Vui lòng xóa các phòng chiếu trước.");
                }

                // 2. Kiểm tra tài khoản nhân viên / quản lý gán trực tiếp cho rạp này
                int userCount = countForCinema(conn, "SELECT COUNT(*) FROM Users WHERE CinemaId = ?", cinemaId);
                if (userCount > 0) {
                    throw new BookingException(400, "Không thể xóa rạp '" + cinema.getName()
                            + "' vì đang có " + userCount + " tài khoản nhân viên/quản lý thuộc rạp này.");
                }

                // 3. Rap con dau vet van hanh nao thi KHONG duoc xoa cung: chinh cac khoa ngoai
                //    NO_ACTION nay dang vo tinh bao ve doanh thu.
                int showtimeCount = countForCinema(conn,
                        "SELECT COUNT(*) FROM Showtimes WHERE CinemaId = ?", cinemaId);
                int deletedRoomCount = countForCinema(conn,
                        "SELECT COUNT(*) FROM Rooms WHERE CinemaId = ?", cinemaId);
                int comboCount = countForCinema(conn,
                        "SELECT COUNT(*) FROM ComboFoods WHERE CinemaId = ?", cinemaId);
                int notificationCount = countForCinema(conn,
                        "SELECT COUNT(*) FROM UserNotifications WHERE CinemaId = ?", cinemaId);
                boolean softDeleted = showtimeCount > 0 || deletedRoomCount > 0 || comboCount > 0
                        || notificationCount > 0;

                if (softDeleted) {
                    // Giu nguyen CinemaFilms: xoa mapping phim <-> rap la mat du lieu that su
                    // (khoa ngoai do la CASCADE) trong khi rap van con de doi chieu lich su.
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE Cinemas SET Status = 'deleted', UpdatedAt = GETDATE() WHERE Id = ?")) {
                        ps.setInt(1, cinemaId);
                        if (ps.executeUpdate() == 0) {
                            throw new BookingException(404, "Không tìm thấy rạp chiếu.");
                        }
                    }
                    insertAudit(conn, actor.getId(), "DELETE_CINEMA_SOFT", "Cinema", cinemaId,
                            String.format("{\"cinemaName\":\"%s\",\"showtimeCount\":%d,\"roomCount\":%d,"
                                    + "\"comboCount\":%d,\"deletedMode\":\"SOFT_DELETE\"}",
                                    jsonText(cinema.getName()), showtimeCount, deletedRoomCount, comboCount));
                } else {
                    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM CinemaFilms WHERE CinemaId = ?")) {
                        ps.setInt(1, cinemaId);
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM Cinemas WHERE Id = ?")) {
                        ps.setInt(1, cinemaId);
                        if (ps.executeUpdate() == 0) {
                            throw new BookingException(404, "Không tìm thấy rạp chiếu.");
                        }
                    }
                    insertAudit(conn, actor.getId(), "DELETE_CINEMA_HARD", "Cinema", cinemaId,
                            String.format("{\"cinemaName\":\"%s\",\"deletedMode\":\"HARD_DELETE\"}",
                                    jsonText(cinema.getName())));
                }
                conn.commit();
            } catch (BookingException ex) {
                conn.rollback();
                throw ex;
            } catch (SQLException ex) {
                conn.rollback();
                // Khong doi loi SQL thanh "con du lieu rang buoc" nua: thong bao do tung che mat
                // mot loi sai ten bang suot ca vong doi tinh nang nay.
                throw new BookingException(500, "Không thể xóa rạp '" + cinema.getName()
                        + "' vì lệnh cơ sở dữ liệu thất bại. Thao tác đã được hoàn tác nguyên vẹn.", ex);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể kết nối cơ sở dữ liệu để xóa rạp chiếu.", ex);
        }

        com.mycompany.website.ban.ve.xem.phim.filter.HeaderDataFilter.invalidate();
        invalidateCatalogCaches();
    }

    private int countForCinema(Connection connection, String sql, int cinemaId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, cinemaId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public void syncSpecialCinema(String cinemaName, String address, String imageUrl, String description, boolean isSpecial, User actor) {
        if (cinemaName == null || cinemaName.isBlank()) return;
        List<com.mycompany.website.ban.ve.xem.phim.controller.SpecialCinemaServlet.SpecialCinema> list = com.mycompany.website.ban.ve.xem.phim.util.CustomContentHelper.getSpecialCinemas();
        List<com.mycompany.website.ban.ve.xem.phim.controller.SpecialCinemaServlet.SpecialCinema> updatedList = new ArrayList<>();
        
        for (com.mycompany.website.ban.ve.xem.phim.controller.SpecialCinemaServlet.SpecialCinema sc : list) {
            if (!sc.getTitle().trim().equalsIgnoreCase(cinemaName.trim())) {
                updatedList.add(sc);
            }
        }
        
        if (isSpecial) {
            String desc = description != null && !description.isBlank() ? description : ("Cụm rạp chiếu chuẩn quốc tế " + cinemaName + " với công nghệ màn hình IMAX & âm thanh Dolby Atmos.");
            updatedList.add(new com.mycompany.website.ban.ve.xem.phim.controller.SpecialCinemaServlet.SpecialCinema(
                    cinemaName,
                    address != null ? address : "",
                    imageUrl != null ? imageUrl : "",
                    desc
            ));
        }
        
        javax.json.JsonArrayBuilder builder = javax.json.Json.createArrayBuilder();
        for (com.mycompany.website.ban.ve.xem.phim.controller.SpecialCinemaServlet.SpecialCinema sc : updatedList) {
            builder.add(javax.json.Json.createObjectBuilder()
                    .add("title", sc.getTitle() != null ? sc.getTitle() : "")
                    .add("address", sc.getAddress() != null ? sc.getAddress() : "")
                    .add("imageUrl", sc.getImageUrl() != null ? sc.getImageUrl() : "")
                    .add("description", sc.getDescription() != null ? sc.getDescription() : ""));
        }
        
        String newJson = builder.build().toString();
        saveSetting("special_cinemas_data", newJson, actor);
    }

    public List<Room> listRooms() {
        return listRooms(LIFECYCLE_ACTIVE);
    }

    /**
     * Danh sach phong cho trang quan tri, tach theo tab vong doi.
     *
     * <p><b>Loi da sua (D-03).</b> Truoc day chi co mot truy van voi
     * {@code Status != 'deleted'} cung nhac, nen phong xoa mem con nguyen trong DB (doanh thu an
     * toan) nhung <b>khong man hinh nao hien no nua</b> — nguoi dung thay no bien mat khong dau
     * vet. Phim da lam dung viec nay tu truoc bang tab vong doi; day la ban sao cua khuon do.</p>
     */
    public List<Room> listRooms(String lifecycleTab) {
        String sql = """
                SELECT r.*, c.Name AS CinemaName, COUNT(s.Id) AS SeatCount
                FROM Rooms r
                JOIN Cinemas c ON c.Id = r.CinemaId
                LEFT JOIN Seats s ON s.RoomId = r.Id AND s.IsActive = 1
                WHERE %s
                GROUP BY r.Id, r.CinemaId, r.Name, r.Status, r.RoomType, r.CreatedAt, r.UpdatedAt, c.Name
                ORDER BY r.CreatedAt DESC
                """.formatted(lifecyclePredicate("r", lifecycleTab));
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            List<Room> rooms = new ArrayList<>();
            while (rs.next()) {
                rooms.add(mapRoom(rs));
            }
            return rooms;
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the tai danh sach phong.", ex);
        }
    }

    public List<Room> listRooms(User actor) {
        return listRooms(actor, LIFECYCLE_ACTIVE);
    }

    public List<Room> listRooms(User actor, String lifecycleTab) {
        List<Room> rooms = listRooms(lifecycleTab);
        if (!ScopeUtil.isManager(actor))
            return rooms;
        int cinemaId = requireActorCinema(actor);
        return rooms.stream().filter(room -> room.getCinemaId() == cinemaId).toList();
    }

    public Optional<Room> findRoomById(int roomId) {
        String sql = """
                SELECT r.*, c.Name AS CinemaName, COUNT(s.Id) AS SeatCount
                FROM Rooms r
                JOIN Cinemas c ON c.Id = r.CinemaId
                LEFT JOIN Seats s ON s.RoomId = r.Id AND s.IsActive = 1
                WHERE r.Id = ?
                GROUP BY r.Id, r.CinemaId, r.Name, r.Status, r.RoomType, r.CreatedAt, r.UpdatedAt, c.Name
                """;
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, roomId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRoom(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the tai thong tin phong.", ex);
        }
    }

    public Optional<Room> findRoomById(int roomId, User actor) {
        assertRoomScope(actor, roomId);
        return findRoomById(roomId);
    }

    /**
     * So do ghe cho man hinh quan tri; {@code Occupied} khoa nut sua o phia trinh duyet.
     *
     * <p><b>Loi da sua (D-01).</b> Bieu thuc {@code Occupied} cu khong co moc thoi gian, khong loc
     * don da huy va khong loc suat tombstone, nen mot ghe ban xong tu nhieu thang truoc van hien
     * mau "dang giu" va bi JavaScript chan click <b>vinh vien</b>. Do chinh la trieu chung
     * "phong QA Phong Rap7 chieu xong tu 01/08 ma toi 07/08 ghe van bi giu" — 5/12 ghe bi khoa,
     * trong do hai ghe chi vi mot don da HUY. Nay dung chung
     * {@link #LIVE_SEAT_REFERENCE_PREDICATE} voi hai chot con lai.</p>
     */
    public List<Seat> getSeatsByRoomId(int roomId) {
        String sql = """
                SELECT s.Id, s.RoomId, s.RowLabel, s.SeatNumber, s.SeatType, s.SeatKey,
                       ISNULL(s.PriceSurcharge, 0) AS PriceSurcharge,
                       CASE WHEN %s THEN 1 ELSE 0 END AS Occupied
                FROM Seats s WHERE s.RoomId = ? AND s.IsActive = 1 ORDER BY s.RowLabel ASC, s.SeatNumber ASC
                """.formatted(LIVE_SEAT_REFERENCE_PREDICATE);
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, roomId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Seat> seats = new ArrayList<>();
                while (rs.next()) {
                    Seat seat = new Seat();
                    seat.setId(rs.getInt("Id"));
                    seat.setRoomId(rs.getInt("RoomId"));
                    seat.setRowLabel(rs.getString("RowLabel"));
                    seat.setSeatNumber(rs.getInt("SeatNumber"));
                    seat.setSeatType(rs.getString("SeatType"));
                    seat.setOccupied(rs.getBoolean("Occupied"));
                    seat.setSeatKey(rs.getString("SeatKey"));
                    seat.setPriceSurcharge(rs.getBigDecimal("PriceSurcharge"));
                    seats.add(seat);
                }
                return seats;
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the tai so do ghe.", ex);
        }
    }

    public List<Seat> getSeatsByRoomId(int roomId, User actor) {
        assertRoomScope(actor, roomId);
        return getSeatsByRoomId(roomId);
    }

    public void saveCustomRoomSeats(int roomId, List<Seat> seats, User actor) {
        assertRoomScope(actor, roomId);
        if (seats == null || seats.isEmpty()) {
            throw new BookingException(400, "Danh sách ghế không được để trống.");
        }
        java.util.Set<String> uniqueKeys = new java.util.HashSet<>();
        for (Seat seat : seats) {
            if (seat.getSeatKey() == null || seat.getSeatKey().isBlank()) {
                throw new BookingException(400, "Mã ghế không được để trống.");
            }
            if (!uniqueKeys.add(seat.getSeatKey().toUpperCase())) {
                throw new BookingException(400,
                        "Mã ghế '" + seat.getSeatKey() + "' bị trùng lặp trong cùng một phòng chiếu.");
            }
        }
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                reconcileRoomSeats(connection, roomId, seats);

                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the luu ma tran ghe.", ex);
        }
        auditAfterCommit(actor.getId(), "UPDATE_SEAT_LAYOUT", "Room", String.valueOf(roomId),
                "Cập nhật sơ đồ " + seats.size() + " ghế");
    }

    /** Non-mutating seat diff used by the confirmation step in the admin UI. */
    public SeatLayoutPreview previewRoomSeats(int roomId, List<Seat> requested, User actor) {
        assertRoomScope(actor, roomId);
        if (requested == null || requested.isEmpty()) {
            throw new BookingException(400, "Danh sách ghế không được để trống.");
        }
        try (Connection connection = DBConnection.getConnection()) {
            Map<String, Integer> current = new LinkedHashMap<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT Id, SeatKey FROM Seats WHERE RoomId=? AND IsActive=1")) {
                ps.setInt(1, roomId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        current.put(rs.getString("SeatKey").trim().toUpperCase(), rs.getInt("Id"));
                    }
                }
            }
            Map<String, Seat> wanted = new LinkedHashMap<>();
            for (Seat seat : requested) {
                String key = seat == null || seat.getSeatKey() == null
                        ? "" : seat.getSeatKey().trim().toUpperCase();
                if (key.isBlank() || wanted.put(key, seat) != null) {
                    throw new BookingException(400, "Mã ghế bị trùng hoặc để trống: " + key);
                }
            }
            for (Seat seat : wanted.values()) {
                if (seat != null && "couple".equalsIgnoreCase(seat.getSeatType())) {
                    String row = seat.getRowLabel() == null ? "" : seat.getRowLabel().trim().toUpperCase();
                    int partnerNumber = seat.getSeatNumber() % 2 == 1
                            ? seat.getSeatNumber() + 1 : seat.getSeatNumber() - 1;
                    Seat partner = wanted.get(row + partnerNumber);
                    if (partner == null || !"couple".equalsIgnoreCase(partner.getSeatType())) {
                        throw new BookingException(400, "Ghế đôi " + seat.getSeatKey()
                                + " phải có đủ hai ghế lẻ-chẵn trong cùng hàng.");
                    }
                }
            }
            List<String> added = wanted.keySet().stream().filter(key -> !current.containsKey(key)).toList();
            List<String> removed = current.keySet().stream().filter(key -> !wanted.containsKey(key)).toList();
            // "Da co tham chieu" o day nghia la: se duoc NGUNG DUNG chu khong bi xoa cung.
            List<String> referenced = new ArrayList<>();
            for (String key : removed) {
                if (seatHasHistory(connection, current.get(key))) {
                    referenced.add(key);
                }
            }
            return new SeatLayoutPreview(roomId, current.size(), wanted.size(), added, removed, referenced,
                    nextLayoutVersion(connection, roomId));
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể xem trước thay đổi sơ đồ ghế.", ex);
        }
    }

    public void saveRoom(Room room, int rowCount, int seatsPerRow, String vipRowsCsv, boolean regenerateLayout,
            User actor) {
        boolean creating = room.getId() == 0;
        if (creating) {
            CinemaCapabilityPolicy.requireAdmin(actor);
        }
        ScopeUtil.assertCinemaScope(actor, room.getCinemaId());
        if (room.getId() > 0)
            assertRoomScope(actor, room.getId());
        if (room.getName() == null || room.getName().isBlank()) {
            throw new BookingException(400, "Tên phòng chiếu không được để trống.");
        }
        if (room.getCinemaId() <= 0) {
            throw new BookingException(400, "Vui lòng chọn cụm rạp chiếu hợp lệ.");
        }
        if (room.getId() == 0) {
            if (rowCount < 1)
                rowCount = 10;
            if (seatsPerRow < 1)
                seatsPerRow = 12;
            if (vipRowsCsv == null || vipRowsCsv.isBlank())
                vipRowsCsv = "C,D,E,F,G";
        }
        if (room.getId() == 0 && (rowCount < 1 || seatsPerRow < 1)) {
            throw new BookingException(400, "Số hàng và số ghế mỗi hàng phải lớn hơn 0.");
        }
        Set<String> vipRows = parseRowLabels(vipRowsCsv);
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                // Check duplicate room name per cinema
                String checkSql = "SELECT COUNT(*) FROM Rooms WHERE CinemaId = ? AND LOWER(LTRIM(RTRIM(Name))) = LOWER(LTRIM(RTRIM(?))) AND Id != ?";
                try (PreparedStatement checkPs = connection.prepareStatement(checkSql)) {
                    checkPs.setInt(1, room.getCinemaId());
                    checkPs.setString(2, room.getName());
                    checkPs.setInt(3, room.getId());
                    try (ResultSet rs = checkPs.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            throw new BookingException(400, "Tên/Mã phòng chiếu '" + room.getName()
                                    + "' đã tồn tại trong rạp này. Vui lòng đặt tên/mã phòng khác.");
                        }
                    }
                }

                if (room.getId() > 0) {
                    try (PreparedStatement ps = connection.prepareStatement(
                            "UPDATE Rooms SET CinemaId = ?, Name = ?, RoomType = ?, UpdatedAt = GETDATE() WHERE Id = ?")) {
                        ps.setInt(1, room.getCinemaId());
                        ps.setString(2, room.getName());
                        ps.setString(3, normalizedRoomType(room.getRoomType()));
                        ps.setInt(4, room.getId());
                        ps.executeUpdate();
                    }
                    if (regenerateLayout) {
                        if (rowCount < 1 || seatsPerRow < 1) {
                            throw new BookingException(400,
                                    "Can nhap so hang va so ghe moi hang de tao lai so do ghe.");
                        }
                        ensureRoomSeatLayoutEditable(connection, room.getId());
                        retireActiveSeats(connection, room.getId());
                        generateSeats(connection, room.getId(), rowCount, seatsPerRow, vipRows);
                        recreateFutureShowtimeSeats(connection, room.getId());
                    }
                } else {
                    try (PreparedStatement ps = connection.prepareStatement(
                            "INSERT INTO Rooms (CinemaId, Name, RoomType) VALUES (?, ?, ?)",
                            Statement.RETURN_GENERATED_KEYS)) {
                        ps.setInt(1, room.getCinemaId());
                        ps.setString(2, room.getName());
                        ps.setString(3, normalizedRoomType(room.getRoomType()));
                        ps.executeUpdate();
                        try (ResultSet keys = ps.getGeneratedKeys()) {
                            if (keys.next()) {
                                room.setId(keys.getInt(1));
                            }
                        }
                    }
                    generateSeats(connection, room.getId(), rowCount, seatsPerRow, vipRows);
                }
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the luu phong.", ex);
        }
        auditAfterCommit(actor.getId(), creating ? "CREATE_ROOM" : "UPDATE_ROOM",
                "Room", String.valueOf(room.getId()), room.getName());
    }

    public Map<String, Object> getRoomDeleteImpactInfo(int roomId) {
        Map<String, Object> result = new java.util.HashMap<>();
        String sqlRoom = "SELECT r.Name, ISNULL(r.Status, 'active'), c.Name AS CinemaName FROM Rooms r JOIN Cinemas c ON c.Id = r.CinemaId WHERE r.Id = ?";
        String sqlShowtimes = "SELECT COUNT(*) FROM Showtimes WHERE RoomId = ?";
        String sqlActiveShowtimes = "SELECT COUNT(*) FROM Showtimes WHERE RoomId = ? AND EndTime > GETDATE()";
        String sqlTotalTickets = "SELECT COUNT(*) FROM Orders o JOIN Showtimes s ON s.Id = o.ShowtimeId WHERE s.RoomId = ?";
        String sqlPendingTickets = "SELECT COUNT(*) FROM Orders o JOIN Showtimes s ON s.Id = o.ShowtimeId WHERE s.RoomId = ? AND o.OrderStatus NOT IN ('redeemed', 'cancelled')";

        try (Connection conn = DBConnection.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(sqlRoom)) {
                ps.setInt(1, roomId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        result.put("roomName", rs.getString(1));
                        result.put("status", rs.getString(2));
                        result.put("cinemaName", rs.getString(3));
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(sqlShowtimes)) {
                ps.setInt(1, roomId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next())
                        result.put("showtimeCount", rs.getInt(1));
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(sqlActiveShowtimes)) {
                ps.setInt(1, roomId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next())
                        result.put("activeShowtimeCount", rs.getInt(1));
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(sqlTotalTickets)) {
                ps.setInt(1, roomId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next())
                        result.put("totalTicketCount", rs.getInt(1));
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(sqlPendingTickets)) {
                ps.setInt(1, roomId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next())
                        result.put("pendingTicketCount", rs.getInt(1));
                }
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the tai thong tin tac dong phong.", ex);
        }
        return result;
    }

    public Map<String, Object> getRoomDeleteImpactInfo(int roomId, User actor) {
        assertRoomScope(actor, roomId);
        return getRoomDeleteImpactInfo(roomId);
    }

    public void deleteRoom(int roomId, User actor) {
        deleteRoom(roomId, actor, false);
    }

    /**
     * Xoa phong: soft delete khi con lich su, hard delete khi sach.
     *
     * <p><b>Loi da sua (D-03).</b> Nhanh soft delete ghi {@code Status = 'deleted'}, nhung
     * {@code fix34} lai dat {@code CK_Rooms_Status CHECK (LOWER(Status) IN ('active','inactive'))}.
     * Hai thu mau thuan tuyet doi, nen <b>nhanh soft delete chua bao gio chay duoc</b>: moi lan
     * xoa mot phong con lich su deu vo rang buoc CHECK, roi bi khoi {@code catch} ben duoi doi
     * thanh <i>"Vui lòng kiểm tra dữ liệu liên quan"</i> — dung thong bao lam nguoi dung tuong la
     * ghe con bi giu. {@code fix40_room_deleted_status.sql} noi rong rang buoc do.</p>
     *
     * <p><b>Luu y:</b> {@code forceHardDelete} hien <b>khong duoc doc o dau ca</b> — che do xoa
     * duoc quyet dinh hoan toan boi {@code showtimeCount}/{@code totalTicketCount}. Chu ky duoc
     * giu nguyen vi hai IT dang truyen {@code true}; dung tin vao ten tham so nay.</p>
     */
    public void deleteRoom(int roomId, User actor, boolean forceHardDelete) {
        assertRoomScope(actor, roomId);
        Map<String, Object> impact = getRoomDeleteImpactInfo(roomId);
        int showtimeCount = (int) impact.getOrDefault("showtimeCount", 0);
        int activeShowtimeCount = (int) impact.getOrDefault("activeShowtimeCount", 0);
        int totalTicketCount = (int) impact.getOrDefault("totalTicketCount", 0);
        int pendingTicketCount = (int) impact.getOrDefault("pendingTicketCount", 0);
        String roomName = (String) impact.getOrDefault("roomName", "Phòng #" + roomId);

        if (activeShowtimeCount > 0) {
            throw new BookingException(400, "Không thể xóa phòng '" + roomName
                    + "' vì đang có " + activeShowtimeCount + " suất chiếu đang/sắp diễn ra. Vui lòng hủy các suất chiếu trước.");
        }

        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                boolean softDeleteDone = false;
                if (totalTicketCount > 0 || showtimeCount > 0) {
                    softDeleteDone = true;
                } else {
                    // Thử Hard Delete (dọn dẹp sơ đồ ghế và phòng)
                    try {
                        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM Seats WHERE RoomId = ?")) {
                            ps.setInt(1, roomId);
                            ps.executeUpdate();
                        }
                        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM Rooms WHERE Id = ?")) {
                            ps.setInt(1, roomId);
                            ps.executeUpdate();
                        }
                        int removedNotifications = notificationDAO.deleteByTarget(connection, "Room", String.valueOf(roomId));
                        connection.commit();
                        String detailJson = String.format(
                                "{\"roomName\":\"%s\",\"deletedMode\":\"HARD_DELETE\",\"resolvedNotifications\":%d}",
                                roomName, removedNotifications);
                        auditAfterCommit(actor.getId(), "DELETE_ROOM_HARD", "Room", String.valueOf(roomId), detailJson);
                        return;
                    } catch (SQLException ex) {
                        // Fail-safe: con rang buoc nao do khong cho xoa cung -> lui ve soft delete
                        // de khong mat du lieu. Ghi lai nguyen nhan; ban cu nuot im lang bang mot
                        // dieu kien chet `if (ex != null)`, nen khong ai biet vi sao lai re nhanh.
                        LOGGER.log(Level.WARNING, ex,
                                () -> "Khong the xoa cung phong #" + roomId + ", chuyen sang soft delete");
                        connection.rollback();
                        connection.setAutoCommit(false);
                        softDeleteDone = true;
                    }
                }

                if (softDeleteDone) {
                    // Soft Delete: Chuyển Status = 'deleted' để bảo toàn 100% dữ liệu lịch sử & doanh thu
                    try (PreparedStatement ps = connection.prepareStatement(
                            "UPDATE Rooms SET Status = 'deleted', UpdatedAt = GETDATE() WHERE Id = ?")) {
                        ps.setInt(1, roomId);
                        ps.executeUpdate();
                    }
                    notificationDAO.deleteByTarget(connection, "Room", String.valueOf(roomId));
                    connection.commit();
                    String detailJson = String.format(
                            "{\"roomName\":\"%s\",\"showtimeCount\":%d,\"totalTicketCount\":%d,\"pendingTicketCount\":%d,\"deletedMode\":\"SOFT_DELETE\"}",
                            roomName, showtimeCount, totalTicketCount, pendingTicketCount);
                    auditAfterCommit(actor.getId(), "DELETE_ROOM_SOFT", "Room", String.valueOf(roomId), detailJson);
                }
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            // 500 chu khong phai 400: den day thi loi la cua he thong, khong phai cua du lieu
            // nguoi dung nhap. Thong bao cu do toi sai cho va che mat CK_Rooms_Status suot mot
            // thoi gian dai.
            throw new BookingException(500, "Không thể xóa phòng chiếu '" + roomName
                    + "' vì lệnh cơ sở dữ liệu thất bại. Thao tác đã được hoàn tác nguyên vẹn.", ex);
        }
    }

    public List<AdminNotification> listAdminNotifications() {
        checkAndNotifyCompletedInactiveRooms();
        checkAndNotifySoldOutShowtimes();
        return notificationDAO.findAll();
    }

    /**
     * Danh sach canh bao ma {@code actor} duoc thay, kem trang thai da doc CUA RIENG actor do.
     *
     * <p><b>Loi da sua (FLOW-NOTIFY-SOLDOUT-004).</b> Trang thai da doc truoc day nam o mot cot
     * {@code AdminNotifications.IsRead} duy nhat. Manager va admin cung nhin mot dong canh bao,
     * nen manager bam "da doc" la admin mat luon dau chua doc — hai nguoi khac nhau bi ep dung
     * chung mot hop thu. "Da doc" la thuoc tinh cua CAP (thong bao, nguoi nhan), nen no da
     * chuyen sang {@code NotificationRecipients}.</p>
     */
    public List<AdminNotification> listAdminNotifications(User actor) {
        List<AdminNotification> notifications = listAdminNotifications();
        if (ScopeUtil.isManager(actor)) {
            notifications = notifications.stream()
                    .filter(note -> notificationWithinScope(note, actor)).toList();
        }
        return applyRecipientReadState(notifications, actor);
    }

    /**
     * Gan lai co {@code isRead} theo so nhan cua {@code actor} va sap chua doc len truoc.
     *
     * <p>Ban ghi khong co so nhan = chua doc. Nho vay canh bao cu (co truoc bang so nhan) hien
     * ra la chua doc cho moi nguoi thay vi bien mat khoi tam mat — an mot canh bao van hanh
     * nguy hiem hon la hien lai no mot lan thua.</p>
     */
    private List<AdminNotification> applyRecipientReadState(List<AdminNotification> notifications,
            User actor) {
        if (actor == null || actor.getId() <= 0 || notifications.isEmpty()) {
            return notifications;
        }
        Set<Integer> read = recipientDAO.readNotificationIds(
                NotificationRecipientDAO.SOURCE_ADMIN, actor.getId());
        List<AdminNotification> view = new ArrayList<>(notifications);
        for (AdminNotification note : view) {
            note.setRead(read.contains(note.getId()));
        }
        // DAO sap xep theo cot IsRead dung chung; sap lai theo trang thai cua chinh actor.
        view.sort(Comparator.comparing(AdminNotification::isRead)
                .thenComparing(AdminNotification::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())));
        return view;
    }

    public int getUnreadNotificationCount() {
        checkAndNotifySoldOutShowtimes();
        return notificationDAO.countUnread();
    }

    public int getUnreadNotificationCount(User actor) {
        if (actor == null || actor.getId() <= 0) {
            return getUnreadNotificationCount();
        }
        return (int) listAdminNotifications(actor).stream().filter(note -> !note.isRead()).count();
    }

    /**
     * Danh dau da doc o muc he thong.
     *
     * <p>Chi con dung cho cac luong khong co actor (job nen, don dep). Moi duong vao tu giao dien
     * deu di qua {@link #markNotificationRead(int, User)} de trang thai la cua rieng nguoi doc.</p>
     */
    public void markNotificationRead(int notificationId) {
        notificationDAO.markAsRead(notificationId);
    }

    public void markNotificationRead(int notificationId, User actor) {
        if (ScopeUtil.isManager(actor)) {
            AdminNotification note = notificationDAO.findAll().stream()
                    .filter(item -> item.getId() == notificationId).findFirst()
                    .orElseThrow(() -> new BookingException(404, "Không tìm thấy thông báo."));
            if (!notificationWithinScope(note, actor)) {
                throw new BookingException(403, "Thông báo không thuộc cụm rạp được phân quyền.");
            }
        }
        if (actor == null || actor.getId() <= 0) {
            markNotificationRead(notificationId);
            return;
        }
        recipientDAO.markRead(NotificationRecipientDAO.SOURCE_ADMIN, notificationId, actor.getId());
    }

    public void markAllNotificationsRead() {
        notificationDAO.markAllAsRead();
    }

    public void markAllNotificationsRead(User actor) {
        if (actor == null || actor.getId() <= 0) {
            markAllNotificationsRead();
            return;
        }
        Set<Integer> visible = listAdminNotifications(actor).stream()
                .map(AdminNotification::getId)
                .collect(java.util.stream.Collectors.toSet());
        recipientDAO.markAllRead(NotificationRecipientDAO.SOURCE_ADMIN, visible, actor.getId());
    }

    private boolean notificationWithinScope(AdminNotification note, User actor) {
        try {
            if (note.getCinemaId() != null) {
                return note.getCinemaId().equals(requireActorCinema(actor));
            }
            int targetId = Integer.parseInt(note.getTargetId());
            String type = note.getTargetType() == null ? "" : note.getTargetType().toLowerCase();
            if (type.contains("showtime"))
                assertShowtimeScope(actor, targetId);
            else if (type.contains("room"))
                assertRoomScope(actor, targetId);
            else if (type.contains("order"))
                assertOrderScope(actor, targetId);
            else if (type.contains("comment"))
                assertCommentInScope(actor, targetId);
            else if (type.contains("userappeal"))
                assertAppealNotificationScope(actor, targetId);
            else
                return false;
            return true;
        } catch (NumberFormatException | BookingException ex) {
            return false;
        }
    }

    /**
     * Dinh nghia "het ghe": moi ghe CO THE BAN cua suat deu da o trang thai {@code booked}.
     *
     * <p><b>Loi da sua (FLOW-NOTIFY-SOLDOUT-001).</b> Ban cu dem so ghe con
     * {@code Status='available'} va coi 0 la het ghe, nen ghe dang {@code held} — mot cho giu
     * tam 10 phut, co the nha ra bat cu luc nao — bi tinh nhu da ban. Chi can vai khach cung mo
     * so do ghe la manager nhan canh bao "chay ve" cho mot suat chua ban duoc ve nao.</p>
     *
     * <p>Ghe {@code maintenance} khong nam trong mau so: no khong bao gio ban duoc, nen mot phong
     * co ghe hong se khong bao gio dat 100% neu tinh ca chung.</p>
     */
    private static final String SOLD_OUT_SHOWTIME_QUERY = """
            SELECT s.Id AS ShowtimeId,
                   s.StartTime,
                   f.Title AS FilmTitle,
                   c.Name AS CinemaName,
                   r.Name AS RoomName
            FROM Showtimes s
            JOIN Films f ON f.Id = s.FilmId
            JOIN Cinemas c ON c.Id = s.CinemaId
            JOIN Rooms r ON r.Id = s.RoomId
            JOIN (
                SELECT ShowtimeId,
                       SUM(CASE WHEN Status <> 'maintenance' THEN 1 ELSE 0 END) AS BookableSeats,
                       SUM(CASE WHEN Status = 'booked' THEN 1 ELSE 0 END) AS BookedSeats
                FROM ShowtimeSeats
                GROUP BY ShowtimeId
            ) st ON st.ShowtimeId = s.Id
            WHERE st.BookableSeats > 0 AND st.BookedSeats = st.BookableSeats
              AND s.StartTime >= DATEADD(day, -1, GETDATE())
            """;

    public void checkAndNotifySoldOutShowtimes() {
        resolveStaleSoldOutAlerts();
        String sql = SOLD_OUT_SHOWTIME_QUERY;
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
            while (rs.next()) {
                int showtimeId = rs.getInt("ShowtimeId");
                String filmTitle = rs.getString("FilmTitle");
                String cinemaName = rs.getString("CinemaName");
                String roomName = rs.getString("RoomName");
                Timestamp stTimestamp = rs.getTimestamp("StartTime");
                String timeStr = stTimestamp != null ? stTimestamp.toLocalDateTime().format(dtf) : "";

                notifyShowtimeSoldOut(showtimeId, filmTitle, cinemaName, roomName, timeStr);
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Khong the quet suat chieu het ghe", ex);
        }
    }

    public void notifyShowtimeSoldOut(int showtimeId, String filmTitle, String cinemaName, String roomName,
            String timeStr) {
        String targetId = String.valueOf(showtimeId);
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                boolean exists;
                try (PreparedStatement check = connection.prepareStatement("""
                        SELECT TOP 1 Id
                        FROM AdminNotifications WITH (UPDLOCK,HOLDLOCK)
                        WHERE Category='room' AND TargetType='Showtime_SoldOut' AND TargetId=?
                        """)) {
                    check.setString(1, targetId);
                    try (ResultSet result = check.executeQuery()) {
                        exists = result.next();
                    }
                }
                if (!exists) {
                    try (PreparedStatement insert = connection.prepareStatement("""
                            INSERT INTO AdminNotifications
                                (Title,Message,Category,Severity,TargetType,TargetId,ActionUrl,IsRead,CreatedAt)
                            VALUES (?,?,'room','warning','Showtime_SoldOut',?,?,0,GETDATE())
                            """)) {
                        insert.setString(1, "Phòng \"" + roomName + "\" (" + cinemaName + ") đã HẾT GHẾ");
                        insert.setString(2, "Suất chiếu phim \"" + filmTitle + "\" lúc " + timeStr
                                + " tại phòng \"" + roomName + "\" (" + cinemaName
                                + ") đã bị khách mua HẾT GHẾ (0 ghế trống còn lại).");
                        insert.setString(3, targetId);
                        insert.setString(4, "/admin/showtimes?focusShowtimeId=" + showtimeId);
                        insert.executeUpdate();
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể tạo thông báo suất chiếu hết ghế.", ex);
        }
    }

    /**
     * Soan va phat mot thong bao toi nguoi dung (FLOW-NOTIFY-USER-001).
     *
     * <p>Tao ban ghi va phat so nhan trong CUNG mot transaction: mot thong bao ton tai ma khong
     * co nguoi nhan nao la thong bao khong ai doc duoc, va no se nam do vinh vien vi khong co
     * co che phat lai.</p>
     *
     * <p>Manager bi rang buoc hai lop: chi gui duoc pham vi {@code CINEMA} cua chinh rap minh,
     * hoac {@code USER}/{@code TIER} — {@code ALL} va rap khac danh cho admin.</p>
     *
     * @return so nguoi nhan thuc te
     */
    public int publishUserNotification(UserNotification notification, User actor) {
        if (notification == null || notification.getTitle() == null
                || notification.getTitle().isBlank()) {
            throw new BookingException(400, "Thông báo phải có tiêu đề.");
        }
        if (notification.getMessage() == null || notification.getMessage().isBlank()) {
            throw new BookingException(400, "Thông báo phải có nội dung.");
        }
        String targetType = notification.getTargetType() == null
                ? UserNotification.TARGET_ALL : notification.getTargetType().toUpperCase();
        notification.setTargetType(targetType);

        if (ScopeUtil.isManager(actor)) {
            int cinemaId = requireActorCinema(actor);
            if (UserNotification.TARGET_ALL.equals(targetType)) {
                throw new BookingException(403,
                        "Quản lý cụm rạp không được gửi thông báo toàn hệ thống.");
            }
            if (UserNotification.TARGET_CINEMA.equals(targetType)) {
                ScopeUtil.assertCinemaScope(actor, parseTargetCinema(notification));
            }
            notification.setCinemaId(cinemaId);
        }
        notification.setCreatedByUserId(actor == null ? 0 : actor.getId());

        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int notificationId = userNotificationDAO.create(connection, notification);
                Set<Integer> recipients = userNotificationDAO.resolveRecipients(connection, notification);
                int delivered = recipientDAO.deliver(connection, notificationId, recipients);
                connection.commit();
                notification.setId(notificationId);
                auditAfterCommit(actor == null ? null : actor.getId(), "PUBLISH_USER_NOTIFICATION",
                        "UserNotification", String.valueOf(notificationId),
                        "{\"targetType\":\"" + targetType + "\",\"recipients\":" + delivered + "}");
                return delivered;
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (IllegalArgumentException ex) {
            throw new BookingException(400, ex.getMessage(), ex);
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể gửi thông báo tới người dùng.", ex);
        }
    }

    /** Hop thu thong bao cua mot nguoi dung, kem trang thai da doc cua chinh ho. */
    public List<UserNotification> listUserNotifications(int userId, boolean unreadOnly) {
        return userNotificationDAO.inbox(userId, unreadOnly);
    }

    /** Nguoi dung danh dau da doc mot thong bao trong hop thu cua minh. */
    public void markUserNotificationRead(int notificationId, int userId) {
        recipientDAO.markRead(NotificationRecipientDAO.SOURCE_USER, notificationId, userId);
    }

    private int parseTargetCinema(UserNotification notification) {
        try {
            return Integer.parseInt(notification.getTargetId());
        } catch (NumberFormatException ex) {
            throw new BookingException(400, "Phạm vi CINEMA cần TargetId là mã rạp.", ex);
        }
    }

    /**
     * Go canh bao "het ghe" cua nhung suat khong con het ghe (FLOW-NOTIFY-SOLDOUT-003).
     *
     * <p>Huy don hoac hoan ve tra ghe ve {@code available}; neu canh bao cu van nam do thi manager
     * doc mot trang thai da khong con dung, va con te hon la se bo qua canh bao that o lan sau.
     * Canh bao la <i>trang thai hien tai</i> chu khong phai su kien lich su, nen no phai tu tat
     * khi dieu kien sinh ra no khong con.</p>
     *
     * <p>Doi xung voi phan don canh bao phong da hoat dong tro lai o
     * {@link #checkAndNotifyCompletedInactiveRooms()}.</p>
     */
    private void resolveStaleSoldOutAlerts() {
        String sql = """
                DELETE FROM AdminNotifications
                WHERE TargetType = 'Showtime_SoldOut'
                  AND TargetId NOT IN (
                      SELECT CAST(soldOut.ShowtimeId AS NVARCHAR(50)) FROM (
                      """ + SOLD_OUT_SHOWTIME_QUERY + """
                      ) soldOut)
                """;
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            int resolved = ps.executeUpdate();
            if (resolved > 0) {
                LOGGER.log(Level.INFO, "Da go {0} canh bao het ghe khong con dung", resolved);
            }
            // So nhan tro toi canh bao vua xoa phai di theo: NotificationId khong co FK
            // (no tro toi hai bang), nen khong co cascade lam thay.
            recipientDAO.deleteOrphans(connection, NotificationRecipientDAO.SOURCE_ADMIN,
                    "AdminNotifications");
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Khong the go canh bao het ghe da cu", ex);
        }
    }

    public void activateRoom(int roomId, User actor) {
        assertRoomScope(actor, roomId);
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn
                        .prepareStatement("UPDATE Rooms SET Status = 'active', UpdatedAt = GETDATE() WHERE Id = ?")) {
            ps.setInt(1, roomId);
            ps.executeUpdate();
            logAction(actor.getId(), "REACTIVATE_ROOM", "Room", String.valueOf(roomId), "{\"status\":\"active\"}");
            checkAndNotifyCompletedInactiveRooms();
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the khoi phuc hoat dong phong chieu.", ex);
        }
    }

    public void deactivateRoom(int roomId, User actor) {
        assertRoomScope(actor, roomId);
        Map<String, Object> impact = getRoomDeleteImpactInfo(roomId);
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(
                        "UPDATE Rooms SET Status='inactive', UpdatedAt=GETDATE() WHERE Id=?")) {
            ps.setInt(1, roomId);
            if (ps.executeUpdate() == 0) {
                throw new BookingException(404, "Không tìm thấy phòng chiếu.");
            }
            logAction(actor.getId(), "DEACTIVATE_ROOM", "Room", String.valueOf(roomId), impact.toString());
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể tạm ngừng phòng chiếu.", ex);
        }
    }

    public void checkAndNotifyCompletedInactiveRooms() {
        // Auto cleanup stale notifications for rooms that are currently active
        String sqlCleanupStale = """
                DELETE FROM AdminNotifications
                WHERE Category = 'room' AND TargetType = 'Room'
                AND TargetId IN (SELECT CAST(Id AS VARCHAR(50)) FROM Rooms WHERE ISNULL(Status, 'active') = 'active')
                """;
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement psClean = conn.prepareStatement(sqlCleanupStale)) {
            psClean.executeUpdate();
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Khong the don thong bao phong cu", ex);
        }

        String sqlInactiveRooms = """
                SELECT r.Id, r.Name, c.Name AS CinemaName
                FROM Rooms r
                JOIN Cinemas c ON c.Id = r.CinemaId
                WHERE ISNULL(r.Status, 'active') = 'inactive'
                """;
        String sqlPendingTickets = """
                SELECT COUNT(*)
                FROM Orders o
                JOIN Showtimes s ON s.Id = o.ShowtimeId
                WHERE s.RoomId = ? AND o.OrderStatus NOT IN ('redeemed', 'cancelled')
                """;
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement psRoom = conn.prepareStatement(sqlInactiveRooms);
                ResultSet rsRoom = psRoom.executeQuery()) {
            while (rsRoom.next()) {
                int roomId = rsRoom.getInt("Id");
                String roomName = rsRoom.getString("Name");
                String cinemaName = rsRoom.getString("CinemaName");

                try (PreparedStatement psCheck = conn.prepareStatement(sqlPendingTickets)) {
                    psCheck.setInt(1, roomId);
                    try (ResultSet rsCheck = psCheck.executeQuery()) {
                        if (rsCheck.next() && rsCheck.getInt(1) == 0) {
                            if (!notificationDAO.existsNotificationForTarget("room", "Room", String.valueOf(roomId))) {
                                AdminNotification note = new AdminNotification();
                                note.setTitle("Phòng \"" + roomName + "\" đã hoàn tất tất cả suất chiếu & vé");
                                note.setMessage("Phòng chiếu \"" + roomName + "\" (Rạp " + cinemaName
                                        + ") đang ở trạng thái Ngừng hoạt động đã sạch toàn bộ vé chờ check-in. Vui lòng xem xét Xóa vĩnh viễn hoặc Khôi phục hoạt động.");
                                note.setCategory("room");
                                note.setSeverity("warning");
                                note.setTargetType("Room");
                                note.setTargetId(String.valueOf(roomId));
                                note.setActionUrl("/admin/rooms");
                                notificationDAO.createNotification(note);
                            }
                        }
                    }
                }
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Khong the quet phong ngung hoat dong", ex);
        }
    }

    public List<Showtime> listShowtimes() {
        String sql = """
                SELECT TOP (500) s.*, f.Title AS FilmTitle, c.Name AS CinemaName, r.Name AS RoomName
                FROM Showtimes s
                JOIN Films f ON f.Id = s.FilmId
                JOIN Cinemas c ON c.Id = s.CinemaId
                JOIN Rooms r ON r.Id = s.RoomId
                ORDER BY s.StartTime DESC
                """;
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            List<Showtime> showtimes = new ArrayList<>();
            while (rs.next()) {
                showtimes.add(mapShowtime(rs));
            }
            return showtimes;
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the tai danh sach suat chieu.", ex);
        }
    }

    public List<Showtime> listShowtimes(User actor) {
        if (!ScopeUtil.isManager(actor))
            return listShowtimes();
        String sql = """
                SELECT s.*, f.Title AS FilmTitle, c.Name AS CinemaName, r.Name AS RoomName
                FROM Showtimes s
                JOIN Films f ON f.Id = s.FilmId
                JOIN Cinemas c ON c.Id = s.CinemaId
                JOIN Rooms r ON r.Id = s.RoomId
                WHERE s.CinemaId = ?
                ORDER BY s.StartTime DESC
                """;
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, requireActorCinema(actor));
            try (ResultSet rs = ps.executeQuery()) {
                List<Showtime> showtimes = new ArrayList<>();
                while (rs.next()) {
                    showtimes.add(mapShowtime(rs));
                }
                return showtimes;
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the tai danh sach suat chieu.", ex);
        }
    }

    /** Loads one showtime for the dedicated edit screen, enforcing manager scope. */
    public Optional<Showtime> findShowtimeById(int showtimeId, User actor) {
        if (showtimeId <= 0) {
            return Optional.empty();
        }
        StringBuilder sql = new StringBuilder("""
                SELECT s.*, f.Title AS FilmTitle, c.Name AS CinemaName, r.Name AS RoomName
                FROM Showtimes s
                JOIN Films f ON f.Id = s.FilmId
                JOIN Cinemas c ON c.Id = s.CinemaId
                JOIN Rooms r ON r.Id = s.RoomId
                WHERE s.Id = ?
                """);
        boolean scoped = ScopeUtil.isManager(actor);
        if (scoped) {
            sql.append(" AND s.CinemaId = ?");
        }
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            ps.setInt(1, showtimeId);
            if (scoped) {
                ps.setInt(2, requireActorCinema(actor));
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapShowtime(rs)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the tai suat chieu.", ex);
        }
    }

    /** Preview impact for the confirmation step before a material showtime edit. */
    public Map<String, Object> getShowtimeChangeImpactInfo(int showtimeId, User actor) {
        if (showtimeId <= 0) {
            throw new BookingException(400, "Suat chieu khong hop le.");
        }
        StringBuilder sql = new StringBuilder("""
                SELECT s.Id,
                       COUNT(DISTINCT CASE WHEN LOWER(ISNULL(o.OrderStatus,'')) NOT IN
                            ('cancelled','canceled','refunded','expired')
                            AND LOWER(ISNULL(o.PaymentStatus,'')) <> 'refunded' THEN o.Id END) AS OrderCount,
                       COUNT(DISTINCT CASE WHEN LOWER(ISNULL(o.OrderStatus,'')) NOT IN
                            ('cancelled','canceled','refunded','expired')
                            AND LOWER(ISNULL(o.PaymentStatus,'')) <> 'refunded' THEN o.UserId END) AS CustomerCount,
                       COUNT(DISTINCT CASE WHEN ss.Status IN ('held','booked') OR ss.ClaimedByOrderId IS NOT NULL
                            OR EXISTS (SELECT 1 FROM OrderSeats os WHERE os.ShowtimeSeatId=ss.Id) THEN ss.Id END) AS OccupiedSeatCount
                FROM Showtimes s
                LEFT JOIN Orders o ON o.ShowtimeId=s.Id
                LEFT JOIN ShowtimeSeats ss ON ss.ShowtimeId=s.Id
                WHERE s.Id=?
                """);
        boolean scoped = ScopeUtil.isManager(actor);
        if (scoped) {
            sql.append(" AND s.CinemaId=?");
        }
        sql.append(" GROUP BY s.Id");
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            ps.setInt(1, showtimeId);
            if (scoped) {
                ps.setInt(2, requireActorCinema(actor));
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new BookingException(404, "Khong tim thay suat chieu.");
                }
                Map<String, Object> impact = new LinkedHashMap<>();
                impact.put("showtimeId", rs.getInt("Id"));
                impact.put("orderCount", rs.getInt("OrderCount"));
                impact.put("customerCount", rs.getInt("CustomerCount"));
                impact.put("occupiedSeatCount", rs.getInt("OccupiedSeatCount"));
                impact.put("requiresConfirmation", rs.getInt("OrderCount") > 0);
                return impact;
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the kiem tra anh huong suat chieu.", ex);
        }
    }

    public ShowtimeChangeImpact previewShowtimeChangeImpact(int showtimeId, User actor) {
        Map<String, Object> impact = getShowtimeChangeImpactInfo(showtimeId, actor);
        return new ShowtimeChangeImpact(showtimeId,
                (int) impact.getOrDefault("orderCount", 0),
                (int) impact.getOrDefault("customerCount", 0),
                (int) impact.getOrDefault("occupiedSeatCount", 0),
                Boolean.TRUE.equals(impact.get("requiresConfirmation")));
    }

    /**
     * Dinh dang suat chieu duoc phep chon. Kiem o server, khong tin dropdown cua
     * trinh duyet.
     */
    private static final Set<String> ALLOWED_SHOWTIME_FORMATS = Set.of("2D", "3D", "IMAX", "4DX");

    /**
     * Tran so suat chieu duoc luu trong MOT transaction (N-08).
     *
     * <p>31 = so ngay cua thang dai nhat: du cho moi lich lap thuc te ("chieu hang ngay
     * trong thang nay"), va du nho de mot transaction khong giu khoa qua lau.</p>
     */
    public static final int MAX_SHOWTIME_BATCH_SIZE = 31;

    public void saveShowtime(Showtime showtime, User actor) {
        saveShowtime(showtime, actor, false);
    }

    /**
     * Luu mot suat chieu, co duong xac nhan anh huong danh cho quan ly (BUG-06, INV-8).
     *
     * @param confirmImpact quan ly da doc canh bao va chap nhan doi tham so cua mot suat DA BAN VE.
     *                      Khi bat, he thong bat buoc gui thong bao cho moi nguoi dang giu ve
     *                      trong <b>cung transaction</b> voi thay doi.
     */
    public void saveShowtime(Showtime showtime, User actor, boolean confirmImpact) {
        saveShowtimes(List.of(showtime), actor, confirmImpact);
    }

    /**
     * Luu mot hoac nhieu suat chieu trong <b>mot transaction duy nhat</b> (ST-02).
     *
     * <p>
     * <b>Van de goc.</b> Chuc nang "lap lai N ngay" o servlet goi
     * {@code saveShowtime()} N lan,
     * moi lan mot transaction rieng. Neu ngay thu 3 trung lich thi ngay 1 va 2 da
     * nam trong DB
     * roi, con nguoi dung nhan duoc mot thong bao loi — khong biet la da tao duoc
     * mot phan hay
     * chua tao gi. Sua lai lich con kho hon vi khong ro phai xoa nhung suat nao.
     * </p>
     *
     * <p>
     * Nay ca loat nam trong mot transaction: hoac tat ca cac ngay duoc tao, hoac
     * khong ngay
     * nao duoc tao va thong bao chi ro ngay nao vuong.
     * </p>
     *
     * @return so suat chieu da luu
     */
    public int saveShowtimes(List<Showtime> showtimes, User actor) {
        return saveShowtimes(showtimes, actor, false);
    }

    public int saveShowtimes(List<Showtime> showtimes, User actor, boolean confirmImpact) {
        if (showtimes == null || showtimes.isEmpty()) {
            throw new BookingException(400, "Không có suất chiếu nào để lưu.");
        }
        // N-08: tran phai o TANG SERVICE, khong chi o servlet.
        //
        // ManagerPortalServlet doc repeatDays bang Math.max(1, ...) — chi chan phia duoi.
        // Mot POST repeatDays=100000 tu tai khoan manager hop le sinh 100.000 suat cong
        // ~3,6 trieu dong ShowtimeSeats trong MOT transaction: lock escalation, pool 20
        // connection can, ca site treo. Dat chot o day de moi duong goi (servlet, REST,
        // batch) deu di qua cung mot gioi han.
        if (showtimes.size() > MAX_SHOWTIME_BATCH_SIZE) {
            throw new BookingException(400, "Chỉ được tạo tối đa " + MAX_SHOWTIME_BATCH_SIZE
                    + " suất chiếu trong một lần lưu (đang yêu cầu " + showtimes.size() + ").");
        }
        for (Showtime showtime : showtimes) {
            ScopeUtil.assertCinemaScope(actor, showtime.getCinemaId());
            if (showtime.getId() > 0)
                assertShowtimeScope(actor, showtime.getId());
            validateShowtimeInput(showtime);
        }

        // B.2: gom audit lai, ghi SAU commit. Truoc day logAction duoc goi ngay trong vong lap,
        // ma no mo mot Connection RIENG chay autocommit — vua vi pham quy uoc "khong mo connection
        // moi khi dang giu khoa", vua khien dong audit cua suat thu nhat NAM LAI khi suat thu hai
        // lam ca lo rollback: audit mo ta mot thay doi da bi huy.
        List<PendingAudit> pendingAudits = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                for (Showtime showtime : showtimes) {
                    validateShowtimeRelations(connection, showtime);
                    persistShowtime(connection, showtime, actor, confirmImpact, pendingAudits);
                }
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể lưu suất chiếu.", ex);
        }
        flushPendingAudits(pendingAudits);
        return showtimes.size();
    }

    /** Mot dong audit da san sang nhung phai doi transaction commit xong moi duoc ghi (B.2). */
    private record PendingAudit(Integer actorUserId, String action, String targetType, String targetId,
            String beforeJson, String afterJson) {
    }

    private void flushPendingAudits(List<PendingAudit> pending) {
        for (PendingAudit entry : pending) {
            auditAfterCommit(entry.actorUserId(), entry.action(), entry.targetType(), entry.targetId(),
                    entry.beforeJson(), entry.afterJson());
        }
    }

    /** Kiem tra cac truong khong can truy van DB. */
    private void validateShowtimeInput(Showtime showtime) {
        if (showtime.getStartTime() == null || showtime.getEndTime() == null) {
            throw new BookingException(400, "Vui lòng nhập đầy đủ ngày và giờ chiếu.");
        }
        if (!showtime.getEndTime().isAfter(showtime.getStartTime())) {
            throw new BookingException(400, "Giờ kết thúc phải sau giờ bắt đầu.");
        }
        // N-08 / ST-02: gio bat dau khong duoc nam trong qua khu, do theo GIO DB.
        //
        // Truoc day khong duong dan nao so getStartTime() voi gio hien tai, nen admin xep
        // duoc suat cho HOM QUA: suat luu thanh cong, sinh du ShowtimeSeats, hien trong danh
        // sach va lam sai ti le lap ghe cua bao cao. BusinessClock la dong ho da dong bo voi
        // GETDATE() — dung LocalDateTime.now() o day se tai hien dung lop loi ma CLAUDE.md
        // cam ("tron gio app voi GETDATE()").
        LocalDateTime businessNow = BusinessClock.now();
        if (showtime.getStartTime().isBefore(businessNow)) {
            throw new BookingException(400, "Giờ chiếu " + showtime.getStartTime()
                    + " đã nằm trong quá khứ (hiện tại là " + businessNow.withNano(0)
                    + "). Hãy chọn thời điểm trong tương lai.");
        }
        if (showtime.getBasePrice() == null || showtime.getBasePrice().signum() <= 0) {
            throw new BookingException(400, "Giá vé phải lớn hơn 0.");
        }
        String format = showtime.getFormat();
        if (format != null && !format.isBlank() && !ALLOWED_SHOWTIME_FORMATS.contains(format.trim().toUpperCase())) {
            throw new BookingException(400, "Định dạng chiếu không hợp lệ. Chỉ nhận: "
                    + String.join(", ", ALLOWED_SHOWTIME_FORMATS) + ".");
        }
    }

    /**
     * Kiem tra quan he phim–rap–phong va vong doi phim o <b>tang server</b>
     * (ST-01).
     *
     * <p>
     * <b>Van de goc.</b> Form co bien {@code cinemaFilmMap} de JavaScript loc
     * dropdown, nhung
     * {@code saveShowtime()} khong kiem lai bat cu quan he nao. Ba tinh huong deu
     * lot:
     * </p>
     * <ul>
     * <li>phim chua duoc gan cho rap — chinh la truong hop phim moi admin vua
     * tao;</li>
     * <li>phong thuoc rap KHAC voi rap cua suat chieu, hoac phong dang tam
     * ngung;</li>
     * <li>ngay chieu nam ngoai khoang {@code ReleaseDate..EndDate} cua phim.</li>
     * </ul>
     * <p>
     * JavaScript chi la tien ich hien thi; request sua tay hoac dropdown cu trong
     * cache van
     * gui duoc du lieu sai, nen chot that phai o day.
     * </p>
     */
    private void validateShowtimeRelations(Connection connection, Showtime showtime) throws SQLException {
        // 1. Phong phai thuoc dung rap va dang hoat dong.
        String roomSql = """
                SELECT r.Name, r.CinemaId, ISNULL(r.Status,'active') AS Status,
                       c.Name AS CinemaName, ISNULL(c.Status,'active') AS CinemaStatus
                FROM Rooms r LEFT JOIN Cinemas c ON c.Id = r.CinemaId
                WHERE r.Id = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(roomSql)) {
            ps.setInt(1, showtime.getRoomId());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new BookingException(400, "Không tìm thấy phòng chiếu đã chọn.");
                }
                int roomCinemaId = rs.getInt("CinemaId");
                if (roomCinemaId != showtime.getCinemaId()) {
                    throw new BookingException(400, "Phòng \"" + rs.getString("Name")
                            + "\" thuộc cụm rạp \"" + rs.getString("CinemaName")
                            + "\", không thuộc cụm rạp đã chọn cho suất chiếu này.");
                }
                // Phong/rap da xoa mem van con nguyen trong bang de giu doanh thu, nen dropdown
                // cu hoac mot request sua tay van gui duoc Id cua chung len day. Chot that phai
                // o server, khong phai o cho danh sach lua chon.
                if ("deleted".equalsIgnoreCase(rs.getString("Status"))) {
                    throw new BookingException(400, "Phòng \"" + rs.getString("Name")
                            + "\" đã bị xóa nên không thể xếp suất chiếu.");
                }
                if ("deleted".equalsIgnoreCase(rs.getString("CinemaStatus"))) {
                    throw new BookingException(400, "Rạp \"" + rs.getString("CinemaName")
                            + "\" đã bị xóa nên không thể xếp suất chiếu.");
                }
                if ("inactive".equalsIgnoreCase(rs.getString("Status"))) {
                    throw new BookingException(400, "Phòng \"" + rs.getString("Name")
                            + "\" đang tạm ngưng hoạt động nên không thể xếp suất chiếu.");
                }
            }
        }

        // 2. Phim phai duoc gan cho rap nay.
        String filmSql = """
                SELECT f.Title, f.Status, f.ReleaseDate, f.EndDate, f.DeletedAt,
                       (SELECT COUNT(*) FROM CinemaFilms cf WHERE cf.FilmId = f.Id AND cf.CinemaId = ? AND cf.Status=N'active') AS Assigned
                FROM Films f WHERE f.Id = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(filmSql)) {
            ps.setInt(1, showtime.getCinemaId());
            ps.setInt(2, showtime.getFilmId());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new BookingException(400, "Không tìm thấy phim đã chọn.");
                }
                String title = rs.getString("Title");
                if (rs.getTimestamp("DeletedAt") != null) {
                    throw new BookingException(409,
                            "Phim \"" + title + "\" đã được xóa và không thể xếp suất mới.");
                }
                if (rs.getInt("Assigned") == 0) {
                    throw new BookingException(400, "Phim \"" + title
                            + "\" chưa được gán cho cụm rạp này. "
                            + "Vào Quản lý rạp → Phim chiếu để gán phim cho rạp trước khi thêm suất.");
                }
                if ("ended".equalsIgnoreCase(rs.getString("Status"))) {
                    throw new BookingException(400, "Phim \"" + title
                            + "\" đang ở trạng thái Ngừng chiếu nên không thể xếp suất mới.");
                }
                // 3. Ngay chieu phai nam trong vong doi phim (EX-01).
                LocalDate showDate = showtime.getStartTime().toLocalDate();
                Date release = rs.getDate("ReleaseDate");
                if (release != null && showDate.isBefore(release.toLocalDate())) {
                    throw new BookingException(400, "Suất chiếu ngày " + showDate
                            + " nằm trước ngày khởi chiếu của phim \"" + title + "\" ("
                            + release.toLocalDate() + ").");
                }
                Date end = rs.getDate("EndDate");
                if (end != null && showDate.isAfter(end.toLocalDate())) {
                    throw new BookingException(400, "Suất chiếu ngày " + showDate
                            + " nằm sau ngày kết thúc chiếu của phim \"" + title + "\" ("
                            + end.toLocalDate() + "). Hãy gia hạn phim trước nếu vẫn muốn chiếu.");
                }
            }
        }
    }

    /**
     * Ghi mot suat chieu trong transaction dang mo. Khong tu commit.
     *
     * <p>Audit khong duoc ghi tai cho: no duoc dua vao {@code pendingAudits} de nguoi goi ghi sau
     * khi commit (B.2).</p>
     */
    private void persistShowtime(Connection connection, Showtime showtime, User actor, boolean confirmImpact,
            List<PendingAudit> pendingAudits) throws SQLException {
        {
            {
                checkShowtimeOverlap(connection, showtime);
                if (showtime.getId() > 0) {
                    try (PreparedStatement lifecycle = connection.prepareStatement(
                            "SELECT SaleStatus FROM Showtimes WITH (UPDLOCK,HOLDLOCK) WHERE Id=?")) {
                        lifecycle.setInt(1, showtime.getId());
                        try (ResultSet state = lifecycle.executeQuery()) {
                            if (!state.next()) throw new BookingException(404, "Không tìm thấy suất chiếu.");
                            if (!"ON_SALE".equalsIgnoreCase(state.getString(1))) {
                                throw new BookingException(409,
                                        "Suất chiếu đang ngưng bán hoặc đã xóa; hãy xử lý yêu cầu xóa trước khi sửa.");
                            }
                        }
                    }
                    boolean roomChanged = hasRoomChanged(connection, showtime.getId(), showtime.getRoomId());
                    // BUG-06 (INV-8): truoc day chi DOI PHONG moi qua ensureShowtimeEditable, nen
                    // doi StartTime cua suat da ban ve khong qua kiem tra nao — trong khi XOA chinh
                    // suat do lai bi chan dung. Mau thuan noi bo do bi xoa o day: moi thay doi
                    // trong yeu (gio bat dau, gio ket thuc, phong, phim) deu chiu cung mot chot.
                    ShowtimeSnapshot before = loadShowtimeSnapshot(connection, showtime.getId());
                    boolean materialChange = before != null && before.differsMateriallyFrom(showtime);
                    if (roomChanged) {
                        // B.5: doi phong KHONG override duoc, du co tick hay khong — chan ngay o
                        // day de quan ly nhan mot thong bao noi dung van de, thay vi loi
                        // "khong sua/xoa duoc suat" chung chung tu recreateShowtimeSeats.
                        ensureRoomChangeAllowed(connection, showtime.getId(), showtime.getRoomId());
                    }
                    if (materialChange && !confirmImpact) {
                        ensureShowtimeEditable(connection, showtime.getId());
                    }
                    try (PreparedStatement ps = connection.prepareStatement(
                            """
                                    UPDATE Showtimes
                                    SET FilmId = ?, CinemaId = ?, RoomId = ?, StartTime = ?, EndTime = ?, BasePrice = ?, Format = ?, Version = ?, Language = ?, UpdatedAt = GETDATE()
                                    WHERE Id = ?
                                    """)) {
                        bindShowtime(ps, showtime, false);
                        ps.setInt(10, showtime.getId());
                        ps.executeUpdate();
                    }
                    if (roomChanged) {
                        remapShowtimeSeats(connection, showtime.getId(), showtime.getRoomId());
                    }
                    pendingAudits.add(new PendingAudit(actor.getId(), "UPDATE_SHOWTIME", "Showtime",
                            String.valueOf(showtime.getId()), null, String.valueOf(showtime.getFilmId())));
                    if (materialChange && confirmImpact) {
                        notifyTicketHoldersOfShowtimeChange(connection, before, showtime, actor, pendingAudits);
                    }
                } else {
                    try (PreparedStatement ps = connection.prepareStatement(
                            """
                                    INSERT INTO Showtimes (FilmId, CinemaId, RoomId, StartTime, EndTime, BasePrice, Format, Version, Language)
                                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                                    """,
                            Statement.RETURN_GENERATED_KEYS)) {
                        bindShowtime(ps, showtime, true);
                        ps.executeUpdate();
                        try (ResultSet keys = ps.getGeneratedKeys()) {
                            if (keys.next()) {
                                showtime.setId(keys.getInt(1));
                            }
                        }
                    }
                    recreateShowtimeSeats(connection, showtime.getId(), showtime.getRoomId());
                    pendingAudits.add(new PendingAudit(actor.getId(), "CREATE_SHOWTIME", "Showtime",
                            String.valueOf(showtime.getId()), null, String.valueOf(showtime.getFilmId())));
                }
            }
        }
    }

    public ShowtimeDeletionImpact requestShowtimeDeletion(int showtimeId, User actor) {
        assertShowtimeScope(actor, showtimeId);
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                ShowtimeLifecycleLock.acquire(connection, showtimeId);
                ShowtimeDeletionImpact impact = loadShowtimeDeletionImpact(connection, showtimeId, actor, true);
                if ("DELETED".equals(impact.saleStatus())) {
                    throw new BookingException(409, "Suất chiếu đã được xóa và chỉ còn dữ liệu lịch sử.");
                }
                if (impact.committedOrderCount() > 0) {
                    throw new BookingException(409,
                            "Không thể ngưng bán: suất chiếu đang có order/vé còn hiệu lực.");
                }
                if (!"SUSPENDED".equals(impact.saleStatus())) {
                    try (PreparedStatement ps = connection.prepareStatement("""
                            UPDATE Showtimes
                            SET SaleStatus = 'SUSPENDED',
                                DeleteRequestedAt = SYSDATETIME(),
                                DeleteNotBefore = DATEADD(MINUTE, 5, SYSDATETIME()),
                                DeleteRequestedByUserId = ?, UpdatedAt = SYSDATETIME()
                            WHERE Id = ? AND SaleStatus = 'ON_SALE'
                            """)) {
                        ps.setInt(1, actor.getId());
                        ps.setInt(2, showtimeId);
                        if (ps.executeUpdate() != 1) {
                            throw new BookingException(409, "Trạng thái suất chiếu vừa thay đổi. Hãy tải lại.");
                        }
                    }
                    insertAudit(connection, actor.getId(), "REQUEST_DELETE_SHOWTIME", "Showtime",
                            String.valueOf(showtimeId), impact.saleStatus(), "SUSPENDED");
                }
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể ngưng bán suất chiếu.", ex);
        }
        return previewShowtimeDeletion(showtimeId, actor);
    }

    public ShowtimeDeletionImpact previewShowtimeDeletion(int showtimeId, User actor) {
        assertShowtimeScope(actor, showtimeId);
        try (Connection connection = DBConnection.getConnection()) {
            return loadShowtimeDeletionImpact(connection, showtimeId, actor, false);
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể kiểm tra điều kiện xóa suất chiếu.", ex);
        }
    }

    public ShowtimeDeletionImpact resumeShowtimeSale(int showtimeId, User actor) {
        assertShowtimeScope(actor, showtimeId);
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                ShowtimeLifecycleLock.acquire(connection, showtimeId);
                ShowtimeDeletionImpact impact = loadShowtimeDeletionImpact(connection, showtimeId, actor, true);
                if ("DELETED".equals(impact.saleStatus())) {
                    throw new BookingException(409, "Suất chiếu đã xóa không thể mở bán lại.");
                }
                if ("SUSPENDED".equals(impact.saleStatus())) {
                    try (PreparedStatement ps = connection.prepareStatement("""
                            UPDATE Showtimes
                            SET SaleStatus = 'ON_SALE', DeleteRequestedAt = NULL,
                                DeleteNotBefore = NULL, DeleteRequestedByUserId = NULL,
                                UpdatedAt = SYSDATETIME()
                            WHERE Id = ? AND SaleStatus = 'SUSPENDED'
                            """)) {
                        ps.setInt(1, showtimeId);
                        if (ps.executeUpdate() != 1) {
                            throw new BookingException(409, "Trạng thái suất chiếu vừa thay đổi. Hãy tải lại.");
                        }
                    }
                    insertAudit(connection, actor.getId(), "RESUME_SHOWTIME_SALE", "Showtime",
                            String.valueOf(showtimeId), "SUSPENDED", "ON_SALE");
                }
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể mở bán lại suất chiếu.", ex);
        }
        return previewShowtimeDeletion(showtimeId, actor);
    }

    public boolean confirmShowtimeDeletion(int showtimeId, User actor) {
        assertShowtimeScope(actor, showtimeId);
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                ShowtimeLifecycleLock.acquire(connection, showtimeId);
                ShowtimeDeletionImpact impact = loadShowtimeDeletionImpact(connection, showtimeId, actor, true);
                if (!impact.ready()) {
                    throw new BookingException(409, impact.blockedReason() == null
                            ? "Điều kiện xóa đã thay đổi. Hãy tải lại." : impact.blockedReason());
                }
                boolean hardDeleted = impact.terminalOrderCount() == 0;
                if (hardDeleted) {
                    try (PreparedStatement ps = connection.prepareStatement(
                            "DELETE FROM ShowtimeSeats WHERE ShowtimeId = ?")) {
                        ps.setInt(1, showtimeId);
                        ps.executeUpdate();
                    }
                    notificationDAO.deleteByTarget(connection, "Showtime", String.valueOf(showtimeId));
                    notificationDAO.deleteByTarget(connection, "Showtime_SoldOut", String.valueOf(showtimeId));
                    try (PreparedStatement ps = connection.prepareStatement("DELETE FROM Showtimes WHERE Id = ?")) {
                        ps.setInt(1, showtimeId);
                        if (ps.executeUpdate() != 1) {
                            throw new BookingException(409, "Suất chiếu vừa thay đổi. Hãy tải lại.");
                        }
                    }
                } else {
                    try (PreparedStatement ps = connection.prepareStatement("""
                            UPDATE Showtimes SET SaleStatus = 'DELETED', UpdatedAt = SYSDATETIME()
                            WHERE Id = ? AND SaleStatus = 'SUSPENDED'
                            """)) {
                        ps.setInt(1, showtimeId);
                        if (ps.executeUpdate() != 1) {
                            throw new BookingException(409, "Suất chiếu vừa thay đổi. Hãy tải lại.");
                        }
                    }
                }
                insertAudit(connection, actor.getId(), hardDeleted ? "HARD_DELETE_SHOWTIME" : "TOMBSTONE_SHOWTIME",
                        "Showtime", String.valueOf(showtimeId), "SUSPENDED", hardDeleted ? null : "DELETED");
                connection.commit();
                return hardDeleted;
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể xóa suất chiếu.", ex);
        }
    }

    /** Compatibility entry point for older callers. */
    public void deleteShowtime(int showtimeId, User actor) {
        confirmShowtimeDeletion(showtimeId, actor);
    }

    private ShowtimeDeletionImpact loadShowtimeDeletionImpact(Connection connection, int showtimeId,
            User actor, boolean lockRow) throws SQLException {
        String lockHint = lockRow ? " WITH (UPDLOCK, HOLDLOCK)" : "";
        String sql = ("""
                SELECT s.Id, s.CinemaId, s.StartTime, s.EndTime, s.SaleStatus, s.DeleteRequestedAt,
                       s.DeleteNotBefore, f.Title AS FilmTitle, c.Name AS CinemaName, r.Name AS RoomName,
                       CASE WHEN s.DeleteNotBefore IS NULL THEN 0
                            WHEN s.DeleteNotBefore <= SYSDATETIME() THEN 0
                            ELSE DATEDIFF(SECOND, SYSDATETIME(), s.DeleteNotBefore) END AS SecondsRemaining,
                       (SELECT COUNT(*) FROM ShowtimeSeats ss
                        WHERE ss.ShowtimeId=s.Id AND ss.Status='held' AND ss.HeldUntil > SYSDATETIME()) AS ActiveHolds,
                       (SELECT COUNT(*) FROM Orders o
                        WHERE o.ShowtimeId=s.Id AND o.PaymentStatus='pending'
                          AND o.OrderStatus IN ('created','pending')) AS ActiveDraftOrders,
                       (SELECT COUNT(*) FROM Orders o
                        WHERE o.ShowtimeId=s.Id AND
                          ((o.PaymentStatus='paid' AND o.OrderStatus NOT IN ('cancelled','refunded','redeemed'))
                           OR (o.PaymentStatus='pending' AND o.OrderStatus='confirmed'))) AS CommittedOrders,
                       (SELECT COUNT(*) FROM Orders o
                        WHERE o.ShowtimeId=s.Id AND
                          (o.OrderStatus IN ('cancelled','refunded','redeemed') OR o.PaymentStatus IN ('cancelled','refunded'))) AS TerminalOrders
                FROM Showtimes s%s
                JOIN Films f ON f.Id=s.FilmId
                JOIN Cinemas c ON c.Id=s.CinemaId
                JOIN Rooms r ON r.Id=s.RoomId
                WHERE s.Id=?
                """).formatted(lockHint);
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, showtimeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new BookingException(404, "Không tìm thấy suất chiếu.");
                }
                if (ScopeUtil.isManager(actor) && actor.getCinemaId() != rs.getInt("CinemaId")) {
                    throw new BookingException(403, "Bạn không có quyền quản lý suất chiếu của rạp này.");
                }
                String saleStatus = rs.getString("SaleStatus");
                int holds = rs.getInt("ActiveHolds");
                int drafts = rs.getInt("ActiveDraftOrders");
                int committed = rs.getInt("CommittedOrders");
                int terminal = rs.getInt("TerminalOrders");
                long seconds = rs.getLong("SecondsRemaining");
                boolean ready = "SUSPENDED".equals(saleStatus) && seconds == 0
                        && holds == 0 && drafts == 0 && committed == 0;
                String blocked = null;
                if (!"SUSPENDED".equals(saleStatus)) blocked = "Suất chiếu chưa ở trạng thái ngưng bán.";
                else if (seconds > 0) blocked = "Chưa đủ thời gian chờ 5 phút.";
                else if (holds > 0) blocked = "Vẫn còn lượt giữ ghế chưa hết hạn.";
                else if (drafts > 0) blocked = "Vẫn còn order nháp đang hoạt động.";
                else if (committed > 0) blocked = "Suất chiếu có order/vé còn hiệu lực.";
                return new ShowtimeDeletionImpact(showtimeId, rs.getString("FilmTitle"),
                        rs.getString("CinemaName"), rs.getString("RoomName"),
                        rs.getTimestamp("StartTime").toLocalDateTime(),
                        rs.getTimestamp("EndTime").toLocalDateTime(), saleStatus,
                        rs.getTimestamp("DeleteRequestedAt") == null ? null
                                : rs.getTimestamp("DeleteRequestedAt").toLocalDateTime(),
                        rs.getTimestamp("DeleteNotBefore") == null ? null
                                : rs.getTimestamp("DeleteNotBefore").toLocalDateTime(),
                        holds, drafts, committed, terminal, seconds, ready, blocked);
            }
        }
    }

    public List<User> listUsers(String role) {
        String sql = """
                SELECT TOP (200) u.*, c.Name AS CinemaName
                FROM Users u
                LEFT JOIN Cinemas c ON c.Id = u.CinemaId
                WHERE (? IS NULL OR u.Role = ?)
                ORDER BY u.CreatedAt DESC
                """;
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            if (role == null) {
                ps.setNull(1, java.sql.Types.NVARCHAR);
                ps.setNull(2, java.sql.Types.NVARCHAR);
            } else {
                ps.setString(1, role);
                ps.setString(2, role);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<User> users = new ArrayList<>();
                while (rs.next()) {
                    users.add(mapUser(rs));
                }
                return users;
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the tai danh sach tai khoan.", ex);
        }
    }

    public List<User> listUsers(String role, User actor) {
        if (!ScopeUtil.isManager(actor))
            return listUsers(role);
        int cinemaId = requireActorCinema(actor);
        boolean exactOperationalRole = AppConstants.ROLE_STAFF.equalsIgnoreCase(role)
                || AppConstants.ROLE_MANAGER.equalsIgnoreCase(role);
        String sql = """
                SELECT u.*, c.Name AS CinemaName
                FROM Users u
                LEFT JOIN Cinemas c ON c.Id = u.CinemaId
                WHERE (? IS NULL OR u.Role = ?)
                """ + (exactOperationalRole
                ? " AND u.CinemaId = ? "
                : """
                  AND (u.CinemaId = ? OR EXISTS (
                    SELECT 1 FROM Orders o
                    JOIN Showtimes s ON s.Id = o.ShowtimeId
                    WHERE o.UserId = u.Id AND s.CinemaId = ?
                  ))
                  """) + " ORDER BY u.CreatedAt DESC";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            if (role == null) {
                ps.setNull(1, java.sql.Types.NVARCHAR);
                ps.setNull(2, java.sql.Types.NVARCHAR);
            } else {
                ps.setString(1, role);
                ps.setString(2, role);
            }
            ps.setInt(3, cinemaId);
            if (!exactOperationalRole) {
                ps.setInt(4, cinemaId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<User> users = new ArrayList<>();
                while (rs.next()) {
                    users.add(mapUser(rs));
                }
                return users;
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the tai danh sach tai khoan.", ex);
        }
    }

    public void setUserDeleted(int userId, boolean deleted, User actor, String actionName) {
        assertUserScope(actor, userId);
        String checkSql = "SELECT Role FROM Users WHERE Id = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(checkSql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String role = rs.getString("Role");
                    if (AppConstants.ROLE_ADMIN.equalsIgnoreCase(role)
                            && !AppConstants.ROLE_ADMIN.equalsIgnoreCase(actor.getRole())) {
                        throw new BookingException(403, "Không thể khóa tài khoản Admin tối cao.");
                    }
                }
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the kiem tra thong tin tai khoan.", ex);
        }

        String sql = "UPDATE Users SET Deleted = ?, UpdatedAt = GETDATE() WHERE Id = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setBoolean(1, deleted);
            ps.setInt(2, userId);
            ps.executeUpdate();
            AccountStateGuard.invalidate(userId);
            logAction(actor.getId(), actionName, "User", String.valueOf(userId), deleted ? "locked" : "unlocked");
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the cap nhat trang thai tai khoan.", ex);
        }
    }

    public void createMember(User user, User actor) {
        if (ScopeUtil.isManager(actor)) {
            // Derive ownership from the authenticated actor; never trust a submitted cinema.
            user.setCinemaId(requireActorCinema(actor));
        }
        createPrivilegedOrMemberUser(user, "member", actor, "CREATE_MEMBER");
    }

    public void createManager(User user, User actor) {
        CinemaCapabilityPolicy.requireAdmin(actor);
        if (user.getCinemaId() == null || user.getCinemaId() <= 0) {
            throw new BookingException(400, "Manager phải được gán một cụm rạp.");
        }
        assertActiveCinemaForUser(user.getCinemaId());
        user.setRole("manager");
        createPrivilegedOrMemberUser(user, "manager", actor, "CREATE_MANAGER");
    }

    /**
     * Tao tai khoan nhan vien quay ve (chi thu tien tai quay + soat/check-in ve).
     */
    public void createStaff(User user, User actor) {
        if (ScopeUtil.isManager(actor)) {
            user.setCinemaId(requireActorCinema(actor));
        } else {
            CinemaCapabilityPolicy.requireAdmin(actor);
        }
        if (user.getCinemaId() == null || user.getCinemaId() <= 0) {
            throw new BookingException(400, "Nhân viên phải được phân vào một rạp.");
        }
        assertActiveCinemaForUser(user.getCinemaId());
        user.setRole(AppConstants.ROLE_STAFF);
        createPrivilegedOrMemberUser(user, AppConstants.ROLE_STAFF, actor, "CREATE_STAFF");
    }

    private void assertActiveCinemaForUser(int cinemaId) {
        String sql = "SELECT COUNT(*) FROM Cinemas WHERE Id=? AND LOWER(ISNULL(Status,'active'))='active'";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, cinemaId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getInt(1) == 0) {
                    throw new BookingException(400, "Rạp phân công không tồn tại hoặc đã ngừng hoạt động.");
                }
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể kiểm tra rạp phân công.", ex);
        }
    }

    /** Admin-only reassignment. Old sessions and pending requests are revoked atomically. */
    public void reassignCinemaScopedUser(int userId, int newCinemaId, User actor) {
        CinemaCapabilityPolicy.requireAdmin(actor);
        if (newCinemaId <= 0) {
            throw new BookingException(400, "Vui lòng chọn rạp phân công mới.");
        }
        String role;
        int oldCinemaId;
        int cancelledRequests = 0;
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement cinema = connection.prepareStatement(
                        "SELECT COUNT(*) FROM Cinemas WHERE Id=? AND LOWER(ISNULL(Status,'active'))='active'")) {
                    cinema.setInt(1, newCinemaId);
                    try (ResultSet rs = cinema.executeQuery()) {
                        if (!rs.next() || rs.getInt(1) == 0) {
                            throw new BookingException(400, "Rạp phân công mới không khả dụng.");
                        }
                    }
                }
                try (PreparedStatement user = connection.prepareStatement(
                        "SELECT Role,CinemaId FROM Users WITH (UPDLOCK,HOLDLOCK) WHERE Id=?")) {
                    user.setInt(1, userId);
                    try (ResultSet rs = user.executeQuery()) {
                        if (!rs.next()) {
                            throw new BookingException(404, "Không tìm thấy tài khoản cần điều chuyển.");
                        }
                        role = rs.getString("Role");
                        oldCinemaId = rs.getInt("CinemaId");
                        if (rs.wasNull() || !("manager".equalsIgnoreCase(role)
                                || AppConstants.ROLE_STAFF.equalsIgnoreCase(role))) {
                            throw new BookingException(400, "Chỉ có thể điều chuyển manager hoặc staff.");
                        }
                    }
                }
                if (oldCinemaId == newCinemaId) {
                    connection.rollback();
                    return;
                }
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE Users SET CinemaId=?,UpdatedAt=GETDATE() WHERE Id=?")) {
                    update.setInt(1, newCinemaId);
                    update.setInt(2, userId);
                    if (update.executeUpdate() != 1) {
                        throw new BookingException(409, "Tài khoản đã thay đổi trong lúc điều chuyển.");
                    }
                }
                if ("manager".equalsIgnoreCase(role)) {
                    try (PreparedStatement cancel = connection.prepareStatement("""
                            UPDATE ApprovalRequests
                            SET Status=N'CANCELLED',ReviewedAt=SYSDATETIME(),
                                ReviewNote=N'Tài khoản manager đã được điều chuyển sang rạp khác.'
                            WHERE RequestedByUserId=? AND CinemaId=? AND Status=N'PENDING'
                            """)) {
                        cancel.setInt(1, userId);
                        cancel.setInt(2, oldCinemaId);
                        cancelledRequests = cancel.executeUpdate();
                    }
                }
                try (PreparedStatement revoke = connection.prepareStatement("""
                        UPDATE RefreshTokens SET RevokedAt=SYSDATETIME(),
                            RevocationReason=N'CINEMA_REASSIGNED'
                        WHERE UserId=? AND RevokedAt IS NULL
                        """)) {
                    revoke.setInt(1, userId);
                    revoke.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể điều chuyển tài khoản sang rạp mới.", ex);
        }
        AccountStateGuard.invalidate(userId);
        auditAfterCommit(actor.getId(), "REASSIGN_CINEMA", "User", String.valueOf(userId),
                "role=" + role + ";oldCinemaId=" + oldCinemaId + ";newCinemaId=" + newCinemaId
                        + ";cancelledRequests=" + cancelledRequests);
    }

    /**
     * Xoa tai khoan nhan vien quay ve voi xu ly toan ven du lieu foreign key &
     * fallback an toan.
     */
    public void deleteStaff(int staffId, User actor) {
        assertStaffScope(actor, staffId);
        if (actor != null && actor.getId() == staffId) {
            throw new BookingException(400, "Bạn không thể tự xóa tài khoản của chính mình.");
        }

        String checkSql = "SELECT Role FROM Users WHERE Id = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(checkSql)) {
            ps.setInt(1, staffId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new BookingException(400, "Tài khoản nhân viên không tồn tại.");
                }
                String role = rs.getString("Role");
                if (!AppConstants.ROLE_STAFF.equalsIgnoreCase(role)) {
                    throw new BookingException(400, "Chỉ có thể thực hiện xóa trên tài khoản Nhân viên quầy vé.");
                }
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể kiểm tra thông tin tài khoản.", ex);
        }

        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                // Clear nullable references in associated tables to prevent FK blocking
                try (PreparedStatement ps1 = connection
                        .prepareStatement("UPDATE SeatHolds SET HeldByUserId = NULL WHERE HeldByUserId = ?")) {
                    ps1.setInt(1, staffId);
                    ps1.executeUpdate();
                }
                try (PreparedStatement ps2 = connection
                        .prepareStatement("UPDATE AuditLogs SET ActorUserId = NULL WHERE ActorUserId = ?")) {
                    ps2.setInt(1, staffId);
                    ps2.executeUpdate();
                }
                try (PreparedStatement ps3 = connection
                        .prepareStatement("UPDATE CommentReports SET ReporterUserId = NULL WHERE ReporterUserId = ?")) {
                    ps3.setInt(1, staffId);
                    ps3.executeUpdate();
                }
                try (PreparedStatement ps4 = connection.prepareStatement(
                        "UPDATE UserAppeals SET ResolvedByUserId = NULL WHERE ResolvedByUserId = ?")) {
                    ps4.setInt(1, staffId);
                    ps4.executeUpdate();
                }

                // Try hard delete from Users
                boolean hardDeleted = false;
                try (PreparedStatement psDelete = connection
                        .prepareStatement("DELETE FROM Users WHERE Id = ? AND Role = ?")) {
                    psDelete.setInt(1, staffId);
                    psDelete.setString(2, AppConstants.ROLE_STAFF);
                    int rows = psDelete.executeUpdate();
                    if (rows > 0) {
                        hardDeleted = true;
                    }
                } catch (SQLException deleteEx) {
                    // If hard delete fails due to FK constraint (e.g. Orders created by staff),
                    // fallback to soft-delete deactivation.
                    //
                    // N-12: nhanh du phong nay la fail-safe (tai khoan van bi khoa va ha quyen),
                    // nhung nguyen nhan phai duoc ghi lai — neu khong, mot loi DB khac han FK
                    // cung roi vao day va bien mat khong dau vet.
                    LOGGER.log(Level.INFO, "Khong hard-delete duoc nhan vien id=" + staffId
                            + "; chuyen sang vo hieu hoa tai khoan", deleteEx);
                    try (PreparedStatement psSoft = connection.prepareStatement(
                            "UPDATE Users SET Deleted = 1, Role = 'member', CinemaId = NULL, IsLocked = 1, LockReason = N'Tài khoản nhân viên đã bị xóa', UpdatedAt = GETDATE() WHERE Id = ?")) {
                        psSoft.setInt(1, staffId);
                        psSoft.executeUpdate();
                    }
                }

                connection.commit();
                AccountStateGuard.invalidate(staffId);
                auditAfterCommit(actor != null ? actor.getId() : 0, "DELETE_STAFF", "User", String.valueOf(staffId),
                        hardDeleted ? "hard_deleted" : "soft_deleted");
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể xóa tài khoản nhân viên.", ex);
        }
    }

    public List<FilmComment> listComments(Integer filmId, Boolean reportedOnly) {
        return listComments(filmId, reportedOnly, null);
    }

    /** Admins see the global queue; managers only receive comments authored by scoped members. */
    public List<FilmComment> listComments(Integer filmId, Boolean reportedOnly, User actor) {
        boolean manager = ScopeUtil.isManager(actor);
        Integer cinemaId = manager ? requireActorCinema(actor) : null;
        StringBuilder sql = new StringBuilder("""
                SELECT cm.*, f.Title AS FilmTitle, u.FullName AS UserFullName, u.Email AS UserEmail,
                       ISNULL(u.WarningCount, 0) AS UserWarningCount, ISNULL(u.IsLocked, 0) AS UserIsLocked,
                       CASE WHEN f.DeletedAt IS NULL THEN 0 ELSE 1 END AS FilmDeleted
                FROM Comments cm
                JOIN Films f ON f.Id = cm.FilmId
                JOIN Users u ON u.Id = cm.UserId
                WHERE 1=1
                """);
        List<Object> params = new ArrayList<>();
        if (manager) {
            sql.append("""
                     AND u.Role='member' AND EXISTS (
                         SELECT 1 FROM Orders scopedOrder
                         JOIN Showtimes scopedShowtime ON scopedShowtime.Id=scopedOrder.ShowtimeId
                         WHERE scopedOrder.UserId=cm.UserId
                           AND scopedShowtime.FilmId=cm.FilmId
                           AND scopedShowtime.CinemaId=?
                           AND scopedOrder.OrderStatus='redeemed'
                       )
                    """);
            params.add(cinemaId);
        }
        if (filmId != null && filmId > 0) {
            sql.append(" AND cm.FilmId = ?");
            params.add(filmId);
        }
        if (reportedOnly != null && reportedOnly) {
            sql.append(" AND cm.Report = 1");
        }
        sql.append(" ORDER BY cm.CreatedAt DESC");

        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<FilmComment> comments = new ArrayList<>();
                while (rs.next()) {
                    comments.add(mapComment(rs));
                }
                return comments;
            }
        } catch (SQLException ex) {
            // Khong duoc tra list rong: hang cho kiem duyet binh luan trong y het khi khong
            // con
            // binh luan nao bi bao cao.
            throw new BookingException(500, "Khong the tai danh sach binh luan.", ex);
        }
    }

    public List<FilmComment> listCommentsByFilm(int filmId) {
        return listComments(filmId, null);
    }

    /** Filter options come only from films that actually have visible comments. */
    public List<Film> listCommentFilms(User actor) {
        boolean manager = ScopeUtil.isManager(actor);
        Integer cinemaId = manager ? requireActorCinema(actor) : null;
        String sql = """
                SELECT DISTINCT f.* FROM Films f
                JOIN Comments cm ON cm.FilmId=f.Id
                WHERE (?=0 OR EXISTS (
                    SELECT 1 FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId
                    WHERE o.UserId=cm.UserId AND s.FilmId=cm.FilmId AND s.CinemaId=?
                      AND o.OrderStatus='redeemed'))
                ORDER BY f.Title
                """;
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, manager ? 1 : 0);
            if (cinemaId == null) {
                ps.setNull(2, java.sql.Types.INTEGER);
            } else {
                ps.setInt(2, cinemaId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Film> films = new ArrayList<>();
                while (rs.next()) films.add(mapFilm(rs));
                return films;
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể tải bộ lọc phim có bình luận.", ex);
        }
    }

    /**
     * Gui danh gia phim (BUG-08, INV-11).
     *
     * <p>Truoc day day la mot cau INSERT tran: ca {@code FilmServlet} lan
     * {@code /api/v1/films/&#123;id&#125;/comments} chi kiem {@code rate} 1..5 va noi dung khong rong.
     * Do thuc te: tai khoan 0 don hang danh gia duoc; cung tai khoan danh gia 3 lan lien tiep cung
     * mot phim deu thanh cong — diem trung binh bi thao tung tuy y.</p>
     *
     * <p>Hai luat, dat o <b>tang service</b> de ca hai duong vao dung chung mot chot:</p>
     * <ol>
     *   <li>Phai co it nhat mot don {@code redeemed} cho chinh phim do — da mua ma chua check-in
     *       thi chua goi la "da dung dich vu". Khong du dieu kien: 403.</li>
     *   <li>Mot nguoi mot danh gia cho mot phim. Lan hai: <b>409</b>. Chon 409 thay vi cho sua de
     *       khong am tham ghi de noi dung cu — sua danh gia la mot tinh nang rieng, co man hinh
     *       rieng, khong nen an sau nut "Gui".</li>
     * </ol>
     *
     * <p>Ca hai kiem tra nam trong cung mot transaction voi {@code UPDLOCK, HOLDLOCK}: hai request
     * gui gan nhu cung luc phai bi tuan tu hoa, neu khong van lot hai dong.</p>
     */
    public void addComment(int userId, int filmId, int rate, String content) {
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (!hasRedeemedTicketForFilm(connection, userId, filmId)) {
                    throw new BookingException(403,
                            "Bạn cần xem phim này trước khi đánh giá. Đánh giá chỉ dành cho khách đã check-in vé.");
                }
                if (hasReviewedFilm(connection, userId, filmId)) {
                    throw new BookingException(409, "Bạn đã đánh giá phim này rồi.");
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO Comments (UserId, FilmId, Rate, Content, Report) VALUES (?, ?, ?, ?, 0)")) {
                    ps.setInt(1, userId);
                    ps.setInt(2, filmId);
                    ps.setInt(3, rate);
                    ps.setString(4, content);
                    ps.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the gui binh luan.", ex);
        }
    }

    private boolean hasRedeemedTicketForFilm(Connection connection, int userId, int filmId) throws SQLException {
        String sql = """
                SELECT TOP 1 1
                FROM Orders o
                JOIN Showtimes s ON s.Id = o.ShowtimeId
                JOIN Films f ON f.Id=s.FilmId
                WHERE o.UserId = ? AND s.FilmId = ? AND o.OrderStatus = 'redeemed'
                  AND f.DeletedAt IS NULL
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, filmId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean hasReviewedFilm(Connection connection, int userId, int filmId) throws SQLException {
        String sql = "SELECT TOP 1 1 FROM Comments WITH (UPDLOCK, HOLDLOCK) WHERE UserId = ? AND FilmId = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, filmId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void reportComment(int commentId, Integer reporterUserId, String reason) {
        if (reason == null || reason.isBlank()) {
            reason = "Nội dung vi phạm quy định cộng đồng.";
        }
        String sqlReport = "UPDATE Comments SET Report = 1 WHERE Id = ?";
        String sqlInsert = "INSERT INTO CommentReports (CommentId, ReporterUserId, Reason) VALUES (?, ?, ?)";
        String sqlFetch = """
                SELECT cm.Content, f.Title AS FilmTitle, u.Email AS UserEmail, u.FullName AS UserFullName
                FROM Comments cm
                JOIN Films f ON f.Id = cm.FilmId
                JOIN Users u ON u.Id = cm.UserId
                WHERE cm.Id = ?
                """;
        try (Connection conn = DBConnection.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(sqlReport)) {
                ps.setInt(1, commentId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(sqlInsert)) {
                ps.setInt(1, commentId);
                if (reporterUserId != null) {
                    ps.setInt(2, reporterUserId);
                } else {
                    ps.setNull(2, java.sql.Types.INTEGER);
                }
                ps.setString(3, reason);
                ps.executeUpdate();
            }

            String userEmail = "";
            String filmTitle = "";
            String content = "";
            try (PreparedStatement ps = conn.prepareStatement(sqlFetch)) {
                ps.setInt(1, commentId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        content = rs.getString("Content");
                        filmTitle = rs.getString("FilmTitle");
                        userEmail = rs.getString("UserEmail");
                    }
                }
            }

            String targetId = String.valueOf(commentId);
            AdminNotification note = new AdminNotification();
            note.setTitle("Báo cáo bình luận vi phạm: " + userEmail);
            note.setMessage("Bình luận của tài khoản \"" + userEmail + "\" trong phim \"" + filmTitle
                    + "\" có nội dung: \"" + content + "\" vừa bị báo cáo với lý do: \"" + reason + "\".");
            note.setCategory("comment");
            note.setSeverity("warning");
            note.setTargetType("CommentReport");
            note.setTargetId(targetId);
            note.setActionUrl("/admin/comments?reported=true");
            notificationDAO.createNotification(note);

        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể gửi báo cáo bình luận.", ex);
        }
    }

    public void warnUserForComment(int commentId, User actor) {
        CommentWarningOutcome outcome;
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int commentUserId = assertCommentInScope(connection, actor, commentId, true);
                try (PreparedStatement clear = connection.prepareStatement(
                        "UPDATE Comments SET Report=0 WHERE Id=? AND Report=1")) {
                    clear.setInt(1, commentId);
                    if (clear.executeUpdate() != 1) {
                        throw new BookingException(409,
                                "Báo cáo bình luận này đã được xử lý ở một yêu cầu khác.");
                    }
                }

                String lockReason = "Tài khoản bị khóa tự động do tích lũy đủ 3 lần cảnh cáo "
                        + "vi phạm quy định bình luận.";
                try (PreparedStatement warning = connection.prepareStatement("""
                        UPDATE Users WITH (UPDLOCK, HOLDLOCK)
                        SET WarningCount=CASE WHEN WarningCount < 3 THEN WarningCount + 1 ELSE WarningCount END,
                            IsLocked=CASE WHEN WarningCount + 1 >= 3 THEN 1 ELSE IsLocked END,
                            LockReason=CASE WHEN WarningCount + 1 >= 3 THEN ? ELSE LockReason END,
                            LockedAt=CASE WHEN WarningCount + 1 >= 3 AND IsLocked=0
                                          THEN GETDATE() ELSE LockedAt END,
                            UpdatedAt=GETDATE()
                        OUTPUT INSERTED.WarningCount, DELETED.IsLocked AS WasLocked,
                               INSERTED.IsLocked, INSERTED.Email, INSERTED.FullName
                        WHERE Id=?
                        """)) {
                    warning.setString(1, lockReason);
                    warning.setInt(2, commentUserId);
                    try (ResultSet result = warning.executeQuery()) {
                        if (!result.next()) {
                            throw new BookingException(404, "Không tìm thấy tài khoản người dùng.");
                        }
                        boolean wasLocked = result.getBoolean("WasLocked");
                        boolean isLocked = result.getBoolean("IsLocked");
                        outcome = new CommentWarningOutcome(
                                commentUserId,
                                result.getInt("WarningCount"),
                                !wasLocked && isLocked,
                                result.getString("Email"),
                                result.getString("FullName"));
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể cảnh cáo tài khoản từ bình luận.", ex);
        }

        if (outcome.newlyLocked()) {
            AccountStateGuard.invalidate(outcome.userId());
        }
        String action = outcome.newlyLocked() ? "AUTO_LOCK_USER_WARNING_LIMIT" : "WARN_USER_COMMENT";
        auditAfterCommit(actor.getId(), action, "User", String.valueOf(outcome.userId()),
                "Cảnh cáo lần " + outcome.warningCount() + "/3; commentId=" + commentId);
        auditAfterCommit(actor.getId(), "CLEAR_COMMENT_REPORT", "Comment", String.valueOf(commentId),
                "Đã xử lý bằng cảnh cáo userId=" + outcome.userId());

        if (outcome.newlyLocked()) {
            AdminNotification note = new AdminNotification();
            note.setTitle("TỰ ĐỘNG KHÓA TÀI KHOẢN: " + outcome.email());
            note.setMessage("Tài khoản \"" + outcome.fullName() + "\" (" + outcome.email()
                    + ") đã tích lũy 3/3 lần cảnh cáo vi phạm bình luận. Hệ thống đã tự động KHÓA TÀI KHOẢN.");
            note.setCategory("user");
            note.setSeverity("danger");
            note.setTargetType("UserLock");
            note.setTargetId(String.valueOf(outcome.userId()));
            note.setActionUrl("/admin/users");
            try {
                notificationDAO.createNotification(note);
            } catch (RuntimeException ex) {
                LOGGER.log(Level.SEVERE, ex,
                        () -> "Khong tao duoc thong bao auto-lock cho user " + outcome.userId());
            }
        }
    }

    private record CommentWarningOutcome(int userId, int warningCount, boolean newlyLocked,
            String email, String fullName) {
    }

    /** Locks the author selected from the target comment, never from a submitted user id. */
    public void lockUserForComment(int commentId, String reason, User actor) {
        String resolvedReason = reason == null || reason.isBlank()
                ? "Tai khoan bi khoa do vi pham quy dinh cong dong CineBook."
                : reason;
        int commentUserId;
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                commentUserId = assertCommentInScope(connection, actor, commentId, true);
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE Users
                        SET IsLocked=1, LockReason=?, LockedAt=GETDATE(), UpdatedAt=GETDATE()
                        WHERE Id=?
                        """)) {
                    statement.setString(1, resolvedReason);
                    statement.setInt(2, commentUserId);
                    if (statement.executeUpdate() != 1) {
                        throw new BookingException(404, "Khong tim thay tai khoan can khoa.");
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the khoa tac gia binh luan.", ex);
        }
        AccountStateGuard.invalidate(commentUserId);
        auditAfterCommit(actor.getId(), "LOCK_USER", "User", String.valueOf(commentUserId), resolvedReason);
    }

    public void lockUserDirectly(int userId, String reason, User actor) {
        assertUserScope(actor, userId);
        if (reason == null || reason.isBlank()) {
            reason = "Tài khoản bị khóa do vi phạm quy định cộng đồng CineBook.";
        }
        userDAO.updateLockStatus(userId, true, reason);
        AccountStateGuard.invalidate(userId);
        logAction(actor.getId(), "LOCK_USER", "User", String.valueOf(userId), reason);
    }

    public void lockMemberDirectly(int userId, String reason, User actor) {
        assertMemberInScope(actor, userId);
        lockUserDirectly(userId, reason, actor);
    }

    public void lockStaffDirectly(int staffId, String reason, User actor) {
        String resolvedReason = reason == null || reason.isBlank()
                ? "Tai khoan nhan vien bi khoa boi quan tri CineBook."
                : reason.trim();
        updateStaffLockState(staffId, true, resolvedReason, actor);
    }

    public void unlockStaff(int staffId, User actor) {
        updateStaffLockState(staffId, false, null, actor);
    }

    /**
     * Khoa/mo khoa chi mot tai khoan staff, voi role va pham vi duoc doc tu dong
     * dang giu khoa thay vi tin id/role tren form.
     */
    private void updateStaffLockState(int staffId, boolean locked, String reason, User actor) {
        boolean manager = ScopeUtil.isManager(actor);
        boolean admin = actor != null && AppConstants.ROLE_ADMIN.equalsIgnoreCase(actor.getRole());
        if (!manager && !admin) {
            throw new BookingException(403, "Chi admin hoac manager duoc quan ly nhan vien.");
        }

        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT Role,CinemaId FROM Users WITH (UPDLOCK,HOLDLOCK) WHERE Id=?")) {
                    ps.setInt(1, staffId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new BookingException(404, "Khong tim thay tai khoan nhan vien.");
                        }
                        if (!AppConstants.ROLE_STAFF.equalsIgnoreCase(rs.getString("Role"))) {
                            throw new BookingException(403,
                                    "Route nhan vien chi duoc khoa/mo khoa tai khoan staff.");
                        }
                        if (manager) {
                            int cinemaId = rs.getInt("CinemaId");
                            ScopeUtil.assertCinemaScope(actor, rs.wasNull() ? -1 : cinemaId);
                        }
                    }
                }

                String updateSql = locked
                        ? "UPDATE Users SET IsLocked=1,LockReason=?,LockedAt=GETDATE(),"
                                + "UpdatedAt=GETDATE() WHERE Id=? AND Role='staff'"
                        : "UPDATE Users SET IsLocked=0,LockReason=NULL,LockedAt=NULL,WarningCount=0,"
                                + "UpdatedAt=GETDATE() WHERE Id=? AND Role='staff'";
                try (PreparedStatement ps = connection.prepareStatement(updateSql)) {
                    int index = 1;
                    if (locked) {
                        ps.setString(index++, reason);
                    }
                    ps.setInt(index, staffId);
                    if (ps.executeUpdate() != 1) {
                        throw new BookingException(409,
                                "Tai khoan da thay doi trong luc xu ly. Vui long thu lai.");
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the cap nhat trang thai nhan vien.", ex);
        }

        AccountStateGuard.invalidate(staffId);
        auditAfterCommit(actor.getId(), locked ? "LOCK_USER" : "UNLOCK_USER", "User",
                String.valueOf(staffId), locked ? reason : "Mo khoa & reset canh cao");
    }

    public void unlockUser(int userId, User actor) {
        assertUserScope(actor, userId);
        userDAO.updateLockStatus(userId, false, null);
        userDAO.resetWarnings(userId);
        AccountStateGuard.invalidate(userId);
        logAction(actor.getId(), "UNLOCK_USER", "User", String.valueOf(userId), "Mở khóa & reset cảnh cáo");
    }

    public void unlockMember(int userId, User actor) {
        assertMemberInScope(actor, userId);
        unlockUser(userId, actor);
    }

    public void submitAppeal(String email, String reason) {
        if (email == null || email.isBlank() || reason == null || reason.isBlank()) {
            throw new BookingException(400, "Vui lòng nhập đầy đủ Email và Lý do kháng cáo.");
        }
        Optional<User> optUser = userDAO.findByEmail(email.trim());
        if (optUser.isEmpty()) {
            throw new BookingException(404, "Không tìm thấy tài khoản với Email này.");
        }
        User user = optUser.get();

        if (!user.isLocked()) {
            throw new BookingException(400, "Tài khoản của bạn hiện không ở trạng thái bị khóa.");
        }

        if (userAppealDAO.findPendingByUserId(user.getId()).isPresent()) {
            throw new BookingException(400, "Bạn đã gửi một đơn kháng cáo đang chờ Quản trị viên xử lý.");
        }

        UserAppeal appeal = new UserAppeal();
        appeal.setUserId(user.getId());
        appeal.setEmail(user.getEmail());
        appeal.setReason(reason.trim());
        if (AppConstants.ROLE_MANAGER.equalsIgnoreCase(user.getRole())
                || AppConstants.ROLE_STAFF.equalsIgnoreCase(user.getRole())) {
            appeal.setCinemaId(user.getCinemaId());
        }
        int appealId = userAppealDAO.create(appeal);

        AdminNotification note = new AdminNotification();
        note.setTitle("ĐƠN KHÁNG CÁO MỚI từ " + user.getEmail());
        note.setMessage("Tài khoản \"" + user.getFullName() + "\" (" + user.getEmail()
                + ") vừa gửi đơn kháng cáo với lý do: \"" + reason + "\".");
        note.setCategory("user");
        note.setSeverity("info");
        note.setTargetType("UserAppeal");
        note.setTargetId(String.valueOf(appealId));
        note.setActionUrl("/admin/appeals");
        note.setCinemaId(appeal.getCinemaId());
        notificationDAO.createNotification(note);
    }

    /**
     * Danh sach khang cao. Nem loi khi khong doc duoc — <b>khong</b> tra danh sach
     * rong (F-005).
     *
     * <p>
     * Tra list rong khi DB loi khien man hinh xet duyet trong y het nhu "khong co
     * don nao",
     * nen admin co the bo sot don mo khoa tai khoan hoac don xin hoan tien. Ai can
     * hien thi suy
     * giam thi dung {@link #appealQueue(String)}.
     * </p>
     */
    public List<UserAppeal> listAppeals(String status) {
        return userAppealDAO.findAll(status);
    }

    /** Actor-scoped queue: admins are global; managers never receive foreign PII. */
    public List<UserAppeal> listAppeals(String status, User actor) {
        requireAppealOperator(actor);
        Integer cinemaId = ScopeUtil.isManager(actor) ? requireActorCinema(actor) : null;
        List<UserAppeal> appeals = userAppealDAO.findAll(status, cinemaId);
        if (ScopeUtil.isManager(actor)) {
            appeals = appeals.stream().filter(appeal -> appeal.getUserId() != actor.getId()).toList();
        }
        return appeals;
    }

    /**
     * Hang doi khang cao kem trang thai doc duoc hay khong (F-005).
     *
     * <p>
     * Day la ket qua co kieu, khong phai list rong: giao dien phan biet duoc "hang
     * doi trong"
     * voi "khong doc duoc hang doi" va hien nut thu lai thay vi bao yen on gia.
     * </p>
     */
    public AppealQueue appealQueue(String status) {
        return appealQueue(status, null);
    }

    public AppealQueue appealQueue(String status, User actor) {
        try {
            return AppealQueue.readable(actor == null
                    ? listAppeals(status)
                    : listAppeals(status, actor));
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE,
                    "Khong doc duoc hang doi khang cao (status=" + status
                            + "); tra trang thai UNAVAILABLE cho giao dien xet duyet.",
                    ex);
            return AppealQueue.unavailable();
        }
    }

    /** Ket qua doc hang doi khang cao: danh sach kem co doc duoc hay khong. */
    public static final class AppealQueue {
        private final List<UserAppeal> appeals;
        private final boolean available;

        private AppealQueue(List<UserAppeal> appeals, boolean available) {
            this.appeals = appeals;
            this.available = available;
        }

        static AppealQueue readable(List<UserAppeal> appeals) {
            return new AppealQueue(List.copyOf(appeals), true);
        }

        static AppealQueue unavailable() {
            return new AppealQueue(List.of(), false);
        }

        public List<UserAppeal> getAppeals() {
            return appeals;
        }

        public boolean isAvailable() {
            return available;
        }

        public boolean isUnavailable() {
            return !available;
        }

        /**
         * Hang doi doc duoc va dung la khong co don nao — khac han truong hop doc loi.
         */
        public boolean isEmptyQueue() {
            return available && appeals.isEmpty();
        }
    }

    public AppealResolutionResult resolveAppeal(
            int appealId, boolean approve, String adminResponse, User actor) {
        requireAppealOperator(actor);
        UserAppeal preliminary = userAppealDAO.findById(appealId).orElse(null);
        if (preliminary == null) {
            throw new BookingException(404, "Không tìm thấy đơn kháng cáo.");
        }
        if (preliminary.isRefundAppeal()) {
            try (Connection connection = DBConnection.getConnection()) {
                assertAppealScope(connection, preliminary, actor);
            } catch (SQLException ex) {
                throw new BookingException(500, "Không thể kiểm tra phạm vi yêu cầu hoàn tiền.", ex);
            }
            if (preliminary.getOrderId() == null
                    || preliminary.getTicketCode() == null
                    || preliminary.getTicketCode().isBlank()) {
                throw new BookingException(500,
                        "Yêu cầu hoàn tiền đang thiếu liên kết đơn vé. Vui lòng kiểm tra dữ liệu.");
            }
            return AppealResolutionResult.refundRedirect(
                    preliminary.getOrderId(), preliminary.getTicketCode());
        }
        String responseText = adminResponse == null ? "" : adminResponse.trim();
        UserAppeal resolvedAppeal;
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                acquireAppealTransactionLock(connection, preliminary);
                resolvedAppeal = userAppealDAO.findByIdForUpdate(connection, appealId)
                        .orElseThrow(() -> new BookingException(404,
                                "Không tìm thấy đơn kháng cáo."));
                assertAppealScope(connection, resolvedAppeal, actor);
                if (!"pending".equalsIgnoreCase(resolvedAppeal.getStatus())) {
                    throw new BookingException(409, "Đơn kháng cáo này đã được xử lý trước đó.");
                }

                if (resolvedAppeal.isRefundAppeal()) {
                    throw new BookingException(409,
                            "Yêu cầu đã chuyển sang quy trình hoàn tiền của đơn vé.");
                }
                if (approve) {
                    unlockAccountInTransaction(connection, resolvedAppeal.getUserId());
                }

                String newStatus = approve ? "approved" : "rejected";
                notificationDAO.resolveByTarget(connection, "UserAppeal", String.valueOf(appealId), actor.getId(),
                        approve ? "approved" : "rejected");
                if (!userAppealDAO.updateStatus(connection, appealId, "pending", newStatus,
                        responseText, actor.getId())) {
                    throw new BookingException(409, "Đơn kháng cáo này đã được xử lý trước đó.");
                }
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể xử lý đơn kháng cáo.", ex);
        }

        if (approve) {
            AccountStateGuard.invalidate(resolvedAppeal.getUserId());
            auditAfterCommit(actor.getId(), "APPROVE_ACCOUNT_APPEAL", "UserAppeal",
                    String.valueOf(appealId), "Đã duyệt mở khóa tài khoản");
        } else {
            auditAfterCommit(actor.getId(), "REJECT_ACCOUNT_APPEAL", "UserAppeal",
                    String.valueOf(appealId), responseText);
        }
        sendAppealResultEmail(resolvedAppeal, approve, responseText);
        return AppealResolutionResult.accountResolved();
    }

    /**
     * Bao ket qua don khang cao cho chu tai khoan. <b>Chi goi sau khi da commit.</b>
     *
     * <p>Van gui cho tai khoan <b>dang bi khoa</b> khi don bi tu choi — do chinh la nguoi can la
     * thu nay nhat, va {@code IsLocked} chan dang nhap chu khong chan nhan thu.</p>
     *
     * <p>{@code UserAppeal} thuong da mang san email/ten qua join, nhung khong phai duong doc nao
     * cung nap hai truong do, nen co duong lui ve {@code userDAO} truoc khi bo cuoc.</p>
     */
    private void sendAppealResultEmail(UserAppeal appeal, boolean approved, String adminResponse) {
        try {
            String email = appeal.getEmail();
            String fullName = appeal.getUserFullName();
            if (email == null || email.isBlank()) {
                Optional<User> owner = userDAO.findById(appeal.getUserId());
                if (owner.isEmpty()) {
                    LOGGER.warning("Khong tim thay chu don khang cao appealId=" + appeal.getId());
                    return;
                }
                email = owner.get().getEmail();
                fullName = owner.get().getFullName();
            }
            new EmailService().sendAppealResult(email, fullName, appeal.getId(), approved,
                    EmailService.formatDateTime(appeal.getCreatedAt()), adminResponse);
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE,
                    "Khong the gui thu ket qua khang cao appealId=" + appeal.getId(), ex);
        }
    }

    private void requireAppealOperator(User actor) {
        if (actor == null || (!AppConstants.ROLE_ADMIN.equalsIgnoreCase(actor.getRole())
                && !AppConstants.ROLE_MANAGER.equalsIgnoreCase(actor.getRole()))) {
            throw new BookingException(403, "Bạn không có quyền xử lý đơn kháng cáo.");
        }
    }

    private void assertAppealScope(Connection connection, UserAppeal appeal, User actor)
            throws SQLException {
        if (!ScopeUtil.isManager(actor)) {
            return;
        }
        int cinemaId = requireActorCinema(actor);
        if (appeal.getUserId() == actor.getId()) {
            throw new BookingException(403, "Manager không được tự xử lý đơn của chính mình.");
        }
        if (appeal.isRefundAppeal()) {
            if (appeal.getCinemaId() == null || appeal.getCinemaId() != cinemaId) {
                throw new BookingException(403,
                        "Đơn hoàn tiền không thuộc cụm rạp của quản lý.");
            }
            return;
        }
        String sql = """
                SELECT 1 FROM Users u
                WHERE u.Id=? AND u.Role IN ('manager','staff')
                  AND ?=(SELECT CinemaId FROM UserAppeals WHERE Id=? AND AppealType='account')
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, appeal.getUserId());
            statement.setInt(2, cinemaId);
            statement.setInt(3, appeal.getId());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new BookingException(403,
                            "Đơn mở khóa không thuộc manager/staff trong rạp được phân quyền.");
                }
            }
        }
    }

    private void unlockAccountInTransaction(Connection connection, int userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE Users
                SET IsLocked=0, LockReason=NULL, LockedAt=NULL, WarningCount=0, UpdatedAt=GETDATE()
                WHERE Id=?
                """)) {
            statement.setInt(1, userId);
            if (statement.executeUpdate() != 1) {
                throw new BookingException(404, "Không tìm thấy tài khoản cần mở khóa.");
            }
        }
    }

    private void acquireAppealTransactionLock(Connection connection, UserAppeal appeal)
            throws SQLException {
        String resource = appeal.isRefundAppeal()
                ? "cinebook:refund-appeal:" + appeal.getTicketCode().trim().toUpperCase(java.util.Locale.ROOT)
                : "cinebook:account-appeal:" + appeal.getUserId();
        // sp_getapplock with LockOwner='Transaction' needs an already-active SQL
        // Server transaction. A TOP 0 table query activates JDBC's implicit
        // transaction without locking a business row.
        try (Statement transactionStarter = connection.createStatement()) {
            transactionStarter.execute("SELECT TOP (0) Id FROM UserAppeals");
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                SET NOCOUNT ON;
                DECLARE @lockResult INT;
                EXEC @lockResult=sys.sp_getapplock
                     @Resource=?, @LockMode='Exclusive', @LockOwner='Transaction', @LockTimeout=10000;
                SELECT @lockResult AS LockResult;
                """)) {
            statement.setString(1, resource);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || result.getInt("LockResult") < 0) {
                    throw new BookingException(503,
                            "Đơn kháng cáo đang được xử lý ở một yêu cầu khác.");
                }
            }
        }
    }

    public void clearCommentReport(int commentId, User actor) {
        assertCommentInScope(actor, commentId);
        String sql = "UPDATE Comments SET Report = 0 WHERE Id = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, commentId);
            ps.executeUpdate();
            logAction(actor.getId(), "CLEAR_COMMENT_REPORT", "Comment", String.valueOf(commentId), null);
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the cap nhat bao cao binh luan.", ex);
        }
    }

    public void deleteComment(int commentId, User actor) {
        assertCommentInScope(actor, commentId);
        executeDelete(
                "DELETE FROM Comments WHERE Id = ?",
                commentId,
                actor,
                "DELETE_COMMENT",
                "Comment",
                String.valueOf(commentId),
                "Khong the xoa binh luan.");
    }

    public List<Promotion> listPromotions() {
        String sql = """
                SELECT TOP (200) p.*, u.FullName AS CreatedByName
                FROM Promotions p
                LEFT JOIN Users u ON u.Id=p.CreatedByUserId
                ORDER BY p.CreatedAt DESC
                """;
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            List<Promotion> promotions = new ArrayList<>();
            while (rs.next()) {
                promotions.add(mapPromotion(rs));
            }
            return promotions;
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the tai khuyen mai.", ex);
        }
    }

    public List<Promotion> listPromotions(User actor) {
        if (!CinemaCapabilityPolicy.canCreatePromotion(actor)) {
            throw new BookingException(403, "Bạn không có quyền xem khu vực khuyến mãi.");
        }
        return listPromotions();
    }

    public void updateUserMembershipTier(int userId, String newTier, User actor) {
        assertMemberInScope(actor, userId);
        MembershipTier tier = MembershipTier.fromCode(newTier)
                .orElseThrow(() -> new BookingException(400, "Vui lòng chọn hạng thành viên hợp lệ."));
        String sql = "UPDATE Users SET MembershipTier = ?, UpdatedAt = GETDATE() WHERE Id = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, tier.name());
            ps.setInt(2, userId);
            ps.executeUpdate();
            logAction(actor.getId(), "CHANGE_MEMBERSHIP_TIER", "User", String.valueOf(userId),
                    "Nâng hạng thành: " + newTier);
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the cap nhat hang thanh vien.", ex);
        }
    }

    public void savePromotion(Promotion promotion, User actor) {
        requirePromotionCapability(actor);
        if (promotion.getId() > 0) {
            assertPromotionOwnership(actor, promotion.getId());
        }
        if (promotion.getCode() == null || promotion.getCode().isBlank()
                || promotion.getStartDate() == null || promotion.getEndDate() == null) {
            throw new BookingException(400, "Vui long nhap day du thong tin khuyen mai.");
        }
        if (promotion.getEndDate().isBefore(promotion.getStartDate())) {
            throw new BookingException(400, "Ngay ket thuc khong duoc truoc ngay bat dau.");
        }
        if (promotion.getDiscountPercent() == null
                || promotion.getDiscountPercent() < 0
                || promotion.getDiscountPercent() > 100) {
            throw new BookingException(400, "Phần trăm giảm giá phải nằm trong khoảng 0 đến 100.");
        }
        if (promotion.getPerUserLimit() < 0 || promotion.getPerUserLimit() > 1) {
            throw new BookingException(400, "Giới hạn mỗi người hiện chỉ hỗ trợ 0 hoặc 1.");
        }
        boolean created = false;
        try (Connection connection = DBConnection.getConnection()) {
            if (promotion.getId() > 0) {
                String sql = """
                        UPDATE Promotions
                        SET Code = ?, Description = ?, DiscountPercent = ?, MaxDiscount = ?, StartDate = ?, EndDate = ?,
                            ConditionsJson = ?, UsageLimit = ?, Status = ?, VoucherType = ?, TargetTier = ?,
                            PointsRequired = ?, PerUserLimit = ?, UpdatedAt = GETDATE()
                        WHERE Id = ?
                        """;
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    bindPromotionParams(ps, promotion);
                    ps.setInt(14, promotion.getId());
                    ps.executeUpdate();
                }
                logAction(actor.getId(), "UPDATE_PROMOTION", "Promotion", String.valueOf(promotion.getId()),
                        promotion.getCode());
            } else {
                String sql = """
                        INSERT INTO Promotions (Code, Description, DiscountPercent, MaxDiscount, StartDate, EndDate,
                            ConditionsJson, UsageLimit, Status, VoucherType, TargetTier, PointsRequired, PerUserLimit,
                            CreatedByUserId)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """;
                try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    bindPromotionParams(ps, promotion);
                    ps.setInt(14, actor.getId());
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (keys.next()) {
                            promotion.setId(keys.getInt(1));
                        }
                    }
                }
                logAction(actor.getId(), "CREATE_PROMOTION", "Promotion", String.valueOf(promotion.getId()),
                        promotion.getCode());
                created = true;
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the luu khuyen mai.", ex);
        }

        // CHI gui khi TAO MOI.
        //
        // savePromotion dung chung cho ca tao lan sua, nen day la cho de sai nhat cua ca tinh nang:
        // dat loi goi ngoai nhanh nay thi moi lan quan tri vien bam Luu — ke ca chi sua mot dau
        // phay trong mo ta — deu tro thanh mot dot gui cho TOAN BO danh sach member.
        if (created) {
            announceNewPromotion(promotion);
        }
    }

    /** Mac dinh khi {@code mail.promotionMaxRecipients} chua co trong {@code SystemSettings}. */
    private static final int DEFAULT_PROMOTION_MAX_RECIPIENTS = 200;

    /**
     * Bao uu dai moi cho member thuoc dung doi tuong.
     *
     * <p>Khong gui cho uu dai tao san o trang thai {@code inactive} hay uu dai da het han: thu bao
     * ve mot ma khong dung duoc chi lam phien va lam nguoi ta bo qua nhung thu sau.</p>
     */
    private void announceNewPromotion(Promotion promotion) {
        try {
            if (!"active".equalsIgnoreCase(promotion.getStatus())) {
                return;
            }
            if (promotion.getEndDate() != null
                    && promotion.getEndDate().isBefore(DBConnection.dbNow().toLocalDate())) {
                return;
            }

            int cap = com.mycompany.website.ban.ve.xem.phim.config.SettingsReader.readInt(
                    "mail.promotionMaxRecipients",
                    DEFAULT_PROMOTION_MAX_RECIPIENTS, 1, 5000);
            List<PromotionRecipient> recipients = findPromotionRecipients(promotion.getTargetTier());
            if (recipients.size() > cap) {
                LOGGER.warning("Uu dai " + promotion.getCode() + " co " + recipients.size()
                        + " nguoi nhan, vuot tran " + cap + " — chi gui cho " + cap
                        + " nguoi dau tien de khong dot het han ngach gui cua ca ngay.");
                recipients = recipients.subList(0, cap);
            }

            String discount = promotion.getDiscountPercent() == null
                    ? "—" : trimZero(promotion.getDiscountPercent()) + "%";
            String maxDiscount = promotion.getMaxDiscount() == null
                    ? "Không giới hạn" : EmailService.formatMoney(promotion.getMaxDiscount());
            String endDate = EmailService.formatDate(promotion.getEndDate());
            String audience = promotion.getTargetTierDisplay();

            EmailService email = new EmailService();
            for (PromotionRecipient recipient : recipients) {
                email.sendPromotionAnnouncement(recipient.email(), recipient.fullName(),
                        promotion.getCode(), promotion.getDescription(),
                        discount, maxDiscount, endDate, audience);
            }
        } catch (RuntimeException ex) {
            // Uu dai da luu thanh cong roi; thu hong khong duoc lam thao tac do that bai.
            LOGGER.log(Level.SEVERE,
                    "Khong the gui thu uu dai code=" + promotion.getCode(), ex);
        }
    }

    /**
     * Member se nhan thu uu dai, lay trong <b>mot</b> cau SQL.
     *
     * <p>Dung dung vi ngu voi {@code JdbcUserNotificationDAO.resolveRecipients} — {@code Role='member'}
     * va tai khoan khong bi khoa — de thu va thong bao trong ung dung khong bao gio lech tap nguoi
     * nhan. Lay ca email trong cau nay thay vi lap {@code findById} cho tung nguoi: N+1 trong vong
     * lap la thu du an cam.</p>
     *
     * @param targetTier {@code "ALL"} nghia la moi hang thanh vien
     */
    private List<PromotionRecipient> findPromotionRecipients(String targetTier) {
        boolean allTiers = targetTier == null || targetTier.isBlank()
                || "ALL".equalsIgnoreCase(targetTier);
        String sql = """
                SELECT u.Email, u.FullName FROM Users u
                WHERE u.Role = 'member' AND ISNULL(u.IsLocked, 0) = 0
                  AND u.Email IS NOT NULL AND LTRIM(RTRIM(u.Email)) <> ''
                """
                + (allTiers ? "" : " AND UPPER(ISNULL(u.MembershipTier, 'BRONZE')) = UPPER(?)")
                + " ORDER BY u.Id";
        List<PromotionRecipient> recipients = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            if (!allTiers) {
                ps.setString(1, targetTier);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    recipients.add(new PromotionRecipient(rs.getString("Email"), rs.getString("FullName")));
                }
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Khong doc duoc danh sach nhan thu uu dai", ex);
            return List.of();
        }
        return recipients;
    }

    /** Bo ".0" thua cua {@code Double} de thu khong hien "50.0%". */
    private static String trimZero(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private record PromotionRecipient(String email, String fullName) {
    }

    private void bindPromotionParams(PreparedStatement ps, Promotion promotion) throws SQLException {
        ps.setString(1, promotion.getCode());
        ps.setString(2, promotion.getDescription());
        bindNullableDouble(ps, 3, promotion.getDiscountPercent());
        ps.setBigDecimal(4, promotion.getMaxDiscount());
        ps.setDate(5, Date.valueOf(promotion.getStartDate()));
        ps.setDate(6, Date.valueOf(promotion.getEndDate()));
        ps.setString(7, promotion.getConditionsJson());
        bindNullableInt(ps, 8, promotion.getUsageLimit());
        ps.setString(9,
                promotion.getStatus() == null || promotion.getStatus().isBlank() ? "active" : promotion.getStatus());
        ps.setString(10, promotion.getVoucherType());
        ps.setString(11, promotion.getTargetTier());
        ps.setInt(12, promotion.getPointsRequired());
        ps.setInt(13, promotion.getPerUserLimit());
    }

    /**
     * @return {@code true} when the row was physically deleted; {@code false}
     *         when history/dependencies required an inactive transition.
     */
    public boolean deletePromotion(int promotionId, User actor) {
        requirePromotionCapability(actor);
        assertPromotionOwnership(actor, promotionId);
        boolean hardDeleted = false;
        String auditAction = null;
        String auditDetail = null;
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                // Recheck the preview under a row lock.  Redemption/voucher creation
                // racing with a preview must make the operation inactive rather than
                // allowing a FK failure or history loss.
                Map<String, Object> impact = loadPromotionDeleteImpactInfo(connection, promotionId);
                int orderRefs = (int) impact.getOrDefault("orderRefs", 0);
                int usageRefs = (int) impact.getOrDefault("usageRefs", 0);
                int voucherRefs = (int) impact.getOrDefault("voucherRefs", 0);
                int usedCount = (int) impact.getOrDefault("usedCount", 0);
                if (orderRefs > 0 || usageRefs > 0 || voucherRefs > 0) {
                    try (PreparedStatement ps = connection.prepareStatement(
                            "UPDATE Promotions SET Status='inactive', UpdatedAt=GETDATE() WHERE Id=?")) {
                        ps.setInt(1, promotionId);
                        if (ps.executeUpdate() == 0) {
                            throw new BookingException(404, "Khong tim thay khuyen mai.");
                        }
                    }
                    auditAction = "DEACTIVATE_PROMOTION";
                    auditDetail = impact.toString();
                } else if (usedCount > 0) {
                    try (PreparedStatement ps = connection.prepareStatement(
                            "UPDATE Promotions SET Status='inactive', UpdatedAt=GETDATE() WHERE Id=?")) {
                        ps.setInt(1, promotionId);
                        ps.executeUpdate();
                    }
                    auditAction = "DEACTIVATE_PROMOTION";
                    auditDetail = impact + ";UsedCount mismatch: no usage rows but counter is positive";
                } else {
                    try (PreparedStatement ps = connection.prepareStatement("DELETE FROM Promotions WHERE Id=?")) {
                        ps.setInt(1, promotionId);
                        if (ps.executeUpdate() == 0) {
                            throw new BookingException(404, "Khong tim thay khuyen mai.");
                        }
                    }
                    hardDeleted = true;
                    auditAction = "DELETE_PROMOTION";
                    auditDetail = impact.toString();
                }
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the xu ly vong doi khuyen mai.", ex);
        }
        auditAfterCommit(actor.getId(), auditAction, "Promotion", String.valueOf(promotionId), auditDetail);
        return hardDeleted;
    }

    public Map<String, Object> getPromotionDeleteImpactInfo(int promotionId) {
        try (Connection connection = DBConnection.getConnection()) {
            return loadPromotionDeleteImpactInfo(connection, promotionId);
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the kiem tra anh huong khuyen mai.", ex);
        }
    }

    public PromotionDeleteImpact previewPromotionDeleteImpact(int promotionId, User actor) {
        requirePromotionCapability(actor);
        assertPromotionOwnership(actor, promotionId);
        Map<String, Object> impact = getPromotionDeleteImpactInfo(promotionId);
        return new PromotionDeleteImpact(promotionId,
                String.valueOf(impact.getOrDefault("code", "")),
                (int) impact.getOrDefault("orderRefs", 0),
                (int) impact.getOrDefault("usageRefs", 0),
                (int) impact.getOrDefault("voucherRefs", 0),
                (int) impact.getOrDefault("usedCount", 0));
    }

    private Map<String, Object> loadPromotionDeleteImpactInfo(Connection connection, int promotionId)
            throws SQLException {
        String sql = """
                SELECT p.Code,
                       (SELECT COUNT(*) FROM Orders WHERE PromotionId=p.Id) AS OrderRefs,
                       (SELECT COUNT(*) FROM PromotionUsage WHERE PromotionId=p.Id) AS UsageRefs,
                       (SELECT COUNT(*) FROM UserVouchers WHERE PromotionId=p.Id) AS VoucherRefs,
                       p.UsedCount
                FROM Promotions p WITH (UPDLOCK, HOLDLOCK) WHERE p.Id=?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, promotionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new BookingException(404, "Khong tim thay khuyen mai.");
                }
                Map<String, Object> impact = new LinkedHashMap<>();
                impact.put("code", rs.getString("Code"));
                impact.put("orderRefs", rs.getInt("OrderRefs"));
                impact.put("usageRefs", rs.getInt("UsageRefs"));
                impact.put("voucherRefs", rs.getInt("VoucherRefs"));
                impact.put("usedCount", rs.getInt("UsedCount"));
                return impact;
            }
        }
    }

    // ==================== Combo bap nuoc (ComboFoods) ====================
    // Tang dat ve doc bang nay qua JdbcComboFoodDAO.findActive() (chi
    // Status='active').
    // Vi vay "ngung ban" = doi Status sang 'inactive', khong can xoa.

    /**
     * Danh sach combo cho trang quan tri, kem so luong da ban de manager biet
     * combo nao dang chay va combo nao khong the xoa.
     */
    public List<ComboFood> listCombos() {
        return listCombos(null);
    }

    /**
     * Danh sach combo cho trang quan tri, kem so luong da ban.
     *
     * <p>
     * <b>Loi da sua (CB-01).</b> Ban cu la
     * {@code return ScopeUtil.isManager(actor) ?
     * List.of() : listCombos();} — manager mo trang Combo thay danh sach rong hoan
     * toan, va moi
     * thao tac ghi bi {@code requireGlobalCatalogAdmin} chan. Khong phai phan
     * quyen, chi la
     * chua lam.
     * </p>
     *
     * <p>
     * Manager chi thay combo cua rap minh. Admin o context toan he thong thay
     * combo cua moi rap; combo legacy khong co rap khong quay lai danh muc van hanh.
     * Quyen sua nam o {@link #assertComboWritable}.
     * </p>
     */
    public List<ComboFood> listCombos(User actor) {
        boolean scoped = ScopeUtil.isManager(actor);
        String sql = """
                SELECT c.Id, c.Name, c.Image, c.Price, c.Description, c.Status, c.CinemaId,
                       cin.Name AS CinemaName, ISNULL(SUM(ocf.Quantity), 0) AS SoldQuantity
                FROM ComboFoods c
                LEFT JOIN Cinemas cin ON cin.Id = c.CinemaId
                LEFT JOIN OrderComboFoods ocf ON ocf.ComboFoodId = c.Id
                """
                + (scoped ? "WHERE c.CinemaId = ? " : "WHERE c.CinemaId IS NOT NULL ")
                + """
                        GROUP BY c.Id, c.Name, c.Image, c.Price, c.Description, c.Status, c.CinemaId, cin.Name
                        ORDER BY c.Status ASC, c.Name ASC
                        """;
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            if (scoped) {
                ps.setInt(1, requireActorCinema(actor));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<ComboFood> combos = new ArrayList<>();
                while (rs.next()) {
                    combos.add(mapCombo(rs, true));
                }
                return combos;
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể tải danh sách combo.", ex);
        }
    }

    /**
     * Chan sua combo ngoai pham vi cua actor (CB-01).
     *
     * <p>
     * Manager chi duoc dung toi combo cua chinh rap minh. Combo dung chung
     * ({@code CinemaId IS NULL}) anh huong moi rap nen chi admin duoc sua — day la
     * ly do
     * khong don gian mo quyen ghi global cho manager.
     * </p>
     */
    private void assertComboWritable(User actor, Integer comboCinemaId) {
        if (comboCinemaId == null || comboCinemaId <= 0) {
            if (ScopeUtil.isManager(actor)) {
                throw new BookingException(403,
                        "Combo is outside the manager's assigned cinema.");
            }
            throw new BookingException(400, "Vui lòng chọn rạp sở hữu combo.");
        }
        if (!ScopeUtil.isManager(actor)) {
            return;
        }
        ScopeUtil.assertCinemaScope(actor, comboCinemaId);
    }

    private void assertActiveCinemaForCombo(int cinemaId) {
        String sql = "SELECT COUNT(*) FROM Cinemas WHERE Id=? AND LOWER(ISNULL(Status,'active'))='active'";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, cinemaId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getInt(1) == 0) {
                    throw new BookingException(400, "Rạp sở hữu combo không tồn tại hoặc đã ngừng hoạt động.");
                }
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể kiểm tra rạp sở hữu combo.", ex);
        }
    }

    /** Doc CinemaId hien tai cua mot combo; nem 404 khi combo khong ton tai. */
    private Integer comboCinemaId(int comboId) {
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(
                        "SELECT CinemaId FROM ComboFoods WHERE Id = ?")) {
            ps.setInt(1, comboId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new BookingException(404, "Không tìm thấy combo.");
                }
                int cinemaId = rs.getInt(1);
                return rs.wasNull() ? null : cinemaId;
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể đọc phạm vi của combo.", ex);
        }
    }

    public Optional<ComboFood> findComboById(int comboId) {
        String sql = """
                SELECT c.*, cin.Name AS CinemaName
                FROM ComboFoods c LEFT JOIN Cinemas cin ON cin.Id = c.CinemaId
                WHERE c.Id = ?
                """;
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, comboId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapCombo(rs, false)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể tải combo.", ex);
        }
    }

    public void saveCombo(ComboFood combo, User actor) {
        if (combo.getName() == null || combo.getName().isBlank()) {
            throw new BookingException(400, "Vui lòng nhập tên combo.");
        }
        if (combo.getPrice() == null || combo.getPrice().signum() < 0) {
            throw new BookingException(400, "Giá combo phải là số không âm.");
        }
        // Manager luon bi ep ve rap cua minh, ke ca khi form gui len CinemaId khac
        // (CB-01).
        if (ScopeUtil.isManager(actor)) {
            combo.setCinemaId(requireActorCinema(actor));
        }
        if (combo.getId() > 0) {
            Integer existingCinemaId = comboCinemaId(combo.getId());
            assertComboWritable(actor, existingCinemaId);
            // Ownership is immutable so historical orders never appear to move cinemas.
            combo.setCinemaId(existingCinemaId);
        }
        assertComboWritable(actor, combo.getCinemaId());
        assertActiveCinemaForCombo(combo.getCinemaId());
        try (Connection connection = DBConnection.getConnection()) {
            if (combo.getId() > 0) {
                String sql = """
                        UPDATE ComboFoods
                        SET Name = ?, Image = ?, Price = ?, Description = ?, Status = ?, CinemaId = ?
                        WHERE Id = ?
                        """;
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    bindComboParams(ps, combo);
                    ps.setInt(7, combo.getId());
                    ps.executeUpdate();
                }
                logAction(actor.getId(), "UPDATE_COMBO", "ComboFood",
                        String.valueOf(combo.getId()), combo.getName());
            } else {
                String sql = """
                        INSERT INTO ComboFoods (Name, Image, Price, Description, Status, CinemaId)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """;
                try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    bindComboParams(ps, combo);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (keys.next()) {
                            combo.setId(keys.getInt(1));
                        }
                    }
                }
                logAction(actor.getId(), "CREATE_COMBO", "ComboFood",
                        String.valueOf(combo.getId()), combo.getName());
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể lưu combo.", ex);
        }
    }

    /**
     * Doi trang thai ban/ngung ban ma khong dung toi don hang cu.
     * Day la thao tac nen dung thay cho xoa.
     */
    public void updateComboStatus(int comboId, String status, User actor) {
        assertComboWritable(actor, comboCinemaId(comboId));
        String normalized = "active".equalsIgnoreCase(status) ? "active" : "inactive";
        String sql = "UPDATE ComboFoods SET Status = ? WHERE Id = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, normalized);
            ps.setInt(2, comboId);
            if (ps.executeUpdate() == 0) {
                throw new BookingException(404, "Không tìm thấy combo.");
            }
            logAction(actor.getId(), "UPDATE_COMBO_STATUS", "ComboFood",
                    String.valueOf(comboId), normalized);
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể đổi trạng thái combo.", ex);
        }
    }

    public void deleteCombo(int comboId, User actor) {
        assertComboWritable(actor, comboCinemaId(comboId));
        try (Connection connection = DBConnection.getConnection()) {
            ensureComboDeletable(connection, comboId);
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM ComboFoods WHERE Id = ?")) {
                ps.setInt(1, comboId);
                ps.executeUpdate();
            }
            logAction(actor.getId(), "DELETE_COMBO", "ComboFood", String.valueOf(comboId), null);
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể xóa combo.", ex);
        }
    }

    /**
     * OrderComboFoods co khoa ngoai toi ComboFoods, xoa combo da ban se vo rang
     * buoc
     * va lam hong lich su don hang. Chan tu truoc de bao loi ro rang thay vi loi
     * SQL.
     */
    private void ensureComboDeletable(Connection connection, int comboId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM OrderComboFoods WHERE ComboFoodId = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, comboId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                if (rs.getInt(1) > 0) {
                    throw new BookingException(400,
                            "Combo này đã có trong đơn hàng nên không thể xóa. "
                                    + "Hãy chuyển sang trạng thái \"Ngừng bán\" để ẩn khỏi trang đặt vé.");
                }
            }
        }
    }

    private void bindComboParams(PreparedStatement ps, ComboFood combo) throws SQLException {
        ps.setString(1, combo.getName());
        ps.setString(2, combo.getImage() == null || combo.getImage().isBlank() ? null : combo.getImage());
        ps.setBigDecimal(3, combo.getPrice());
        ps.setString(4, combo.getDescription());
        ps.setString(5, combo.getStatus() == null || combo.getStatus().isBlank() ? "active" : combo.getStatus());
        // NULL = combo dung chung toan he thong (CB-01).
        bindNullableInt(ps, 6, combo.getCinemaId());
    }

    private ComboFood mapCombo(ResultSet rs, boolean withSoldQuantity) throws SQLException {
        ComboFood combo = new ComboFood();
        combo.setId(rs.getInt("Id"));
        combo.setName(rs.getString("Name"));
        combo.setImage(rs.getString("Image"));
        combo.setPrice(rs.getBigDecimal("Price"));
        combo.setDescription(rs.getString("Description"));
        combo.setStatus(rs.getString("Status"));
        int comboCinemaId = rs.getInt("CinemaId");
        combo.setCinemaId(rs.wasNull() ? null : comboCinemaId);
        if (hasColumn(rs, "CinemaName")) {
            combo.setCinemaName(rs.getString("CinemaName"));
        }
        if (withSoldQuantity) {
            combo.setSoldQuantity(rs.getInt("SoldQuantity"));
        }
        return combo;
    }

    /**
     * Mot trang don hang, mac dinh 50 dong dau.
     *
     * <p>
     * <b>Khong dung ham nay de dem.</b> Ket qua da bi cat theo trang, nen
     * {@code .size()} cua no
     * chi la so dong cua trang chu khong phai tong so don (F-003). Can tong thi goi
     * {@link #countOrdersForAdmin(String, LocalDate, LocalDate, Integer, String, User)}
     * hoac doc
     * {@code PageResult.totalItems()}.
     * </p>
     */
    public List<OrderRecord> listOrdersForAdmin() {
        return listOrdersForAdmin(1, 50, null, null, null, null, null).items();
    }

    /**
     * Dieu kien loc don hang — dung chung cho ca truy van trang va truy van dem.
     */
    private record OrderFilter(String joins, String where, List<Object> values) {
    }

    /**
     * Mot cho duy nhat dinh nghia bo loc don hang.
     *
     * <p>
     * So dem tren dashboard va danh sach o {@code /admin/orders} phai lay tu dung
     * mot bieu thuc
     * WHERE; neu tach thanh hai ban sao thi mot ngay nao do chung se lech nhau.
     * </p>
     */
    private OrderFilter orderFilter(String status, LocalDate from, LocalDate to,
            Integer cinemaId, String ticketCode) {
        return orderFilter(status, from, to, cinemaId, ticketCode, null);
    }

    private OrderFilter orderFilter(String status, LocalDate from, LocalDate to,
            Integer cinemaId, String ticketCode, String bucket) {
        List<Object> values = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        if (status != null && !status.isBlank()) {
            where.append(" AND (o.OrderStatus=? OR o.PaymentStatus=?)");
            values.add(status);
            values.add(status);
        }
        if (from != null) {
            where.append(" AND o.CreatedAt>=?");
            values.add(Date.valueOf(from));
        }
        if (to != null) {
            where.append(" AND o.CreatedAt<DATEADD(DAY,1,?)");
            values.add(Date.valueOf(to));
        }
        if (cinemaId != null) {
            where.append(" AND s.CinemaId=?");
            values.add(cinemaId);
        }
        if (ticketCode != null && !ticketCode.isBlank()) {
            where.append(" AND o.TicketCode LIKE ?");
            values.add("%" + ticketCode.trim() + "%");
        }
        String bucketPredicate = switch (bucket == null ? "" : bucket) {
            case "pending" -> " AND o.PaymentStatus='paid' AND o.OrderStatus='confirmed' AND o.RefundRejectedAt IS NULL AND (s.StartTime IS NULL OR s.StartTime>=GETDATE())";
            case "late" -> " AND o.PaymentStatus='paid' AND o.OrderStatus='confirmed' AND o.RefundRejectedAt IS NULL AND s.StartTime<GETDATE() AND (s.EndTime IS NULL OR s.EndTime>=GETDATE())";
            case "refund" -> " AND o.PaymentStatus='paid' AND o.OrderStatus='confirmed' AND o.RefundRejectedAt IS NULL "
                    + "AND EXISTS (SELECT 1 FROM UserAppeals ua WHERE ua.OrderId=o.Id "
                    + "AND ua.AppealType='refund' AND ua.Status='pending')";
            case "rejected" -> " AND o.RefundRejectedAt IS NOT NULL";
            case "redeemed" -> " AND o.OrderStatus='redeemed'";
            case "cancelled" -> " AND (o.OrderStatus='cancelled' OR o.PaymentStatus='cancelled')";
            default -> "";
        };
        where.append(bucketPredicate);
        String joins = """
                 FROM Orders o
                 JOIN Users u ON u.Id=o.UserId
                 JOIN Showtimes s ON s.Id=o.ShowtimeId
                 JOIN Films f ON f.Id=s.FilmId
                 JOIN Cinemas c ON c.Id=s.CinemaId
                 JOIN Rooms r ON r.Id=s.RoomId
                """;
        return new OrderFilter(joins, where.toString(), values);
    }

    /**
     * Tong so don khop bo loc — mot query {@code COUNT_BIG}, khong tai dong nao
     * (F-003).
     */
    public long countOrdersForAdmin(String status, LocalDate from, LocalDate to,
            Integer cinemaId, String ticketCode) {
        OrderFilter filter = orderFilter(status, from, to, cinemaId, ticketCode);
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(
                        "SELECT COUNT_BIG(*)" + filter.joins() + filter.where())) {
            bind(ps, filter.values());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể đếm số đơn hàng.", ex);
        }
    }

    /**
     * Nhu tren, nhung gioi han theo cum rap cua manager giong danh sach
     * {@code /admin/orders}.
     */
    public long countOrdersForAdmin(String status, LocalDate from, LocalDate to,
            Integer cinemaId, String ticketCode, User actor) {
        return countOrdersForAdmin(status, from, to, effectiveOrderCinema(cinemaId, actor), ticketCode);
    }

    public long countOrdersForAdmin(String status, LocalDate from, LocalDate to,
            Integer cinemaId, String ticketCode, String bucket, User actor) {
        OrderFilter filter = orderFilter(status, from, to, effectiveOrderCinema(cinemaId, actor), ticketCode, bucket);
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement("SELECT COUNT_BIG(*)" + filter.joins() + filter.where())) {
            bind(ps, filter.values());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getLong(1);
                }
        } catch (SQLException ex) {
            throw new BookingException(500, "KhÃ´ng thá»ƒ Ä‘áº¿m sá»‘ Ä‘Æ¡n hÃ ng.", ex);
        }
    }

    /**
     * Exactly four queries per page: count, page rows, all seats and all combos.
     */
    public PageResult<OrderRecord> listOrdersForAdmin(int page, int size, String status,
            LocalDate from, LocalDate to, Integer cinemaId, String ticketCode) {
        return listOrdersForAdmin(page, size, status, from, to, cinemaId, ticketCode, (String) null);
    }

    public PageResult<OrderRecord> listOrdersForAdmin(int page, int size, String status,
            LocalDate from, LocalDate to, Integer cinemaId, String ticketCode, String bucket) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(10, size));
        OrderFilter filter = orderFilter(status, from, to, cinemaId, ticketCode, bucket);
        List<Object> values = filter.values();
        String where = filter.where();
        String joins = filter.joins();
        try (Connection connection = DBConnection.getConnection()) {
            long total;
            try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT_BIG(*)" + joins + where)) {
                bind(ps, values);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    total = rs.getLong(1);
                }
            }

            String pageSql = """
                    SELECT o.*, f.Title AS FilmTitle, f.DurationMinutes, c.Name AS CinemaName,
                           r.Name AS RoomName, s.StartTime, s.EndTime,
                           u.Email AS UserEmail, u.FullName
                    """ + joins + where + " ORDER BY o.CreatedAt DESC, o.Id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
            List<OrderRecord> orders = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(pageSql)) {
                int index = bind(ps, values);
                ps.setInt(index++, (safePage - 1) * safeSize);
                ps.setInt(index, safeSize);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        OrderRecord order = mapOrder(rs);
                        order.setFilmTitle(rs.getString("FilmTitle"));
                        order.setDurationMinutes(rs.getInt("DurationMinutes"));
                        order.setCinemaName(rs.getString("CinemaName"));
                        order.setRoomName(rs.getString("RoomName"));
                        order.setStartTime(toLocalDateTime(rs.getTimestamp("StartTime")));
                        order.setEndTime(toLocalDateTime(rs.getTimestamp("EndTime")));
                        order.setUserEmail(rs.getString("UserEmail"));
                        order.setUserFullName(rs.getString("FullName"));
                        order.setRefundedAt(toLocalDateTime(rs.getTimestamp("RefundedAt")));
                        order.setRefundAmount(rs.getBigDecimal("RefundAmount"));
                        order.setCancelReason(rs.getString("CancelReason"));
                        orders.add(order);
                    }
                }
            }
            loadOrderChildren(connection, orders);
            return new PageResult<>(orders, safePage, safeSize, total);
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể tải danh sách đơn hàng.", ex);
        }
    }

    public PageResult<OrderRecord> listOrdersForAdmin(int page, int size, String status,
            LocalDate from, LocalDate to, Integer cinemaId, String ticketCode, User actor) {
        return listOrdersForAdmin(page, size, status, from, to,
                effectiveOrderCinema(cinemaId, actor), ticketCode);
    }

    public PageResult<OrderRecord> listOrdersForAdmin(int page, int size, String status,
            LocalDate from, LocalDate to, Integer cinemaId, String ticketCode, User actor, String bucket) {
        return listOrdersForAdmin(page, size, status, from, to,
                effectiveOrderCinema(cinemaId, actor), ticketCode, bucket);
    }

    /**
     * Cum rap thuc su duoc phep xem: manager bi ep ve rap cua minh, admin giu
     * nguyen bo loc.
     */
    private Integer effectiveOrderCinema(Integer cinemaId, User actor) {
        if (!ScopeUtil.isManager(actor)) {
            return cinemaId;
        }
        int assigned = requireActorCinema(actor);
        if (cinemaId != null) {
            ScopeUtil.assertCinemaScope(actor, cinemaId);
        }
        return assigned;
    }

    /**
     * So dem cho trang tong quan admin/manager (F-003).
     *
     * <p>
     * Truoc day dashboard lay {@code .size()} cua cac danh sach, ma cac danh sach
     * do deu bi cat:
     * don hang 50 dong, audit log 50, phim/thanh vien/khuyen mai {@code TOP (200)},
     * suat chieu
     * {@code TOP (500)}. Ket qua la o nao vuot nguong cung dung yen o dung con so
     * nguong do. Day
     * dem bang {@code COUNT_BIG(*)} tren cung dieu kien loc ma tung man hinh danh
     * sach dang dung,
     * nen so o dashboard khop voi so dong nguoi dung thay khi bam vao.
     * </p>
     *
     * <p>
     * Manager chi duoc dem trong pham vi cum rap cua minh, giong het cac man hinh
     * danh sach:
     * phim qua {@code CinemaFilms}, phong qua {@code Rooms.CinemaId}, suat chieu
     * qua rap cua phong,
     * thanh vien qua {@code Users.CinemaId}, khuyen mai la 0 vi
     * {@code listPromotions(actor)} khong
     * tra gi cho manager.
     * </p>
     *
     * <p>
     * Nem {@link BookingException} khi khong doc duoc — <b>khong</b> tra 0. So 0
     * gia lam nguoi
     * quan tri tin rang he thong dang trong.
     * </p>
     */
    public Map<String, Long> dashboardCounts(User actor) {
        boolean manager = ScopeUtil.isManager(actor);
        Integer cinema = manager ? requireActorCinema(actor) : null;
        boolean admin = actor != null && AppConstants.ROLE_ADMIN.equalsIgnoreCase(actor.getRole());
        Map<String, Long> counts = new LinkedHashMap<>();
        try (Connection connection = DBConnection.getConnection()) {
            counts.put("filmCount", manager
                    ? scalarCount(connection, "SELECT COUNT_BIG(*) FROM CinemaFilms WHERE CinemaId = ? AND Status=N'active'", cinema)
                    : scalarCount(connection, "SELECT COUNT_BIG(*) FROM Films", null));
            // D-04: hai o nay truoc day khong loc 'deleted' trong khi /admin/cinemas va
            // /admin/rooms thi co, nen ngay khi xoa mem mot phong la Tong quan dem nhieu hon
            // danh sach. Dung dung bo loc voi listRooms()/listCinemas().
            String activeCinema = "ISNULL(c.Status, 'active') <> 'deleted'";
            String activeRoom = "ISNULL(r.Status, 'active') <> 'deleted'";
            counts.put("cinemaCount", manager
                    ? scalarCount(connection,
                            "SELECT COUNT_BIG(*) FROM Cinemas c JOIN Cities ci ON ci.Id = c.CityId WHERE c.Id = ? AND "
                                    + activeCinema, cinema)
                    : scalarCount(connection,
                            "SELECT COUNT_BIG(*) FROM Cinemas c JOIN Cities ci ON ci.Id = c.CityId WHERE "
                                    + activeCinema, null));
            counts.put("roomCount", manager
                    ? scalarCount(connection,
                            "SELECT COUNT_BIG(*) FROM Rooms r JOIN Cinemas c ON c.Id = r.CinemaId"
                                    + " WHERE r.CinemaId = ? AND " + activeRoom, cinema)
                    : scalarCount(connection,
                            "SELECT COUNT_BIG(*) FROM Rooms r JOIN Cinemas c ON c.Id = r.CinemaId WHERE "
                                    + activeRoom, null));
            String showtimeJoins = """
                     FROM Showtimes s
                     JOIN Films f ON f.Id = s.FilmId
                     JOIN Cinemas c ON c.Id = s.CinemaId
                     JOIN Rooms r ON r.Id = s.RoomId
                    """;
            counts.put("showtimeCount", manager
                    ? scalarCount(connection, "SELECT COUNT_BIG(*)" + showtimeJoins + " WHERE s.CinemaId = ?", cinema)
                    : scalarCount(connection, "SELECT COUNT_BIG(*)" + showtimeJoins, null));
            counts.put("memberCount", manager
                    ? scalarCount(connection,
                            """
                            WITH Scope(CinemaId) AS (SELECT CAST(? AS INT))
                            SELECT COUNT_BIG(*)
                            FROM Users u CROSS JOIN Scope scoped
                            WHERE u.Role = 'member' AND (
                              u.CinemaId = scoped.CinemaId OR EXISTS (
                                SELECT 1 FROM Orders o
                                JOIN Showtimes s ON s.Id = o.ShowtimeId
                                WHERE o.UserId = u.Id AND s.CinemaId = scoped.CinemaId
                              )
                            )
                            """, cinema)
                    : scalarCount(connection, "SELECT COUNT_BIG(*) FROM Users WHERE Role = 'member'", null));
            counts.put("promotionCount",
                    scalarCount(connection, "SELECT COUNT_BIG(*) FROM Promotions", null));
            if (admin) {
                counts.put("managerCount",
                        scalarCount(connection, "SELECT COUNT_BIG(*) FROM Users WHERE Role = 'manager'", null));
                counts.put("auditCount", scalarCount(connection, "SELECT COUNT_BIG(*) FROM AuditLogs", null));
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể tải số liệu tổng quan.", ex);
        }
        if (admin) {
            // Doc sau khi da dong connection o tren: ensureDefaultSettings() tu mo
            // connection rieng,
            // long connection trong khi dang giu mot connection khac la mau da gay deadlock
            // truoc day.
            counts.put("settingCount", (long) listSettings().size());
        }
        counts.put("orderCount", countOrdersForAdmin(null, null, null, null, null, actor));
        return counts;
    }

    private long scalarCount(Connection connection, String sql, Integer cinemaId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (cinemaId != null) {
                ps.setInt(1, cinemaId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private void loadOrderChildren(Connection connection, List<OrderRecord> orders) throws SQLException {
        if (orders.isEmpty())
            return;
        Map<Integer, OrderRecord> byId = new HashMap<>();
        orders.forEach(order -> byId.put(order.getId(), order));
        String placeholders = String.join(",", java.util.Collections.nCopies(orders.size(), "?"));
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT os.OrderId, os.ShowtimeSeatId, os.SeatKey, os.SeatType, os.UnitPrice
                FROM OrderSeats os WHERE os.OrderId IN (
                """ + placeholders + ") ORDER BY os.OrderId, os.SeatKey")) {
            bindIds(ps, orders);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    OrderSeatItem seat = new OrderSeatItem();
                    seat.setShowtimeSeatId(rs.getInt("ShowtimeSeatId"));
                    seat.setSeatKey(rs.getString("SeatKey"));
                    seat.setSeatType(rs.getString("SeatType"));
                    seat.setUnitPrice(rs.getBigDecimal("UnitPrice"));
                    byId.get(rs.getInt("OrderId")).getSeats().add(seat);
                }
            }
        }
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT oc.OrderId, oc.ComboFoodId, cf.Name AS ComboName, oc.Quantity, oc.UnitPrice
                FROM OrderComboFoods oc JOIN ComboFoods cf ON cf.Id=oc.ComboFoodId
                WHERE oc.OrderId IN (
                """ + placeholders + ") ORDER BY oc.OrderId, oc.ComboFoodId")) {
            bindIds(ps, orders);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    OrderComboItem combo = new OrderComboItem();
                    combo.setComboFoodId(rs.getInt("ComboFoodId"));
                    combo.setComboName(rs.getString("ComboName"));
                    combo.setQuantity(rs.getInt("Quantity"));
                    combo.setUnitPrice(rs.getBigDecimal("UnitPrice"));
                    byId.get(rs.getInt("OrderId")).getCombos().add(combo);
                }
            }
        }
    }

    private int bind(PreparedStatement ps, List<Object> values) throws SQLException {
        int index = 1;
        for (Object value : values)
            ps.setObject(index++, value);
        return index;
    }

    private void bindIds(PreparedStatement ps, List<OrderRecord> orders) throws SQLException {
        for (int i = 0; i < orders.size(); i++)
            ps.setInt(i + 1, orders.get(i).getId());
    }

    public Optional<OrderRecord> findOrderById(int orderId) {
        return orderDAO.findById(orderId);
    }

    public Optional<OrderRecord> findOrderById(int orderId, User actor) {
        assertOrderScope(actor, orderId);
        return findOrderById(orderId);
    }

    public void cancelOrder(int orderId, User actor) {
        cancelOrder(orderId, actor, "Admin/Manager hủy đơn");
    }

    public void cancelOrder(int orderId, User actor, String reason) {
        String orderBeforeJson;
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int userId;
                String paymentMethod;
                String paymentStatus;
                String orderStatus;
                BigDecimal totalAmount;

                String selectSql = """
                        SELECT o.UserId, o.PaymentMethod, o.PaymentStatus,
                               o.OrderStatus, o.TotalAmount, s.CinemaId
                        FROM Orders o WITH (UPDLOCK, HOLDLOCK)
                        JOIN Showtimes s ON s.Id=o.ShowtimeId WHERE o.Id=?
                        """;
                try (PreparedStatement ps = connection.prepareStatement(selectSql)) {
                    ps.setInt(1, orderId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new BookingException(404, "Không tìm thấy đơn hàng.");
                        }
                        userId = rs.getInt("UserId");
                        paymentMethod = rs.getString("PaymentMethod");
                        paymentStatus = rs.getString("PaymentStatus");
                        orderStatus = rs.getString("OrderStatus");
                        totalAmount = rs.getBigDecimal("TotalAmount");
                        ScopeUtil.assertCinemaScope(actor, rs.getInt("CinemaId"));
                    }
                }

                if ("paid".equalsIgnoreCase(paymentStatus)
                        || "refunded".equalsIgnoreCase(paymentStatus)
                        || "redeemed".equalsIgnoreCase(orderStatus)) {
                    throw new BookingException(409,
                            "Đơn đã thanh toán/hoàn tiền/check-in nên không thể hủy trực tiếp. "
                            + "Vui lòng sử dụng quy trình hoàn tiền phù hợp.");
                }
                if ("cancelled".equalsIgnoreCase(orderStatus)
                        && "cancelled".equalsIgnoreCase(paymentStatus)) {
                    connection.commit();
                    return; // Đã huỷ trước đó
                }

                boolean unpaidDraft = "pending".equalsIgnoreCase(paymentStatus)
                        && ("created".equalsIgnoreCase(orderStatus)
                        || "pending".equalsIgnoreCase(orderStatus));
                boolean pendingCounter = "counter".equalsIgnoreCase(paymentMethod)
                        && "pending".equalsIgnoreCase(paymentStatus)
                        && "confirmed".equalsIgnoreCase(orderStatus);
                if (!unpaidDraft && !pendingCounter) {
                    throw new BookingException(409,
                            "Đơn đã thanh toán hoặc không còn ở trạng thái cho phép hủy. "
                            + "Vui lòng sử dụng quy trình hoàn tiền.");
                }

                // B.3: trang thai truoc khi huy, de audit tra loi duoc "don dang o dau luc bi huy".
                orderBeforeJson = javax.json.Json.createObjectBuilder()
                        .add("paymentMethod", String.valueOf(paymentMethod))
                        .add("paymentStatus", String.valueOf(paymentStatus))
                        .add("orderStatus", String.valueOf(orderStatus))
                        .add("totalAmount", totalAmount == null ? "null" : totalAmount.toPlainString())
                        .build().toString();

                // Conditional update is the final guard even though the row is already locked.
                String updateOrderSql = """
                        UPDATE Orders
                        SET OrderStatus='cancelled', PaymentStatus='cancelled',
                            CounterExpiresAt=NULL, CancelledAt=GETDATE(),
                            CancelReason=?, UpdatedAt=GETDATE()
                        WHERE Id=? AND PaymentStatus='pending' AND (
                          OrderStatus IN ('created','pending') OR
                          (PaymentMethod='counter' AND OrderStatus='confirmed')
                        )
                        """;
                try (PreparedStatement ps = connection.prepareStatement(updateOrderSql)) {
                    ps.setString(1, reason);
                    ps.setInt(2, orderId);
                    if (ps.executeUpdate() != 1) {
                        throw new BookingException(409,
                                "Trạng thái đơn đã thay đổi. Vui lòng tải lại danh sách.");
                    }
                }

                // Release only resources reserved by this unpaid order.
                String releaseSeatsSql = """
                        UPDATE ss
                        SET Status='available', HeldByUserId=NULL, HeldAt=NULL, HeldUntil=NULL,
                            ClaimedByOrderId=NULL
                        FROM ShowtimeSeats ss
                        JOIN OrderSeats os ON os.ShowtimeSeatId=ss.Id
                        WHERE os.OrderId=? AND (
                          (ss.Status='held' AND ss.HeldByUserId=?) OR ss.Status='booked'
                        )
                          AND (ss.ClaimedByOrderId=os.OrderId OR ss.ClaimedByOrderId IS NULL)
                        """;
                try (PreparedStatement ps = connection.prepareStatement(releaseSeatsSql)) {
                    ps.setInt(1, orderId);
                    ps.setInt(2, userId);
                    ps.executeUpdate();
                }

                // Draft orders have not consumed a promotion yet. Counter-confirmed orders have,
                // so only that shape may restore voucher/promotion quota.
                if (pendingCounter) {
                    restorePersonalVoucher(connection, orderId);
                    releasePromotionUsage(connection, orderId);
                }

                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Khong the huy don hang id=" + orderId, ex);
            throw new BookingException(500, "Không thể hủy đơn hàng: " + ex.getMessage());
        }
        auditAfterCommit(actor == null ? null : actor.getId(), "CANCEL_ORDER", "Order",
                String.valueOf(orderId), orderBeforeJson, reason);
    }

    public void refundOrder(int orderId, BigDecimal refundAmount, String reason, User actor) {
        refundOrder(orderId, refundAmount, reason, actor, false);
    }

    /**
     * Hoan tien cho mot don, co duong vuot dieu kien danh cho quan ly (BUG-11, INV-8).
     *
     * <p>Truoc day ham nay chi kiem {@code PaymentStatus='paid'} va khoang tien hop le; cau SELECT
     * co doc {@code OrderStatus} nhung <b>khong dung den</b>, nen ve da check-in — khach da xem
     * xong phim — van hoan tien duoc.</p>
     *
     * <p>Chinh sach da chot:</p>
     * <ol>
     *   <li>Tu choi khi {@code OrderStatus = 'redeemed'}.</li>
     *   <li>Chi hoan khi con cach gio chieu it nhat N phut, N doc theo thu tu
     *       {@code refund.cutoffMinutes} → {@code booking.cutoffMinutes} → 15.</li>
     *   <li>Quan ly duoc bo qua ca hai, nhung <b>bat buoc</b> nhap ly do; audit ghi
     *       {@code REFUND_OVERRIDE} kem ly do va dieu kien da bo qua.</li>
     * </ol>
     *
     * <p>So sanh thoi gian nam trong SQL (INV-10) — gio SQL Server la nguon thoi gian duy nhat.</p>
     */
    public void refundOrder(int orderId, BigDecimal refundAmount, String reason, User actor,
            boolean overrideRestrictions) {
        RefundOutcome outcome;
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                outcome = refundOrderInTransaction(connection, orderId, refundAmount, reason, actor,
                        overrideRestrictions ? RefundMode.MANAGER_OVERRIDE : RefundMode.STANDARD);
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Khong the hoan tien don hang id=" + orderId, ex);
            throw new BookingException(500, "Không thể hoàn tiền cho đơn hàng: " + ex.getMessage());
        }
        finishRefundAfterCommit(outcome, actor, false);
    }

    private RefundOutcome refundOrderInTransaction(Connection connection, int orderId,
            BigDecimal refundAmount, String reason, User actor, RefundMode mode) throws SQLException {
        int userId;
        String paymentStatus;
        String orderStatus;
        BigDecimal totalAmount;
        boolean withinRefundWindow;
        boolean showtimeEnded;
        boolean redeemed;
        boolean refunded;
        boolean refundRejected;
        int cutoffMinutes = refundCutoffMinutes(connection);

        String selectSql = """
                SELECT o.UserId, o.PaymentStatus, o.OrderStatus, o.TotalAmount, s.CinemaId,
                       CASE WHEN s.StartTime IS NULL
                              OR DATEADD(MINUTE, ?, GETDATE()) <= s.StartTime
                            THEN 1 ELSE 0 END AS WithinRefundWindow,
                       CASE WHEN s.EndTime IS NOT NULL AND s.EndTime <= GETDATE()
                            THEN 1 ELSE 0 END AS ShowtimeEnded,
                       CASE WHEN o.RedeemedAt IS NOT NULL OR o.OrderStatus='redeemed'
                            THEN 1 ELSE 0 END AS IsRedeemed,
                       CASE WHEN o.RefundedAt IS NOT NULL OR o.PaymentStatus='refunded'
                            THEN 1 ELSE 0 END AS IsRefunded,
                       CASE WHEN o.RefundRejectedAt IS NOT NULL THEN 1 ELSE 0 END AS IsRefundRejected
                FROM Orders o WITH (UPDLOCK, HOLDLOCK)
                JOIN Showtimes s ON s.Id=o.ShowtimeId WHERE o.Id=?
                """;
        try (PreparedStatement statement = connection.prepareStatement(selectSql)) {
            statement.setInt(1, cutoffMinutes);
            statement.setInt(2, orderId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new BookingException(404, "Không tìm thấy đơn hàng.");
                }
                userId = result.getInt("UserId");
                paymentStatus = result.getString("PaymentStatus");
                orderStatus = result.getString("OrderStatus");
                totalAmount = result.getBigDecimal("TotalAmount");
                withinRefundWindow = result.getInt("WithinRefundWindow") == 1;
                showtimeEnded = result.getInt("ShowtimeEnded") == 1;
                redeemed = result.getInt("IsRedeemed") == 1;
                refunded = result.getInt("IsRefunded") == 1;
                refundRejected = result.getInt("IsRefundRejected") == 1;
                ScopeUtil.assertCinemaScope(actor, result.getInt("CinemaId"));
            }
        }

        String orderBeforeJson = javax.json.Json.createObjectBuilder()
                .add("paymentStatus", String.valueOf(paymentStatus))
                .add("orderStatus", String.valueOf(orderStatus))
                .add("totalAmount", totalAmount == null ? "null" : totalAmount.toPlainString())
                .add("withinRefundWindow", withinRefundWindow)
                .add("showtimeEnded", showtimeEnded)
                .build().toString();

        if (refunded || !"paid".equalsIgnoreCase(paymentStatus)) {
            throw new BookingException(refunded ? 409 : 400,
                    refunded ? "Đơn này đã được hoàn tiền."
                            : "Chỉ có thể hoàn tiền cho đơn đã thanh toán.");
        }
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0
                || totalAmount == null || refundAmount.compareTo(totalAmount) > 0) {
            throw new BookingException(400, "Số tiền hoàn không hợp lệ.");
        }
        if (refundAmount.compareTo(totalAmount) != 0) {
            throw new BookingException(400,
                    "Chỉ hỗ trợ hoàn tiền toàn bộ đúng bằng TotalAmount của đơn hàng.");
        }

        String overriddenConditions = null;
        if (mode == RefundMode.MISSED_TICKET_APPEAL) {
            if (redeemed) {
                throw new BookingException(409, "Vé đã được check-in, không thể duyệt hoàn tiền.");
            }
            if (!"confirmed".equalsIgnoreCase(orderStatus)) {
                throw new BookingException(409, "Vé không còn ở trạng thái đã xác nhận.");
            }
            if (refundRejected) {
                throw new BookingException(409, "Yêu cầu hoàn tiền đã bị từ chối trước đó.");
            }
            if (!showtimeEnded) {
                throw new BookingException(409,
                        "Chỉ duyệt luồng khiếu nại sau khi suất chiếu đã kết thúc.");
            }
            if (refundAmount.compareTo(totalAmount) != 0) {
                throw new BookingException(400,
                        "Yêu cầu hoàn tiền vé phải hoàn đúng toàn bộ giá trị đơn.");
            }
        } else {
            List<String> blocked = new ArrayList<>();
            if (redeemed) {
                blocked.add("vé đã được sử dụng (đã check-in)");
            }
            if (!withinRefundWindow) {
                blocked.add("đã quá hạn hoàn tiền (phải còn ít nhất " + cutoffMinutes
                        + " phút trước giờ chiếu)");
            }
            if (!blocked.isEmpty()) {
                String joined = String.join("; ", blocked);
                if (mode != RefundMode.MANAGER_OVERRIDE) {
                    throw new BookingException(400, "Không thể hoàn tiền: " + joined
                            + ". Quản lý có thể bỏ qua điều kiện này nhưng phải ghi rõ lý do.");
                }
                if (reason == null || reason.isBlank()) {
                    throw new BookingException(400,
                            "Bỏ qua điều kiện hoàn tiền thì bắt buộc phải nhập lý do.");
                }
                overriddenConditions = joined;
            }
        }

        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE Orders
                SET PaymentStatus='refunded', OrderStatus='cancelled',
                    RefundedAt=GETDATE(), RefundAmount=?, RefundReason=?, RefundedBy=?,
                    CancelledAt=GETDATE(), CancelReason=?, UpdatedAt=GETDATE()
                WHERE Id=? AND PaymentStatus='paid'
                """)) {
            statement.setBigDecimal(1, refundAmount);
            statement.setString(2, reason);
            statement.setInt(3, actor.getId());
            statement.setString(4, reason);
            statement.setInt(5, orderId);
            if (statement.executeUpdate() != 1) {
                throw new BookingException(409, "Đơn đã được xử lý bởi yêu cầu khác.");
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE ShowtimeSeats
                SET Status='available', HeldByUserId=NULL, HeldAt=NULL, HeldUntil=NULL,
                    ClaimedByOrderId=NULL
                WHERE Id IN (SELECT ShowtimeSeatId FROM OrderSeats WHERE OrderId=?)
                """)) {
            statement.setInt(1, orderId);
            statement.executeUpdate();
        }
        resolveRefundAppealForOrder(connection, orderId, "approved", reason, actor.getId());
        restorePersonalVoucher(connection, orderId);
        releasePromotionUsage(connection, orderId);
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO RefundTransactions (OrderId,Amount,Reason,RefundedBy,RefundedAt)
                VALUES (?,?,?,?,GETDATE())
                """)) {
            statement.setInt(1, orderId);
            statement.setBigDecimal(2, refundAmount);
            statement.setString(3, reason);
            statement.setInt(4, actor.getId());
            statement.executeUpdate();
        }
        new LoyaltyService().reversePointsOnRefund(connection, userId, orderId, refundAmount, reason);
        return new RefundOutcome(orderId, refundAmount, reason, orderBeforeJson,
                overriddenConditions, mode == RefundMode.MISSED_TICKET_APPEAL);
    }

    private void finishRefundAfterCommit(RefundOutcome outcome, User actor, boolean fromAppeal) {
        auditAfterCommit(actor.getId(), "REFUND_ORDER", "Order", String.valueOf(outcome.orderId()),
                outcome.beforeJson(), outcome.refundAmount().toPlainString());
        if (outcome.overriddenConditions() != null) {
            auditAfterCommit(actor.getId(), "REFUND_OVERRIDE", "Order",
                    String.valueOf(outcome.orderId()), outcome.beforeJson(),
                    javax.json.Json.createObjectBuilder()
                            .add("reason", outcome.reason())
                            .add("overriddenConditions", outcome.overriddenConditions())
                            .add("refundAmount", outcome.refundAmount().toPlainString())
                            .build().toString());
        }
        if (fromAppeal || outcome.fromAppeal()) {
            auditAfterCommit(actor.getId(), "APPROVE_REFUND_APPEAL", "Order",
                    String.valueOf(outcome.orderId()), outcome.beforeJson(), outcome.reason());
        }
        try {
            new InvoiceService().issueRefundAdjustment(outcome.orderId(), outcome.refundAmount());
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE,
                    "Khong the sinh hoa don dieu chinh orderId=" + outcome.orderId(), ex);
        }
        try {
            loadOrderMailContext(outcome.orderId()).ifPresent(mail ->
                    new EmailService().sendRefundApproved(mail.email(), mail.fullName(),
                            outcome.orderId(), mail.film(), mail.showtime(),
                            EmailService.formatMoney(outcome.refundAmount()), outcome.reason(),
                            EmailService.formatDateTime(DBConnection.dbNow())));
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE,
                    "Khong the gui thu hoan tien orderId=" + outcome.orderId(), ex);
        }
    }

    /**
     * Ghi nhan quyet dinh <b>tu choi</b> hoan tien (BUG-10, INV-9).
     *
     * <p>Truoc day nhanh {@code rejectRefund} o {@code ManagerPortalServlet} chi hien mot flash
     * message roi khong lam gi ca: khong doi trang thai, khong luu ly do, khong ghi audit. Quyet
     * dinh cua quan ly bien mat — don van nam trong tab "Cho hoan tien" o lan xem sau, va khi khach
     * khieu nai thi khong co gi doi chat.</p>
     *
     * <p>Khong tai dung {@code OrderStatus='cancelled'} lam dau: hai hang so doanh thu loai don
     * {@code cancelled}, nen danh dau nham se lam bao cao hut mot ve da ban va da thu tien.</p>
     */
    public void rejectRefund(int orderId, String reason, User actor) {
        if (reason == null || reason.isBlank()) {
            throw new BookingException(400, "Từ chối hoàn tiền thì bắt buộc phải nhập lý do.");
        }
        RefundRejectionOutcome outcome;
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                outcome = rejectRefundInTransaction(connection, orderId, reason, actor);
                notificationDAO.resolveByTarget(connection, "Order", String.valueOf(orderId), actor.getId(), "rejected");
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Khong the ghi nhan tu choi hoan tien orderId=" + orderId, ex);
            throw new BookingException(500, "Không thể ghi nhận từ chối hoàn tiền: " + ex.getMessage());
        }
        finishRefundRejectionAfterCommit(outcome, actor, false);
    }

    private RefundRejectionOutcome rejectRefundInTransaction(Connection connection, int orderId,
            String reason, User actor) throws SQLException {
        String beforeJson;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT o.PaymentStatus, o.OrderStatus, o.RefundRejectedAt,
                       o.RefundRejectReason, s.CinemaId
                FROM Orders o WITH (UPDLOCK, HOLDLOCK)
                JOIN Showtimes s ON s.Id=o.ShowtimeId WHERE o.Id=?
                """)) {
            statement.setInt(1, orderId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new BookingException(404, "Không tìm thấy đơn hàng.");
                }
                ScopeUtil.assertCinemaScope(actor, result.getInt("CinemaId"));
                if ("refunded".equalsIgnoreCase(result.getString("PaymentStatus"))) {
                    throw new BookingException(409,
                            "Đơn này đã được hoàn tiền, không thể từ chối.");
                }
                if (result.getTimestamp("RefundRejectedAt") != null) {
                    throw new BookingException(409,
                            "Đơn này đã bị từ chối hoàn tiền trước đó.");
                }
                beforeJson = javax.json.Json.createObjectBuilder()
                        .add("paymentStatus", String.valueOf(result.getString("PaymentStatus")))
                        .add("orderStatus", String.valueOf(result.getString("OrderStatus")))
                        .add("refundRejectedAt", "null")
                        .build().toString();
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE Orders
                SET RefundRejectedAt=GETDATE(), RefundRejectReason=?,
                    RefundRejectedBy=?, UpdatedAt=GETDATE()
                WHERE Id=? AND RefundRejectedAt IS NULL AND PaymentStatus<>'refunded'
                """)) {
            statement.setString(1, reason);
            statement.setInt(2, actor.getId());
            statement.setInt(3, orderId);
            if (statement.executeUpdate() != 1) {
                throw new BookingException(409, "Đơn đã được xử lý bởi yêu cầu khác.");
            }
        }
        resolveRefundAppealForOrder(connection, orderId, "rejected", reason, actor.getId());
        return new RefundRejectionOutcome(orderId, reason, beforeJson);
    }

    private void resolveRefundAppealForOrder(Connection connection, int orderId, String status,
            String response, int resolverId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE UserAppeals
                SET Status=?, AdminResponse=?, ResolvedByUserId=?, ResolvedAt=GETDATE()
                WHERE OrderId=? AND AppealType='refund' AND Status='pending'
                """)) {
            statement.setString(1, status);
            statement.setString(2, response);
            statement.setInt(3, resolverId);
            statement.setInt(4, orderId);
            statement.executeUpdate();
        }
    }

    private void finishRefundRejectionAfterCommit(RefundRejectionOutcome outcome, User actor,
            boolean fromAppeal) {
        auditAfterCommit(actor.getId(), "REJECT_REFUND", "Order", String.valueOf(outcome.orderId()),
                outcome.beforeJson(), javax.json.Json.createObjectBuilder()
                        .add("reason", outcome.reason())
                        .add("refundRejectedBy", actor.getId())
                        .build().toString());
        if (fromAppeal) {
            auditAfterCommit(actor.getId(), "REJECT_REFUND_APPEAL", "Order",
                    String.valueOf(outcome.orderId()), outcome.beforeJson(), outcome.reason());
        }
        try {
            // outcome.reason() la chuoi quan tri vien tu go — day chinh la ly do renderHtmlDocument
            // bat buoc escape gia tri.
            loadOrderMailContext(outcome.orderId()).ifPresent(mail ->
                    new EmailService().sendRefundRejected(mail.email(), mail.fullName(),
                            outcome.orderId(), mail.film(), mail.showtime(), outcome.reason()));
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE,
                    "Khong the gui thu tu choi hoan tien orderId=" + outcome.orderId(), ex);
        }
    }

    private enum RefundMode {
        STANDARD,
        MANAGER_OVERRIDE,
        MISSED_TICKET_APPEAL
    }

    private record RefundOutcome(int orderId, BigDecimal refundAmount, String reason,
            String beforeJson, String overriddenConditions, boolean fromAppeal) {
    }

    private record RefundRejectionOutcome(int orderId, String reason, String beforeJson) {
    }

    /**
     * So phut toi thieu con lai truoc gio chieu thi con duoc hoan tien.
     *
     * <p>Thu tu uu tien: {@code refund.cutoffMinutes} → {@code booking.cutoffMinutes} → 15. Doc ca
     * hai khoa trong mot cau de khong ban them round trip trong luc transaction dang giu khoa.</p>
     */
    private int refundCutoffMinutes(Connection connection) throws SQLException {
        Integer refundCutoff = null;
        Integer bookingCutoff = null;
        String sql = """
                SELECT SettingKey, SettingValue FROM SystemSettings
                WHERE SettingKey IN ('refund.cutoffMinutes', 'booking.cutoffMinutes')
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Integer parsed = parseSettingInt(rs.getString("SettingKey"), rs.getString("SettingValue"));
                if ("refund.cutoffMinutes".equalsIgnoreCase(rs.getString("SettingKey"))) {
                    refundCutoff = parsed;
                } else {
                    bookingCutoff = parsed;
                }
            }
        }
        if (refundCutoff != null) {
            return refundCutoff;
        }
        return bookingCutoff == null ? DEFAULT_REFUND_CUTOFF_MINUTES : bookingCutoff;
    }

    private Integer parseSettingInt(String key, String raw) {
        try {
            int parsed = Integer.parseInt(raw == null ? "" : raw.trim());
            return parsed < 0 ? null : parsed;
        } catch (NumberFormatException ex) {
            LOGGER.log(Level.WARNING, "Cau hinh ''{0}'' = ''{1}'' khong phai so, bo qua", new Object[] {key, raw});
            return null;
        }
    }

    /**
     * Tra lai luot dung cua ma khuyen mai khi don bi hoan (BUG-17, INV-12).
     *
     * <p>Luc ban, {@code payOrder} tang {@code Promotions.UsedCount} va ghi {@code PromotionUsage}.
     * Luc hoan, truoc day chi {@code UserVouchers} duoc tra lai — ma cong khai thi khong. Hau qua:
     * quy ma cong khai can dan sau moi lan hoan, va nguoi co {@code PerUserLimit} mat vinh vien mot
     * luot dung cho mot don ho khong duoc su dung.</p>
     *
     * <p>Chay bang chinh {@code Connection} cua transaction hoan tien nen hai buoc nay cung song
     * cung chet voi viec hoan tien.</p>
     */
    private void releasePromotionUsage(Connection connection, int orderId) throws SQLException {
        String decrementSql = """
                UPDATE p
                SET p.UsedCount = CASE WHEN p.UsedCount > 0 THEN p.UsedCount - 1 ELSE 0 END
                FROM Promotions p
                JOIN Orders o ON o.PromotionId = p.Id
                WHERE o.Id = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(decrementSql)) {
            ps.setInt(1, orderId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM PromotionUsage WHERE OrderId = ?")) {
            ps.setInt(1, orderId);
            ps.executeUpdate();
        }
    }

    private void restorePersonalVoucher(Connection connection, int orderId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                UPDATE UserVouchers SET IsUsed=0, UsedAt=NULL, UsedOrderId=NULL
                WHERE UsedOrderId=?
                """)) {
            ps.setInt(1, orderId);
            ps.executeUpdate();
        }
    }

    public int cancelExpiredCounterOrders() {
        String findExpiredSql = """
                SELECT Id FROM Orders WITH (UPDLOCK, HOLDLOCK)
                WHERE PaymentMethod = 'counter' AND PaymentStatus = 'pending' AND OrderStatus = 'confirmed'
                AND CounterExpiresAt IS NOT NULL AND CounterExpiresAt <= GETDATE()
                """;
        List<Integer> expiredIds = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(findExpiredSql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                expiredIds.add(rs.getInt("Id"));
            }
        } catch (SQLException ex) {
            // N-12: khong nuot nguyen nhan. Tra 0 la fail-safe (khong huy nham don nao),
            // nhung neu khong log thi mot loi DB keo dai se im lang lam don counter qua han
            // khong bao gio duoc don, va khong ai biet vi sao.
            LOGGER.log(Level.SEVERE, "Khong doc duoc danh sach don counter qua han; bo qua luot quet nay", ex);
            return 0;
        }

        User systemActor = new User();
        systemActor.setId(1); // System Admin ID
        systemActor.setRole("admin");

        int cancelledCount = 0;
        for (int id : expiredIds) {
            try {
                cancelOrder(id, systemActor, "Đơn thanh toán tại quầy quá hạn");
                cancelledCount++;
            } catch (RuntimeException ex) {
                LOGGER.log(Level.WARNING, "Khong the huy don counter qua han id=" + id, ex);
            }
        }
        return cancelledCount;
    }

    public void redeemTicket(String ticketCode, User actor) {
        redeemTicket(ticketCode, actor, false, null);
    }

    /** Explicitly audited manager/admin check-in override for the time window. */
    public void redeemTicket(String ticketCode, User actor, boolean override, String overrideReason) {
        if (ticketCode == null || ticketCode.isBlank()) {
            throw new BookingException(400, "Ma ve khong duoc de rong.");
        }
        String cleanCode = ticketCode.trim();
        String verifiedCode = QrCodeUtil.verifiedTicketCode(cleanCode);
        if (verifiedCode == null) {
            if (cleanCode.indexOf('.') < 0) {
                verifiedCode = cleanCode;
            } else {
                throw new BookingException(400, "Chu ky ma ve khong hop le.");
            }
        }
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(
                    """
                            SELECT o.Id, o.OrderStatus, o.PaymentStatus, o.RedeemedAt, s.CinemaId,
                                   CASE WHEN DATEADD(MINUTE, -60, s.StartTime) <= GETDATE()
                                             AND (s.EndTime IS NULL OR GETDATE() <= s.EndTime)
                                        THEN 1 ELSE 0 END AS CheckInWindowOpen
                            FROM Orders o WITH (UPDLOCK, HOLDLOCK)
                            JOIN Showtimes s ON s.Id=o.ShowtimeId WHERE o.TicketCode=?
                            """)) {
                ps.setString(1, verifiedCode);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new BookingException(404, "Khong tim thay ve.");
                    }
                    if (!"paid".equalsIgnoreCase(rs.getString("PaymentStatus"))
                            || !"confirmed".equalsIgnoreCase(rs.getString("OrderStatus"))) {
                        throw new BookingException(400, "Ve chua thanh toan hoac khong hop le.");
                    }
                    ScopeUtil.assertCinemaScope(actor, rs.getInt("CinemaId"));
                    if (rs.getTimestamp("RedeemedAt") != null
                            || "redeemed".equalsIgnoreCase(rs.getString("OrderStatus"))) {
                        throw new BookingException(409, "Ve da su dung.");
                    }
                    boolean checkInWindowOpen = rs.getInt("CheckInWindowOpen") == 1;
                    if (!checkInWindowOpen) {
                        boolean privileged = actor != null
                                && ("manager".equalsIgnoreCase(actor.getRole())
                                || "admin".equalsIgnoreCase(actor.getRole()));
                        if (!override || !privileged) {
                            throw new BookingException(400,
                                    "Ve chi duoc check-in tu 60 phut truoc gio chieu den khi suat ket thuc.");
                        }
                        if (overrideReason == null || overrideReason.isBlank()) {
                            throw new BookingException(400,
                                    "Check-in ngoai khung gio bat buoc phai co ly do.");
                        }
                    }
                    int orderId = rs.getInt("Id");
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE Orders SET OrderStatus = 'redeemed', RedeemedAt = GETDATE(), UpdatedAt = GETDATE() WHERE Id = ?")) {
                        update.setInt(1, orderId);
                        update.executeUpdate();
                    }
                    connection.commit();
                    auditAfterCommit(actor.getId(), "REDEEM_TICKET", "Order", String.valueOf(orderId), verifiedCode);
                    if (!checkInWindowOpen) {
                        auditAfterCommit(actor.getId(), "CHECK_IN_OVERRIDE", "Order",
                                String.valueOf(orderId), verifiedCode, overrideReason);
                    }
                    // Chi toi day khi check-in THAT SU thanh cong. Hai nhan vien quet cung luc thi
                    // nguoi thua da bi nem 409 "Ve da su dung" tu trong transaction UPDLOCK ben tren,
                    // nen mot ve khong the sinh ra hai la thu.
                    sendCheckInEmailAfterCommit(orderId);
                }
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the check-in ve luc nay.", ex);
        }
    }

    /**
     * Xac nhan da thu tien mat cho don dat "thanh toan tai quay".
     * Don counter duoc tao voi PaymentStatus='pending', nen truoc buoc nay ve khong
     * the check-in
     * va khach cung chua duoc cong diem loyalty.
     */
    public void markCounterOrderPaid(int orderId, User actor) {
        int userId;
        BigDecimal totalAmount;
        String orderBeforeJson;
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(
                    """
                            SELECT o.UserId, o.TotalAmount, o.PaymentMethod, o.PaymentStatus,
                                   o.OrderStatus, o.CounterExpiresAt, s.CinemaId,
                                   r.Name AS RoomName, ISNULL(r.Status, 'active') AS RoomStatus
                            FROM Orders o WITH (UPDLOCK, HOLDLOCK)
                            JOIN Showtimes s ON s.Id=o.ShowtimeId
                            JOIN Rooms r ON r.Id=s.RoomId WHERE o.Id=?
                            """)) {
                ps.setInt(1, orderId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new BookingException(404, "Khong tim thay don hang.");
                    }
                    if (!"counter".equalsIgnoreCase(rs.getString("PaymentMethod"))) {
                        throw new BookingException(400, "Don nay khong phai loai thanh toan tai quay.");
                    }
                    ScopeUtil.assertCinemaScope(actor, rs.getInt("CinemaId"));
                    // BUG-05 (INV-7): khong nhan tien cho cho ngoi khong con phuc vu duoc. Quay la
                    // mot duong thu tien doc lap voi payOrder nen phai chiu dung chot nay.
                    if (!"active".equalsIgnoreCase(rs.getString("RoomStatus"))) {
                        throw new BookingException(400, "Phòng chiếu '" + rs.getString("RoomName")
                                + "' đang tạm ngưng hoạt động. Không thể thu tiền cho đơn này.");
                    }
                    if ("paid".equalsIgnoreCase(rs.getString("PaymentStatus"))) {
                        throw new BookingException(409, "Don nay da duoc thu tien truoc do.");
                    }
                    if (!"pending".equalsIgnoreCase(rs.getString("PaymentStatus"))
                            || !"confirmed".equalsIgnoreCase(rs.getString("OrderStatus"))) {
                        throw new BookingException(409,
                                "Đơn không còn ở trạng thái chờ thu tiền tại quầy.");
                    }
                    Timestamp counterExpiresAt = rs.getTimestamp("CounterExpiresAt");
                    if (counterExpiresAt == null) {
                        throw new BookingException(409,
                                "Đơn tại quầy không có hạn thu tiền hợp lệ.");
                    }
                    userId = rs.getInt("UserId");
                    totalAmount = rs.getBigDecimal("TotalAmount");
                    orderBeforeJson = javax.json.Json.createObjectBuilder()
                            .add("paymentMethod", String.valueOf(rs.getString("PaymentMethod")))
                            .add("paymentStatus", String.valueOf(rs.getString("PaymentStatus")))
                            .add("orderStatus", String.valueOf(rs.getString("OrderStatus")))
                            .add("counterExpiresAt", counterExpiresAt.toString())
                            .add("totalAmount", totalAmount == null
                                    ? "null" : totalAmount.toPlainString())
                            .build().toString();
                }
                try (PreparedStatement update = connection.prepareStatement(
                        """
                        UPDATE Orders
                        SET PaymentStatus='paid', TransactionId=?,
                            CounterExpiresAt=NULL, UpdatedAt=GETDATE()
                        WHERE Id=? AND PaymentMethod='counter'
                          AND PaymentStatus='pending' AND OrderStatus='confirmed'
                          AND CounterExpiresAt IS NOT NULL AND CounterExpiresAt>GETDATE()
                        """)) {
                    update.setString(1, "COUNTER-" + orderId);
                    update.setInt(2, orderId);
                    if (update.executeUpdate() != 1) {
                        throw new BookingException(409,
                                "Đơn tại quầy đã hết hạn hoặc trạng thái vừa thay đổi.");
                    }
                }
                // Money state and loyalty are one transaction. A ledger failure must roll back
                // payment, TransactionId and deadline clearing together.
                new LoyaltyService().addPointsOnPayment(
                        connection, userId, totalAmount, orderId);
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the xac nhan thu tien tai quay.", ex);
        }

        auditAfterCommit(actor == null ? null : actor.getId(), "MARK_COUNTER_PAID", "Order",
                String.valueOf(orderId), orderBeforeJson,
                javax.json.Json.createObjectBuilder()
                        .add("paymentStatus", "paid")
                        .add("transactionId", "COUNTER-" + orderId)
                        .add("totalAmount", totalAmount == null
                                ? "null" : totalAmount.toPlainString())
                        .build().toString());
        try {
            // Idempotent by (OrderId, InvoiceType='sale'); retry/race cannot duplicate it.
            new InvoiceService().issueSale(orderId);
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE,
                    "Khong the sinh hoa don thu tien tai quay orderId=" + orderId, ex);
        }

        // Khach tra tien mat cung phai nhan ve dien tu nhu khach tra the.
        //
        // BookingService.payOrder chi gui thu khi PaymentStatus='paid'; don tai quay sinh ra o
        // trang thai 'pending' nen di qua nhanh do ma khong bao gio duoc gui. Nay don quay nhan
        // ve tai dung thoi diem thu tien xong. Dung lai CHINH sendTicket cua duong the — mot loai
        // thu, mot template, khong the lech noi dung giua hai hinh thuc thanh toan.
        sendTicketEmailAfterCommit(orderId, "thu tien tai quay");
    }

    /**
     * Gui thu ve dien tu cho mot don da thanh toan. <b>Chi goi sau khi da commit.</b>
     *
     * <p>Thu hong khong duoc lam hong thao tac da commit, nen nuot loi o day la dung — nhung phai
     * log kem ngu canh, giong het khuon cua {@code InvoiceService.issueSale} ngay ben tren.</p>
     */
    private void sendTicketEmailAfterCommit(int orderId, String context) {
        try {
            loadOrderMailContext(orderId).ifPresent(mail -> new EmailService().sendTicket(
                    mail.email(), mail.fullName(), orderId, mail.ticketCode(),
                    mail.film(), mail.cinema(), mail.showtime()));
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE,
                    "Khong the gui thu ve (" + context + ") orderId=" + orderId, ex);
        }
    }

    /**
     * Thong tin mot don, du de dung bat ky la thu nao ve don do.
     *
     * <p>Doc <b>sau commit</b> bang mot connection rieng: cac cau SELECT trong transaction cua
     * {@code redeemTicket}/{@code refundOrder} deu dang giu {@code UPDLOCK}, nen them cot vao
     * chung la noi rong pham vi khoa cho mot viec khong lien quan gi toi quyet dinh nghiep vu.</p>
     *
     * <p>Ghe lay thang tu {@code OrderSeats.SeatKey} — bang nay da luu san nhan ghe, khong can
     * join qua {@code ShowtimeSeats}/{@code Seats}.</p>
     */
    private Optional<OrderMailContext> loadOrderMailContext(int orderId) {
        String sql = """
                SELECT u.Email, u.FullName, o.TicketCode, o.TicketQrUrl,
                       f.Title AS FilmTitle, c.Name AS CinemaName,
                       FORMAT(s.StartTime, 'HH:mm dd/MM/yyyy') AS ShowtimeDisplay,
                       FORMAT(o.RedeemedAt, 'HH:mm dd/MM/yyyy') AS RedeemedAtDisplay,
                       (SELECT STRING_AGG(os.SeatKey, ', ') FROM OrderSeats os
                        WHERE os.OrderId = o.Id) AS SeatList
                FROM Orders o
                JOIN Users u ON u.Id = o.UserId
                JOIN Showtimes s ON s.Id = o.ShowtimeId
                JOIN Films f ON f.Id = s.FilmId
                JOIN Cinemas c ON c.Id = s.CinemaId
                WHERE o.Id = ?
                """;
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    LOGGER.warning("Khong tim thay du lieu gui thu cho orderId=" + orderId);
                    return Optional.empty();
                }
                String email = rs.getString("Email");
                if (email == null || email.isBlank()) {
                    LOGGER.warning("Don orderId=" + orderId + " khong co email nguoi nhan.");
                    return Optional.empty();
                }
                return Optional.of(new OrderMailContext(email, rs.getString("FullName"),
                        rs.getString("TicketCode"), rs.getString("TicketQrUrl"),
                        rs.getString("FilmTitle"), rs.getString("CinemaName"),
                        rs.getString("ShowtimeDisplay"), rs.getString("SeatList"),
                        rs.getString("RedeemedAtDisplay")));
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Khong doc duoc du lieu gui thu orderId=" + orderId, ex);
            return Optional.empty();
        }
    }

    /** Xac nhan da vao rap, kem loi cam on. <b>Chi goi sau khi da commit.</b> */
    private void sendCheckInEmailAfterCommit(int orderId) {
        try {
            loadOrderMailContext(orderId).ifPresent(mail -> new EmailService().sendCheckInSuccess(
                    mail.email(), mail.fullName(), mail.ticketCode(), mail.film(),
                    mail.cinema(), mail.showtime(), mail.seats(), mail.redeemedAt()));
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE, "Khong the gui thu check-in orderId=" + orderId, ex);
        }
    }

    private record OrderMailContext(String email, String fullName, String ticketCode, String qrUrl,
            String film, String cinema, String showtime, String seats, String redeemedAt) {
    }

    // ---------------------------------------------------------------- bao cao
    //
    // FLOW-REPORT-001/002/003 — ba dinh nghia duoi day la NGUON DUY NHAT cho moi so lieu
    // doanh thu, du la dashboard admin, bao cao theo cum rap cua manager, hay file CSV.
    //
    // Truoc day moi truy van tu viet dieu kien loc, nen ba loi cung ton tai:
    //   * dashboard chi loc PaymentStatus='paid' -> don da HUY nhung con 'paid' van cong
    //     vao doanh thu;
    //   * cac bao cao khac loc RefundedAt IS NULL -> don hoan MOT PHAN bi loai ca don thay
    //     vi chi tru phan da hoan;
    //   * manager va admin cho hai con so khac nhau cho cung mot don.
    // Gom lai thanh hang so dung chung thi ba be mat khong the lech nhau nua.

    /**
     * Don duoc tinh doanh thu: da thu tien va chua bi huy.
     *
     * <p>{@code 'refunded'} nam trong danh sach la co y — mot don hoan MOT PHAN mang trang
     * thai nay nhung phan tien khach that su tra van la doanh thu. Loai no ra la bao cao thap
     * hon thuc te. Phan da tra lai duoc tru bang {@link #NET_REVENUE_EXPRESSION}.</p>
     */
    /** Han hoan tien mac dinh khi ca hai khoa cau hinh deu thieu (BUG-11). */
    static final int DEFAULT_REFUND_CUTOFF_MINUTES = 15;

    static final String REVENUE_ORDER_PREDICATE =
            "o.PaymentStatus IN ('paid', 'refunded') AND o.OrderStatus <> 'cancelled'";

    /** Doanh thu thuan cua mot don: tong tru so tien da hoan, khong bao gio am. */
    static final String NET_REVENUE_EXPRESSION =
            "CASE WHEN o.TotalAmount - COALESCE(o.RefundAmount, 0) > 0"
            + " THEN o.TotalAmount - COALESCE(o.RefundAmount, 0) ELSE 0 END";

    /**
     * Ve da ban: don da thu tien, chua huy va chua hoan.
     *
     * <p>Khac voi doanh thu, so ve khong chia nho duoc: he thong khong luu ghe nao bi hoan
     * trong mot lan hoan mot phan, nen don da hoan khong duoc dem la ve ban. Tap nay la tap
     * con cua {@link #REVENUE_ORDER_PREDICATE}.</p>
     */
    static final String SOLD_SEAT_ORDER_PREDICATE =
            "o.PaymentStatus = 'paid' AND o.OrderStatus <> 'cancelled' AND o.RefundedAt IS NULL";

    public List<RevenueRow> dailyRevenueRows() {
        String sql = """
                SELECT CONVERT(VARCHAR(10), o.CreatedAt, 120) AS RevenueDate,
                       COUNT(*) AS OrderCount, SUM(%s) AS TotalRevenue
                FROM Orders o
                WHERE %s
                GROUP BY CONVERT(VARCHAR(10), o.CreatedAt, 120)
                ORDER BY RevenueDate DESC
                """.formatted(NET_REVENUE_EXPRESSION, REVENUE_ORDER_PREDICATE);
        return revenueRows(sql, "RevenueDate");
    }

    public List<RevenueRow> dailyRevenueRows(User actor) {
        if (!ScopeUtil.isManager(actor))
            return dailyRevenueRows();
        return scopedRevenueRows("""
                SELECT CONVERT(VARCHAR(10), o.CreatedAt, 120) AS RevenueDate,
                       COUNT(*) AS OrderCount, SUM(%s) AS TotalRevenue
                FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId
                WHERE %s AND s.CinemaId=?
                GROUP BY CONVERT(VARCHAR(10), o.CreatedAt, 120)
                ORDER BY RevenueDate DESC
                """.formatted(NET_REVENUE_EXPRESSION, REVENUE_ORDER_PREDICATE),
                "RevenueDate", requireActorCinema(actor));
    }

    public List<RevenueRow> monthlyRevenueRows() {
        String sql = """
                SELECT FORMAT(o.CreatedAt, 'yyyy-MM') AS RevenueMonth,
                       COUNT(*) AS OrderCount, SUM(%s) AS TotalRevenue
                FROM Orders o
                WHERE %s
                GROUP BY FORMAT(o.CreatedAt, 'yyyy-MM')
                ORDER BY RevenueMonth DESC
                """.formatted(NET_REVENUE_EXPRESSION, REVENUE_ORDER_PREDICATE);
        return revenueRows(sql, "RevenueMonth");
    }

    public List<RevenueRow> monthlyRevenueRows(User actor) {
        if (!ScopeUtil.isManager(actor))
            return monthlyRevenueRows();
        return scopedRevenueRows("""
                SELECT FORMAT(o.CreatedAt, 'yyyy-MM') AS RevenueMonth,
                       COUNT(*) AS OrderCount, SUM(%s) AS TotalRevenue
                FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId
                WHERE %s AND s.CinemaId=?
                GROUP BY FORMAT(o.CreatedAt, 'yyyy-MM')
                ORDER BY RevenueMonth DESC
                """.formatted(NET_REVENUE_EXPRESSION, REVENUE_ORDER_PREDICATE),
                "RevenueMonth", requireActorCinema(actor));
    }

    public List<RevenueRow> yearlyRevenueRows() {
        String sql = """
                SELECT FORMAT(o.CreatedAt, 'yyyy') AS RevenueYear,
                       COUNT(*) AS OrderCount, SUM(%s) AS TotalRevenue
                FROM Orders o
                WHERE %s
                GROUP BY FORMAT(o.CreatedAt, 'yyyy')
                ORDER BY RevenueYear DESC
                """.formatted(NET_REVENUE_EXPRESSION, REVENUE_ORDER_PREDICATE);
        return revenueRows(sql, "RevenueYear");
    }

    public List<RevenueRow> yearlyRevenueRows(User actor) {
        if (!ScopeUtil.isManager(actor))
            return yearlyRevenueRows();
        return scopedRevenueRows("""
                SELECT FORMAT(o.CreatedAt, 'yyyy') AS RevenueYear,
                       COUNT(*) AS OrderCount, SUM(%s) AS TotalRevenue
                FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId
                WHERE %s AND s.CinemaId=?
                GROUP BY FORMAT(o.CreatedAt, 'yyyy')
                ORDER BY RevenueYear DESC
                """.formatted(NET_REVENUE_EXPRESSION, REVENUE_ORDER_PREDICATE),
                "RevenueYear", requireActorCinema(actor));
    }

    /**
     * Top phim theo so ve ban va doanh thu.
     *
     * <p>
     * <b>Hai loi da sua o day (RP-01).</b> Ban cu viet
     * {@code LEFT JOIN OrderSeats os ... COUNT(os.Id) ... SUM(o.TotalAmount)}:
     * </p>
     * <ol>
     * <li>{@code os.Id} khong ton tai tren DB production — {@code OrderSeats} dung
     * khoa kep
     * {@code (OrderId, ShowtimeSeatId)} — nen SQL Server nem "Invalid column name"
     * va
     * trang bao cao tra 500. Dem so ghe khong duoc phep gia dinh bang co cot dinh
     * danh
     * rieng, vi vay dung {@code COUNT_BIG(*)}.</li>
     * <li>{@code LEFT JOIN} nhan ban moi dong don len bang so ghe cua don do, nen
     * {@code SUM(o.TotalAmount)} cong tong tien mot lan cho MOI ghe — don 5 ghe bi
     * tinh
     * doanh thu gap 5. {@code OUTER APPLY} gom so ghe truoc, giu dung mot dong moi
     * don.</li>
     * </ol>
     */
    public List<TopFilmRow> topFilms() {
        String sql = """
                SELECT TOP (10) f.Id AS FilmId, f.Title,
                       SUM(CASE WHEN %s THEN seatAgg.SeatCount ELSE 0 END) AS SoldSeats,
                       SUM(%s) AS TotalRevenue
                FROM Orders o
                JOIN Showtimes s ON s.Id = o.ShowtimeId
                JOIN Films f ON f.Id = s.FilmId
                OUTER APPLY (
                    SELECT COUNT_BIG(*) AS SeatCount FROM OrderSeats os WHERE os.OrderId = o.Id
                ) seatAgg
                WHERE %s
                GROUP BY f.Id, f.Title
                ORDER BY SoldSeats DESC, TotalRevenue DESC, f.Id ASC
                """.formatted(SOLD_SEAT_ORDER_PREDICATE, NET_REVENUE_EXPRESSION,
                        REVENUE_ORDER_PREDICATE);
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            List<TopFilmRow> rows = new ArrayList<>();
            while (rs.next()) {
                TopFilmRow row = new TopFilmRow();
                row.setFilmId(rs.getInt("FilmId"));
                row.setFilmTitle(rs.getString("Title"));
                row.setSoldSeats(rs.getInt("SoldSeats"));
                row.setTotalRevenue(rs.getBigDecimal("TotalRevenue"));
                rows.add(row);
            }
            return rows;
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the tai bao cao top phim.", ex);
        }
    }

    public List<TopFilmRow> topFilms(User actor) {
        if (!ScopeUtil.isManager(actor))
            return topFilms();
        String sql = """
                SELECT TOP (10) f.Id AS FilmId, f.Title,
                       SUM(CASE WHEN %s THEN seatAgg.SeatCount ELSE 0 END) AS SoldSeats,
                       SUM(%s) AS TotalRevenue
                FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId
                JOIN Films f ON f.Id=s.FilmId
                OUTER APPLY (
                    SELECT COUNT_BIG(*) AS SeatCount FROM OrderSeats os WHERE os.OrderId = o.Id
                ) seatAgg
                WHERE %s AND s.CinemaId=?
                GROUP BY f.Id,f.Title
                ORDER BY SoldSeats DESC, TotalRevenue DESC, f.Id ASC
                """.formatted(SOLD_SEAT_ORDER_PREDICATE, NET_REVENUE_EXPRESSION,
                        REVENUE_ORDER_PREDICATE);
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, requireActorCinema(actor));
            List<TopFilmRow> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TopFilmRow row = new TopFilmRow();
                    row.setFilmId(rs.getInt("FilmId"));
                    row.setFilmTitle(rs.getString("Title"));
                    row.setSoldSeats(rs.getInt("SoldSeats"));
                    row.setTotalRevenue(rs.getBigDecimal("TotalRevenue"));
                    rows.add(row);
                }
            }
            return rows;
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể tải báo cáo top phim theo cụm rạp.", ex);
        }
    }

    private List<RevenueRow> scopedRevenueRows(String sql, String labelColumn, int cinemaId) {
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, cinemaId);
            List<RevenueRow> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RevenueRow row = new RevenueRow();
                    row.setLabel(rs.getString(labelColumn));
                    row.setOrderCount(rs.getInt("OrderCount"));
                    row.setTotalRevenue(rs.getBigDecimal("TotalRevenue"));
                    rows.add(row);
                }
            }
            return rows;
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể tải doanh thu theo cụm rạp.", ex);
        }
    }

    public List<SystemSetting> listSettings() {
        ensureDefaultSettings();
        String sql = "SELECT * FROM SystemSettings ORDER BY SettingKey";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            List<SystemSetting> settings = new ArrayList<>();
            while (rs.next()) {
                SystemSetting setting = new SystemSetting();
                setting.setSettingKey(rs.getString("SettingKey"));
                setting.setSettingValue(rs.getString("SettingValue"));
                setting.setUpdatedAt(toLocalDateTime(rs.getTimestamp("UpdatedAt")));
                settings.add(setting);
            }
            return settings;
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the tai cau hinh he thong.", ex);
        }
    }

    public String getSettingValue(String key) {
        String sql = "SELECT SettingValue FROM SystemSettings WHERE SettingKey = ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("SettingValue");
                }
            }
            return null;
        } catch (SQLException ex) {
            // N-12: khong nuot nguyen nhan. Van tra null vi moi noi goi deu co gia tri mac dinh
            // fail-safe (vi du payment.mode roi ve "simulated" — khong co tien that nao chay),
            // nhung loi doc phai de lai dau vet, neu khong no giong het "chua cau hinh".
            LOGGER.log(Level.SEVERE, "Khong doc duoc cau hinh he thong key=" + key
                    + "; se dung gia tri mac dinh cua noi goi", ex);
            return null;
        }
    }

    public void saveSetting(String key, String value, User actor) {
        assertGlobalAdmin(actor);
        String sql = """
                MERGE SystemSettings AS target
                USING (SELECT ? AS SettingKey, ? AS SettingValue) AS source
                ON target.SettingKey = source.SettingKey
                WHEN MATCHED THEN
                    UPDATE SET SettingValue = source.SettingValue, UpdatedAt = GETDATE()
                WHEN NOT MATCHED THEN
                    INSERT (SettingKey, SettingValue, UpdatedAt)
                    VALUES (source.SettingKey, source.SettingValue, GETDATE());
                """;
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
            try {
                logAction(actor.getId(), "SAVE_SETTING", "SystemSetting", key, value);
            } catch (Exception logEx) {
                LOGGER.log(Level.WARNING, "Không thể ghi audit log cho saveSetting key=" + key, logEx);
            }
            com.mycompany.website.ban.ve.xem.phim.filter.HeaderDataFilter.invalidate();
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the luu cau hinh he thong.", ex);
        }
    }

    public List<AuditLogEntry> listAuditLogs() {
        return listAuditLogs(1, 50).items();
    }

    public PageResult<AuditLogEntry> listAuditLogs(int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(10, size));
        String sql = """
                SELECT a.*, u.Email AS ActorEmail
                FROM AuditLogs a
                LEFT JOIN Users u ON u.Id = a.ActorUserId
                ORDER BY a.CreatedAt DESC, a.Id DESC
                OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """;
        try (Connection connection = DBConnection.getConnection()) {
            long total;
            try (PreparedStatement count = connection.prepareStatement("SELECT COUNT_BIG(*) FROM AuditLogs");
                    ResultSet rs = count.executeQuery()) {
                rs.next();
                total = rs.getLong(1);
            }
            List<AuditLogEntry> logs = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, (safePage - 1) * safeSize);
                ps.setInt(2, safeSize);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        AuditLogEntry log = new AuditLogEntry();
                        log.setId(rs.getInt("Id"));
                        int actorId = rs.getInt("ActorUserId");
                        log.setActorUserId(rs.wasNull() ? null : actorId);
                        log.setActorEmail(rs.getString("ActorEmail"));
                        log.setAction(rs.getString("Action"));
                        log.setTargetType(rs.getString("TargetType"));
                        log.setTargetId(rs.getString("TargetId"));
                        log.setDetailJson(rs.getString("DetailJson"));
                        log.setBeforeJson(rs.getString("BeforeJson"));
                        log.setAfterJson(rs.getString("AfterJson"));
                        log.setIpAddress(rs.getString("IpAddress"));
                        log.setUserAgent(rs.getString("UserAgent"));
                        log.setCreatedAt(toLocalDateTime(rs.getTimestamp("CreatedAt")));
                        logs.add(log);
                    }
                }
            }
            return new PageResult<>(logs, safePage, safeSize, total);
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the tai audit log.", ex);
        }
    }

    public int releaseStaleHeldSeats(User actor) {
        String sql = """
                UPDATE ShowtimeSeats
                SET Status = 'available', HeldByUserId = NULL, HeldAt = NULL, HeldUntil = NULL,
                    ClaimedByOrderId = NULL
                WHERE Status = 'held'
                  AND (
                    (HeldUntil IS NOT NULL AND HeldUntil < GETDATE())
                    OR (HeldAt IS NOT NULL AND HeldAt < DATEADD(MINUTE, -30, GETDATE()))
                  )
                """;
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            int affected = ps.executeUpdate();
            logAction(actor.getId(), "RELEASE_STALE_HELD_SEATS", "ShowtimeSeat", String.valueOf(affected), null);
            return affected;
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the giai phong cac ghe dang bi giu.", ex);
        }
    }

    public int clearAuditLogs(int olderThanDays, User actor) {
        if (olderThanDays < 1) {
            throw new BookingException(400, "So ngay giu log phai lon hon 0.");
        }
        String sql = "DELETE FROM AuditLogs WHERE CreatedAt < DATEADD(DAY, ?, GETDATE())";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, -olderThanDays);
            int affected = ps.executeUpdate();
            logAction(actor.getId(), "CLEAR_AUDIT_LOGS", "AuditLog", String.valueOf(affected),
                    "olderThanDays=" + olderThanDays);
            return affected;
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the don dep audit log.", ex);
        }
    }

    public String backupDatabase(User actor) {
        try {
            String configuredDirectory = getSettingValue("backup.directory");
            String databaseName = getSettingValue("backup.databaseName");
            if (configuredDirectory == null || configuredDirectory.isBlank()) {
                configuredDirectory = "C:\\tmp\\cinebook-backups";
            }
            if (databaseName == null || !databaseName.matches("[A-Za-z0-9_]+")) {
                throw new BookingException(500, "Tên database backup trong cấu hình không hợp lệ.");
            }
            Path backupDir = Paths.get(configuredDirectory).toAbsolutePath().normalize();
            Files.createDirectories(backupDir);
            if (!Files.isDirectory(backupDir) || !Files.isWritable(backupDir)) {
                throw new BookingException(500, "Thư mục backup không ghi được: " + backupDir);
            }
            String fileName = databaseName + "_" + LocalDateTime.now().format(BACKUP_STAMP) + ".bak";
            Path backupFile = backupDir.resolve(fileName);
            String sql = "BACKUP DATABASE [" + databaseName + "] TO DISK = N'"
                    + backupFile.toString().replace("'", "''") + "' WITH INIT, COPY_ONLY";
            try (Connection connection = DBConnection.getConnection();
                    PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.execute();
            }
            logAction(actor.getId(), "BACKUP_DATABASE", "System", backupFile.toString(), null);
            return backupFile.toString();
        } catch (Exception ex) {
            throw new BookingException(500, "Khong the backup database. Kiem tra quyen ghi cua SQL Server.");
        }
    }

    /**
     * Ghi audit cho mot thao tac, tu lay ngu canh HTTP tu {@link RequestContext} (BUG-09, INV-9).
     *
     * <p>Truoc day ban nay truyen thang {@code null} cho {@code ipAddress}/{@code userAgent}, nen
     * do tren 71 dong audit that: {@code IpAddress} 0, {@code UserAgent} 0 — ghi duoc ai/gi/khi nao
     * nhung khong truy duoc ve IP hay thiet bi. Vi gan nhu moi cho trong lop nay goi ban 5 tham so,
     * sua o day la du de dong lo cho toan bo cac thao tac hien co.</p>
     *
     * <p>Thao tac khong den tu HTTP (sweeper, job nen, test) se co {@code null} — dung y.</p>
     */
    public void logAction(Integer actorUserId, String action, String targetType, String targetId, String detailJson) {
        logAction(actorUserId, action, targetType, targetId, null, detailJson,
                RequestContext.ipAddress(), RequestContext.userAgent());
    }

    /**
     * Ghi audit cho mot giao dich <b>da commit</b> — hong thi log, khong nem (A.3, BUG-09).
     *
     * <p>{@code logAction} mo connection rieng va bien moi loi INSERT thanh
     * {@code BookingException(500)}. Goi thang no sau {@code connection.commit()} nghia la mot
     * dong audit hong se lam ca thao tac <i>bao that bai</i> du no da thanh cong that:
     * {@code rollback()} luc do la no-op, tien da chuyen, ve da phat — chi co nguoi dung nhan 500
     * va nhung buoc phia sau (vi du {@code issueRefundAdjustment}) bi bo qua im lang.</p>
     *
     * <p>Theo dung mau {@code BookingService.auditAfterCommit} da co: {@code SEVERE} kem nguyen
     * nhan la hanh vi thay the duy nhat con lai — mat mot dong audit van hon lam hong mot giao
     * dich khong the quay lai. Cac duong ghi audit <b>trong</b> transaction thi khong dung ham
     * nay: o do nem la dung, vi con rollback duoc.</p>
     */
    private void auditAfterCommit(Integer actorUserId, String action, String targetType, String targetId,
            String beforeJson, String afterJson) {
        try {
            logAction(actorUserId, action, targetType, targetId, beforeJson, afterJson,
                    RequestContext.ipAddress(), RequestContext.userAgent());
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE, ex,
                    () -> "Khong ghi duoc audit " + action + " cho " + targetType + " " + targetId
                            + " — giao dich DA commit va van co hieu luc.");
        }
    }

    /** Ban rut gon cho cac thao tac chua co {@code BeforeJson} (xem B.3). */
    private void auditAfterCommit(Integer actorUserId, String action, String targetType, String targetId,
            String detailJson) {
        auditAfterCommit(actorUserId, action, targetType, targetId, null, detailJson);
    }

    public void logAction(Integer actorUserId, String action, String targetType, String targetId,
            String beforeJson, String afterJson, String ipAddress, String userAgent) {
        String sql = """
                INSERT INTO AuditLogs
                    (ActorUserId, Action, TargetType, TargetId, DetailJson,
                     BeforeJson, AfterJson, IpAddress, UserAgent)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            if (actorUserId == null) {
                ps.setNull(1, java.sql.Types.INTEGER);
            } else {
                ps.setInt(1, actorUserId);
            }
            ps.setString(2, action);
            ps.setString(3, targetType);
            ps.setString(4, targetId);
            ps.setString(5, afterJson);
            ps.setString(6, beforeJson);
            ps.setString(7, afterJson);
            ps.setString(8, ipAddress);
            ps.setString(9, userAgent);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the ghi audit log.", ex);
        }
    }

    private void createPrivilegedOrMemberUser(User user, String role, User actor, String action) {
        // D10 (P10) — tai khoan do quan tri vien tao cung phai qua chinh sach mat khau.
        // Bo qua o day
        // thi duong de nhat de tao mot tai khoan quyen cao voi mat khau "123456" van
        // con nguyen,
        // ma day lai la loai tai khoan dang gia nhat trong he thong.
        PasswordPolicy.Result policy = PasswordPolicy.validate(
                user.getPasswordHash(), SecuritySettings.passwordMinLength(), user.getEmail());
        if (!policy.isValid()) {
            throw new BookingException(400, policy.getMessage());
        }

        String sql = """
                INSERT INTO Users (Username, FullName, Email, PasswordHash, Phone, Address, Avatar, Role, CinemaId)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getFullName());
            ps.setString(3, user.getEmail());
            ps.setString(4, PasswordUtil.hash(user.getPasswordHash()));
            ps.setString(5, user.getPhone());
            ps.setString(6, user.getAddress());
            ps.setString(7, user.getAvatar());
            ps.setString(8, role);
            if (user.getCinemaId() == null || user.getCinemaId() <= 0) {
                ps.setNull(9, java.sql.Types.INTEGER);
            } else {
                ps.setInt(9, user.getCinemaId());
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    user.setId(keys.getInt(1));
                }
            }
            logAction(actor.getId(), action, "User", String.valueOf(user.getId()), user.getEmail());
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the tao tai khoan.", ex);
        }
    }

    private void bindFilm(PreparedStatement ps, Film film, boolean insert) throws SQLException {
        ps.setString(1, film.getTitle());
        ps.setString(2, film.getOtherTitles());
        ps.setString(3, film.getActors());
        ps.setString(4, film.getDirectors());
        if (film.getRating() == null) {
            ps.setNull(5, java.sql.Types.FLOAT);
        } else {
            ps.setDouble(5, film.getRating());
        }
        if (film.getReleaseDate() == null) {
            ps.setNull(6, java.sql.Types.DATE);
        } else {
            ps.setDate(6, Date.valueOf(film.getReleaseDate()));
        }
        if (film.getDurationMinutes() == null) {
            ps.setNull(7, java.sql.Types.INTEGER);
        } else {
            ps.setInt(7, film.getDurationMinutes());
        }
        ps.setString(8, film.getAgeRating());
        ps.setString(9, film.getTrailerUrl());
        ps.setString(10, film.getThumbnail());
        ps.setString(11, film.getLanguage());
        ps.setString(12, film.getSubtitles());
        ps.setString(13, film.getDescription());
        ps.setString(14, film.getCountry());
        ps.setString(15, film.getFormat());
        // PHAI la getRawStatus(): getStatus() nay la trang thai SUY RA tu vong doi
        // ('expired', 'coming'...). Ghi gia tri suy ra nguoc lai cot Status se lam
        // trang thai
        // bien tap cua admin bi de len boi ngay thang — chi luu dung cai admin chon.
        String rawStatus = film.getRawStatus();
        ps.setString(16, rawStatus == null || rawStatus.isBlank() ? "showing" : rawStatus);
        ps.setString(17, film.getBanner());
        if (film.getEndDate() == null) {
            ps.setNull(18, java.sql.Types.DATE);
        } else {
            ps.setDate(18, Date.valueOf(film.getEndDate()));
        }
    }

    private void bindShowtime(PreparedStatement ps, Showtime showtime, boolean insert) throws SQLException {
        ps.setInt(1, showtime.getFilmId());
        ps.setInt(2, showtime.getCinemaId());
        ps.setInt(3, showtime.getRoomId());
        if (showtime.getStartTime() == null) {
            ps.setNull(4, java.sql.Types.TIMESTAMP);
        } else {
            ps.setTimestamp(4, Timestamp.valueOf(showtime.getStartTime()));
        }
        if (showtime.getEndTime() == null) {
            ps.setNull(5, java.sql.Types.TIMESTAMP);
        } else {
            ps.setTimestamp(5, Timestamp.valueOf(showtime.getEndTime()));
        }
        if (showtime.getBasePrice() == null) {
            ps.setNull(6, java.sql.Types.DECIMAL);
        } else {
            ps.setBigDecimal(6, showtime.getBasePrice());
        }
        ps.setString(7, showtime.getFormat());
        ps.setString(8, showtime.getVersion());
        ps.setString(9, showtime.getLanguage());
    }

    private void checkShowtimeOverlap(Connection connection, Showtime showtime) throws SQLException {
        int bufferMinutes = settingInt(connection, "showtime.cleanupBufferMinutes", 15);
        String sql = """
                SELECT TOP 1 s.Id, f.Title AS FilmTitle, s.StartTime, s.EndTime
                FROM Showtimes s
                JOIN Films f ON f.Id = s.FilmId
                WHERE s.RoomId = ?
                  AND s.Id != ?
                  AND s.StartTime < DATEADD(MINUTE, ?, ?)
                  AND s.EndTime > DATEADD(MINUTE, -?, ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, showtime.getRoomId());
            ps.setInt(2, showtime.getId());
            ps.setInt(3, bufferMinutes);
            ps.setTimestamp(4, java.sql.Timestamp.valueOf(showtime.getEndTime()));
            ps.setInt(5, bufferMinutes);
            ps.setTimestamp(6, java.sql.Timestamp.valueOf(showtime.getStartTime()));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String filmTitle = rs.getString("FilmTitle");
                    java.sql.Timestamp start = rs.getTimestamp("StartTime");
                    java.sql.Timestamp end = rs.getTimestamp("EndTime");
                    java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter
                            .ofPattern("HH:mm dd/MM/yyyy");
                    String timeRange = (start != null ? start.toLocalDateTime().format(fmt) : "") + " - "
                            + (end != null ? end.toLocalDateTime().format(fmt) : "");
                    throw new BookingException(400, "Phòng chiếu xung đột với suất phim '" + filmTitle
                            + "' (" + timeRange + "), bao gồm " + bufferMinutes
                            + " phút dọn phòng. Vui lòng chọn giờ khác.");
                }
            }
        }
    }

    private void executeDelete(String sql, int id, User actor, String action, String targetType, String targetId,
            String errorMessage) {
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
            logAction(actor.getId(), action, targetType, targetId, null);
        } catch (SQLException ex) {
            throw new BookingException(500, errorMessage, ex);
        }
    }

    private record SeatDbSnapshot(int id, String rowLabel, int seatNumber, String seatType,
            String seatKey, BigDecimal surcharge) {
    }

    private void reconcileRoomSeats(Connection connection, int roomId, List<Seat> requested)
            throws SQLException {
        Map<String, SeatDbSnapshot> current = new LinkedHashMap<>();
        String currentSql = "SELECT Id, RowLabel, SeatNumber, SeatType, SeatKey, ISNULL(PriceSurcharge,0) AS PriceSurcharge "
                + "FROM Seats WITH (UPDLOCK,HOLDLOCK) WHERE RoomId=? AND IsActive=1";
        try (PreparedStatement ps = connection.prepareStatement(currentSql)) {
            ps.setInt(1, roomId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SeatDbSnapshot snapshot = new SeatDbSnapshot(rs.getInt("Id"), rs.getString("RowLabel"),
                            rs.getInt("SeatNumber"), rs.getString("SeatType"), rs.getString("SeatKey"),
                            rs.getBigDecimal("PriceSurcharge"));
                    current.put(snapshot.seatKey().toUpperCase(), snapshot);
                }
            }
        }
        Map<String, Seat> wanted = new LinkedHashMap<>();
        String maxExistingRow = current.values().stream().map(SeatDbSnapshot::rowLabel)
                .filter(java.util.Objects::nonNull).map(String::toUpperCase).max(String::compareTo).orElse("");
        for (Seat seat : requested) {
            String key = seat.getSeatKey() == null ? "" : seat.getSeatKey().trim().toUpperCase();
            if (key.isBlank() || wanted.put(key, seat) != null) {
                throw new BookingException(400, "Mã ghế bị trùng hoặc để trống: " + key);
            }
            seat.setSeatKey(key);
            seat.setRowLabel(seat.getRowLabel() == null ? "" : seat.getRowLabel().trim().toUpperCase());
            if (seat.getRowLabel().isBlank() || seat.getSeatNumber() <= 0) {
                throw new BookingException(400, "Hàng ghế và số ghế phải hợp lệ.");
            }
        }
        for (Seat seat : wanted.values()) {
            if ("couple".equalsIgnoreCase(seat.getSeatType())) {
                int partnerNumber = seat.getSeatNumber() % 2 == 1
                        ? seat.getSeatNumber() + 1 : seat.getSeatNumber() - 1;
                String partnerKey = seat.getRowLabel() + partnerNumber;
                Seat partner = wanted.get(partnerKey.toUpperCase());
                if (partner == null || !"couple".equalsIgnoreCase(partner.getSeatType())) {
                    throw new BookingException(400, "Ghế đôi " + seat.getSeatKey()
                            + " phải có đủ hai ghế lẻ-chẵn trong cùng hàng.");
                }
            }
        }
        int layoutVersion = nextLayoutVersion(connection, roomId);
        for (SeatDbSnapshot old : current.values()) {
            if (wanted.containsKey(old.seatKey().toUpperCase())) {
                continue;
            }
            // Ghe co lich su khong duoc XOA CUNG (khoa ngoai ShowtimeSeats.SeatId la NO_ACTION,
            // va xoa di la mat luon doi chieu cua nhung don da ban) — chi ngung dung.
            if (seatHasHistory(connection, old.id())) {
                retireSeat(connection, old.id());
            } else {
                try (PreparedStatement ps = connection.prepareStatement("DELETE FROM Seats WHERE Id=?")) {
                    ps.setInt(1, old.id());
                    ps.executeUpdate();
                } catch (SQLException ex) {
                    // Fail-safe: van con tham chieu ngoai du bo loc noi tren -> giu ghe lai bang
                    // cach ngung dung. Ghi lai nguyen nhan, khong nuot am tham nhu ban cu.
                    LOGGER.log(Level.WARNING, ex,
                            () -> "Khong the xoa cung ghe #" + old.id() + ", chuyen sang IsActive=0");
                    retireSeat(connection, old.id());
                }
            }
        }
        for (Seat seat : wanted.values()) {
            SeatDbSnapshot old = current.get(seat.getSeatKey().toUpperCase());
            String type = seat.getSeatType() == null || seat.getSeatType().isBlank()
                    ? "standard" : seat.getSeatType().trim().toLowerCase();
            BigDecimal surcharge = seat.getPriceSurcharge() == null ? BigDecimal.ZERO : seat.getPriceSurcharge();
            if (old != null) {
                boolean changed = !old.rowLabel().equalsIgnoreCase(seat.getRowLabel())
                        || old.seatNumber() != seat.getSeatNumber()
                        || !old.seatType().equalsIgnoreCase(type)
                        || old.surcharge().compareTo(surcharge) != 0;
                if (changed && seatIsLockedByLiveShowtime(connection, old.id())) {
                    throw new BookingException(409, "Ghế " + seat.getSeatKey()
                            + " đang được giữ/bán ở một suất chiếu chưa kết thúc nên không thể sửa.");
                }
                if (changed) {
                    try (PreparedStatement ps = connection.prepareStatement(
                            "UPDATE Seats SET RowLabel=?, SeatNumber=?, SeatType=?, PriceSurcharge=? WHERE Id=?")) {
                        ps.setString(1, seat.getRowLabel());
                        ps.setInt(2, seat.getSeatNumber());
                        ps.setString(3, type);
                        ps.setBigDecimal(4, surcharge);
                        ps.setInt(5, old.id());
                        ps.executeUpdate();
                    }
                }
            } else {
                if (!maxExistingRow.isBlank() && seat.getRowLabel().compareTo(maxExistingRow) > 0) {
                    maxExistingRow = seat.getRowLabel();
                } else if (!maxExistingRow.isBlank() && !current.values().stream()
                        .anyMatch(item -> item.rowLabel().equalsIgnoreCase(seat.getRowLabel()))) {
                    throw new BookingException(400, "Hàng mới chỉ được thêm ở cuối sơ đồ ghế.");
                }
                try (PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO Seats (RoomId, RowLabel, SeatNumber, SeatType, SeatKey, PriceSurcharge,
                                           IsActive, LayoutVersion)
                        VALUES (?, ?, ?, ?, ?, ?, 1, ?)
                        """)) {
                    ps.setInt(1, roomId);
                    ps.setString(2, seat.getRowLabel());
                    ps.setInt(3, seat.getSeatNumber());
                    ps.setString(4, type);
                    ps.setString(5, seat.getSeatKey());
                    ps.setBigDecimal(6, surcharge);
                    ps.setInt(7, layoutVersion);
                    ps.executeUpdate();
                }
            }
        }
    }

    /** Ngung dung mot ghe thay vi xoa cung, de moi doi chieu lich su van tro toi duoc. */
    private void retireSeat(Connection connection, int seatId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE Seats SET IsActive=0 WHERE Id=?")) {
            ps.setInt(1, seatId);
            ps.executeUpdate();
        }
    }

    /**
     * Ghe da tung duoc mot suat chieu hoac mot don hang tro toi <b>bat ky luc nao</b>.
     *
     * <p><b>Chi dung cho cau hoi "duoc phep XOA CUNG khong".</b> {@code ShowtimeSeats.SeatId} la
     * khoa ngoai {@code NO_ACTION}, nen {@code DELETE FROM Seats} tren mot ghe co lich su se vo
     * rang buoc va lam hong ca transaction. Vi vay cau nay <i>khong</i> co moc thoi gian: mot ghe
     * ban tu nam ngoai van khong duoc xoa cung, chi duoc chuyen {@code IsActive=0}.</p>
     *
     * <p><b>Khong dung no de chan SUA.</b> Do chinh la loi D-01: mot ghe ban xong tu thang truoc
     * bi coi la "dang duoc giu" vinh vien. Cau hoi "duoc phep SUA khong" thuoc ve
     * {@link #seatIsLockedByLiveShowtime(Connection, int)}.</p>
     *
     * <p><b>Vi sao chi soi {@code ShowtimeSeats}.</b> Do la khoa ngoai <i>duy nhat</i> tro toi
     * {@code Seats} trong toan bo schema, nen "khong co dong {@code ShowtimeSeats} nao" tuong duong
     * chinh xac voi "{@code DELETE} se thanh cong". {@code OrderSeats} luon di qua
     * {@code ShowtimeSeatId} nen da nam trong tap nay. Ban cu con them dieu kien
     * {@code StartTime >= GETDATE()}, khien ghe cua suat da chieu xong lot qua roi {@code DELETE}
     * vo khoa ngoai — loi bi mot khoi {@code catch} nuot mat va am tham chuyen sang
     * {@code IsActive=0}.</p>
     */
    private boolean seatHasHistory(Connection connection, int seatId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT CASE WHEN EXISTS (SELECT 1 FROM ShowtimeSeats ss WHERE ss.SeatId = ?)"
                        + " THEN 1 ELSE 0 END")) {
            ps.setInt(1, seatId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) == 1;
            }
        }
    }

    /**
     * Ghe dang bi mot suat chieu <b>chua ket thuc</b> giu/ban — nguon su that duy nhat cua
     * "ghe nay dang bi khoa".
     *
     * <p>Vi tu viet theo alias {@code s} cua bang {@code Seats} de vua nhung duoc vao
     * {@link #getSeatsByRoomId(int)} (co {@code Occupied}) vua dung lai duoc trong
     * {@link #seatIsLockedByLiveShowtime(Connection, int)}. Ba chot bao ve cua cung mot tinh nang
     * truoc day dung ba dieu kien khac nhau va mau thuan nhau (D-01).</p>
     *
     * <p><b>Ba dieu kien, moi cai bit mot lo da do duoc tren du lieu that:</b></p>
     * <ul>
     *   <li>{@code st.EndTime > GETDATE()} — het khoa khi phim chieu xong. Dung {@code EndTime}
     *       chu khong phai {@code StartTime}: suat dang chieu do van phai chan. Moc nay trung voi
     *       {@code activeShowtimeCount} trong {@link #getRoomDeleteImpactInfo(int)}.</li>
     *   <li>Loai don da huy — ghe A4/A5 phong "QA Phong Rap7" bi khoa chi vi mot don
     *       {@code cancelled} con dong {@code OrderSeats} lam lich su.</li>
     *   <li>Loai suat tombstone {@code SaleStatus='DELETED'} — suat da xoa khong con giu ghe nao.</li>
     * </ul>
     *
     * <p><b>Vi sao noi long cho nay an toan tuyet doi voi doanh thu:</b> {@code OrderSeats} luu
     * <i>ban sao</i> {@code SeatKey}/{@code SeatType}/{@code UnitPrice} va khong co khoa ngoai toi
     * {@code Seats}. Sua {@code Seats.SeatType} hay {@code PriceSurcharge} khong the lam lech mot
     * don cu nao, va bao cao doanh thu chi doc bang {@code Orders}.</p>
     */
    private static final String LIVE_SEAT_REFERENCE_PREDICATE = """
            EXISTS (SELECT 1 FROM ShowtimeSeats ss
                    JOIN Showtimes st ON st.Id = ss.ShowtimeId
                    WHERE ss.SeatId = s.Id
                      AND st.EndTime > GETDATE()
                      AND ISNULL(st.SaleStatus, 'ON_SALE') <> 'DELETED'
                      AND (ss.Status IN ('held', 'booked')
                           OR ss.ClaimedByOrderId IS NOT NULL
                           OR EXISTS (SELECT 1 FROM OrderSeats os
                                      JOIN Orders o ON o.Id = os.OrderId
                                      WHERE os.ShowtimeSeatId = ss.Id
                                        AND ISNULL(o.OrderStatus, '') <> 'cancelled')))
            """;

    /** Xem {@link #LIVE_SEAT_REFERENCE_PREDICATE} — chot duy nhat cho quyen SUA mot ghe. */
    private boolean seatIsLockedByLiveShowtime(Connection connection, int seatId) throws SQLException {
        String sql = "SELECT CASE WHEN " + LIVE_SEAT_REFERENCE_PREDICATE
                + " THEN 1 ELSE 0 END FROM Seats s WHERE s.Id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, seatId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) == 1;
            }
        }
    }

    /**
     * Chan tao lai toan bo so do khi con ghe bi mot suat <b>chua ket thuc</b> giu/ban.
     *
     * <p>Dung chung {@link #LIVE_SEAT_REFERENCE_PREDICATE} voi {@code reconcileRoomSeats} va
     * {@link #getSeatsByRoomId(int)}. Truoc day ba chot bao ve cua cung mot tinh nang dung ba
     * dieu kien khac nhau — ham nay chan gan dung ({@code StartTime >= GETDATE()}) trong khi
     * {@code seatIsReferenced} chan sai hoan toan (khong co moc thoi gian nao).</p>
     *
     * <p>Bi danh so {@code ss2}/{@code st2} de khong dung ten voi {@code ss}/{@code st} ben trong
     * vi tu dung chung — trung ten thi cot bao cao se lay tu mot dong bat ky thay vi dong dang
     * thuc su chan.</p>
     */
    private void ensureRoomSeatLayoutEditable(Connection connection, int roomId) throws SQLException {
        // Cung dieu kien voi LIVE_SEAT_REFERENCE_PREDICATE, neu khong thong bao se chi ra mot suat
        // tuong lai bat ky thay vi dung suat dang giu ghe.
        String liveShowtime = """
                SELECT TOP 1 %s FROM ShowtimeSeats ss2
                JOIN Showtimes st2 ON st2.Id = ss2.ShowtimeId
                WHERE ss2.SeatId = s.Id AND st2.EndTime > GETDATE()
                  AND ISNULL(st2.SaleStatus, 'ON_SALE') <> 'DELETED'
                  AND (ss2.Status IN ('held', 'booked')
                       OR ss2.ClaimedByOrderId IS NOT NULL
                       OR EXISTS (SELECT 1 FROM OrderSeats os2
                                  JOIN Orders o2 ON o2.Id = os2.OrderId
                                  WHERE os2.ShowtimeSeatId = ss2.Id
                                    AND ISNULL(o2.OrderStatus, '') <> 'cancelled'))
                ORDER BY st2.StartTime
                """;
        String sql = """
                SELECT TOP 1 s.SeatKey,
                       (%s) AS ShowtimeId,
                       (%s) AS StartTime
                FROM Seats s
                WHERE s.RoomId = ? AND s.IsActive = 1 AND %s
                ORDER BY s.RowLabel, s.SeatNumber
                """.formatted(liveShowtime.formatted("st2.Id"), liveShowtime.formatted("st2.StartTime"),
                        LIVE_SEAT_REFERENCE_PREDICATE);
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, roomId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    throw new BookingException(400, "Không thể đổi sơ đồ: ghế " + rs.getString("SeatKey")
                            + " đang được giữ/bán ở suất #" + rs.getInt("ShowtimeId")
                            + " lúc " + rs.getTimestamp("StartTime") + ".");
                }
            }
        }
    }

    private void ensureShowtimeEditable(Connection connection, int showtimeId) throws SQLException {
        String occupied = firstOccupiedSeat(connection, showtimeId);
        if (occupied != null) {
            throw new BookingException(400, "Không thể sửa/xóa suất #" + showtimeId
                    + " vì ghế " + occupied + ".");
        }
    }

    /**
     * Doi phong khong nam trong pham vi cua {@code confirmImpact} (B.5).
     *
     * <p><b>Van de cu.</b> Khi doi {@code RoomId}, {@code persistShowtime} bo qua
     * {@code ensureShowtimeEditable} vi quan ly da tick xac nhan — nhung
     * {@code recreateShowtimeSeats} ngay sau do lai goi chinh ham ay, nen thao tac <b>luon</b>
     * hong voi mot thong bao khong he nhac toi override. O tick tren man hinh hua mot kha nang
     * khong bao gio chay duoc.</p>
     *
     * <p><b>Quyet dinh: bo {@code RoomId} khoi danh sach truong duoc override.</b> Cho override
     * that se phai <i>di chuyen</i> ghe da ban sang phong moi thay vi DELETE + INSERT lai (neu
     * khong la mat {@code OrderSeats} — dung loi N-02). Nhung hai phong co so do ghe khac nhau,
     * nen "ghe E1 phong 01 thanh ghe nao o phong 02" la mot luat nghiep vu MOI phai dat ra
     * (ghep theo hang? theo loai? khach mua ghe doi ma phong moi khong co ghe doi thi sao?).
     * Dat ra luat do vuot pham vi dot sua nay, nen o day bao loi tuong minh va noi that voi
     * quan ly rang thao tac nay khong lam duoc.</p>
     */
    private void ensureRoomChangeAllowed(Connection connection, int showtimeId, int targetRoomId)
            throws SQLException {
        String roomSql = """
                SELECT oldRoom.RoomType AS OldRoomType, newRoom.RoomType AS NewRoomType,
                       oldCinema.CinemaType AS OldCinemaType, newCinema.CinemaType AS NewCinemaType
                FROM Showtimes st
                JOIN Rooms oldRoom ON oldRoom.Id=st.RoomId
                JOIN Cinemas oldCinema ON oldCinema.Id=oldRoom.CinemaId
                JOIN Rooms newRoom ON newRoom.Id=?
                JOIN Cinemas newCinema ON newCinema.Id=newRoom.CinemaId
                WHERE st.Id=?
                """;
        try (PreparedStatement ps = connection.prepareStatement(roomSql)) {
            ps.setInt(1, targetRoomId);
            ps.setInt(2, showtimeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new BookingException(404, "Không tìm thấy suất chiếu hoặc phòng đích.");
                }
                if ("VIP".equalsIgnoreCase(rs.getString("OldRoomType"))
                        || "VIP".equalsIgnoreCase(rs.getString("NewRoomType"))
                        || "VIP".equalsIgnoreCase(rs.getString("OldCinemaType"))
                        || "VIP".equalsIgnoreCase(rs.getString("NewCinemaType"))) {
                    throw new BookingException(409, "Không được đổi phòng/cụm rạp VIP cho suất chiếu đã tồn tại.");
                }
            }
        }
        String missingSeatSql = """
                SELECT TOP 1 oldSeat.SeatKey
                FROM ShowtimeSeats ss
                JOIN Seats oldSeat ON oldSeat.Id=ss.SeatId
                WHERE ss.ShowtimeId=?
                  AND (ss.Status IN ('held','booked') OR ss.ClaimedByOrderId IS NOT NULL
                       OR EXISTS (SELECT 1 FROM OrderSeats os WHERE os.ShowtimeSeatId=ss.Id))
                  AND NOT EXISTS (SELECT 1 FROM Seats newSeat
                                  WHERE newSeat.RoomId=? AND newSeat.IsActive=1
                                    AND newSeat.SeatKey=oldSeat.SeatKey)
                """;
        try (PreparedStatement ps = connection.prepareStatement(missingSeatSql)) {
            ps.setInt(1, showtimeId);
            ps.setInt(2, targetRoomId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    throw new BookingException(409, "Phòng đích không có ghế " + rs.getString(1)
                            + "; không thể đổi phòng mà vẫn giữ nguyên chỗ của khách.");
                }
            }
        }
    }

    /** {@code "A1 đang ở trạng thái booked"}, hoac {@code null} neu suat chua ai dung toi. */
    private void remapShowtimeSeats(Connection connection, int showtimeId, int targetRoomId) throws SQLException {
        String occupiedSql = "SELECT COUNT(*) FROM ShowtimeSeats ss WHERE ss.ShowtimeId=? "
                + "AND (ss.Status IN ('held','booked') OR ss.ClaimedByOrderId IS NOT NULL "
                + "OR EXISTS (SELECT 1 FROM OrderSeats os WHERE os.ShowtimeSeatId=ss.Id))";
        boolean occupied;
        try (PreparedStatement ps = connection.prepareStatement(occupiedSql)) {
            ps.setInt(1, showtimeId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                occupied = rs.getInt(1) > 0;
            }
        }
        if (!occupied) {
            recreateShowtimeSeats(connection, showtimeId, targetRoomId);
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement("""
                UPDATE ss SET SeatId=targetSeat.Id
                FROM ShowtimeSeats ss
                JOIN Seats oldSeat ON oldSeat.Id=ss.SeatId
                JOIN Seats targetSeat ON targetSeat.RoomId=? AND targetSeat.IsActive=1
                    AND targetSeat.SeatKey=oldSeat.SeatKey
                WHERE ss.ShowtimeId=?
                  AND (ss.Status IN ('held','booked') OR ss.ClaimedByOrderId IS NOT NULL
                       OR EXISTS (SELECT 1 FROM OrderSeats os WHERE os.ShowtimeSeatId=ss.Id))
                """)) {
            ps.setInt(1, targetRoomId);
            ps.setInt(2, showtimeId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement("""
                DELETE FROM ShowtimeSeats
                WHERE ShowtimeId=? AND Status NOT IN ('held','booked')
                  AND ClaimedByOrderId IS NULL
                  AND NOT EXISTS (SELECT 1 FROM OrderSeats os WHERE os.ShowtimeSeatId=ShowtimeSeats.Id)
                """)) {
            ps.setInt(1, showtimeId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO ShowtimeSeats (ShowtimeId, SeatId, Status, ExtraFee)
                SELECT ?, s.Id, CASE WHEN s.SeatType='maintenance' THEN 'maintenance' ELSE 'available' END,
                       ISNULL(s.PriceSurcharge, CASE WHEN s.SeatType='vip' THEN 20000 ELSE 0 END)
                FROM Seats s
                WHERE s.RoomId=? AND s.IsActive=1
                  AND NOT EXISTS (SELECT 1 FROM ShowtimeSeats existing
                                  WHERE existing.ShowtimeId=? AND existing.SeatId=s.Id)
                """)) {
            ps.setInt(1, showtimeId);
            ps.setInt(2, targetRoomId);
            ps.setInt(3, showtimeId);
            ps.executeUpdate();
        }
    }

    private String firstOccupiedSeat(Connection connection, int showtimeId) throws SQLException {
        String sql = """
                SELECT TOP 1 se.SeatKey, ss.Status
                FROM ShowtimeSeats ss
                JOIN Seats se ON se.Id = ss.SeatId
                WHERE ss.ShowtimeId = ?
                  AND (ss.Status IN ('held', 'booked')
                       OR ss.ClaimedByOrderId IS NOT NULL
                       OR EXISTS (SELECT 1 FROM OrderSeats os WHERE os.ShowtimeSeatId = ss.Id))
                ORDER BY se.RowLabel, se.SeatNumber
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, showtimeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("SeatKey") + " đang ở trạng thái " + rs.getString("Status");
                }
                return null;
            }
        }
    }

    /** Trang thai suat chieu truoc khi sua, dung de so sanh va de ghi {@code BeforeJson} (BUG-06). */
    private record ShowtimeSnapshot(int id, int filmId, int roomId,
            LocalDateTime startTime, LocalDateTime endTime) {

        /**
         * Thay doi co lam nguoi da mua ve phai biet khong.
         *
         * <p>Bon truong nay quyet dinh "khach den dau, luc may gio, xem phim gi". Doi gia hay doi
         * dinh dang khong lam khach di nham cho, nen khong nam trong danh sach.</p>
         */
        boolean differsMateriallyFrom(Showtime updated) {
            return filmId != updated.getFilmId()
                    || roomId != updated.getRoomId()
                    || !java.util.Objects.equals(startTime, updated.getStartTime())
                    || !java.util.Objects.equals(endTime, updated.getEndTime());
        }

        String toJson() {
            return javax.json.Json.createObjectBuilder()
                    .add("filmId", filmId)
                    .add("roomId", roomId)
                    .add("startTime", String.valueOf(startTime))
                    .add("endTime", String.valueOf(endTime))
                    .build().toString();
        }
    }

    private ShowtimeSnapshot loadShowtimeSnapshot(Connection connection, int showtimeId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT FilmId, RoomId, StartTime, EndTime FROM Showtimes WHERE Id = ?")) {
            ps.setInt(1, showtimeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new ShowtimeSnapshot(showtimeId, rs.getInt("FilmId"), rs.getInt("RoomId"),
                        rs.getTimestamp("StartTime") == null ? null : rs.getTimestamp("StartTime").toLocalDateTime(),
                        rs.getTimestamp("EndTime") == null ? null : rs.getTimestamp("EndTime").toLocalDateTime());
            }
        }
    }

    /**
     * Bao cho moi nguoi dang giu ve cua suat chieu vua bi doi tham so (BUG-06, INV-8).
     *
     * <p>Nam trong <b>cung transaction</b> voi cau UPDATE Showtimes: khong gui duoc thong bao thi
     * ca thay doi bi rollback. Doi gio ma im lang la cach chac chan nhat de khach den rap sai gio
     * va mat buoi xem — bi chan con hon.</p>
     *
     * <p>Audit ghi rieng {@code UPDATE_SHOWTIME_IMPACT} kem {@code BeforeJson}/{@code AfterJson}
     * de sau nay truy duoc moc cu va moc moi.</p>
     */
    private void notifyTicketHoldersOfShowtimeChange(Connection connection, ShowtimeSnapshot before,
            Showtime after, User actor, List<PendingAudit> pendingAudits) throws SQLException {
        Set<Integer> holders = new LinkedHashSet<>();
        String holderSql = """
                SELECT DISTINCT o.UserId
                FROM Orders o
                WHERE o.ShowtimeId = ?
                  AND LOWER(ISNULL(o.OrderStatus, '')) NOT IN ('cancelled', 'canceled', 'refunded', 'expired')
                  AND LOWER(ISNULL(o.PaymentStatus, '')) <> 'refunded'
                """;
        try (PreparedStatement ps = connection.prepareStatement(holderSql)) {
            ps.setInt(1, before.id());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    holders.add(rs.getInt(1));
                }
            }
        }

        String beforeJson = before.toJson();
        String afterJson = javax.json.Json.createObjectBuilder()
                .add("filmId", after.getFilmId())
                .add("roomId", after.getRoomId())
                .add("startTime", String.valueOf(after.getStartTime()))
                .add("endTime", String.valueOf(after.getEndTime()))
                .add("notifiedHolders", holders.size())
                .build().toString();

        if (!holders.isEmpty()) {
            UserNotification notification = new UserNotification();
            notification.setTitle("Suất chiếu của bạn đã được thay đổi");
            notification.setMessage("Suất chiếu #" + before.id() + " "
                    + describeShowtimeChanges(connection, before, after)
                    + ". Vui lòng kiểm tra lại vé của bạn; nếu không sắp xếp được, hãy liên hệ rạp để được hỗ trợ.");
            notification.setSeverity("warning");
            // B.4: TargetType phai khop voi TargetId. Ban cu dat "USER" kem TargetId = showtimeId —
            // hai truong khong khop nhau nen khong tra cuu lai duoc thong bao theo suat chieu, dung
            // lop loi thong bao mo coi ma RM-01/fix18/fix23 da sua hai lan. Nguoi nhan von da duoc
            // chot bang recipientDAO.deliver, nen o day targetType/targetId chi la sieu du lieu.
            notification.setTargetType("Showtime");
            notification.setTargetId(String.valueOf(before.id()));
            notification.setCinemaId(after.getCinemaId());
            notification.setStatus("active");
            notification.setCreatedByUserId(actor == null ? 0 : actor.getId());
            int notificationId = userNotificationDAO.create(connection, notification);
            int delivered = recipientDAO.deliver(connection, notificationId, holders);
            if (delivered < holders.size()) {
                throw new BookingException(500, "Không gửi được thông báo cho đủ người giữ vé;"
                        + " thay đổi suất chiếu đã được hoàn tác.");
            }
        }

        pendingAudits.add(new PendingAudit(actor == null ? null : actor.getId(), "UPDATE_SHOWTIME_IMPACT",
                "Showtime", String.valueOf(before.id()), beforeJson, afterJson));
    }

    /**
     * Cau mo ta dung nhung gi da doi, cho khach doc (B.4).
     *
     * <p>Ban cu luon in "da doi tu &lt;startTime cu&gt; sang &lt;startTime moi&gt;" — ke ca khi
     * thu doi la PHONG hoac PHIM. Khi do khach nhan mot cau co hai moc gio giong het nhau va
     * khong he biet minh phai doi phong hay doi phim.</p>
     *
     * <p>Tra cuu ten phong/ten phim bang chinh {@code Connection} cua transaction — khong mo
     * connection thu hai trong luc dang giu khoa.</p>
     */
    private String describeShowtimeChanges(Connection connection, ShowtimeSnapshot before, Showtime after)
            throws SQLException {
        List<String> changes = new ArrayList<>();
        if (!java.util.Objects.equals(before.startTime(), after.getStartTime())) {
            changes.add("đổi giờ chiếu từ " + before.startTime() + " sang " + after.getStartTime());
        }
        if (!java.util.Objects.equals(before.endTime(), after.getEndTime())) {
            changes.add("đổi giờ kết thúc từ " + before.endTime() + " sang " + after.getEndTime());
        }
        if (before.roomId() != after.getRoomId()) {
            changes.add("đổi phòng chiếu từ " + nameOf(connection, "Rooms", "Name", before.roomId())
                    + " sang " + nameOf(connection, "Rooms", "Name", after.getRoomId()));
        }
        if (before.filmId() != after.getFilmId()) {
            changes.add("đổi phim từ " + nameOf(connection, "Films", "Title", before.filmId())
                    + " sang " + nameOf(connection, "Films", "Title", after.getFilmId()));
        }
        return changes.isEmpty() ? "đã được cập nhật" : "đã " + String.join("; ", changes);
    }

    private String nameOf(Connection connection, String table, String column, int id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT " + column + " FROM " + table + " WHERE Id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getString(1) != null ? rs.getString(1) : "#" + id;
            }
        }
    }

    private boolean hasRoomChanged(Connection connection, int showtimeId, int roomId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT RoomId FROM Showtimes WHERE Id = ?")) {
            ps.setInt(1, showtimeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) != roomId;
                }
                return false;
            }
        }
    }

    private void recreateShowtimeSeats(Connection connection, int showtimeId, int roomId) throws SQLException {
        ensureShowtimeEditable(connection, showtimeId);
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM ShowtimeSeats WHERE ShowtimeId = ?")) {
            delete.setInt(1, showtimeId);
            delete.executeUpdate();
        }
        String sql = """
                INSERT INTO ShowtimeSeats (ShowtimeId, SeatId, Status, ExtraFee)
                SELECT ?, s.Id, CASE WHEN s.SeatType = 'maintenance' THEN 'maintenance' ELSE 'available' END, ISNULL(s.PriceSurcharge, CASE WHEN s.SeatType = 'vip' THEN 20000 ELSE 0 END)
                FROM Seats s
                WHERE s.RoomId = ? AND s.IsActive = 1
                """;
        try (PreparedStatement insert = connection.prepareStatement(sql)) {
            insert.setInt(1, showtimeId);
            insert.setInt(2, roomId);
            insert.executeUpdate();
        }
    }

    private void recreateFutureShowtimeSeats(Connection connection, int roomId) throws SQLException {
        try (PreparedStatement release = connection.prepareStatement(
                "UPDATE ShowtimeSeats SET Status='available', HeldByUserId=NULL, HeldAt=NULL, HeldUntil=NULL, ClaimedByOrderId=NULL "
                        + "WHERE Status='held' AND HeldUntil IS NOT NULL AND HeldUntil <= GETDATE() AND ShowtimeId IN "
                        + "(SELECT Id FROM Showtimes WHERE RoomId=? AND StartTime >= GETDATE())")) {
            release.setInt(1, roomId);
            release.executeUpdate();
        }
        List<Integer> showtimeIds = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT Id FROM Showtimes WHERE RoomId = ? AND StartTime >= GETDATE()")) {
            ps.setInt(1, roomId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    showtimeIds.add(rs.getInt(1));
                }
            }
        }
        for (int showtimeId : showtimeIds) {
            recreateShowtimeSeats(connection, showtimeId, roomId);
        }
    }

    private int settingInt(Connection connection, String key, int fallback) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT SettingValue FROM SystemSettings WHERE SettingKey = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    try {
                        return Math.max(0, Integer.parseInt(rs.getString(1)));
                    } catch (NumberFormatException ex) {
                        return fallback;
                    }
                }
            }
        }
        return fallback;
    }

    private void generateSeats(Connection connection, int roomId, int rowCount, int seatsPerRow, Set<String> vipRows)
            throws SQLException {
        int layoutVersion = nextLayoutVersion(connection, roomId);
        String sql = "INSERT INTO Seats (RoomId, RowLabel, SeatNumber, SeatType, SeatKey, IsActive, LayoutVersion) VALUES (?, ?, ?, ?, ?, 1, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int row = 0; row < rowCount; row++) {
                String rowLabel = String.valueOf((char) ('A' + row));
                for (int seat = 1; seat <= seatsPerRow; seat++) {
                    ps.setInt(1, roomId);
                    ps.setString(2, rowLabel);
                    ps.setInt(3, seat);
                    ps.setString(4, vipRows.contains(rowLabel) ? "vip" : "standard");
                    ps.setString(5, rowLabel + seat);
                    ps.setInt(6, layoutVersion);
                    ps.addBatch();
                }
            }
            ps.executeBatch();
        }
    }

    private int nextLayoutVersion(Connection connection, int roomId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT ISNULL(MAX(LayoutVersion), 0) + 1 FROM Seats WITH (UPDLOCK, HOLDLOCK) WHERE RoomId=?")) {
            ps.setInt(1, roomId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return Math.max(1, rs.getInt(1));
            }
        }
    }

    /** Retire the current snapshot without breaking ShowtimeSeats/OrderSeats history. */
    private int retireActiveSeats(Connection connection, int roomId) throws SQLException {
        int version = nextLayoutVersion(connection, roomId);
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE Seats SET IsActive=0, RetiredAt=SYSDATETIME() WHERE RoomId=? AND IsActive=1")) {
            ps.setInt(1, roomId);
            ps.executeUpdate();
        }
        return version;
    }

    private Set<String> parseRowLabels(String csv) {
        Set<String> rows = new LinkedHashSet<>();
        if (csv == null || csv.isBlank()) {
            return rows;
        }
        for (String token : csv.split(",")) {
            String value = token.trim().toUpperCase();
            if (!value.isBlank()) {
                rows.add(value);
            }
        }
        return rows;
    }

    private void ensureDefaultSettings() {
        String sql = """
                MERGE SystemSettings AS target
                USING (VALUES
                    ('site_name', 'CineBook'),
                    ('support_email', 'support@cinebook.local'),
                    ('seat_hold_minutes', '10')
                ) AS source (SettingKey, SettingValue)
                ON target.SettingKey = source.SettingKey
                WHEN NOT MATCHED THEN
                    INSERT (SettingKey, SettingValue, UpdatedAt)
                    VALUES (source.SettingKey, source.SettingValue, GETDATE());
                """;
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the khoi tao system settings.", ex);
        }
    }

    private List<RevenueRow> revenueRows(String sql, String labelColumn) {
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            List<RevenueRow> rows = new ArrayList<>();
            while (rs.next()) {
                RevenueRow row = new RevenueRow();
                row.setLabel(rs.getString(labelColumn));
                row.setOrderCount(rs.getInt("OrderCount"));
                row.setTotalRevenue(rs.getBigDecimal("TotalRevenue"));
                rows.add(row);
            }
            return rows;
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Khong the tai du lieu doanh thu", ex);
            throw new BookingException(500, "Khong the tai du lieu doanh thu. Vui long thu lai sau.");
        }
    }

    private void bindNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private String normalizedRoomType(String value) {
        String normalized = value == null ? "STANDARD" : value.trim().toUpperCase();
        if (!Set.of("STANDARD", "VIP").contains(normalized)) {
            throw new BookingException(400, "Loại phòng/rạp chỉ được là STANDARD hoặc VIP.");
        }
        return normalized;
    }

    private Film mapFilm(ResultSet rs) throws SQLException {
        Film film = mapFilmFields(rs);
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(
                        "SELECT STRING_AGG(c.Title, ', ') WITHIN GROUP (ORDER BY c.Title) "
                                + "FROM FilmCategories fc JOIN Categories c ON c.Id=fc.CategoryId WHERE fc.FilmId=?")) {
            ps.setInt(1, film.getId());
            try (ResultSet categories = ps.executeQuery()) {
                if (categories.next()) {
                    film.setCategories(categories.getString(1));
                }
            }
        }
        return film;
    }

    private Film mapFilm(ResultSet rs, Connection connection) throws SQLException {
        Film film = mapFilmFields(rs);
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT STRING_AGG(c.Title, ', ') WITHIN GROUP (ORDER BY c.Title) "
                        + "FROM FilmCategories fc JOIN Categories c ON c.Id=fc.CategoryId WHERE fc.FilmId=?")) {
            ps.setInt(1, film.getId());
            try (ResultSet categories = ps.executeQuery()) {
                if (categories.next()) {
                    film.setCategories(categories.getString(1));
                }
            }
        }
        return film;
    }

    private Film mapFilmFields(ResultSet rs) throws SQLException {
        Film film = new Film();
        film.setId(rs.getInt("Id"));
        film.setTitle(rs.getString("Title"));
        film.setOtherTitles(rs.getString("OtherTitles"));
        film.setActors(rs.getString("Actors"));
        film.setDirectors(rs.getString("Directors"));
        double rating = rs.getDouble("Rating");
        film.setRating(rs.wasNull() ? null : rating);
        Date releaseDate = rs.getDate("ReleaseDate");
        film.setReleaseDate(releaseDate == null ? null : releaseDate.toLocalDate());
        Date endDate = rs.getDate("EndDate");
        film.setEndDate(endDate == null ? null : endDate.toLocalDate());
        int duration = rs.getInt("DurationMinutes");
        film.setDurationMinutes(rs.wasNull() ? null : duration);
        film.setAgeRating(rs.getString("AgeRating"));
        film.setTrailerUrl(rs.getString("TrailerUrl"));
        film.setThumbnail(rs.getString("Thumbnail"));
        film.setLanguage(rs.getString("Language"));
        film.setSubtitles(rs.getString("Subtitles"));
        film.setDescription(rs.getString("Description"));
        film.setCountry(rs.getString("Country"));
        film.setFormat(rs.getString("Format"));
        film.setStatus(rs.getString("Status"));
        film.setBanner(rs.getString("Banner"));
        film.setCreatedAt(toLocalDateTime(rs.getTimestamp("CreatedAt")));
        film.setUpdatedAt(toLocalDateTime(rs.getTimestamp("UpdatedAt")));
        if (hasColumn(rs, "DeletedAt")) {
            film.setDeletedAt(toLocalDateTime(rs.getTimestamp("DeletedAt")));
            int deletedBy = rs.getInt("DeletedByUserId");
            film.setDeletedByUserId(rs.wasNull() ? null : deletedBy);
            film.setDeletionMode(rs.getString("DeletionMode"));
        }
        return film;
    }

    private Cinema mapCinema(ResultSet rs) throws SQLException {
        Cinema cinema = new Cinema();
        cinema.setId(rs.getInt("Id"));
        cinema.setCityId(rs.getInt("CityId"));
        cinema.setCityName(rs.getString("CityName"));
        cinema.setName(rs.getString("Name"));
        cinema.setAddress(rs.getString("Address"));
        cinema.setAvatar(rs.getString("Avatar"));
        cinema.setDescription(rs.getString("Description"));
        cinema.setBannerUrl(rs.getString("BannerUrl"));
        cinema.setPhone(rs.getString("Phone"));
        cinema.setStatus(rs.getString("Status"));
        if (hasColumn(rs, "CinemaType")) {
            cinema.setCinemaType(rs.getString("CinemaType"));
        }
        if (hasColumn(rs, "RoomCount")) {
            cinema.setRoomCount(rs.getInt("RoomCount"));
        }
        cinema.setCreatedAt(toLocalDateTime(rs.getTimestamp("CreatedAt")));
        cinema.setUpdatedAt(toLocalDateTime(rs.getTimestamp("UpdatedAt")));
        return cinema;
    }

    private Room mapRoom(ResultSet rs) throws SQLException {
        Room room = new Room();
        room.setId(rs.getInt("Id"));
        room.setCinemaId(rs.getInt("CinemaId"));
        room.setCinemaName(rs.getString("CinemaName"));
        room.setName(rs.getString("Name"));
        room.setSeatCount(rs.getInt("SeatCount"));
        room.setStatus(rs.getString("Status"));
        if (hasColumn(rs, "RoomType")) {
            room.setRoomType(rs.getString("RoomType"));
        }
        room.setCreatedAt(toLocalDateTime(rs.getTimestamp("CreatedAt")));
        room.setUpdatedAt(toLocalDateTime(rs.getTimestamp("UpdatedAt")));
        return room;
    }

    private Showtime mapShowtime(ResultSet rs) throws SQLException {
        Showtime showtime = new Showtime();
        showtime.setId(rs.getInt("Id"));
        showtime.setFilmId(rs.getInt("FilmId"));
        showtime.setCinemaId(rs.getInt("CinemaId"));
        showtime.setRoomId(rs.getInt("RoomId"));
        showtime.setFilmTitle(rs.getString("FilmTitle"));
        showtime.setCinemaName(rs.getString("CinemaName"));
        showtime.setRoomName(rs.getString("RoomName"));
        showtime.setStartTime(toLocalDateTime(rs.getTimestamp("StartTime")));
        showtime.setEndTime(toLocalDateTime(rs.getTimestamp("EndTime")));
        showtime.setBasePrice(rs.getBigDecimal("BasePrice"));
        showtime.setFormat(rs.getString("Format"));
        showtime.setVersion(rs.getString("Version"));
        showtime.setLanguage(rs.getString("Language"));
        showtime.setCreatedAt(toLocalDateTime(rs.getTimestamp("CreatedAt")));
        showtime.setUpdatedAt(toLocalDateTime(rs.getTimestamp("UpdatedAt")));
        if (hasColumn(rs, "SaleStatus")) {
            showtime.setSaleStatus(rs.getString("SaleStatus"));
            showtime.setDeleteRequestedAt(toLocalDateTime(rs.getTimestamp("DeleteRequestedAt")));
            showtime.setDeleteNotBefore(toLocalDateTime(rs.getTimestamp("DeleteNotBefore")));
            int requestedBy = rs.getInt("DeleteRequestedByUserId");
            showtime.setDeleteRequestedByUserId(rs.wasNull() ? null : requestedBy);
        }
        return showtime;
    }

    private User mapUser(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getInt("Id"));
        user.setUsername(rs.getString("Username"));
        user.setFullName(rs.getString("FullName"));
        user.setEmail(rs.getString("Email"));
        user.setPasswordHash(rs.getString("PasswordHash"));
        user.setPhone(rs.getString("Phone"));
        user.setAddress(rs.getString("Address"));
        user.setAvatar(rs.getString("Avatar"));
        user.setRole(rs.getString("Role"));
        user.setDeleted(rs.getBoolean("Deleted"));
        int cid = rs.getInt("CinemaId");
        if (!rs.wasNull()) {
            user.setCinemaId(cid);
        }
        if (hasColumn(rs, "CinemaName")) {
            user.setCinemaName(rs.getString("CinemaName"));
        }
        user.setLoyaltyPoints(rs.getInt("LoyaltyPoints"));
        user.setTotalSpent(rs.getBigDecimal("TotalSpent"));
        user.setMembershipTier(rs.getString("MembershipTier"));
        user.setCreatedAt(toLocalDateTime(rs.getTimestamp("CreatedAt")));
        user.setUpdatedAt(toLocalDateTime(rs.getTimestamp("UpdatedAt")));
        user.setEmailVerifiedAt(toLocalDateTime(rs.getTimestamp("EmailVerifiedAt")));
        return user;
    }

    private FilmComment mapComment(ResultSet rs) throws SQLException {
        FilmComment comment = new FilmComment();
        comment.setId(rs.getInt("Id"));
        comment.setUserId(rs.getInt("UserId"));
        comment.setFilmId(rs.getInt("FilmId"));
        comment.setRate(rs.getInt("Rate"));
        comment.setContent(rs.getString("Content"));
        comment.setReport(rs.getBoolean("Report"));
        comment.setCreatedAt(toLocalDateTime(rs.getTimestamp("CreatedAt")));
        if (hasColumn(rs, "FilmTitle")) {
            comment.setFilmTitle(rs.getString("FilmTitle"));
            comment.setUserFullName(rs.getString("UserFullName"));
            comment.setUserEmail(rs.getString("UserEmail"));
            comment.setUserWarningCount(rs.getInt("UserWarningCount"));
            comment.setUserIsLocked(rs.getBoolean("UserIsLocked"));
            comment.setFilmDeleted(hasColumn(rs, "FilmDeleted") && rs.getBoolean("FilmDeleted"));
        }
        return comment;
    }

    private Promotion mapPromotion(ResultSet rs) throws SQLException {
        Promotion promotion = new Promotion();
        promotion.setId(rs.getInt("Id"));
        promotion.setCode(rs.getString("Code"));
        promotion.setDescription(rs.getString("Description"));
        double discount = rs.getDouble("DiscountPercent");
        promotion.setDiscountPercent(rs.wasNull() ? null : discount);
        promotion.setMaxDiscount(rs.getBigDecimal("MaxDiscount"));
        Date startDate = rs.getDate("StartDate");
        Date endDate = rs.getDate("EndDate");
        promotion.setStartDate(startDate == null ? null : startDate.toLocalDate());
        promotion.setEndDate(endDate == null ? null : endDate.toLocalDate());
        promotion.setConditionsJson(rs.getString("ConditionsJson"));
        int usageLimit = rs.getInt("UsageLimit");
        promotion.setUsageLimit(rs.wasNull() ? null : usageLimit);
        promotion.setUsedCount(rs.getInt("UsedCount"));
        promotion.setStatus(rs.getString("Status"));
        promotion.setVoucherType(rs.getString("VoucherType"));
        promotion.setTargetTier(rs.getString("TargetTier"));
        promotion.setPointsRequired(rs.getInt("PointsRequired"));
        promotion.setPerUserLimit(rs.getInt("PerUserLimit"));
        if (hasColumn(rs, "CreatedByUserId")) {
            int createdByUserId = rs.getInt("CreatedByUserId");
            promotion.setCreatedByUserId(rs.wasNull() ? null : createdByUserId);
        }
        if (hasColumn(rs, "CreatedByName")) {
            promotion.setCreatedByName(rs.getString("CreatedByName"));
        }
        return promotion;
    }

    private void requirePromotionCapability(User actor) {
        if (!CinemaCapabilityPolicy.canCreatePromotion(actor)) {
            throw new BookingException(403, "Chỉ admin hoặc manager đang hoạt động được quản lý khuyến mãi.");
        }
    }

    private void assertPromotionOwnership(User actor, int promotionId) {
        if (CinemaCapabilityPolicy.isAdmin(actor)) {
            return;
        }
        String sql = "SELECT CreatedByUserId FROM Promotions WHERE Id=?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, promotionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new BookingException(404, "Không tìm thấy khuyến mãi.");
                }
                int ownerId = rs.getInt(1);
                if (rs.wasNull() || ownerId != actor.getId()) {
                    throw new BookingException(403,
                            "Manager chỉ được sửa hoặc ngừng khuyến mãi do chính mình tạo.");
                }
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể kiểm tra người tạo khuyến mãi.", ex);
        }
    }

    private int requireActorCinema(User actor) {
        if (!ScopeUtil.isManager(actor)) {
            throw new IllegalArgumentException("Cinema assignment is only required for a manager");
        }
        if (actor.getCinemaId() == null || actor.getCinemaId() <= 0) {
            throw new BookingException(403, "Tài khoản manager chưa được gán cụm rạp.");
        }
        return actor.getCinemaId();
    }

    /**
     * Chan cua cho cac thao tac <b>toan he thong</b>: chi admin, khong co manager.
     *
     * <p>Ban cu nhan ca {@code manager} — trai nguoc voi chinh ten ham. Hau qua that:
     * {@code /admin/films} chi doi vai {@code manager} o {@code AuthFilter}, nen mot manager
     * cua mot cum rap goi duoc {@link #deleteFilm} va xoa <b>bat ky phim nao</b>, ke ca phim
     * dang chieu o cum rap khac — cac rap con lai mat phim ma khong biet vi sao. Cung lo hong
     * do mo luon {@link #saveSetting} (cau hinh he thong) cho manager o tang service.</p>
     *
     * <p>Pham vi cum rap ({@code assertFilmScope}, {@code ScopeUtil}) khong bit duoc lo nay:
     * no chi doi hoi phim CO lien ket toi rap cua manager, ma mot phim dung chung thi luon thoa.
     * Nen chan phai nam o day. {@code FilmCinemaScopeIT#managerCannotHardDeleteFilmSharedWith​AnotherCinema}
     * va {@code #managerStillDeletesFilmThatBelongsOnlyToOwnCinema} giu dung hop dong nay.</p>
     */
    /**
     * Trung binh moi ngay, giu 1 chu so thap phan.
     *
     * <p>Ban cu lam bang phep chia nguyen {@code ticketsSold / days}. Voi thang 08/2026 —
     * 23 ve ban / 31 ngay — o "Ve ban TB / Ngay" hien dung so <b>0</b>, trong khi bang
     * "Top phim ban chay" ngay ben duoi van liet ke ghe da ban va "Tong doanh thu" van
     * 2.407.000 d. Bat ky thang nao ban duoc it ve hon so ngay trong thang deu bao 0, va
     * o so sanh thang truoc cung thanh vo nghia vi ca hai ky deu bi cat ve 0.</p>
     */
    static BigDecimal averagePerDay(int total, int days) {
        if (days <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(total)
                .divide(BigDecimal.valueOf(days), 1, java.math.RoundingMode.HALF_UP);
    }

    private void assertGlobalAdmin(User actor) {
        if (actor == null) {
            throw new BookingException(401, "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
        }
        if (!AppConstants.ROLE_ADMIN.equalsIgnoreCase(actor.getRole())) {
            throw new BookingException(403, "Chức năng toàn hệ thống chỉ dành cho Admin.");
        }
    }

    private void requireGlobalCatalogAdmin(User actor, String resourceName) {
        if (ScopeUtil.isManager(actor)) {
            throw new BookingException(403, "Chỉ admin hệ thống được thay đổi " + resourceName
                    + " dùng chung giữa các cụm rạp.");
        }
    }

    private void assertFilmScope(User actor, int filmId) {
        if (!ScopeUtil.isManager(actor))
            return;
        int cinemaId = requireActorCinema(actor);
        String sql = "SELECT 1 FROM CinemaFilms WHERE CinemaId=? AND FilmId=? AND Status=N'active'";
        if (!resourceExists(sql, cinemaId, filmId)) {
            throw new BookingException(403, "Phim không thuộc cụm rạp được phân quyền.");
        }
    }

    private void assertRoomScope(User actor, int roomId) {
        if (!ScopeUtil.isManager(actor))
            return;
        ScopeUtil.assertCinemaScope(actor, resourceCinema(
                "SELECT CinemaId FROM Rooms WHERE Id=?", roomId));
    }

    private void assertShowtimeScope(User actor, int showtimeId) {
        if (!ScopeUtil.isManager(actor))
            return;
        ScopeUtil.assertCinemaScope(actor, resourceCinema(
                "SELECT CinemaId FROM Showtimes WHERE Id=?", showtimeId));
    }

    private void assertOrderScope(User actor, int orderId) {
        if (!ScopeUtil.isManager(actor))
            return;
        ScopeUtil.assertCinemaScope(actor, resourceCinema("""
                SELECT s.CinemaId FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId
                WHERE o.Id=?
                """, orderId));
    }

    private void assertUserScope(User actor, int userId) {
        if (!ScopeUtil.isManager(actor))
            return;
        int cinemaId = requireActorCinema(actor);
        String sql = """
                SELECT 1 FROM Users u
                WHERE u.Id=? AND (u.CinemaId=? OR EXISTS (
                  SELECT 1 FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId
                  WHERE o.UserId=u.Id AND s.CinemaId=?
                ))
                """;
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, cinemaId);
            ps.setInt(3, cinemaId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return;
            }
            throw new BookingException(403, "Tài khoản không thuộc phạm vi cụm rạp.");
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể kiểm tra phạm vi tài khoản.", ex);
        }
    }

    /** Staff ownership is always the assigned CinemaId, never a personal ticket history. */
    private void assertStaffScope(User actor, int staffId) {
        boolean manager = ScopeUtil.isManager(actor);
        boolean admin = actor != null && AppConstants.ROLE_ADMIN.equalsIgnoreCase(actor.getRole());
        if (!manager && !admin) {
            throw new BookingException(403, "Only admin or manager may manage staff accounts.");
        }
        if (admin) {
            return;
        }
        String sql = "SELECT 1 FROM Users WHERE Id=? AND Role='staff' AND CinemaId=?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, staffId);
            ps.setInt(2, requireActorCinema(actor));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return;
                }
            }
            throw new BookingException(403, "Staff account is outside the assigned cinema.");
        } catch (SQLException ex) {
            throw new BookingException(500, "Cannot verify staff cinema scope.", ex);
        }
    }

    private void assertMemberInScope(User actor, int userId) {
        boolean manager = ScopeUtil.isManager(actor);
        boolean admin = actor != null && AppConstants.ROLE_ADMIN.equalsIgnoreCase(actor.getRole());
        if ((!manager && !admin) || actor.getId() == userId) {
            throw new BookingException(403, "Chỉ được thao tác trên tài khoản thành viên phù hợp phạm vi.");
        }
        int cinemaId = manager ? requireActorCinema(actor) : -1;
        String sql = manager
                ? """
                  SELECT 1 FROM Users u
                  WHERE u.Id=? AND u.Role='member' AND (
                    u.CinemaId=? OR EXISTS (
                      SELECT 1 FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId
                      WHERE o.UserId=u.Id AND s.CinemaId=?
                    )
                  )
                  """
                : "SELECT 1 FROM Users WHERE Id=? AND Role='member'";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, userId);
            if (manager) {
                ps.setInt(2, cinemaId);
                ps.setInt(3, cinemaId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return;
            }
            throw new BookingException(403, "Chỉ được thao tác trên tài khoản thành viên phù hợp phạm vi.");
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể kiểm tra phạm vi tài khoản thành viên.", ex);
        }
    }

    private int assertCommentInScope(User actor, int commentId) {
        try (Connection connection = DBConnection.getConnection()) {
            return assertCommentInScope(connection, actor, commentId, false);
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the kiem tra pham vi binh luan.", ex);
        }
    }

    /**
     * A manager may moderate a comment only when its author has a redeemed order
     * for the exact film at that manager's cinema. This intentionally does not
     * inherit the broader member/account scope used by other admin features.
     */
    private int assertCommentInScope(Connection connection, User actor, int commentId, boolean forUpdate)
            throws SQLException {
        if (!ScopeUtil.isManager(actor)) {
            return commentAuthorId(connection, commentId, forUpdate);
        }
        int cinemaId = requireActorCinema(actor);
        String lockHint = forUpdate ? "WITH (UPDLOCK, HOLDLOCK) " : "";
        String sql = """
                SELECT cm.UserId
                FROM Comments cm %s
                JOIN Users u ON u.Id=cm.UserId AND u.Role='member'
                WHERE cm.Id=? AND EXISTS (
                    SELECT 1
                    FROM Orders o
                    JOIN Showtimes s ON s.Id=o.ShowtimeId
                    WHERE o.UserId=cm.UserId
                      AND s.FilmId=cm.FilmId
                      AND s.CinemaId=?
                      AND o.OrderStatus='redeemed'
                )
                """.formatted(lockHint);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, commentId);
            statement.setInt(2, cinemaId);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return result.getInt("UserId");
                }
            }
        }
        if (commentExists(connection, commentId)) {
            throw new BookingException(403, "Comment is outside the permitted cinema scope.");
        }
        throw new BookingException(404, "Khong tim thay binh luan.");
    }

    private boolean commentExists(Connection connection, int commentId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM Comments WHERE Id=?")) {
            statement.setInt(1, commentId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private int commentAuthorId(Connection connection, int commentId, boolean forUpdate)
            throws SQLException {
        String sql = "SELECT UserId FROM Comments "
                + (forUpdate ? "WITH (UPDLOCK, HOLDLOCK) " : "") + "WHERE Id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, commentId);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return result.getInt("UserId");
                }
            }
        }
        throw new BookingException(404, "Khong tim thay binh luan.");
    }

    private void assertAppealNotificationScope(User actor, int appealId) {
        if (!ScopeUtil.isManager(actor)) {
            return;
        }
        int cinemaId = requireActorCinema(actor);
        String sql = """
                SELECT 1
                FROM UserAppeals a
                JOIN Users u ON u.Id=a.UserId
                WHERE a.Id=? AND a.CinemaId=? AND a.UserId<>?
                  AND (a.AppealType='refund'
                       OR (a.AppealType='account' AND u.Role IN ('manager','staff')))
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, appealId);
            statement.setInt(2, cinemaId);
            statement.setInt(3, actor.getId());
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return;
                }
            }
            throw new BookingException(403, "Appeal is outside the permitted cinema scope.");
        } catch (SQLException ex) {
            throw new BookingException(500, "Khong the kiem tra pham vi khang cao.", ex);
        }
    }

    private int resourceCinema(String sql, int resourceId) {
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, resourceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getInt(1);
            }
            throw new BookingException(404, "Không tìm thấy tài nguyên.");
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể kiểm tra phạm vi cụm rạp.", ex);
        }
    }

    private boolean resourceExists(String sql, int firstId, int secondId) {
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, firstId);
            ps.setInt(2, secondId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể kiểm tra phạm vi cụm rạp.", ex);
        }
    }

    private OrderRecord mapOrder(ResultSet rs) throws SQLException {
        OrderRecord order = new OrderRecord();
        order.setId(rs.getInt("Id"));
        order.setUserId(rs.getInt("UserId"));
        order.setShowtimeId(rs.getInt("ShowtimeId"));
        int promotionId = rs.getInt("PromotionId");
        order.setPromotionId(rs.wasNull() ? null : promotionId);
        order.setSeatSubtotal(rs.getBigDecimal("SeatSubtotal"));
        order.setComboSubtotal(rs.getBigDecimal("ComboSubtotal"));
        order.setDiscountAmount(rs.getBigDecimal("DiscountAmount"));
        order.setTotalAmount(rs.getBigDecimal("TotalAmount"));
        order.setTicketCode(rs.getString("TicketCode"));
        order.setTicketQrUrl(rs.getString("TicketQrUrl"));
        order.setPaymentMethod(rs.getString("PaymentMethod"));
        order.setPaymentStatus(rs.getString("PaymentStatus"));
        order.setTransactionId(rs.getString("TransactionId"));
        order.setPayRedirectUrl(rs.getString("PayRedirectUrl"));
        order.setCounterExpiresAt(toLocalDateTime(rs.getTimestamp("CounterExpiresAt")));
        order.setOrderStatus(rs.getString("OrderStatus"));
        order.setRedeemedAt(toLocalDateTime(rs.getTimestamp("RedeemedAt")));
        order.setRefundRejectedAt(toLocalDateTime(rs.getTimestamp("RefundRejectedAt")));
        order.setRefundRejectReason(rs.getString("RefundRejectReason"));
        order.setCreatedAt(toLocalDateTime(rs.getTimestamp("CreatedAt")));
        order.setUpdatedAt(toLocalDateTime(rs.getTimestamp("UpdatedAt")));
        return order;
    }

    private LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private void bindNullableDouble(PreparedStatement ps, int index, Double value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.DOUBLE);
        } else {
            ps.setDouble(index, value);
        }
    }

    /**
     * Tong hop bao cao thang hien tai so voi thang truoc.
     *
     * <p>
     * <b>Ba loi da sua o day (RP-01).</b>
     * </p>
     * <ol>
     * <li>{@code COUNT(os.Id)} tham chieu cot khong ton tai tren DB production nen
     * ca trang
     * bao cao tra 500 — thay bang {@code COUNT(*)}.</li>
     * <li>Moc thang lay tu {@code YearMonth.now()} cua JVM. Neu may ung dung lech
     * ngay voi
     * SQL Server thi bao cao "thang nay" cua hai node co the la hai thang khac nhau
     * —
     * lay tu {@link BusinessClock} (gio DB) thay the.</li>
     * <li>{@code FORMAT(CreatedAt,'yyyy-MM') = ?} khong sargable: SQL Server phai
     * tinh ham
     * tren tung dong nen khong dung duoc index {@code IX_Orders_CreatedAt_Id}. Doi
     * sang
     * khoang nua mo {@code CreatedAt >= @from AND CreatedAt < @to}, vua dung index
     * vua
     * khong con ranh gioi 23:59:59 bi bo sot.</li>
     * </ol>
     */
    public ReportSummaryDto getReportSummary() {
        ReportSummaryDto dto = new ReportSummaryDto();

        java.time.YearMonth now = java.time.YearMonth.from(BusinessClock.now().toLocalDate());
        java.time.YearMonth prevMonthObj = now.minusMonths(1);
        LocalDate currentFrom = now.atDay(1);
        LocalDate currentTo = now.plusMonths(1).atDay(1);
        LocalDate prevFrom = prevMonthObj.atDay(1);
        LocalDate prevTo = currentFrom;

        dto.setCurrentMonthLabel(String.format("Tháng %02d/%d", now.getMonthValue(), now.getYear()));
        dto.setPrevMonthLabel(String.format("Tháng %02d/%d", prevMonthObj.getMonthValue(), prevMonthObj.getYear()));

        int daysInCurrentMonth = now.lengthOfMonth();
        int daysInPrevMonth = prevMonthObj.lengthOfMonth();

        // 1. Total Revenue — doanh thu THUAN, dung chung dinh nghia voi moi bao cao khac.
        String totalRevSql = "SELECT ISNULL(SUM(" + NET_REVENUE_EXPRESSION + "), 0) FROM Orders o "
                + "WHERE " + REVENUE_ORDER_PREDICATE + " AND o.CreatedAt >= ? AND o.CreatedAt < ?";
        BigDecimal totalRevCurrent = queryReportBigDecimal(totalRevSql, currentFrom, currentTo);
        BigDecimal totalRevPrev = queryReportBigDecimal(totalRevSql, prevFrom, prevTo);
        dto.setTotalRevenueCurrent(totalRevCurrent);
        dto.setTotalRevenuePrev(totalRevPrev);
        dto.setTotalRevenueDiffPercent(calcDiffPercent(totalRevCurrent, totalRevPrev));

        // 2. Combo Revenue — cung tap don voi doanh thu tong.
        // Hoan mot phan khong tach duoc thanh phan combo (he thong khong luu dong nao bi hoan),
        // nen combo giu nguyen gia tri va phan hoan duoc tru vao doanh thu ve o buoc 3.
        String comboRevSql = """
                SELECT ISNULL(SUM(ocf.Quantity * ocf.UnitPrice), 0)
                FROM OrderComboFoods ocf JOIN Orders o ON o.Id = ocf.OrderId
                WHERE %s AND o.CreatedAt >= ? AND o.CreatedAt < ?
                """.formatted(REVENUE_ORDER_PREDICATE);
        BigDecimal comboRevCurrent = queryReportBigDecimal(comboRevSql, currentFrom, currentTo);
        BigDecimal comboRevPrev = queryReportBigDecimal(comboRevSql, prevFrom, prevTo);

        // 3. Ticket Revenue (Total - Combo)
        BigDecimal ticketRevCurrent = totalRevCurrent.subtract(comboRevCurrent).max(BigDecimal.ZERO);
        BigDecimal ticketRevPrev = totalRevPrev.subtract(comboRevPrev).max(BigDecimal.ZERO);

        // 4. Tickets Sold — dem ghe that trong OrderSeats, khong dem so don.
        String ticketsSoldSql = """
                SELECT COUNT(*) FROM OrderSeats os JOIN Orders o ON o.Id = os.OrderId
                WHERE %s AND o.CreatedAt >= ? AND o.CreatedAt < ?
                """.formatted(SOLD_SEAT_ORDER_PREDICATE);
        int ticketsSoldCurrent = queryReportInt(ticketsSoldSql, currentFrom, currentTo);
        int ticketsSoldPrev = queryReportInt(ticketsSoldSql, prevFrom, prevTo);

        BigDecimal avgTicketsCurrent = averagePerDay(ticketsSoldCurrent, daysInCurrentMonth);
        BigDecimal avgTicketsPrev = averagePerDay(ticketsSoldPrev, daysInPrevMonth);
        dto.setAvgTicketsPerDayCurrent(avgTicketsCurrent);
        dto.setAvgTicketsPerDayPrev(avgTicketsPrev);
        dto.setAvgTicketsPerDayDiffPercent(calcDiffPercent(avgTicketsCurrent, avgTicketsPrev));

        // 5. Cancelled Orders Count & Total Amount
        String cancelledCountSql = "SELECT COUNT(Id) FROM Orders "
                + "WHERE (OrderStatus = 'cancelled' OR PaymentStatus = 'cancelled') "
                + "AND CreatedAt >= ? AND CreatedAt < ?";
        int cancelledOrdersCurrent = queryReportInt(cancelledCountSql, currentFrom, currentTo);
        int cancelledOrdersPrev = queryReportInt(cancelledCountSql, prevFrom, prevTo);

        String cancelledRevSql = "SELECT ISNULL(SUM(TotalAmount), 0) FROM Orders "
                + "WHERE (OrderStatus = 'cancelled' OR PaymentStatus = 'cancelled') "
                + "AND CreatedAt >= ? AND CreatedAt < ?";
        BigDecimal cancelledRevCurrent = queryReportBigDecimal(cancelledRevSql, currentFrom, currentTo);
        BigDecimal cancelledRevPrev = queryReportBigDecimal(cancelledRevSql, prevFrom, prevTo);

        String totalOrdersSql = "SELECT COUNT(Id) FROM Orders WHERE CreatedAt >= ? AND CreatedAt < ?";
        int totalOrdersCurrent = queryReportInt(totalOrdersSql, currentFrom, currentTo);
        int totalOrdersPrev = queryReportInt(totalOrdersSql, prevFrom, prevTo);

        double cancelRateCurrent = totalOrdersCurrent > 0 ? (double) cancelledOrdersCurrent * 100.0 / totalOrdersCurrent
                : 0.0;
        double cancelRatePrev = totalOrdersPrev > 0 ? (double) cancelledOrdersPrev * 100.0 / totalOrdersPrev : 0.0;
        dto.setCancelRateCurrent(cancelRateCurrent);
        dto.setCancelRatePrev(cancelRatePrev);
        dto.setCancelRateDiffPoint(cancelRateCurrent - cancelRatePrev);

        // 6. Occupancy Rate
        //
        // N-10 — ban cu tron HAI MOC THOI GIAN khac nhau trong cung mot phan so:
        //   tu so  ticketsSold   loc theo o.CreatedAt  (ngay DAT VE)
        //   mau so availableSeats loc theo s.StartTime (ngay CHIEU)
        // Ve ban thang 7 cho suat thang 8 vao tu so thang 7 con ghe vao mau so thang 8, nen
        // ti le lap ghe thang 7 co the vuot 100% va thang 8 bi hut. Ti le lap ghe la chi so
        // cua SUAT CHIEU, nen ca tu so lan mau so deu phai neo vao s.StartTime.
        //
        // Mau so cung doi tu "COUNT(*) FROM Seats WHERE RoomId = s.RoomId" — so ghe HIEN TAI
        // cua phong — sang ShowtimeSeats, la anh chup ghe cua chinh suat do. Sua so do ghe
        // cua mot phong khong duoc lam thay doi bao cao cua nhung thang da qua.
        String seatsSoldByShowDateSql = """
                SELECT COUNT(*)
                FROM OrderSeats os
                JOIN Orders o ON o.Id = os.OrderId
                JOIN Showtimes s ON s.Id = o.ShowtimeId
                WHERE %s AND s.StartTime >= ? AND s.StartTime < ?
                """.formatted(SOLD_SEAT_ORDER_PREDICATE);
        int seatsSoldByShowDateCurrent = queryReportInt(seatsSoldByShowDateSql, currentFrom, currentTo);
        int seatsSoldByShowDatePrev = queryReportInt(seatsSoldByShowDateSql, prevFrom, prevTo);

        String availableSeatsSql = """
                SELECT COUNT(*)
                FROM ShowtimeSeats ss
                JOIN Showtimes s ON s.Id = ss.ShowtimeId
                WHERE s.StartTime >= ? AND s.StartTime < ?
                """;
        int totalAvailableSeatsCurrent = queryReportInt(availableSeatsSql, currentFrom, currentTo);
        int totalAvailableSeatsPrev = queryReportInt(availableSeatsSql, prevFrom, prevTo);

        double occRateCurrent = totalAvailableSeatsCurrent > 0
                ? (double) seatsSoldByShowDateCurrent * 100.0 / totalAvailableSeatsCurrent
                : 0.0;
        double occRatePrev = totalAvailableSeatsPrev > 0
                ? (double) seatsSoldByShowDatePrev * 100.0 / totalAvailableSeatsPrev
                : 0.0;

        // 7. New Members
        String newMembersSql = "SELECT COUNT(Id) FROM Users "
                + "WHERE Role = 'member' AND CreatedAt >= ? AND CreatedAt < ?";
        int newMembersCurrent = queryReportInt(newMembersSql, currentFrom, currentTo);
        int newMembersPrev = queryReportInt(newMembersSql, prevFrom, prevTo);

        // 8. Avg Ticket Price
        BigDecimal avgTicketPriceCurrent = ticketsSoldCurrent > 0
                ? ticketRevCurrent.divide(BigDecimal.valueOf(ticketsSoldCurrent), 0, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal avgTicketPricePrev = ticketsSoldPrev > 0
                ? ticketRevPrev.divide(BigDecimal.valueOf(ticketsSoldPrev), 0, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Metric Rows
        java.text.NumberFormat nf = java.text.NumberFormat.getInstance(java.util.Locale.forLanguageTag("vi-VN"));

        dto.getMetrics().add(createReportMetricRow("Doanh thu vé", formatCurrencyVal(ticketRevCurrent),
                formatCurrencyVal(ticketRevPrev), ticketRevCurrent, ticketRevPrev, false));
        dto.getMetrics().add(createReportMetricRow("Doanh thu combo", formatCurrencyVal(comboRevCurrent),
                formatCurrencyVal(comboRevPrev), comboRevCurrent, comboRevPrev, false));
        dto.getMetrics()
                .add(createReportMetricRow("Số vé bán", nf.format(ticketsSoldCurrent), nf.format(ticketsSoldPrev),
                        BigDecimal.valueOf(ticketsSoldCurrent), BigDecimal.valueOf(ticketsSoldPrev), false));
        dto.getMetrics()
                .add(createReportMetricRow("Số đơn vé hủy", nf.format(cancelledOrdersCurrent),
                        nf.format(cancelledOrdersPrev), BigDecimal.valueOf(cancelledOrdersCurrent),
                        BigDecimal.valueOf(cancelledOrdersPrev), false));
        dto.getMetrics().add(createReportMetricRow("Tổng tiền đơn vé hủy", formatCurrencyVal(cancelledRevCurrent),
                formatCurrencyVal(cancelledRevPrev), cancelledRevCurrent, cancelledRevPrev, false));
        dto.getMetrics()
                .add(new ReportSummaryDto.MetricRow("Tỉ lệ lấp ghế",
                        String.format(java.util.Locale.US, "%.1f%%", occRateCurrent),
                        String.format(java.util.Locale.US, "%.1f%%", occRatePrev),
                        formatPointDiffVal(occRateCurrent - occRatePrev), occRateCurrent >= occRatePrev, true));
        dto.getMetrics()
                .add(createReportMetricRow("Thành viên mới", nf.format(newMembersCurrent), nf.format(newMembersPrev),
                        BigDecimal.valueOf(newMembersCurrent), BigDecimal.valueOf(newMembersPrev), false));
        dto.getMetrics().add(createReportMetricRow("Giá vé trung bình", formatCurrencyVal(avgTicketPriceCurrent),
                formatCurrencyVal(avgTicketPricePrev), avgTicketPriceCurrent, avgTicketPricePrev, false));

        return dto;
    }

    public ReportSummaryDto getReportSummary(User actor) {
        if (!ScopeUtil.isManager(actor))
            return getReportSummary();
        int cinemaId = requireActorCinema(actor);
        // Gio DB la nguon duy nhat — xem ghi chu o getReportSummary().
        java.time.YearMonth currentMonth = java.time.YearMonth.from(BusinessClock.now().toLocalDate());
        java.time.YearMonth previousMonth = currentMonth.minusMonths(1);
        ScopedReport current = scopedReport(cinemaId, currentMonth.atDay(1),
                currentMonth.plusMonths(1).atDay(1));
        ScopedReport previous = scopedReport(cinemaId, previousMonth.atDay(1),
                currentMonth.atDay(1));
        ReportSummaryDto dto = new ReportSummaryDto();
        dto.setCurrentMonthLabel(String.format("Tháng %02d/%d",
                currentMonth.getMonthValue(), currentMonth.getYear()));
        dto.setPrevMonthLabel(String.format("Tháng %02d/%d",
                previousMonth.getMonthValue(), previousMonth.getYear()));
        dto.setTotalRevenueCurrent(current.revenue);
        dto.setTotalRevenuePrev(previous.revenue);
        dto.setTotalRevenueDiffPercent(calcDiffPercent(current.revenue, previous.revenue));
        // FLOW-REPORT-003: "ve trung binh/ngay" phai chia SO VE cho so ngay. Ban cu chia so DON,
        // nen mot don 31 ghe trong thang 31 ngay cho ket qua 0 ve/ngay thay vi 1.
        // Lan sua do van giu phep chia nguyen, nen moi thang ban it ve hon so ngay van bao 0 —
        // xem averagePerDay() va ReportAveragePerDayTest.
        BigDecimal currentAverage = averagePerDay(current.soldSeats(), currentMonth.lengthOfMonth());
        BigDecimal previousAverage = averagePerDay(previous.soldSeats(), previousMonth.lengthOfMonth());
        dto.setAvgTicketsPerDayCurrent(currentAverage);
        dto.setAvgTicketsPerDayPrev(previousAverage);
        dto.setAvgTicketsPerDayDiffPercent(calcDiffPercent(currentAverage, previousAverage));
        double currentCancel = current.totalOrders == 0 ? 0
                : current.cancelledOrders * 100.0 / current.totalOrders;
        double previousCancel = previous.totalOrders == 0 ? 0
                : previous.cancelledOrders * 100.0 / previous.totalOrders;
        dto.setCancelRateCurrent(currentCancel);
        dto.setCancelRatePrev(previousCancel);
        dto.setCancelRateDiffPoint(currentCancel - previousCancel);
        // N-11: o "chenh lech" cua dong nay truoc day lay
        // formatPercentDiffVal(dto.getAvgTicketsPerDayDiffPercent()) — phan tram thay doi cua
        // VE TRUNG BINH/NGAY, mot chi so khac han. Manager doc duoc mot con so khong lien quan
        // toi chinh chi so dung canh no. Chenh lech phai tinh tu chinh paidOrders.
        double paidOrdersDiffPercent = calcDiffPercent(
                BigDecimal.valueOf(current.paidOrders), BigDecimal.valueOf(previous.paidOrders));
        dto.setMetrics(List.of(
                new ReportSummaryDto.MetricRow("Đơn đã thanh toán",
                        String.valueOf(current.paidOrders), String.valueOf(previous.paidOrders),
                        formatPercentDiffVal(paidOrdersDiffPercent),
                        current.paidOrders >= previous.paidOrders, false),
                new ReportSummaryDto.MetricRow("Đơn đã hủy",
                        String.valueOf(current.cancelledOrders), String.valueOf(previous.cancelledOrders),
                        formatPointDiffVal(currentCancel - previousCancel),
                        current.cancelledOrders <= previous.cancelledOrders, true)));
        return dto;
    }

    /**
     * Tong hop cua mot cum rap trong khoang {@code [from, to)}.
     *
     * <p>Dung dung {@link #REVENUE_ORDER_PREDICATE}/{@link #NET_REVENUE_EXPRESSION} nhu bao cao
     * toan he thong, chi khac o dieu kien {@code s.CinemaId=?}. Nho vay cung mot don cho cung
     * mot con so du xem bang mat admin hay mat manager (FLOW-REPORT-004).</p>
     */
    private ScopedReport scopedReport(int cinemaId, LocalDate from, LocalDate to) {
        String sql = """
                SELECT
                  COALESCE(SUM(CASE WHEN %1$s THEN %2$s ELSE 0 END),0) AS Revenue,
                  SUM(CASE WHEN o.PaymentStatus='paid' THEN 1 ELSE 0 END) AS PaidOrders,
                  COUNT(*) AS TotalOrders,
                  SUM(CASE WHEN o.OrderStatus='cancelled' THEN 1 ELSE 0 END) AS CancelledOrders,
                  COALESCE(SUM(CASE WHEN %3$s THEN seatAgg.SeatCount ELSE 0 END),0) AS SoldSeats
                FROM Orders o JOIN Showtimes s ON s.Id=o.ShowtimeId
                OUTER APPLY (
                    SELECT COUNT_BIG(*) AS SeatCount FROM OrderSeats os WHERE os.OrderId = o.Id
                ) seatAgg
                WHERE s.CinemaId=? AND o.CreatedAt>=? AND o.CreatedAt<?
                """.formatted(REVENUE_ORDER_PREDICATE, NET_REVENUE_EXPRESSION,
                        SOLD_SEAT_ORDER_PREDICATE);
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, cinemaId);
            ps.setDate(2, Date.valueOf(from));
            ps.setDate(3, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new ScopedReport(rs.getBigDecimal("Revenue"), rs.getInt("PaidOrders"),
                        rs.getInt("TotalOrders"), rs.getInt("CancelledOrders"),
                        rs.getInt("SoldSeats"));
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể tải tổng hợp theo cụm rạp.", ex);
        }
    }

    private record ScopedReport(BigDecimal revenue, int paidOrders,
            int totalOrders, int cancelledOrders, int soldSeats) {
    }

    /**
     * Chay mot truy van bao cao tra ve mot so tien, loc theo khoang nua mo
     * {@code [from, to)}.
     *
     * <p>
     * Truoc day ham nay nhan mot chuoi {@code "yyyy-MM"} roi so bang
     * {@code FORMAT(CreatedAt,'yyyy-MM') = ?}. Cach do khong dung duoc index va de
     * sai o ranh
     * gioi cuoi thang. Nhan hai moc thoi gian giup moi truy van bao cao dung chung
     * mot quy uoc:
     * lay ca {@code from}, khong lay {@code to}.
     * </p>
     */
    private BigDecimal queryReportBigDecimal(String sql, LocalDate from, LocalDate to) {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            bindReportRange(ps, from, to);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    BigDecimal val = rs.getBigDecimal(1);
                    return val != null ? val : BigDecimal.ZERO;
                }
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể tính báo cáo doanh thu.", ex);
        }
        return BigDecimal.ZERO;
    }

    /** Nhu {@link #queryReportBigDecimal} nhung tra ve so dem. */
    private int queryReportInt(String sql, LocalDate from, LocalDate to) {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            bindReportRange(ps, from, to);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException ex) {
            throw new BookingException(500, "Không thể tính báo cáo số lượng.", ex);
        }
        return 0;
    }

    /**
     * Gan khoang thoi gian bao cao. Dung {@code Timestamp} chu khong phai
     * {@code Date} de
     * so sanh voi cot {@code DATETIME} khong phu thuoc cach driver ep kieu.
     */
    private void bindReportRange(PreparedStatement ps, LocalDate from, LocalDate to) throws SQLException {
        ps.setTimestamp(1, Timestamp.valueOf(from.atStartOfDay()));
        ps.setTimestamp(2, Timestamp.valueOf(to.atStartOfDay()));
    }

    private boolean hasColumn(ResultSet rs, String column) throws SQLException {
        java.sql.ResultSetMetaData metadata = rs.getMetaData();
        for (int index = 1; index <= metadata.getColumnCount(); index++) {
            if (column.equalsIgnoreCase(metadata.getColumnLabel(index))) {
                return true;
            }
        }
        return false;
    }

    private double calcDiffPercent(BigDecimal current, BigDecimal prev) {
        if (prev == null || prev.compareTo(BigDecimal.ZERO) == 0) {
            return current != null && current.compareTo(BigDecimal.ZERO) > 0 ? 100.0 : 0.0;
        }
        return current.subtract(prev).multiply(BigDecimal.valueOf(100)).doubleValue() / prev.doubleValue();
    }

    private ReportSummaryDto.MetricRow createReportMetricRow(String name, String currStr, String prevStr,
            BigDecimal curr, BigDecimal prev, boolean pointDiff) {
        double diffPct = calcDiffPercent(curr, prev);
        boolean trendUp = curr != null && prev != null && curr.compareTo(prev) >= 0;
        String diffText = formatPercentDiffVal(diffPct);
        return new ReportSummaryDto.MetricRow(name, currStr, prevStr, diffText, trendUp, pointDiff);
    }

    private String formatCurrencyVal(BigDecimal amount) {
        if (amount == null)
            return "0 đ";
        java.text.NumberFormat nf = java.text.NumberFormat.getInstance(java.util.Locale.forLanguageTag("vi-VN"));
        return nf.format(amount) + " đ";
    }

    private String formatPercentDiffVal(double diff) {
        if (diff > 0)
            return String.format(java.util.Locale.US, "+%.1f%%", diff);
        if (diff < 0)
            return String.format(java.util.Locale.US, "%.1f%%", diff);
        return "0,0%";
    }

    private String formatPointDiffVal(double diff) {
        if (diff > 0)
            return String.format(java.util.Locale.US, "+%.1f điểm", diff);
        if (diff < 0)
            return String.format(java.util.Locale.US, "%.1f điểm", diff);
        return "0,0 điểm";
    }

    public void exportReportCsv(java.io.PrintWriter writer) {
        ReportSummaryDto summary = getReportSummary();
        writer.println("CINEBOOK - BÁO CÁO THỐNG KÊ DOANH THU & KINH DOANH");
        writer.println(
                "Thời gian báo cáo: " + summary.getCurrentMonthLabel() + " so với " + summary.getPrevMonthLabel());
        writer.println();

        writer.println("--- CHỈ SỐ TỔNG QUAN ---");
        writer.println("Chỉ số," + summary.getCurrentMonthLabel() + "," + summary.getPrevMonthLabel() + ",Biến động");
        writer.println("Tổng doanh thu tháng," + formatCurrencyVal(summary.getTotalRevenueCurrent()) + ","
                + formatCurrencyVal(summary.getTotalRevenuePrev()) + ","
                + String.format(java.util.Locale.US, "%.1f%%", summary.getTotalRevenueDiffPercent()));
        writer.println("Vé bán trung bình/ngày," + summary.getAvgTicketsPerDayCurrent() + ","
                + summary.getAvgTicketsPerDayPrev() + ","
                + String.format(java.util.Locale.US, "%.1f%%", summary.getAvgTicketsPerDayDiffPercent()));
        writer.println("Tỷ lệ hủy đơn," + String.format(java.util.Locale.US, "%.1f%%", summary.getCancelRateCurrent())
                + "," + String.format(java.util.Locale.US, "%.1f%%", summary.getCancelRatePrev()) + ","
                + String.format(java.util.Locale.US, "%.1f điểm", summary.getCancelRateDiffPoint()));
        writer.println();

        writer.println("--- CHỈ SỐ CHI TIẾT ---");
        writer.println("Chỉ số," + summary.getCurrentMonthLabel() + "," + summary.getPrevMonthLabel() + ",Biến động");
        for (ReportSummaryDto.MetricRow row : summary.getMetrics()) {
            writer.println("\"" + row.getName() + "\",\"" + row.getCurrentValue() + "\",\"" + row.getPrevValue()
                    + "\",\"" + row.getDiffText() + "\"");
        }
        writer.println();

        writer.println("--- DOANH THU THEO NGÀY THỰC TẾ ---");
        writer.println("Ngày,Số đơn paid,Tổng doanh thu");
        List<RevenueRow> daily = dailyRevenueRows();
        for (RevenueRow row : daily) {
            writer.println(row.getLabel() + "," + row.getOrderCount() + ",\"" + formatCurrencyVal(row.getTotalRevenue())
                    + "\"");
        }
        writer.println();

        writer.println("--- TOP PHIM BÁN CHẠY NHẤT ---");
        writer.println("Tên phim,Số ghế đã bán,Doanh thu");
        List<TopFilmRow> top = topFilms();
        for (TopFilmRow row : top) {
            writer.println("\"" + row.getFilmTitle() + "\"," + row.getSoldSeats() + ",\""
                    + formatCurrencyVal(row.getTotalRevenue()) + "\"");
        }
    }

    public void exportReportCsv(java.io.PrintWriter writer, User actor) {
        if (!ScopeUtil.isManager(actor)) {
            exportReportCsv(writer);
            return;
        }
        ReportSummaryDto summary = getReportSummary(actor);
        writer.println("CINEBOOK - BÁO CÁO THEO CỤM RẠP");
        writer.println("Cụm rạp ID: " + requireActorCinema(actor));
        writer.println("Kỳ," + summary.getCurrentMonthLabel() + "," + summary.getPrevMonthLabel());
        writer.println("Doanh thu," + summary.getTotalRevenueCurrent() + "," + summary.getTotalRevenuePrev());
        writer.println("Tỷ lệ hủy," + summary.getCancelRateCurrent() + "," + summary.getCancelRatePrev());
        writer.println();
        writer.println("Ngày,Số đơn paid,Tổng doanh thu");
        for (RevenueRow row : dailyRevenueRows(actor)) {
            writer.println(row.getLabel() + "," + row.getOrderCount() + "," + row.getTotalRevenue());
        }
        writer.println();
        writer.println("Tên phim,Số ghế đã bán,Doanh thu");
        for (TopFilmRow row : topFilms(actor)) {
            writer.println("\"" + row.getFilmTitle().replace("\"", "\"\"") + "\","
                    + row.getSoldSeats() + "," + row.getTotalRevenue());
        }
    }
}
