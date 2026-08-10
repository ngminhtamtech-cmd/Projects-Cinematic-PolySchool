package com.mycompany.website.ban.ve.xem.phim.controller;

import java.io.IOException;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class EventServlet extends HttpServlet {
    public static class EventItem {
        private String title;
        private String imageUrl;
        private String targetUrl;
        private Integer filmId;
        private String description;
        private String section;
        private String releaseDate;
        private String endDate;
        private String directors;

        public EventItem(String title, String imageUrl) {
            this(title, imageUrl, null, null, null, null, null, null, null);
        }

        public EventItem(String title, String imageUrl, String targetUrl, Integer filmId, String description, String section) {
            this(title, imageUrl, targetUrl, filmId, description, section, null, null, null);
        }

        public EventItem(String title, String imageUrl, String targetUrl, Integer filmId, String description, String section, String releaseDate, String endDate, String directors) {
            this.title = title;
            this.imageUrl = imageUrl;
            this.targetUrl = targetUrl;
            this.filmId = filmId;
            this.description = description;
            this.section = section;
            this.releaseDate = releaseDate;
            this.endDate = endDate;
            this.directors = directors;
        }

        public String getTitle() { return title; }
        public String getImageUrl() { return imageUrl; }
        public String getTargetUrl() { return targetUrl; }
        public Integer getFilmId() { return filmId; }
        public String getDescription() { return description; }
        public String getSection() { return section; }
        public String getReleaseDate() { return releaseDate; }
        public String getEndDate() { return endDate; }
        public String getDirectors() { return directors; }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String section = "uu-dai";
        
        if (path.contains("/phim-hay")) {
            section = "phim-hay";
        }

        Integer cinemaId = (Integer) request.getAttribute("publicCinemaId");
        List<EventItem> events = com.mycompany.website.ban.ve.xem.phim.util.CustomContentHelper.getEventItems(section, cinemaId);

        request.setAttribute("section", section);
        request.setAttribute("events", events);
        request.setAttribute("displaySection", section.equals("phim-hay") ? "PHIM HAY THÁNG" : "ƯU ĐÃI");

        // Sidebar data
        try {
            var filmDAO = new com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcFilmDAO();
            var cinemaDAO = new com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcCinemaDAO();
            var showtimeDAO = new com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcShowtimeDAO();
            
            // N-04: dai "Phim noi bat" o day tung dung filmDAO.search() — cau khong co
            // PUBLIC_LIFECYCLE_PREDICATE va khong di qua FilmAvailabilityPolicy — nen phim
            // da qua EndDate van hien, bam vao ra trang 410. searchPublic() loc ngay trong
            // SQL, dung cap DAO ma FilmApiServlet dang dung.
            request.setAttribute("featuredFilms", com.mycompany.website.ban.ve.xem.phim.service
                    .FilmAvailabilityPolicy.publicOnly(filmDAO.searchPublic(null, null, 0, 4)));
            request.setAttribute("cinemas", cinemaDAO.findAll(null));
            request.setAttribute("showtimes", showtimeDAO.findByFilmAndCinema(null, null));
        } catch (RuntimeException ex) {
            throw new ServletException("Không thể nạp dữ liệu phụ cho trang sự kiện.", ex);
        }

        request.getRequestDispatcher("/WEB-INF/views/events.jsp").forward(request, response);
    }
}
