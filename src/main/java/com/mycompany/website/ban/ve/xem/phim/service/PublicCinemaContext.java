package com.mycompany.website.ban.ve.xem.phim.service;

import com.mycompany.website.ban.ve.xem.phim.model.Cinema;
import java.util.List;
import javax.servlet.http.HttpServletRequest;

/** Persists the public visitor's cinema choice for cinema-owned content. */
public final class PublicCinemaContext {
    public static final String SESSION_CINEMA_ID = "publicCinemaContextId";

    private PublicCinemaContext() {
    }

    public static Integer resolve(HttpServletRequest request, List<Cinema> cinemas) {
        Integer requested = parse(request.getParameter("cinemaId"));
        Integer selected = requested != null ? requested
                : request.getSession().getAttribute(SESSION_CINEMA_ID) instanceof Integer id ? id : null;
        Cinema cinema = find(cinemas, selected);
        if (requested != null && cinema == null) {
            throw new BookingException(404, "Không tìm thấy rạp đã chọn.");
        }
        if (cinema == null && cinemas != null && !cinemas.isEmpty()) cinema = cinemas.get(0);
        if (cinema == null) {
            request.setAttribute("publicCinemaId", null);
            request.setAttribute("publicCinemaName", "Chưa có rạp");
            return null;
        }
        request.getSession().setAttribute(SESSION_CINEMA_ID, cinema.getId());
        request.setAttribute("publicCinemaId", cinema.getId());
        request.setAttribute("publicCinemaName", cinema.getName());
        return cinema.getId();
    }

    public static void remember(HttpServletRequest request, int cinemaId) {
        if (cinemaId > 0) request.getSession().setAttribute(SESSION_CINEMA_ID, cinemaId);
    }

    private static Cinema find(List<Cinema> cinemas, Integer id) {
        if (cinemas == null || id == null) return null;
        return cinemas.stream().filter(cinema -> cinema.getId() == id).findFirst().orElse(null);
    }

    private static Integer parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            int value = Integer.parseInt(raw);
            return value > 0 ? value : null;
        } catch (NumberFormatException ex) {
            throw new BookingException(400, "Rạp đã chọn không hợp lệ.");
        }
    }
}
