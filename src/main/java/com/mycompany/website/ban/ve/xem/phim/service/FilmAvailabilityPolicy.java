package com.mycompany.website.ban.ve.xem.phim.service;

import com.mycompany.website.ban.ve.xem.phim.config.SettingsReader;
import com.mycompany.website.ban.ve.xem.phim.model.Film;
import com.mycompany.website.ban.ve.xem.phim.util.BusinessClock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * Noi duy nhat quyet dinh mot phim dang o giai doan nao va co duoc hien ra ngoai hay khong
 * (EX-01, PU-01).
 *
 * <p><b>Van de goc.</b> Truoc lop nay, moi be mat tu phan loai lay mot kieu:
 * {@code HeaderDataFilter} so {@code ReleaseDate} voi {@code LocalDate.now()} cua JVM,
 * {@code CatalogApiServlet} lap lai y het doan do, {@code Film.getStatus()} lai co nhanh rieng,
 * con trang chi tiet thi khong loc gi ca. Hau qua: cung mot phim co the "dang chieu" o header,
 * "sap chieu" o API, va van mo duoc bang URL truc tiep du da ngung chieu. Them {@code EndDate}
 * ma khong gom quy tac lai thi se co them mot cach phan loai thu tu.</p>
 *
 * <p><b>Quy tac.</b> Tinh theo <i>ngay cua DB</i> ({@link BusinessClock}), khong phai dong ho JVM:</p>
 * <pre>
 *   WITHDRAWN : Status = 'ended'                      -> bien tap da rut, chan truoc moi dieu kien ngay
 *   COMING    : today &lt; ReleaseDate
 *   EXPIRED   : today &gt; EndDate                       -> an khoi moi be mat public
 *   EXPIRING  : con 0..N ngay toi EndDate              -> VAN dat ve duoc, chi them badge
 *   SHOWING   : con lai
 * </pre>
 *
 * <p>{@code EndDate} rong nghia la chua gioi han thoi gian chieu — day la trang thai cua toan bo
 * phim co truoc migration {@code fix16}, nen khong duoc coi la het han.</p>
 *
 * <p><b>Trang thai duoc suy ra, khong luu san.</b> Neu ghi ket qua vao cot {@code Status} bang mot
 * job chay dem thi bat ky lan job loi hoac may dung se lam phim hien sai suot ca ngay. Suy ra moi
 * lan doc thi khong bao gio lech.</p>
 */
public final class FilmAvailabilityPolicy {

    /** Cua so canh bao mac dinh khi {@code film.expiringSoonDays} khong doc duoc. */
    public static final int DEFAULT_EXPIRING_SOON_DAYS = 3;

    private static final String KEY_EXPIRING_SOON_DAYS = "film.expiringSoonDays";

    /** Trang thai bien tap chan phim khoi trang public bat ke ngay thang. */
    private static final String STATUS_ENDED = "ended";

    private FilmAvailabilityPolicy() {
    }

    /** Giai doan vong doi cua mot phim. */
    public enum Availability {
        /** Chua toi ngay khoi chieu. Hien public de quang ba, chua dat ve duoc. */
        COMING,
        /** Dang chieu binh thuong. */
        SHOWING,
        /** Sap het chieu (con 0..N ngay). Van dat ve duoc, co badge nhac. */
        EXPIRING_SOON,
        /** Da qua EndDate. An khoi public, admin/manager van thay. */
        EXPIRED,
        /** Bien tap dat Status = 'ended'. An khoi public bat ke ngay thang. */
        WITHDRAWN;

        /** Con nam trong thoi gian chieu hoac dat ve truoc — {@code SHOWING}, {@code EXPIRING_SOON}, {@code COMING}. */
        public boolean isOnScreen() {
            return this == SHOWING || this == EXPIRING_SOON || this == COMING;
        }
    }

    /** Cua so "sap het chieu" hien hanh, doc tu {@code SystemSettings}. */
    public static int expiringSoonDays() {
        return SettingsReader.readInt(KEY_EXPIRING_SOON_DAYS, DEFAULT_EXPIRING_SOON_DAYS, 0, 365);
    }

    /** Giai doan cua phim tai thoi diem hien tai theo gio DB. */
    public static Availability evaluate(Film film) {
        return evaluate(film, BusinessClock.now().toLocalDate(), expiringSoonDays());
    }

    /**
     * Giai doan cua phim tai mot ngay cu the.
     *
     * <p>Overload nay nhan {@code today} va {@code expiringSoonDays} tuong minh de test kiem tra
     * duoc ranh gioi ngay ma khong phai doi dong ho he thong hay ghi {@code SystemSettings}.</p>
     */
    public static Availability evaluate(Film film, LocalDate today, int expiringSoonDays) {
        Objects.requireNonNull(today, "today");
        if (film == null) {
            return Availability.WITHDRAWN;
        }
        if (film.isDeleted()) {
            return Availability.WITHDRAWN;
        }
        if (STATUS_ENDED.equalsIgnoreCase(film.getRawStatus())) {
            return Availability.WITHDRAWN;
        }

        LocalDate release = film.getReleaseDate();
        if (release != null && today.isBefore(release)) {
            return Availability.COMING;
        }

        LocalDate end = film.getEndDate();
        if (end == null) {
            // Chua gioi han ngay ket thuc — khong the "sap het" cung khong the "het han".
            return Availability.SHOWING;
        }
        if (today.isAfter(end)) {
            return Availability.EXPIRED;
        }

        long daysLeft = ChronoUnit.DAYS.between(today, end);
        return daysLeft <= Math.max(0, expiringSoonDays) ? Availability.EXPIRING_SOON : Availability.SHOWING;
    }

    /**
     * Phim co duoc hien tren cac be mat public khong (trang chu, header, tim kiem, danh sach,
     * trang rap, API, bo chon suat chieu).
     *
     * <p>{@code COMING} van hien de quang ba; chi {@code EXPIRED} va {@code WITHDRAWN} bi an.</p>
     */
    public static boolean isPubliclyVisible(Film film) {
        return isPubliclyVisible(evaluate(film));
    }

    /** Nhu tren nhung nhan san giai doan da tinh, tranh tinh lai nhieu lan trong mot vong lap. */
    public static boolean isPubliclyVisible(Availability availability) {
        return availability != Availability.EXPIRED && availability != Availability.WITHDRAWN;
    }

    /** Phim co dang mo ban ve khong (bao gom ca ve dat truoc COMING). Phim het han/rut khong con ban. */
    public static boolean isBookable(Film film) {
        return evaluate(film).isOnScreen();
    }

    /** Loc mot danh sach xuong con phan duoc phep hien ra ngoai. */
    public static List<Film> publicOnly(List<Film> films) {
        if (films == null || films.isEmpty()) {
            return List.of();
        }
        LocalDate today = BusinessClock.now().toLocalDate();
        int window = expiringSoonDays();
        return films.stream()
                .filter(film -> isPubliclyVisible(evaluate(film, today, window)))
                .toList();
    }

    /**
     * So ngay con lai toi {@code EndDate}, hoac {@code null} khi phim khong gioi han ngay ket thuc.
     * Am nghia la da qua han.
     */
    public static Long daysUntilEnd(Film film) {
        if (film == null || film.getEndDate() == null) {
            return null;
        }
        return ChronoUnit.DAYS.between(BusinessClock.now().toLocalDate(), film.getEndDate());
    }
}
