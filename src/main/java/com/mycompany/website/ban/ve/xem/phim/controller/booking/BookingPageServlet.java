package com.mycompany.website.ban.ve.xem.phim.controller.booking;

import com.mycompany.website.ban.ve.xem.phim.service.BookingService;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import com.mycompany.website.ban.ve.xem.phim.util.ServletUtil;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class BookingPageServlet extends HttpServlet {
    private final BookingService bookingService = new BookingService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        ServletUtil.consumeFlash(request);
        int showtimeId = parseInt(request.getParameter("showtimeId"));
        try {
            request.setAttribute("showtime", bookingService.requireBookableShowtime(showtimeId));
        } catch (BookingException ex) {
            ServletUtil.flashError(request, ex.getMessage());
        }

        var filmDAO = new com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcFilmDAO();
        var cinemaDAO = new com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcCinemaDAO();
        var showtimeDAO = new com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcShowtimeDAO();

        var cinemas = cinemaDAO.findAll(null);
        java.util.Set<Integer> seenCityIds = new java.util.HashSet<>();
        var cities = new java.util.ArrayList<com.mycompany.website.ban.ve.xem.phim.model.Cinema>();
        for (var c : cinemas) {
            if (seenCityIds.add(c.getCityId())) {
                cities.add(c);
            }
        }

        // EX-01: bo chon phim cua trang dat ve khong duoc chua phim da het chieu.
        request.setAttribute("films",
                com.mycompany.website.ban.ve.xem.phim.service.FilmAvailabilityPolicy
                        .publicOnly(filmDAO.search(null, 0, 100)));
        request.setAttribute("cinemas", cinemas);
        request.setAttribute("cities", cities);
        request.setAttribute("showtimes", showtimeDAO.findByFilmAndCinema(null, null));
        // CB-01: chi hien combo ban duoc tai rap cua suat chieu nay.
        request.setAttribute("combos", bookingService.findActiveCombosForShowtime(showtimeId));
        // CB-ISS-005: han giu ghe chi duoc dinh nghia mot noi. Trinh duyet luon uu tien
        // remainingSeconds do DB tinh; gia tri nay chi la duong lui khi khong goi duoc API,
        // va no lay tu chinh hang so cua server chu khong hard-code trong JavaScript.
        request.setAttribute("holdMinutes", BookingService.holdMinutes());
        
        request.getRequestDispatcher("/WEB-INF/views/booking/page.jsp").forward(request, response);
    }

    private int parseInt(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }
}
