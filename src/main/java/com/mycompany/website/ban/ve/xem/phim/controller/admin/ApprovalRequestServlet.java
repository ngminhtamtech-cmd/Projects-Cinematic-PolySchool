package com.mycompany.website.ban.ve.xem.phim.controller.admin;

import com.mycompany.website.ban.ve.xem.phim.controller.BasePortalServlet;
import com.mycompany.website.ban.ve.xem.phim.controller.UploadServlet;
import com.mycompany.website.ban.ve.xem.phim.model.Cinema;
import com.mycompany.website.ban.ve.xem.phim.model.Film;
import com.mycompany.website.ban.ve.xem.phim.model.Room;
import com.mycompany.website.ban.ve.xem.phim.model.Seat;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
import com.mycompany.website.ban.ve.xem.phim.service.ApprovalService;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import com.mycompany.website.ban.ve.xem.phim.service.CinemaCapabilityPolicy;
import com.mycompany.website.ban.ve.xem.phim.service.CinemaContextResolver;
import com.mycompany.website.ban.ve.xem.phim.util.ImageUploadUtil;
import com.mycompany.website.ban.ve.xem.phim.util.ServletUtil;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

/** Minimal request queue shared by managers and system administrators. */
@MultipartConfig(
        fileSizeThreshold = 2 * 1024 * 1024,
        maxFileSize = 5 * 1024 * 1024,
        maxRequestSize = 15 * 1024 * 1024)
public class ApprovalRequestServlet extends BasePortalServlet {
    private static final Logger LOGGER = Logger.getLogger(ApprovalRequestServlet.class.getName());
    private final ApprovalService approvalService = new ApprovalService();
    private final AdminService adminService = new AdminService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User actor = currentUser(request);
        try {
            List<Cinema> cinemas = adminService.listCinemas();
            Integer cinemaContext = CinemaContextResolver.prepare(request, actor, cinemas);
            request.setAttribute("unreadNotificationCount", adminService.getUnreadNotificationCount(actor));
            String action = ServletUtil.param(request, "action");
            if ("film".equals(action)) {
                int managerCinema = CinemaCapabilityPolicy.requireManagerCinema(actor);
                request.setAttribute("films", adminService.listFilms());
                request.setAttribute("assignedFilmIds", adminService.getFilmIdsByCinemaId(managerCinema));
                request.setAttribute("categories", approvalService.listCategories());
                Integer filmId = nullableIntParam(request, "filmId");
                if (filmId != null && filmId > 0) {
                    request.setAttribute("film", adminService.findFilmById(filmId, actor)
                            .orElseThrow(() -> new BookingException(404,
                                    "Phim không thuộc rạp của bạn hoặc không tồn tại.")));
                    request.setAttribute("selectedCategoryIds", approvalService.filmCategoryIds(filmId));
                } else {
                    request.setAttribute("film", new Film());
                    request.setAttribute("selectedCategoryIds", List.of());
                }
                forward(request, response, "/WEB-INF/views/admin/request-film.jsp");
                return;
            }
            if ("room".equals(action)) {
                CinemaCapabilityPolicy.requireManagerCinema(actor);
                forward(request, response, "/WEB-INF/views/admin/request-room.jsp");
                return;
            }
            request.setAttribute("statusFilter", ServletUtil.param(request, "status"));
            request.setAttribute("requests", approvalService.listRequests(
                    actor, ServletUtil.param(request, "status"), cinemaContext));
            forward(request, response, "/WEB-INF/views/admin/requests.jsp");
        } catch (BookingException ex) {
            renderError(request, response, ex, LOGGER);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User actor = currentUser(request);
        List<String> uploadedMedia = new ArrayList<>();
        try {
            String action = ServletUtil.param(request, "action");
            switch (action) {
                case "assign-film" -> {
                    approvalService.requestFilmAssignment(intParam(request, "filmId", 0), actor);
                    ServletUtil.flashSuccess(request, "Đã gửi yêu cầu gán phim cho admin duyệt.");
                }
                case "unassign-film" -> {
                    approvalService.requestFilmUnassignment(intParam(request, "filmId", 0), actor);
                    ServletUtil.flashSuccess(request, "Đã gửi yêu cầu gỡ phim cho admin duyệt.");
                }
                case "create-film", "update-film" -> {
                    Film film = readFilm(request, uploadedMedia);
                    List<Integer> categoryIds = readPositiveIds(request.getParameterValues("categoryIds"));
                    if ("update-film".equals(action)) {
                        approvalService.requestFilmUpdate(film, categoryIds, actor);
                        ServletUtil.flashSuccess(request, "Đã gửi yêu cầu cập nhật phim.");
                    } else {
                        approvalService.requestFilmCreation(film, categoryIds, actor);
                        ServletUtil.flashSuccess(request, "Đã gửi đề xuất phim mới.");
                    }
                }
                case "create-room" -> {
                    Room room = new Room();
                    room.setName(ServletUtil.param(request, "name"));
                    room.setRoomType(ServletUtil.param(request, "roomType"));
                    int rows = intParam(request, "layoutRows", 0);
                    int seatsPerRow = intParam(request, "seatsPerRow", 0);
                    List<Seat> seats = buildSeatLayout(request, rows, seatsPerRow);
                    approvalService.requestRoomCreation(room, rows, seatsPerRow, seats, actor);
                    ServletUtil.flashSuccess(request, "Đã gửi phòng và sơ đồ ghế cho admin duyệt.");
                }
                case "approve" -> {
                    approvalService.approve(intParam(request, "id", 0),
                            nullableIntParam(request, "duplicateFilmId"),
                            ServletUtil.param(request, "reviewNote"), actor);
                    ServletUtil.flashSuccess(request, "Đã duyệt yêu cầu và áp dụng dữ liệu.");
                }
                case "reject" -> {
                    approvalService.reject(intParam(request, "id", 0),
                            ServletUtil.param(request, "reviewNote"), actor);
                    ServletUtil.flashSuccess(request, "Đã từ chối yêu cầu.");
                }
                case "cancel" -> {
                    approvalService.cancel(intParam(request, "id", 0), actor);
                    ServletUtil.flashSuccess(request, "Đã hủy yêu cầu đang chờ.");
                }
                default -> throw new BookingException(400, "Hành động phê duyệt không hợp lệ.");
            }
            response.setStatus(HttpServletResponse.SC_SEE_OTHER);
            response.setHeader("Location", request.getContextPath() + "/admin/requests");
        } catch (BookingException ex) {
            cleanupUploads(uploadedMedia);
            ServletUtil.flashError(request, ex.getStatusCode() >= 500
                    ? "Hệ thống đang gặp lỗi vận hành. Vui lòng thử lại sau."
                    : ex.getMessage());
            redirectBack(request, response, "/admin/requests");
        } catch (RuntimeException ex) {
            cleanupUploads(uploadedMedia);
            throw ex;
        }
    }

    private Film readFilm(HttpServletRequest request, List<String> uploadedMedia)
            throws ServletException, IOException {
        Film film = new Film();
        film.setId(intParam(request, "filmId", 0));
        film.setTitle(ServletUtil.param(request, "title"));
        film.setOtherTitles(ServletUtil.param(request, "otherTitles"));
        film.setActors(ServletUtil.param(request, "actors"));
        film.setDirectors(ServletUtil.param(request, "directors"));
        film.setRating(nullableDoubleParam(request, "rating"));
        film.setReleaseDate(nullableDateParam(request, "releaseDate"));
        film.setEndDate(nullableDateParam(request, "endDate"));
        film.setDurationMinutes(nullableIntParam(request, "durationMinutes"));
        film.setAgeRating(ServletUtil.param(request, "ageRating"));
        film.setTrailerUrl(ServletUtil.param(request, "trailerUrl"));
        film.setLanguage(ServletUtil.param(request, "language"));
        film.setSubtitles(ServletUtil.param(request, "subtitles"));
        film.setDescription(ServletUtil.param(request, "description"));
        film.setCountry(ServletUtil.param(request, "country"));
        film.setFormat(ServletUtil.param(request, "format"));
        film.setStatus(ServletUtil.param(request, "status"));
        String thumbnail = storeUpload(request, "thumbnailFile");
        if (thumbnail == null) thumbnail = ServletUtil.param(request, "thumbnail");
        else uploadedMedia.add(thumbnail);
        film.setThumbnail(thumbnail);
        String banner = storeUpload(request, "bannerFile");
        if (banner == null) banner = ServletUtil.param(request, "banner");
        else uploadedMedia.add(banner);
        film.setBanner(banner);
        return film;
    }

    private String storeUpload(HttpServletRequest request, String partName)
            throws ServletException, IOException {
        if (request.getContentType() == null
                || !request.getContentType().toLowerCase(Locale.ROOT).startsWith("multipart/")) {
            return null;
        }
        Part part = request.getPart(partName);
        if (part == null || part.getSize() == 0) return null;
        try (java.io.InputStream input = part.getInputStream()) {
            return "/uploads/" + ImageUploadUtil.validateAndStore(input,
                    part.getSubmittedFileName(), part.getSize(), UploadServlet.uploadDirectory());
        }
    }

    private List<Integer> readPositiveIds(String[] values) {
        if (values == null) return List.of();
        List<Integer> ids = new ArrayList<>();
        for (String value : values) {
            try {
                int id = Integer.parseInt(value);
                if (id <= 0) throw new NumberFormatException();
                ids.add(id);
            } catch (NumberFormatException ex) {
                throw new BookingException(400, "Thể loại phim không hợp lệ.");
            }
        }
        return ids;
    }

    private List<Seat> buildSeatLayout(HttpServletRequest request, int rows, int seatsPerRow) {
        Set<String> vipRows = parseRows(ServletUtil.param(request, "vipRows"));
        Set<String> coupleRows = parseRows(ServletUtil.param(request, "coupleRows"));
        BigDecimal vipSurcharge = decimalParam(request, "vipSurcharge", BigDecimal.ZERO);
        BigDecimal coupleSurcharge = decimalParam(request, "coupleSurcharge", BigDecimal.ZERO);
        List<Seat> seats = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < rows && rowIndex < 26; rowIndex++) {
            String row = String.valueOf((char) ('A' + rowIndex));
            String type = coupleRows.contains(row) ? "couple" : vipRows.contains(row) ? "vip" : "standard";
            BigDecimal surcharge = "couple".equals(type) ? coupleSurcharge
                    : "vip".equals(type) ? vipSurcharge : BigDecimal.ZERO;
            for (int number = 1; number <= seatsPerRow && number <= 50; number++) {
                seats.add(new Seat(0, row, number, type, row + number, surcharge));
            }
        }
        return seats;
    }

    private Set<String> parseRows(String csv) {
        Set<String> rows = new HashSet<>();
        if (csv == null || csv.isBlank()) return rows;
        for (String value : csv.split(",")) {
            String row = value.trim().toUpperCase(Locale.ROOT);
            if (!row.matches("[A-Z]")) {
                throw new BookingException(400, "Mỗi hàng ghế phải là một chữ cái A–Z.");
            }
            rows.add(row);
        }
        return rows;
    }

    private void cleanupUploads(List<String> storedPaths) {
        for (String storedPath : storedPaths) {
            try {
                java.nio.file.Path directory = UploadServlet.uploadDirectory().toAbsolutePath().normalize();
                java.nio.file.Path file = directory.resolve(
                        java.nio.file.Path.of(storedPath).getFileName()).normalize();
                if (file.getParent().equals(directory)) java.nio.file.Files.deleteIfExists(file);
            } catch (IOException | RuntimeException ex) {
                LOGGER.log(Level.WARNING, "Không thể dọn tệp của yêu cầu bị lỗi", ex);
            }
        }
    }
}
