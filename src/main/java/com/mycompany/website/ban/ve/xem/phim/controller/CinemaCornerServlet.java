package com.mycompany.website.ban.ve.xem.phim.controller;

import java.io.IOException;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class CinemaCornerServlet extends HttpServlet {
    public static class CornerItem {
        private String title;
        private String subtitle;
        private String imageUrl;
        private String description;
        private int likes;
        private int views;
        private String prefix; // Used for reviews "[Review]" or "[Preview]"

        public CornerItem(String title, String subtitle, String imageUrl, String description, int likes, int views) {
            this(title, subtitle, imageUrl, description, likes, views, null);
        }

        public CornerItem(String title, String subtitle, String imageUrl, String description, int likes, int views, String prefix) {
            this.title = title;
            this.subtitle = subtitle;
            this.imageUrl = imageUrl;
            this.description = description;
            this.likes = likes;
            this.views = views;
            this.prefix = prefix;
        }

        public String getTitle() { return title; }
        public String getSubtitle() { return subtitle; }
        public String getImageUrl() { return imageUrl; }
        public String getDescription() { return description; }
        public int getLikes() { return likes; }
        public int getViews() { return views; }
        public String getPrefix() { return prefix; }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String section = "the-loai";
        
        if (path.contains("/dien-vien")) {
            section = "dien-vien";
        } else if (path.contains("/dao-dien")) {
            section = "dao-dien";
        } else if (path.contains("/binh-luan")) {
            section = "binh-luan";
        } else if (path.contains("/blog")) {
            section = "blog";
        }

        Integer cinemaId = (Integer) request.getAttribute("publicCinemaId");
        List<CornerItem> items = com.mycompany.website.ban.ve.xem.phim.util.CustomContentHelper.getCornerItems(section, cinemaId);

        String displaySection = switch (section) {
            case "dien-vien" -> "DIỄN VIÊN";
            case "dao-dien" -> "ĐẠO DIỄN";
            case "binh-luan" -> "BÌNH LUẬN PHIM";
            case "blog" -> "BLOG ĐIỆN ẢNH";
            default -> "THỂ LOẠI PHIM";
        };

        String breadcrumb = "Trang chủ / Góc Điện Ảnh / " + switch (section) {
            case "dien-vien" -> "Diễn viên";
            case "dao-dien" -> "Đạo diễn";
            case "binh-luan" -> "Bình luận phim";
            case "blog" -> "Blog điện ảnh";
            default -> "Thể loại phim";
        };

        request.setAttribute("section", section);
        request.setAttribute("displaySection", displaySection);
        request.setAttribute("breadcrumb", breadcrumb);
        request.setAttribute("items", items);

        // Sidebar data
        try {
            var filmDAO = new com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcFilmDAO();
            var cinemaDAO = new com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcCinemaDAO();
            var showtimeDAO = new com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcShowtimeDAO();
            
            // N-04: xem chu thich o EventServlet — searchPublic() loc phim het han ngay
            // trong SQL, filmDAO.search() thi khong.
            request.setAttribute("featuredFilms", com.mycompany.website.ban.ve.xem.phim.service
                    .FilmAvailabilityPolicy.publicOnly(filmDAO.searchPublic(null, null, 0, 4)));
            request.setAttribute("cinemas", cinemaDAO.findAll(null));
            request.setAttribute("showtimes", showtimeDAO.findByFilmAndCinema(null, null));
        } catch (RuntimeException ex) {
            throw new ServletException("Không thể nạp dữ liệu phụ cho góc điện ảnh.", ex);
        }

        request.getRequestDispatcher("/WEB-INF/views/cinema-corner.jsp").forward(request, response);
    }
}
