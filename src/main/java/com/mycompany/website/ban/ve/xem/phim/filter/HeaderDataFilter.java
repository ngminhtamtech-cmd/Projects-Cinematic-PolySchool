package com.mycompany.website.ban.ve.xem.phim.filter;

import com.mycompany.website.ban.ve.xem.phim.dao.CinemaDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.FilmDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcCinemaDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcFilmDAO;
import com.mycompany.website.ban.ve.xem.phim.model.Cinema;
import com.mycompany.website.ban.ve.xem.phim.model.Film;
import com.mycompany.website.ban.ve.xem.phim.service.FilmAvailabilityPolicy;
import com.mycompany.website.ban.ve.xem.phim.service.PublicCinemaContext;
import com.mycompany.website.ban.ve.xem.phim.util.BusinessClock;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.atomic.AtomicReference;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

public class HeaderDataFilter implements Filter {
    private static final Logger LOGGER = Logger.getLogger(HeaderDataFilter.class.getName());
    private static final long CACHE_TTL_MS = 60_000L;
    private static final AtomicReference<CachedHeader> CACHE = new AtomicReference<>();
    private final FilmDAO filmDAO = new JdbcFilmDAO();
    private final CinemaDAO cinemaDAO = new JdbcCinemaDAO();

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        String path = request.getRequestURI().substring(request.getContextPath().length());

        if (shouldLoadHeader(request.getMethod(), path)) {
            List<Cinema> publicCinemas = List.of();
            try {
                CachedHeader header = cachedHeader();
                request.setAttribute("headerNowShowingFilms", header.nowShowing);
                request.setAttribute("headerUpcomingFilms", header.upcoming);
                request.setAttribute("headerCinemas", header.cinemas);
                publicCinemas = header.cinemas;
            } catch (RuntimeException ex) {
                LOGGER.log(Level.WARNING, "Khong the nap du lieu header; dung danh sach rong", ex);
                // If DB is not ready or configured, set empty lists so header doesn't crash
                request.setAttribute("headerNowShowingFilms", List.of());
                request.setAttribute("headerUpcomingFilms", List.of());
                request.setAttribute("headerCinemas", List.of());
            }
            PublicCinemaContext.resolve(request, publicCinemas);
        }

        chain.doFilter(servletRequest, servletResponse);
    }

    public static void invalidate() {
        CACHE.set(null);
    }

    private CachedHeader cachedHeader() {
        long now = System.currentTimeMillis();
        CachedHeader current = CACHE.get();
        if (current != null && now - current.loadedAt < CACHE_TTL_MS) return current;
        synchronized (CACHE) {
            current = CACHE.get();
            if (current != null && now - current.loadedAt < CACHE_TTL_MS) return current;
            // Phan loai bang FilmAvailabilityPolicy (PU-01): truoc day cho nay tu so ReleaseDate
            // voi LocalDate.now() cua JVM, con Catalog API lai co ban sao rieng cua cung doan do.
            // Hai noi de troi khoi nhau, va ca hai deu khong biet toi EndDate nen phim het chieu
            // van nam trong mega-menu.
            List<Film> allFilms = filmDAO.search(null, 0, 100);
            List<Film> showing = new ArrayList<>();
            List<Film> upcoming = new ArrayList<>();
            LocalDate today = BusinessClock.now().toLocalDate();
            int expiringWindow = FilmAvailabilityPolicy.expiringSoonDays();
            for (Film film : allFilms) {
                switch (FilmAvailabilityPolicy.evaluate(film, today, expiringWindow)) {
                    case COMING -> upcoming.add(film);
                    case SHOWING, EXPIRING_SOON -> showing.add(film);
                    case EXPIRED, WITHDRAWN -> { /* khong hien tren be mat public */ }
                    // Xem ghi chu cung cho o CatalogApiServlet: nhanh nay bao loi neu
                    // Availability co them gia tri moi ma quen xu ly.
                    default -> throw new IllegalStateException(
                            "Giai doan vong doi phim chua duoc xu ly: "
                            + FilmAvailabilityPolicy.evaluate(film, today, expiringWindow));
                }
            }
            current = new CachedHeader(List.copyOf(showing), List.copyOf(upcoming),
                    List.copyOf(cinemaDAO.findAll(null)), now);
            CACHE.set(current);
            return current;
        }
    }

    static boolean shouldLoadHeader(String method, String path) {
        if (!"GET".equalsIgnoreCase(method) || isStaticResource(path)) return false;
        return !(path.startsWith("/api/") || path.startsWith("/admin/")
                || path.startsWith("/system/") || path.startsWith("/staff/"));
    }

    private static boolean isStaticResource(String path) {
        String lower = path.toLowerCase();
        return lower.endsWith(".css") || lower.endsWith(".js") || lower.endsWith(".png") 
            || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".svg") 
            || lower.endsWith(".gif") || lower.endsWith(".ico") || lower.endsWith(".woff") 
            || lower.endsWith(".woff2") || lower.endsWith(".ttf");
    }

    @Override
    public void destroy() {
    }

    private record CachedHeader(List<Film> nowShowing, List<Film> upcoming,
                                List<Cinema> cinemas, long loadedAt) {}
}
