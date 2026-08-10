package com.mycompany.website.ban.ve.xem.phim.controller.booking;

import com.mycompany.website.ban.ve.xem.phim.config.AppConstants;
import com.mycompany.website.ban.ve.xem.phim.model.ShowtimeSeat;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.BookingService;
import com.mycompany.website.ban.ve.xem.phim.util.JsonUtil;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ShowtimeSeatServlet extends HttpServlet {
    private static final DateTimeFormatter HOLD_TIME = DateTimeFormatter.ofPattern("HH:mm");
    private final BookingService bookingService = new BookingService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        int showtimeId = parseShowtimeId(request.getPathInfo());
        if (showtimeId < 1) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        User currentUser = (User) request.getSession().getAttribute(AppConstants.SESSION_USER);
        int userId = currentUser == null ? -1 : currentUser.getId();
        List<ShowtimeSeat> seats = bookingService.getSeatMap(showtimeId);
        JsonUtil.write(response, responseBody(seats, userId));
    }

    private int parseShowtimeId(String pathInfo) {
        if (pathInfo == null) {
            return -1;
        }
        String[] segments = pathInfo.split("/");
        if (segments.length != 3 || !"seats".equalsIgnoreCase(segments[2])) {
            return -1;
        }
        try {
            return Integer.parseInt(segments[1]);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    static Map<String, Object> responseBody(List<ShowtimeSeat> seats, int userId) {
        List<Map<String, Object>> items = seats.stream().map(seat -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", seat.getId());
            item.put("seatKey", seat.getSeatKey());
            item.put("rowLabel", seat.getRowLabel());
            item.put("seatNumber", seat.getSeatNumber());
            item.put("seatType", seat.getSeatType());
            item.put("status", seat.getStatus());
            item.put("viewerState", seat.viewerState(userId));
            item.put("selectable", seat.isAvailableFor(userId));
            item.put("heldOrderId", seat.heldOrderIdFor(userId));
            item.put("extraFee", seat.getExtraFee());
            item.put("heldUntil", seat.getHeldUntil() == null ? "" : seat.getHeldUntil().format(HOLD_TIME));
            return item;
        }).toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", false);
        body.put("items", items);
        return body;
    }
}
