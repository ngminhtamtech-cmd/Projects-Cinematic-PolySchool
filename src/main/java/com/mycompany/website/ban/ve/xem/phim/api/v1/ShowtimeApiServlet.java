package com.mycompany.website.ban.ve.xem.phim.api.v1;

import com.mycompany.website.ban.ve.xem.phim.api.ApiException;
import com.mycompany.website.ban.ve.xem.phim.api.BaseApiServlet;
import com.mycompany.website.ban.ve.xem.phim.api.dto.DtoMapper;
import com.mycompany.website.ban.ve.xem.phim.dao.ShowtimeDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcShowtimeDAO;
import com.mycompany.website.ban.ve.xem.phim.model.Showtime;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.BookingService;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * /api/v1/showtimes?filmId=&cinemaId=&date=
 * /api/v1/showtimes/{id}
 * /api/v1/showtimes/{id}/booking-eligibility
 * /api/v1/showtimes/{id}/seats
 */
public class ShowtimeApiServlet extends BaseApiServlet {
    private final BookingService bookingService = new BookingService();
    private final ShowtimeDAO showtimeDAO = new JdbcShowtimeDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String[] segments = segments(request);
        if (segments.length == 0) {
            list(request, response);
            return;
        }
        int showtimeId = requireIntSegment(segments, 0, "showtimeId");
        if (segments.length == 1) {
            Showtime showtime = bookingService.findShowtime(showtimeId)
                    .orElseThrow(() -> ApiException.notFound("Khong tim thay suat chieu."));
            writeData(response, DtoMapper.showtime(showtime));
            return;
        }
        if (segments.length == 2 && "booking-eligibility".equals(segments[1])) {
            writeData(response, bookingService.bookingEligibility(showtimeId));
            return;
        }
        if (!"seats".equals(segments[1])) {
            throw ApiException.notFound("Khong tim thay tai nguyen con.");
        }
        if (segments.length == 3 && "version".equals(segments[2])) {
            seatVersion(request, response, showtimeId);
            return;
        }
        if (segments.length != 2) {
            throw ApiException.notFound("Khong tim thay tai nguyen con.");
        }
        seats(request, response, showtimeId);
    }

    private void list(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer filmId = intOrNull(request, "filmId");
        Integer cinemaId = intOrNull(request, "cinemaId");
        LocalDate date = dateOrNull(request, "date");

        List<Showtime> showtimes = showtimeDAO.findByFilmAndCinema(filmId, cinemaId);
        if (date != null) {
            showtimes = showtimes.stream()
                    .filter(st -> st.getStartTime() != null && date.equals(st.getStartTime().toLocalDate()))
                    .toList();
        }
        writeData(response, DtoMapper.showtimes(showtimes));
    }

    private void seats(HttpServletRequest request, HttpServletResponse response, int showtimeId) throws IOException {
        if (bookingService.findShowtime(showtimeId).isEmpty()) {
            throw ApiException.notFound("Khong tim thay suat chieu.");
        }
        User viewer = currentUser(request);
        int viewerId = viewer == null ? -1 : viewer.getId();
        writeData(response, DtoMapper.seats(bookingService.getSeatMap(showtimeId), viewerId));
    }

    private void seatVersion(HttpServletRequest request, HttpServletResponse response, int showtimeId)
            throws IOException {
        if (bookingService.findShowtime(showtimeId).isEmpty()) {
            throw ApiException.notFound("Khong tim thay suat chieu.");
        }
        String version = bookingService.seatMapVersion(showtimeId);
        String etag = '"' + version + '"';
        response.setHeader("ETag", etag);
        response.setHeader("Cache-Control", "private, no-cache");
        if (etag.equals(request.getHeader("If-None-Match"))) {
            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            return;
        }
        writeData(response, Map.of("version", version));
    }
}
