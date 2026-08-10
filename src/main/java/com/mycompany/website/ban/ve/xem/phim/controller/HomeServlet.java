package com.mycompany.website.ban.ve.xem.phim.controller;

import com.mycompany.website.ban.ve.xem.phim.dao.CinemaDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.FilmDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.ShowtimeDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcCinemaDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcFilmDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcShowtimeDAO;
import com.mycompany.website.ban.ve.xem.phim.service.FilmAvailabilityPolicy;
import com.mycompany.website.ban.ve.xem.phim.util.ServletUtil;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class HomeServlet extends HttpServlet {
    private final FilmDAO filmDAO = new JdbcFilmDAO();
    private final CinemaDAO cinemaDAO = new JdbcCinemaDAO();
    private final ShowtimeDAO showtimeDAO = new JdbcShowtimeDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        ServletUtil.consumeFlash(request);
        try {
            // EX-01: trang chu chi hien phim con trong thoi gian chieu.
            var featuredFilms = FilmAvailabilityPolicy.publicOnly(filmDAO.findFeatured(8));
            var allFilms = FilmAvailabilityPolicy.publicOnly(filmDAO.search(null, 0, 100));
            var cinemas = cinemaDAO.findAll(null);
            var showtimes = showtimeDAO.findByFilmAndCinema(null, null);
            request.setAttribute("featuredFilms", featuredFilms);
            request.setAttribute("featuredShowtimes", showtimes.stream().limit(3).toList());
            request.setAttribute("cinemas", cinemas);
            request.setAttribute("showtimes", showtimes);
            request.setAttribute("filmCount", allFilms.size());
            request.setAttribute("cinemaCount", cinemas.size());
            request.setAttribute("showtimeCount", showtimes.size());
            request.setAttribute("dbReady", true);
        } catch (RuntimeException ex) {
            request.setAttribute("featuredFilms", java.util.List.of());
            request.setAttribute("featuredShowtimes", java.util.List.of());
            request.setAttribute("cinemas", java.util.List.of());
            request.setAttribute("showtimes", java.util.List.of());
            request.setAttribute("filmCount", 0);
            request.setAttribute("cinemaCount", 0);
            request.setAttribute("showtimeCount", 0);
            request.setAttribute("dbReady", false);
            request.setAttribute("dbMessage", "Chưa kết nối được SQL Server. Hãy cấu hình db.properties.");
        }
        request.getRequestDispatcher("/WEB-INF/views/home.jsp").forward(request, response);
    }
}
