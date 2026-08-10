package com.mycompany.website.ban.ve.xem.phim.controller.booking;

import com.mycompany.website.ban.ve.xem.phim.config.AppConstants;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.BookingService;
import com.mycompany.website.ban.ve.xem.phim.util.QrCodeUtil;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class TicketQrServlet extends HttpServlet {
    private final BookingService bookingService = new BookingService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User currentUser = (User) request.getSession().getAttribute(AppConstants.SESSION_USER);
        String ticketCode = extractTicketCode(request.getPathInfo());
        if (ticketCode == null || currentUser == null
                || bookingService.findTicketForUser(ticketCode, currentUser.getId()).isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        response.setContentType("image/png");
        QrCodeUtil.writePng(QrCodeUtil.signedPayload(ticketCode), 280, response.getOutputStream());
    }

    private String extractTicketCode(String pathInfo) {
        if (pathInfo == null || pathInfo.length() < 2) {
            return null;
        }
        return pathInfo.substring(1);
    }
}
