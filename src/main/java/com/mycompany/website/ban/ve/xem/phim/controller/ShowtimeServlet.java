package com.mycompany.website.ban.ve.xem.phim.controller;

import com.mycompany.website.ban.ve.xem.phim.dao.CinemaDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.FilmDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.ShowtimeDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcCinemaDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcFilmDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcShowtimeDAO;
import com.mycompany.website.ban.ve.xem.phim.model.Cinema;
import com.mycompany.website.ban.ve.xem.phim.model.Film;
import com.mycompany.website.ban.ve.xem.phim.model.Showtime;
import com.mycompany.website.ban.ve.xem.phim.service.FilmAvailabilityPolicy;
import com.mycompany.website.ban.ve.xem.phim.service.PublicCinemaContext;
import java.io.IOException;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ShowtimeServlet extends HttpServlet {
    private final ShowtimeDAO showtimeDAO = new JdbcShowtimeDAO();
    private final CinemaDAO cinemaDAO = new JdbcCinemaDAO();
    private final FilmDAO filmDAO = new JdbcFilmDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer filmId = parseNullableInt(request.getParameter("filmId"));

        List<Cinema> cinemas = cinemaDAO.findAll(null);
        Integer cinemaId = PublicCinemaContext.resolve(request, cinemas);
        Cinema selectedCinema = null;

        if (cinemaId != null) {
            for (Cinema c : cinemas) {
                if (c.getId() == cinemaId) {
                    selectedCinema = c;
                    break;
                }
            }
        }

        List<Showtime> showtimes = showtimeDAO.findByFilmAndCinema(filmId, cinemaId);
        // EX-01: bo chon phim cua trang lich chieu khong duoc chua phim da het chieu.
        List<Film> showingFilms = FilmAvailabilityPolicy.publicOnly(
                (cinemaId != null && cinemaId > 0) ? filmDAO.findByCinemaId(cinemaId) : filmDAO.search("", 0, 50));

        request.setAttribute("selectedFilmId", filmId);
        request.setAttribute("selectedCinemaId", cinemaId);
        request.setAttribute("selectedCinema", selectedCinema);
        request.setAttribute("cinemas", cinemas);
        request.setAttribute("films", showingFilms);
        request.setAttribute("showtimes", showtimes);

        request.getRequestDispatcher("/WEB-INF/views/showtime/list.jsp").forward(request, response);
    }

    private Integer parseNullableInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
