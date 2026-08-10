package com.mycompany.website.ban.ve.xem.phim.controller;

import java.io.IOException;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class CineTagServlet extends HttpServlet {
    public static class Product {
        private String name;
        private double price;
        private String imageUrl;

        public Product(String name, double price, String imageUrl) {
            this.name = name;
            this.price = price;
            this.imageUrl = imageUrl;
        }

        public String getName() { return name; }
        public double getPrice() { return price; }
        public String getImageUrl() { return imageUrl; }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer cinemaId = (Integer) request.getAttribute("publicCinemaId");
        List<com.mycompany.website.ban.ve.xem.phim.util.CustomContentHelper.TagInfo> allTags = com.mycompany.website.ban.ve.xem.phim.util.CustomContentHelper.getAllCineTags(cinemaId);

        String tag = request.getParameter("tag");
        if (tag == null || tag.isBlank()) {
            tag = allTags.isEmpty() ? "movie-verse" : allTags.get(0).getSlug();
        }
        tag = tag.toLowerCase().trim();
        
        List<Product> products = com.mycompany.website.ban.ve.xem.phim.util.CustomContentHelper.getCineTagProducts(tag, cinemaId);
        if (products.isEmpty() && !"movie-verse".equals(tag)) {
            boolean tagExists = false;
            for (com.mycompany.website.ban.ve.xem.phim.util.CustomContentHelper.TagInfo t : allTags) {
                if (t.getSlug().equalsIgnoreCase(tag)) {
                    tagExists = true;
                    break;
                }
            }
            if (!tagExists) {
                products = com.mycompany.website.ban.ve.xem.phim.util.CustomContentHelper.getCineTagProducts("movie-verse", cinemaId);
                tag = "movie-verse";
            }
        }

        String displayTag = com.mycompany.website.ban.ve.xem.phim.util.CustomContentHelper.formatTagName(tag);

        request.setAttribute("activeTag", tag);
        request.setAttribute("displayTag", displayTag);
        request.setAttribute("products", products);
        request.setAttribute("allTags", allTags);
        
        // Pass standard side data (quick buy + national now showing)
        try {
            var filmDAO = new com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcFilmDAO();
            var cinemaDAO = new com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcCinemaDAO();
            // N-04: xem chu thich o EventServlet — searchPublic() loc phim het han ngay
            // trong SQL, filmDAO.search() thi khong.
            request.setAttribute("featuredFilms", com.mycompany.website.ban.ve.xem.phim.service
                    .FilmAvailabilityPolicy.publicOnly(filmDAO.searchPublic(null, null, 0, 4)));
            request.setAttribute("cinemas", cinemaDAO.findAll(null));
        } catch (RuntimeException ex) {
            throw new ServletException("Không thể nạp dữ liệu phụ cho trang cinetag.", ex);
        }

        request.getRequestDispatcher("/WEB-INF/views/cinetag.jsp").forward(request, response);
    }
}
