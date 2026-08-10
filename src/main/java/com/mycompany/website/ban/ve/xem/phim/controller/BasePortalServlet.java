package com.mycompany.website.ban.ve.xem.phim.controller;

import com.mycompany.website.ban.ve.xem.phim.config.AppConstants;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import com.mycompany.website.ban.ve.xem.phim.util.ServletUtil;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public abstract class BasePortalServlet extends HttpServlet {
    protected User currentUser(HttpServletRequest request) {
        return (User) request.getSession().getAttribute(AppConstants.SESSION_USER);
    }

    protected void forward(HttpServletRequest request, HttpServletResponse response, String jsp)
            throws ServletException, IOException {
        ServletUtil.consumeFlash(request);
        request.getRequestDispatcher(jsp).forward(request, response);
    }

    protected int intParam(HttpServletRequest request, String name, int fallback) {
        try {
            return Integer.parseInt(ServletUtil.param(request, name));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    protected Integer nullableIntParam(HttpServletRequest request, String name) {
        String raw = ServletUtil.param(request, name);
        if (raw.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    protected Double nullableDoubleParam(HttpServletRequest request, String name) {
        String raw = ServletUtil.param(request, name);
        if (raw.isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    protected BigDecimal decimalParam(HttpServletRequest request, String name, BigDecimal fallback) {
        String raw = ServletUtil.param(request, name);
        if (raw.isBlank()) {
            return fallback;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    protected LocalDate nullableDateParam(HttpServletRequest request, String name) {
        String raw = ServletUtil.param(request, name);
        if (raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    protected LocalDateTime nullableDateTimeParam(HttpServletRequest request, String name) {
        String raw = ServletUtil.param(request, name);
        if (raw.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    protected void redirectBack(HttpServletRequest request, HttpServletResponse response, String fallback)
            throws IOException {
        response.sendRedirect(ServletUtil.internalPathOrFallback(
                request, request.getHeader("Referer"), fallback));
    }

    /**
     * Tra trang loi cho mot {@link BookingException} tren luong GET.
     *
     * <p><b>Hai loi da sua o day (HTTP-01, OBS-01).</b></p>
     * <ul>
     *   <li>Ban cu forward thang toi {@code 500.jsp} ma khong goi
     *       {@code response.setStatus(...)}. Servlet container giu nguyen 200 mac dinh, nen
     *       trang hien chu "500" trong khi curl, uptime monitor va route baseline deu doc
     *       duoc HTTP 200 — loi that bi che hoan toan. Day chinh la ly do trang Combo va
     *       Bao cao "xanh" trong lan quet truoc du ca hai deu hong.</li>
     *   <li>Nguyen nhan goc ({@code SQLException}) bi bo di, log chi con message chung nen
     *       khong biet cot nao sai. Gio ghi log kem correlation ID + duong dan; nguoi dung
     *       chi thay ma tham chieu, khong thay stack trace.</li>
     * </ul>
     *
     * @return ma correlation de hien cho nguoi dung doi chieu voi log
     */
    protected String renderError(HttpServletRequest request, HttpServletResponse response,
                                BookingException ex, java.util.logging.Logger logger)
            throws ServletException, IOException {
        int status = ex.getStatusCode() >= 400 && ex.getStatusCode() <= 599
                ? ex.getStatusCode()
                : HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        String correlationId = Long.toHexString(java.util.concurrent.ThreadLocalRandom.current().nextLong() & 0xFFFFFFFFFFFFL);

        if (status >= 500) {
            logger.log(java.util.logging.Level.SEVERE,
                    ex, () -> "[" + correlationId + "] " + status + " tren " + request.getMethod()
                            + " " + request.getServletPath()
                            + (request.getQueryString() == null ? "" : "?" + request.getQueryString()));
        } else {
            logger.log(java.util.logging.Level.FINE, () -> "[" + correlationId + "] " + status
                    + " tren " + request.getServletPath() + ": " + ex.getMessage());
        }

        response.setStatus(status);
        request.setAttribute("flashError", status >= 500
                ? "Hệ thống đang gặp lỗi vận hành. Vui lòng thử lại sau. Mã tham chiếu: " + correlationId
                : ex.getMessage());
        request.setAttribute("errorCorrelationId", correlationId);
        request.setAttribute("errorStatus", status);
        forward(request, response, errorViewFor(status));
        return correlationId;
    }

    /** Chon trang loi tuong ung; moi ma khong co trang rieng dung 500.jsp. */
    private String errorViewFor(int status) {
        return switch (status) {
            case HttpServletResponse.SC_UNAUTHORIZED -> "/WEB-INF/views/error/401.jsp";
            case HttpServletResponse.SC_FORBIDDEN -> "/WEB-INF/views/error/403.jsp";
            case HttpServletResponse.SC_NOT_FOUND -> "/WEB-INF/views/error/404.jsp";
            default -> "/WEB-INF/views/error/500.jsp";
        };
    }
}
