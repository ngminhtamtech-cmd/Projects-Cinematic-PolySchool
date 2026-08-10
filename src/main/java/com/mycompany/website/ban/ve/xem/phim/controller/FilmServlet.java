package com.mycompany.website.ban.ve.xem.phim.controller;

import com.mycompany.website.ban.ve.xem.phim.config.AppConstants;
import com.mycompany.website.ban.ve.xem.phim.dao.CinemaDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.FilmDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.ShowtimeDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcCinemaDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcFilmDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcShowtimeDAO;
import com.mycompany.website.ban.ve.xem.phim.model.Film;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import com.mycompany.website.ban.ve.xem.phim.service.FilmAvailabilityPolicy;
import com.mycompany.website.ban.ve.xem.phim.util.ServletUtil;
import java.io.IOException;
import java.util.Optional;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class FilmServlet extends HttpServlet {
    private final FilmDAO filmDAO = new JdbcFilmDAO();
    private final CinemaDAO cinemaDAO = new JdbcCinemaDAO();
    private final ShowtimeDAO showtimeDAO = new JdbcShowtimeDAO();
    private final AdminService adminService = new AdminService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        ServletUtil.consumeFlash(request);
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || "/".equals(pathInfo)) {
            list(request, response);
            return;
        }
        detail(request, response, pathInfo);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        if (pathInfo == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String[] parts = pathInfo.substring(1).split("/");
        int filmId = parsePositiveInt(parts[0], -1);
        if (filmId < 1 || filmDAO.findById(filmId).isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String action = request.getParameter("action");
        if ("reportComment".equalsIgnoreCase(action)) {
            int commentId = parsePositiveInt(request.getParameter("commentId"), -1);
            String reason = ServletUtil.param(request, "reason");
            User user = (User) request.getSession().getAttribute(AppConstants.SESSION_USER);
            Integer reporterId = user == null ? null : user.getId();
            try {
                adminService.reportComment(commentId, reporterId, reason);
                ServletUtil.flashSuccess(request, "Đã gửi báo cáo vi phạm bình luận tới Quản trị viên.");
            } catch (BookingException ex) {
                ServletUtil.flashError(request, ex.getMessage());
            }
            response.sendRedirect(request.getContextPath() + "/films/" + filmId);
            return;
        }

        if (parts.length != 2 || !"comments".equals(parts[1])) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        User user = (User) request.getSession().getAttribute(AppConstants.SESSION_USER);
        if (user == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        int rate = parsePositiveInt(request.getParameter("rate"), 0);
        String content = ServletUtil.param(request, "content");
        if (rate < 1 || rate > 5) {
            ServletUtil.flashError(request, "Danh gia phai tu 1 den 5 sao.");
            response.sendRedirect(request.getContextPath() + "/films/" + filmId);
            return;
        }
        if (content.isBlank()) {
            ServletUtil.flashError(request, "Vui long nhap noi dung binh luan.");
            response.sendRedirect(request.getContextPath() + "/films/" + filmId);
            return;
        }

        try {
            adminService.addComment(user.getId(), filmId, rate, content);
            ServletUtil.flashSuccess(request, "Da gui danh gia cua ban.");
        } catch (BookingException ex) {
            ServletUtil.flashError(request, ex.getMessage());
        }
        response.sendRedirect(request.getContextPath() + "/films/" + filmId);
    }

    private void list(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String keyword = ServletUtil.param(request, "q");
        int page = parsePositiveInt(request.getParameter("page"), 1);
        int pageSize = 12;
        int offset = (page - 1) * pageSize;

        request.setAttribute("keyword", keyword);
        request.setAttribute("page", page);
        // EX-01: phim da qua EndDate (hoac bi rut) khong duoc nam trong ket qua tim kiem public.
        //
        // N-13: ban cu phan trang TRUOC roi loc SAU —
        //   FilmAvailabilityPolicy.publicOnly(filmDAO.search(keyword, offset, pageSize))
        // DB tra dung 12 dong roi Java moi bo phim het han, nen trang hien 8/12 muc va nhung
        // phim con han bi day sang trang sau KHONG BAO GIO duoc bu lai; tong so trang cung
        // sai. searchPublic()/countSearchPublic() loc ngay trong SQL bang
        // PUBLIC_LIFECYCLE_PREDICATE — dung cap ma api/v1/FilmApiServlet dang dung, nen hai
        // be mat khong con hai quy tac (trieu chung PU-01).
        long totalFilms = filmDAO.countSearchPublic(keyword, null);
        request.setAttribute("films", filmDAO.searchPublic(keyword, null, offset, pageSize));
        request.setAttribute("totalFilms", totalFilms);
        request.setAttribute("totalPages", (int) Math.max(1, (totalFilms + pageSize - 1) / pageSize));
        request.getRequestDispatcher("/WEB-INF/views/film/list.jsp").forward(request, response);
    }

    private void detail(HttpServletRequest request, HttpServletResponse response, String pathInfo)
            throws ServletException, IOException {
        int filmId = parsePositiveInt(pathInfo.substring(1), -1);
        if (filmId < 1) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        Optional<Film> film = filmDAO.findById(filmId);
        if (film.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // EX-01: an khoi danh sach thoi la chua du — URL truc tiep van mo duoc trang dat ve.
        // Phim het chieu tra 410 Gone kem trang giai thich, khong con nut mua ve.
        FilmAvailabilityPolicy.Availability availability = FilmAvailabilityPolicy.evaluate(film.get());
        if (!FilmAvailabilityPolicy.isPubliclyVisible(availability)) {
            response.setStatus(410); // 410 Gone: phim tung ton tai, nay khong con chieu nua
            request.setAttribute("film", film.get());
            request.getRequestDispatcher("/WEB-INF/views/film/ended.jsp").forward(request, response);
            return;
        }

        request.setAttribute("film", film.get());
        request.setAttribute("filmAvailability", availability.name());
        request.setAttribute("cinemas", cinemaDAO.findAll(null));
        request.setAttribute("showtimes", showtimeDAO.findByFilmAndCinema(filmId, null));
        request.setAttribute("comments", adminService.listCommentsByFilm(filmId));
        request.getRequestDispatcher("/WEB-INF/views/film/detail.jsp").forward(request, response);
    }

    private int parsePositiveInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
