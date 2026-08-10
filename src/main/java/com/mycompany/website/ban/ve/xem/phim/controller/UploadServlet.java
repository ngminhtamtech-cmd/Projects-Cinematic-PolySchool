package com.mycompany.website.ban.ve.xem.phim.controller;

import com.mycompany.website.ban.ve.xem.phim.util.ImageUploadUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class UploadServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String pathInfo = request.getPathInfo();
        String fileName = pathInfo == null || pathInfo.length() < 2 ? "" : pathInfo.substring(1);
        String contentType = ImageUploadUtil.contentType(fileName);
        Path directory = uploadDirectory();
        Path file = directory.resolve(fileName).normalize();
        if (contentType == null || !file.getParent().equals(directory) || !Files.isRegularFile(file)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        response.setContentType(contentType);
        response.setHeader("Content-Disposition", "inline; filename=\"" + fileName + "\"");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setContentLengthLong(Files.size(file));
        Files.copy(file, response.getOutputStream());
    }

    /**
     * Noi luu anh upload — luon nam <b>ngoai</b> webroot.
     *
     * <p>Thu tu uu tien giong cach doc cau hinh DB (CB-ISS-009): system property
     * {@code cinebook.upload.dir} → bien moi truong {@code CINEBOOK_UPLOAD_DIR} →
     * {@code ${catalina.base}/cinebook-uploads} → thu muc tam khi chay ngoai Tomcat (test).
     * Khong co duong dan nao cua may lap trinh vien duoc gan cung trong ma nguon.</p>
     */
    public static Path uploadDirectory() {
        String configured = System.getProperty("cinebook.upload.dir");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("CINEBOOK_UPLOAD_DIR");
        }
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured.trim()).toAbsolutePath().normalize();
        }
        String catalinaBase = System.getProperty("catalina.base", System.getProperty("java.io.tmpdir"));
        return Path.of(catalinaBase, "cinebook-uploads").toAbsolutePath().normalize();
    }
}
