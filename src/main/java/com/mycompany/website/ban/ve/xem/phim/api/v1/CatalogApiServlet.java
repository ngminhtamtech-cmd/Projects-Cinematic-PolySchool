package com.mycompany.website.ban.ve.xem.phim.api.v1;

import com.mycompany.website.ban.ve.xem.phim.api.ApiException;
import com.mycompany.website.ban.ve.xem.phim.api.BaseApiServlet;
import com.mycompany.website.ban.ve.xem.phim.api.dto.DtoMapper;
import com.mycompany.website.ban.ve.xem.phim.api.dto.Dtos;
import com.mycompany.website.ban.ve.xem.phim.dao.CinemaDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.FilmDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcCinemaDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcFilmDAO;
import com.mycompany.website.ban.ve.xem.phim.model.Cinema;
import com.mycompany.website.ban.ve.xem.phim.model.Film;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
import com.mycompany.website.ban.ve.xem.phim.service.BookingService;
import com.mycompany.website.ban.ve.xem.phim.service.FilmAvailabilityPolicy;
import com.mycompany.website.ban.ve.xem.phim.util.BusinessClock;
import com.mycompany.website.ban.ve.xem.phim.util.CustomContentHelper;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Cac endpoint tra cuu dung chung:
 * <ul>
 *   <li>{@code /api/v1/cinemas?cityId=}</li>
 *   <li>{@code /api/v1/cities}</li>
 *   <li>{@code /api/v1/combos}</li>
 *   <li>{@code /api/v1/content/{cinetags|corner|events|special}}</li>
 *   <li>{@code /api/v1/layout/header} - thay the HeaderDataFilter, co cache</li>
 * </ul>
 */
public class CatalogApiServlet extends BaseApiServlet {
    /** Du lieu header doi rat cham; cache ngan da du cat bo 2 truy van DB moi request. */
    private static final long HEADER_CACHE_TTL_MS = 5 * 60 * 1000L;
    private static volatile Dtos.HeaderDataDto cachedHeader;
    private static volatile long cachedHeaderAt;

    /**
     * Xoa cache header cua API (PU-01).
     *
     * <p>Cache nay doc lap voi {@code HeaderDataFilter}: JSP va Next.js dung hai duong khac nhau
     * toi cung du lieu. Truoc day chi {@code HeaderDataFilter} duoc invalidate khi CRUD phim, nen
     * sua phim xong tang JSP cap nhat ngay con tang Next.js van hien du lieu cu toi 5 phut —
     * dung kieu lech nghiep vu giua hai tang ma tai lieu du an canh bao.</p>
     */
    public static void invalidateHeaderCache() {
        cachedHeader = null;
        cachedHeaderAt = 0L;
    }

    private final FilmDAO filmDAO = new JdbcFilmDAO();
    private final CinemaDAO cinemaDAO = new JdbcCinemaDAO();
    private final AdminService adminService = new AdminService();
    private final BookingService bookingService = new BookingService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getServletPath();
        switch (path) {
            case "/api/v1/cinemas" -> writeData(response,
                    DtoMapper.cinemas(cinemaDAO.findAll(intOrNull(request, "cityId"))));
            case "/api/v1/cities" -> writeData(response, DtoMapper.cities(adminService.cityOptions()));
            case "/api/v1/combos" -> {
                Integer cinemaId = intOrNull(request, "cinemaId");
                if (cinemaId == null) {
                    writeData(response, List.of());
                    break;
                }
                if (cinemaId <= 0) {
                    throw ApiException.badRequest("Vui lòng chọn rạp trước khi tải combo.");
                }
                writeData(response, DtoMapper.combos(bookingService.findActiveCombos(cinemaId)));
            }
            case "/api/v1/content" -> content(request, response);
            case "/api/v1/layout" -> layout(request, response);
            default -> throw ApiException.notFound("Khong tim thay endpoint.");
        }
    }

    private void content(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String[] segments = segments(request);
        if (segments.length == 0) {
            throw ApiException.notFound("Thieu loai noi dung.");
        }
        String section = str(request, "section");
        switch (segments[0]) {
            case "cinetags" -> {
                String tag = str(request, "tag");
                writeData(response, CustomContentHelper.getCineTagProducts(tag.isBlank() ? "movie-verse" : tag));
            }
            case "corner" -> writeData(response,
                    CustomContentHelper.getCornerItems(section.isBlank() ? "the-loai" : section));
            case "events" -> writeData(response,
                    CustomContentHelper.getEventItems(section.isBlank() ? "uu-dai" : section));
            case "special" -> writeData(response, CustomContentHelper.getSpecialCinemas());
            default -> throw ApiException.notFound("Loai noi dung khong hop le.");
        }
    }

    private void layout(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String[] segments = segments(request);
        if (segments.length == 0 || !"header".equals(segments[0])) {
            throw ApiException.notFound("Khong tim thay endpoint layout.");
        }
        writeData(response, headerData());
    }

    private Dtos.HeaderDataDto headerData() {
        long now = System.currentTimeMillis();
        Dtos.HeaderDataDto cached = cachedHeader;
        if (cached != null && now - cachedHeaderAt < HEADER_CACHE_TTL_MS) {
            return cached;
        }

        // Dung chung FilmAvailabilityPolicy voi HeaderDataFilter (PU-01). Truoc day cho nay
        // giu MOT BAN SAO cua quy tac phan loai, nen tang JSP va tang Next.js co the hien hai
        // ket qua khac nhau cho cung mot phim. Chi lay 8 phim moi nhom vi mega-menu hien 4.
        List<Film> all = filmDAO.search(null, 0, 100);
        List<Film> nowShowing = new ArrayList<>();
        List<Film> upcoming = new ArrayList<>();
        LocalDate today = BusinessClock.now().toLocalDate();
        int expiringWindow = FilmAvailabilityPolicy.expiringSoonDays();
        for (Film film : all) {
            switch (FilmAvailabilityPolicy.evaluate(film, today, expiringWindow)) {
                case COMING -> upcoming.add(film);
                case SHOWING, EXPIRING_SOON -> nowShowing.add(film);
                case EXPIRED, WITHDRAWN -> { /* khong lo ra API public */ }
                // Da phu het cac gia tri hien co. Nhanh nay chi chay neu ai do them mot
                // giai doan moi vao Availability ma quen cap nhat cho nay — bao ngay con hon
                // de phim roi vao im lang.
                default -> throw new IllegalStateException(
                        "Giai doan vong doi phim chua duoc xu ly: "
                        + FilmAvailabilityPolicy.evaluate(film, today, expiringWindow));
            }
        }
        List<Cinema> cinemas = cinemaDAO.findAll(null);

        Dtos.HeaderDataDto fresh = new Dtos.HeaderDataDto(
                DtoMapper.filmSummaries(nowShowing.stream().limit(8).toList()),
                DtoMapper.filmSummaries(upcoming.stream().limit(8).toList()),
                DtoMapper.cinemas(cinemas));
        cachedHeader = fresh;
        cachedHeaderAt = now;
        return fresh;
    }
}
