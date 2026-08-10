package com.mycompany.website.ban.ve.xem.phim.controller;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class SpecialCinemaServlet extends HttpServlet {
    public static class SpecialCinema {
        private String title;
        private String address;
        private String imageUrl;
        private String description;

        public SpecialCinema(String title, String address, String imageUrl, String description) {
            this.title = title;
            this.address = address;
            this.imageUrl = imageUrl;
            this.description = description;
        }

        public String getTitle() { return title; }
        public String getAddress() { return address; }
        public String getImageUrl() { return imageUrl; }
        public String getDescription() { return description; }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setAttribute("activeMenu", "special");
        Integer cinemaId = (Integer) request.getAttribute("publicCinemaId");
        request.setAttribute("lounges", com.mycompany.website.ban.ve.xem.phim.util.CustomContentHelper.getSpecialCinemas(cinemaId));
        request.getRequestDispatcher("/WEB-INF/views/special-cinemas.jsp").forward(request, response);
    }
}
