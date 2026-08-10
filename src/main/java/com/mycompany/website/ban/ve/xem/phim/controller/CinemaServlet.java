package com.mycompany.website.ban.ve.xem.phim.controller;

import com.mycompany.website.ban.ve.xem.phim.dao.CinemaDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcCinemaDAO;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class CinemaServlet extends HttpServlet {
    private final CinemaDAO cinemaDAO = new JdbcCinemaDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer cityId = parseNullableInt(request.getParameter("cityId"));
        request.setAttribute("selectedCityId", cityId);
        request.setAttribute("cinemas", cinemaDAO.findAll(cityId));
        request.getRequestDispatcher("/WEB-INF/views/cinema/list.jsp").forward(request, response);
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
