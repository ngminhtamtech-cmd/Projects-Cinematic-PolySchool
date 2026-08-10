package com.mycompany.website.ban.ve.xem.phim.controller.admin;

import com.mycompany.website.ban.ve.xem.phim.config.AppConstants;
import com.mycompany.website.ban.ve.xem.phim.controller.BasePortalServlet;
import com.mycompany.website.ban.ve.xem.phim.controller.UploadServlet;
import com.mycompany.website.ban.ve.xem.phim.model.Cinema;
import com.mycompany.website.ban.ve.xem.phim.model.ComboFood;
import com.mycompany.website.ban.ve.xem.phim.model.Film;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.model.PageResult;
import com.mycompany.website.ban.ve.xem.phim.model.Promotion;
import com.mycompany.website.ban.ve.xem.phim.model.Room;
import com.mycompany.website.ban.ve.xem.phim.model.Seat;
import com.mycompany.website.ban.ve.xem.phim.model.Showtime;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
import com.mycompany.website.ban.ve.xem.phim.service.AppealResolutionResult;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import com.mycompany.website.ban.ve.xem.phim.service.CinemaContextResolver;
import com.mycompany.website.ban.ve.xem.phim.service.CinemaCapabilityPolicy;
import com.mycompany.website.ban.ve.xem.phim.service.CinemaContentService;
import com.mycompany.website.ban.ve.xem.phim.service.FilmDeleteImpact;
import com.mycompany.website.ban.ve.xem.phim.service.FilmDeletionMode;
import com.mycompany.website.ban.ve.xem.phim.service.FilmDeletionOutcome;
import com.mycompany.website.ban.ve.xem.phim.service.PolicyDocumentService;
import com.mycompany.website.ban.ve.xem.phim.service.PromotionDeleteImpact;
import com.mycompany.website.ban.ve.xem.phim.service.SeatLayoutPreview;
import com.mycompany.website.ban.ve.xem.phim.service.ShowtimeChangeImpact;
import com.mycompany.website.ban.ve.xem.phim.service.ShowtimeDeletionImpact;
import com.mycompany.website.ban.ve.xem.phim.model.AdminNotification;
import com.mycompany.website.ban.ve.xem.phim.util.BusinessClock;
import com.mycompany.website.ban.ve.xem.phim.util.JsonUtil;
import com.mycompany.website.ban.ve.xem.phim.util.ServletUtil;
import com.mycompany.website.ban.ve.xem.phim.util.ImageUploadUtil;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonWriter;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

@MultipartConfig(
    fileSizeThreshold = 1024 * 1024 * 2, // 2MB
    maxFileSize = 1024 * 1024 * 5,       // 5MB
    maxRequestSize = 1024 * 1024 * 25    // 25MB
)
public class ManagerPortalServlet extends BasePortalServlet {
    private static final Logger LOGGER = Logger.getLogger(ManagerPortalServlet.class.getName());
    private final AdminService adminService;
    private final PolicyDocumentService policyService = new PolicyDocumentService();
    private final CinemaContentService cinemaContentService = new CinemaContentService();

    public ManagerPortalServlet() {
        this(new AdminService());
    }

    ManagerPortalServlet(AdminService adminService) {
        this.adminService = adminService;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            User actor = currentUser(request);
            List<Cinema> cinemaContextOptions = adminService.listCinemas();
            Integer cinemaContextId = CinemaContextResolver.prepare(
                    request, actor, cinemaContextOptions);
            User scopedActor = CinemaContextResolver.scopedReadActor(actor, cinemaContextId);
            request.setAttribute("cinemaScopedActor", scopedActor);
            if ("/admin/custom-content".equals(request.getServletPath())) {
                response.sendRedirect(request.getContextPath() + "/admin/films?tab=custom");
                return;
            }
            if ("/admin/content/refund-policy".equals(request.getServletPath())) {
                requireGlobalAdmin(actor);
                request.setAttribute("policyPublished", policyService.publishedRefundPolicy());
                request.setAttribute("policyDraft", policyService.draftRefundPolicy().orElse(null));
                forward(request, response, "/WEB-INF/views/admin/refund-policy.jsp");
                return;
            }
            if ("/admin/content/about-us".equals(request.getServletPath()) || "/admin/about-us".equals(request.getServletPath())) {
                requireGlobalAdmin(actor);
                request.setAttribute("members", com.mycompany.website.ban.ve.xem.phim.util.CustomContentHelper.getAboutUsMembers());
                request.setAttribute("features", com.mycompany.website.ban.ve.xem.phim.util.CustomContentHelper.getAboutUsFeatures());
                forward(request, response, "/WEB-INF/views/admin/about-us.jsp");
                return;
            }
            if ("/admin/content/terms-of-use".equals(request.getServletPath()) || "/admin/terms-of-use".equals(request.getServletPath())) {
                requireGlobalAdmin(actor);
                request.setAttribute("policyPublished", policyService.publishedTermsOfUse());
                request.setAttribute("policyDraft", policyService.draftTermsOfUse().orElse(null));
                forward(request, response, "/WEB-INF/views/admin/terms-of-use.jsp");
                return;
            }
            try {
                request.setAttribute("unreadNotificationCount",
                        adminService.getUnreadNotificationCount(actor));
                request.setAttribute("notificationsUnavailable", Boolean.FALSE);
            } catch (RuntimeException ex) {
                LOGGER.log(Level.WARNING, "Khong doc duoc so thong bao chua xem.", ex);
                request.setAttribute("unreadNotificationCount", null);
                request.setAttribute("notificationsUnavailable", Boolean.TRUE);
            }
            switch (request.getServletPath()) {
                case "/admin/films" -> {
                    String tab = request.getParameter("tab");
                    if ("custom".equals(tab)) {
                        request.setAttribute("contentCinemaRequired", cinemaContextId == null);
                        request.setAttribute("cinetagsJson", cinemaContextId == null ? "[]"
                                : cinemaContentService.getContent(cinemaContextId, "cinetags_data"));
                        request.setAttribute("cornerItemsJson", cinemaContextId == null ? "[]"
                                : cinemaContentService.getContent(cinemaContextId, "corner_items_data"));
                        request.setAttribute("eventsJson", cinemaContextId == null ? "[]"
                                : cinemaContentService.getContent(cinemaContextId, "events_data"));
                        request.setAttribute("specialCinemasJson", cinemaContextId == null ? "[]"
                                : cinemaContentService.getContent(cinemaContextId, "special_cinemas_data"));
                        request.setAttribute("films", cinemaContextId == null ? List.of()
                                : adminService.listFilms(scopedActor));
                        forward(request, response, "/WEB-INF/views/admin/custom-content.jsp");
                    } else {
                        String action = request.getParameter("action");
                        if ("delete-impact".equals(action)) {
                            int filmId = intParam(request, "id", 0);
                            writeFilmDeleteImpact(request, response,
                                    adminService.previewFilmDeleteImpact(filmId, actor));
                        } else if ("create".equals(action)) {
                            if (CinemaCapabilityPolicy.isManager(actor)) {
                                response.sendRedirect(request.getContextPath() + "/admin/requests?action=film");
                                return;
                            }
                            request.setAttribute("film", new Film());
                            // ST-01: form them phim phai chon rap ngay tu dau.
                            request.setAttribute("cinemas", adminService.listCinemas(scopedActor));
                            request.setAttribute("assignedCinemaIds", List.of());
                            forward(request, response, "/WEB-INF/views/admin/film-form.jsp");
                        } else if ("edit".equals(action)) {
                            int filmId = intParam(request, "id", 0);
                            if (CinemaCapabilityPolicy.isManager(actor)) {
                                response.sendRedirect(request.getContextPath()
                                        + "/admin/requests?action=film&filmId=" + filmId);
                                return;
                            }
                            Film film = adminService.findFilmById(filmId, scopedActor)
                                    .orElseThrow(() -> new BookingException(404, "Không tìm thấy phim."));
                            request.setAttribute("film", film);
                            request.setAttribute("cinemas", adminService.listCinemas(scopedActor));
                            request.setAttribute("assignedCinemaIds", adminService.getCinemaIdsByFilmId(filmId));
                            forward(request, response, "/WEB-INF/views/admin/film-form.jsp");
                        } else {
                            String lifecycle = ServletUtil.param(request, "lifecycle");
                            if (!List.of("active", "archive", "deleted").contains(lifecycle)) {
                                lifecycle = "active";
                            }
                            request.setAttribute("lifecycle", lifecycle);
                            request.setAttribute("films", adminService.listFilms(scopedActor, lifecycle));
                            forward(request, response, "/WEB-INF/views/admin/films.jsp");
                        }
                    }
                }
                case "/admin/cinemas", "/admin/cinemas/films" -> {
                    String action = request.getParameter("action");
                    String servletPath = request.getServletPath();
                    String pathInfo = request.getPathInfo();
                    boolean isFilms = "/admin/cinemas/films".equals(servletPath)
                            || "/films".equals(pathInfo)
                            || "films".equals(action);

                    if (isFilms) {
                        if (CinemaCapabilityPolicy.isManager(actor)) {
                            response.sendRedirect(request.getContextPath() + "/admin/requests?action=film");
                            return;
                        }
                        int cinemaId = intParam(request, "cinemaId", intParam(request, "id", 0));
                        Cinema cinema = adminService.findCinemaById(cinemaId, scopedActor)
                                .orElseThrow(() -> new BookingException(404, "Không tìm thấy rạp chiếu."));
                        request.setAttribute("cinema", cinema);
                        request.setAttribute("allFilms", adminService.listFilms(scopedActor));
                        request.setAttribute("selectedFilmIds", adminService.getFilmIdsByCinemaId(cinemaId));
                        forward(request, response, "/WEB-INF/views/admin/cinema-films.jsp");
                    } else if ("create".equals(action)) {
                        CinemaCapabilityPolicy.requireAdmin(actor);
                        request.setAttribute("cinema", new Cinema());
                        request.setAttribute("cities", adminService.cityOptions());
                        forward(request, response, "/WEB-INF/views/admin/cinema-form.jsp");
                    } else if ("edit".equals(action)) {
                        int cinemaId = intParam(request, "id", 0);
                        Cinema cinema = adminService.findCinemaById(cinemaId, scopedActor)
                                .orElseThrow(() -> new BookingException(404, "Không tìm thấy rạp chiếu."));
                        assertNotDeleted(cinema.getStatus(), "Rạp chiếu này đã bị xóa nên không sửa được."
                                + " Hãy xem ở mục \"Đã bị xóa\".");
                        boolean isSpecial = com.mycompany.website.ban.ve.xem.phim.util.CustomContentHelper.getSpecialCinemas()
                                .stream().anyMatch(sc -> sc.getTitle().equalsIgnoreCase(cinema.getName()));
                        request.setAttribute("cinema", cinema);
                        request.setAttribute("isSpecial", isSpecial);
                        request.setAttribute("cities", adminService.cityOptions());
                        forward(request, response, "/WEB-INF/views/admin/cinema-form.jsp");
                    } else {
                        String lifecycle = AdminService.normalizeLifecycleTab(
                                ServletUtil.param(request, "lifecycle"));
                        request.setAttribute("lifecycle", lifecycle);
                        request.setAttribute("cinemas", adminService.listCinemas(scopedActor, lifecycle));
                        request.setAttribute("cities", adminService.cityOptions());
                        request.setAttribute("specialCinemas", com.mycompany.website.ban.ve.xem.phim.util.CustomContentHelper.getSpecialCinemas());
                        forward(request, response, "/WEB-INF/views/admin/cinemas.jsp");
                    }
                }
                case "/admin/rooms", "/admin/rooms/seats" -> {
                    String action = request.getParameter("action");
                    String servletPath = request.getServletPath();
                    String pathInfo = request.getPathInfo();
                    boolean isImpact = "/impact".equals(pathInfo)
                            || "impact".equals(action);
                    boolean isSeats = "/admin/rooms/seats".equals(servletPath)
                            || "/seats".equals(pathInfo)
                            || "seats".equals(action);
                    if (isImpact) {
                        writeRoomImpact(request, response, actor);
                    } else if (isSeats) {
                        int roomId = intParam(request, "roomId", intParam(request, "id", 0));
                        Room room = requireEditableRoom(roomId, scopedActor);
                        request.setAttribute("room", room);
                        request.setAttribute("seats", adminService.getSeatsByRoomId(roomId, scopedActor));
                        forward(request, response, "/WEB-INF/views/admin/room-seats.jsp");
                    } else if ("create".equals(action)) {
                        if (CinemaCapabilityPolicy.isManager(actor)) {
                            response.sendRedirect(request.getContextPath() + "/admin/requests?action=room");
                            return;
                        }
                        request.setAttribute("room", new Room());
                        request.setAttribute("cinemas", adminService.listCinemas(scopedActor));
                        forward(request, response, "/WEB-INF/views/admin/room-form.jsp");
                    } else if ("edit".equals(action)) {
                        int roomId = intParam(request, "id", 0);
                        request.setAttribute("room", requireEditableRoom(roomId, scopedActor));
                        request.setAttribute("cinemas", adminService.listCinemas(scopedActor));
                        forward(request, response, "/WEB-INF/views/admin/room-form.jsp");
                    } else {
                        String lifecycle = AdminService.normalizeLifecycleTab(
                                ServletUtil.param(request, "lifecycle"));
                        request.setAttribute("lifecycle", lifecycle);
                        request.setAttribute("rooms", adminService.listRooms(scopedActor, lifecycle));
                        request.setAttribute("cinemas", adminService.listCinemas(scopedActor));
                        forward(request, response, "/WEB-INF/views/admin/rooms.jsp");
                    }
                }
                case "/admin/showtimes" -> {
                    String action = ServletUtil.param(request, "action");
                    if ("impact".equals(action)) {
                        writeShowtimeImpact(request, response, actor);
                        return;
                    }
                    if ("deletion-impact".equals(action)) {
                        writeShowtimeDeletionImpact(response, adminService.previewShowtimeDeletion(
                                intParam(request, "id", 0), actor));
                        return;
                    }
                    Integer filmId = nullableIntParam(request, "filmId");
                    request.setAttribute("films", adminService.listFilms(scopedActor));
                    request.setAttribute("cinemas", adminService.listCinemas(scopedActor));
                    request.setAttribute("rooms", adminService.listRooms(scopedActor));
                    request.setAttribute("cinemaFilmMap", adminService.getCinemaFilmMap(scopedActor));
                    request.setAttribute("selectedFilmId", filmId);
                    if ("edit".equals(action)) {
                        int showtimeId = intParam(request, "id", 0);
                        Showtime showtime = adminService.findShowtimeById(showtimeId, scopedActor)
                                .orElseThrow(() -> new BookingException(404, "Khong tim thay suat chieu."));
                        request.setAttribute("showtime", showtime);
                        request.setAttribute("selectedFilmId", showtime.getFilmId());
                        forward(request, response, "/WEB-INF/views/admin/showtime-form.jsp");
                    } else if ("create".equals(action) || "add".equals(action)) {
                        forward(request, response, "/WEB-INF/views/admin/showtime-form.jsp");
                    } else {
                        List<Showtime> showtimes = adminService.listShowtimes(scopedActor);
                        int focusShowtimeId = intParam(request, "focusShowtimeId", 0);
                        boolean focusVisible = showtimes.stream().anyMatch(st -> st.getId() == focusShowtimeId);
                        request.setAttribute("showtimes", showtimes);
                        request.setAttribute("showtimesJson", showtimesJsonForHtml(showtimes));
                        request.setAttribute("businessDate", BusinessClock.now().toLocalDate().toString());
                        request.setAttribute("focusShowtimeId", focusVisible ? focusShowtimeId : 0);
                        forward(request, response, "/WEB-INF/views/admin/showtimes.jsp");
                    }
                }
                case "/admin/users" -> {
                    request.setAttribute("users", adminService.listUsers("member", actor));
                    forward(request, response, "/WEB-INF/views/admin/users.jsp");
                }
                case "/admin/staff" -> {
                    request.setAttribute("staffs", adminService.listUsers(AppConstants.ROLE_STAFF, scopedActor));
                    request.setAttribute("cinemas", adminService.listCinemas(actor));
                    forward(request, response, "/WEB-INF/views/admin/staff.jsp");
                }
                case "/admin/comments" -> {
                    Integer filmId = nullableIntParam(request, "filmId");
                    Boolean reportedOnly = request.getParameter("reportedOnly") == null
                            ? null
                            : Boolean.TRUE;
                    request.setAttribute("selectedCommentFilmId", filmId);
                    request.setAttribute("reportedOnly", reportedOnly != null && reportedOnly);
                    request.setAttribute("comments",
                            adminService.listComments(filmId, reportedOnly, scopedActor));
                    request.setAttribute("films", adminService.listCommentFilms(scopedActor));
                    forward(request, response, "/WEB-INF/views/admin/comments.jsp");
                }
                case "/admin/appeals" -> {
                    String status = request.getParameter("status");
                    // F-005: hang doi khong doc duoc phai hien ro la loi doc, khong duoc hien nhu
                    // "khong co don kháng cáo nào" — admin se bo sot don mo khoa/hoan tien.
                    AdminService.AppealQueue appealQueue = adminService.appealQueue(status, scopedActor);
                    request.setAttribute("selectedStatus", status);
                    request.setAttribute("appeals", appealQueue.getAppeals());
                    request.setAttribute("appealsUnavailable", appealQueue.isUnavailable());
                    if (appealQueue.isUnavailable()) {
                        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                    }
                    forward(request, response, "/WEB-INF/views/admin/appeals.jsp");
                }
                case "/admin/promotions" -> {
                    if (!CinemaCapabilityPolicy.canCreatePromotion(actor)) {
                        throw new BookingException(403, "Bạn không có quyền quản lý khuyến mãi.");
                    }
                    if ("delete-impact".equals(request.getParameter("action"))) {
                        writePromotionDeleteImpact(request, response,
                                adminService.previewPromotionDeleteImpact(intParam(request, "id", 0), actor));
                    } else {
                        request.setAttribute("promotions", adminService.listPromotions(actor));
                        forward(request, response, "/WEB-INF/views/admin/promotions.jsp");
                    }
                }
                case "/admin/combos" -> {
                    request.setAttribute("combos", adminService.listCombos(scopedActor));
                    // CB-01: admin chon duoc rap so huu combo; manager chi thay rap cua minh.
                    request.setAttribute("cinemas", adminService.listCinemas(scopedActor));
                    forward(request, response, "/WEB-INF/views/admin/combos.jsp");
                }
                case "/admin/orders" -> {
                    int page = intParam(request, "page", 1);
                    int size = intParam(request, "size", 25);
                    String status = ServletUtil.param(request, "status");
                    String tab = ServletUtil.param(request, "tab");
                    if (!List.of("pending", "late", "refund", "rejected", "redeemed", "cancelled").contains(tab)) {
                        tab = "pending";
                    }
                    String ticketCode = ServletUtil.param(request, "ticketCode");
                    Integer cinemaId = nullablePositiveInt(request.getParameter("cinemaId"));
                    if (cinemaContextId != null) cinemaId = cinemaContextId;
                    LocalDate from = nullableDate(request.getParameter("from"));
                    LocalDate to = nullableDate(request.getParameter("to"));
                    PageResult<OrderRecord> orderPage = adminService.listOrdersForAdmin(
                            page, size, status, from, to, cinemaId, ticketCode, actor, tab);
                    List<OrderRecord> allOrders = orderPage.items();
                    // F-004: ghim mot moc gio nghiep vu cho ca trang, neu khong mot don co EndTime
                    // roi dung giua hai lan doc dong ho co the hien o hai tab cung luc.
                    LocalDateTime classifiedAt = BusinessClock.now();
                    allOrders.forEach(order -> order.setBusinessNow(classifiedAt));
                    List<OrderRecord> pendingCheckInOrders = "pending".equalsIgnoreCase(tab) ? allOrders : allOrders.stream()
                            .filter(OrderRecord::isPendingOnTimeCheckIn)
                            .collect(java.util.stream.Collectors.toList());
                    List<OrderRecord> lateCheckInOrders = "late".equalsIgnoreCase(tab) ? allOrders : allOrders.stream()
                            .filter(OrderRecord::isLateCheckIn)
                            .collect(java.util.stream.Collectors.toList());
                    List<OrderRecord> refundReviewOrders = "refund".equalsIgnoreCase(tab) ? allOrders : allOrders.stream()
                            .filter(OrderRecord::isRefundReview)
                            .collect(java.util.stream.Collectors.toList());
                    List<OrderRecord> redeemedOrders = "redeemed".equalsIgnoreCase(tab) ? allOrders : allOrders.stream()
                            .filter(OrderRecord::isRedeemed)
                            .collect(java.util.stream.Collectors.toList());
                    List<OrderRecord> cancelledOrders = "cancelled".equalsIgnoreCase(tab) ? allOrders : allOrders.stream()
                            .filter(OrderRecord::isCancelled)
                            .collect(java.util.stream.Collectors.toList());
                    List<OrderRecord> rejectedRefundOrders = "rejected".equalsIgnoreCase(tab) ? allOrders : allOrders.stream()
                            .filter(OrderRecord::isRefundRejected)
                            .collect(java.util.stream.Collectors.toList());

                    request.setAttribute("orders", allOrders);
                    request.setAttribute("pendingCheckInOrders", pendingCheckInOrders);
                    request.setAttribute("lateCheckInOrders", lateCheckInOrders);
                    request.setAttribute("refundReviewOrders", refundReviewOrders);
                    request.setAttribute("redeemedOrders", redeemedOrders);
                    request.setAttribute("cancelledOrders", cancelledOrders);
                    request.setAttribute("rejectedRefundOrders", rejectedRefundOrders);
                    java.util.Map<String, Long> orderTabCounts = new java.util.LinkedHashMap<>();
                    for (String bucket : List.of("pending", "late", "refund", "rejected", "redeemed", "cancelled")) {
                        orderTabCounts.put(bucket, adminService.countOrdersForAdmin(
                                status, from, to, cinemaId, ticketCode, bucket, actor));
                    }
                    request.setAttribute("orderTabCounts", orderTabCounts);
                    request.setAttribute("orderPage", orderPage);
                    request.setAttribute("selectedStatus", status);
                    request.setAttribute("selectedTicketCode", ticketCode);
                    request.setAttribute("selectedCinemaId", cinemaId);
                    request.setAttribute("selectedFrom", from);
                    request.setAttribute("selectedTo", to);
                    request.setAttribute("cinemas", adminService.listCinemas(scopedActor));
                    forward(request, response, "/WEB-INF/views/admin/orders.jsp");
                }
                case "/admin/reports" -> {
                    String action = request.getParameter("action");
                    if ("exportExcel".equals(action) || "export".equals(action)) {
                        response.setContentType("text/csv; charset=UTF-8");
                        response.setHeader("Content-Disposition", "attachment; filename=\"Bao_Cao_CineBook_" + java.time.LocalDate.now() + ".csv\"");
                        java.io.OutputStream os = response.getOutputStream();
                        os.write(0xEF);
                        os.write(0xBB);
                        os.write(0xBF);
                        java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.OutputStreamWriter(os, java.nio.charset.StandardCharsets.UTF_8));
                        adminService.exportReportCsv(writer, scopedActor);
                        writer.flush();
                        return;
                    }
                    request.setAttribute("reportSummary", adminService.getReportSummary(scopedActor));
                    request.setAttribute("dailyRevenue", adminService.dailyRevenueRows(scopedActor));
                    request.setAttribute("monthlyRevenue", adminService.monthlyRevenueRows(scopedActor));
                    request.setAttribute("yearlyRevenue", adminService.yearlyRevenueRows(scopedActor));
                    request.setAttribute("topFilms", adminService.topFilms(scopedActor));
                    forward(request, response, "/WEB-INF/views/admin/reports.jsp");
                }
                case "/admin/notifications" -> {
                    String action = request.getParameter("action");
                    String accept = request.getHeader("Accept");
                    String pathInfo = request.getPathInfo();
                    boolean isJsonReq = "api".equals(action) || "poll".equals(action) || "unread-count".equals(action)
                            || (accept != null && accept.contains("application/json"))
                            || (pathInfo != null && (pathInfo.startsWith("/poll") || pathInfo.startsWith("/api")));
                    if (isJsonReq) {
                        int unreadCount = adminService.getUnreadNotificationCount(actor);
                        List<AdminNotification> notifications = adminService.listAdminNotifications(scopedActor);
                        Map<String, Object> resp = new LinkedHashMap<>();
                        resp.put("success", true);
                        resp.put("unreadCount", unreadCount);
                        resp.put("notifications", notifications);
                        JsonUtil.write(response, resp);
                        return;
                    }
                    request.setAttribute("notifications", adminService.listAdminNotifications(scopedActor));
                    forward(request, response, "/WEB-INF/views/admin/notifications.jsp");
                }
                default -> response.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        } catch (BookingException ex) {
            if (ex.getStatusCode() == HttpServletResponse.SC_FORBIDDEN) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, ex.getMessage());
                return;
            }
            String accept = request.getHeader("Accept");
            if (accept != null && accept.toLowerCase().contains("application/json")) {
                String traceId = java.util.UUID.randomUUID().toString();
                int status = ex.getStatusCode() >= 500 ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                        : ex.getStatusCode();
                String message = status >= 500 ? "Hệ thống đang gặp lỗi vận hành. Vui lòng thử lại sau." : ex.getMessage();
                response.setStatus(status);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(Json.createObjectBuilder()
                        .add("error", message == null ? "Yêu cầu không hợp lệ." : message)
                        .add("traceId", traceId)
                        .build().toString());
                return;
            }
            renderError(request, response, ex, LOGGER);
        }
    }

    private void writeRoomImpact(HttpServletRequest request, HttpServletResponse response, User actor)
            throws IOException {
        int roomId = intParam(request, "roomId", intParam(request, "id", 0));
        Map<String, Object> impact = adminService.getRoomDeleteImpactInfo(roomId, actor);
        response.setContentType("application/json;charset=UTF-8");
        javax.json.JsonObject json = javax.json.Json.createObjectBuilder()
                .add("roomName", String.valueOf(impact.getOrDefault("roomName", "")))
                .add("status", String.valueOf(impact.getOrDefault("status", "active")))
                .add("cinemaName", String.valueOf(impact.getOrDefault("cinemaName", "")))
                .add("showtimeCount", (int) impact.getOrDefault("showtimeCount", 0))
                .add("activeShowtimeCount", (int) impact.getOrDefault("activeShowtimeCount", 0))
                .add("totalTicketCount", (int) impact.getOrDefault("totalTicketCount", 0))
                .add("pendingTicketCount", (int) impact.getOrDefault("pendingTicketCount", 0))
                .build();
        response.getWriter().write(json.toString());
    }

    private void writeFilmDeleteImpact(HttpServletRequest request, HttpServletResponse response,
            FilmDeleteImpact impact)
            throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(Json.createObjectBuilder()
                .add("filmId", impact.filmId())
                .add("title", impact.title())
                .add("currentOrFutureShowtimeCount", impact.currentOrFutureShowtimeCount())
                .add("historicalShowtimeCount", impact.historicalShowtimeCount())
                .add("activeHoldCount", impact.activeHoldCount())
                .add("activeDraftOrderCount", impact.activeDraftOrderCount())
                .add("committedOrderCount", impact.committedOrderCount())
                .add("historicalOrderCount", impact.historicalOrderCount())
                .add("commentCount", impact.commentCount())
                .add("commentReportCount", impact.commentReportCount())
                .add("cinemaCount", impact.cinemaCount())
                .add("expiredOrWithdrawn", impact.expiredOrWithdrawn())
                .add("eligible", impact.eligible())
                .add("blockedReason", impact.blockedReason() == null ? "" : impact.blockedReason())
                .add("traceId", java.util.UUID.randomUUID().toString())
                .build().toString());
    }

    private void writeShowtimeDeletionImpact(HttpServletResponse response, ShowtimeDeletionImpact impact)
            throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(Json.createObjectBuilder()
                .add("showtimeId", impact.showtimeId())
                .add("filmTitle", impact.filmTitle())
                .add("cinemaName", impact.cinemaName())
                .add("roomName", impact.roomName())
                .add("startTime", String.valueOf(impact.startTime()))
                .add("endTime", String.valueOf(impact.endTime()))
                .add("saleStatus", impact.saleStatus())
                .add("deleteRequestedAt", String.valueOf(impact.deleteRequestedAt()))
                .add("deleteNotBefore", String.valueOf(impact.deleteNotBefore()))
                .add("secondsRemaining", impact.secondsRemaining())
                .add("activeHoldCount", impact.activeHoldCount())
                .add("activeDraftOrderCount", impact.activeDraftOrderCount())
                .add("committedOrderCount", impact.committedOrderCount())
                .add("terminalOrderCount", impact.terminalOrderCount())
                .add("ready", impact.ready())
                .add("blockedReason", impact.blockedReason() == null ? "" : impact.blockedReason())
                .build().toString());
    }

    private void writePromotionDeleteImpact(HttpServletRequest request, HttpServletResponse response,
            PromotionDeleteImpact impact)
            throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(Json.createObjectBuilder()
                .add("promotionId", impact.promotionId())
                .add("code", impact.code())
                .add("orderRefs", impact.orderRefs())
                .add("usageRefs", impact.usageRefs())
                .add("voucherRefs", impact.voucherRefs())
                .add("usedCount", impact.usedCount())
                .add("traceId", java.util.UUID.randomUUID().toString())
                .build().toString());
    }

    private void writeShowtimeImpact(HttpServletRequest request, HttpServletResponse response, User actor)
            throws IOException {
        int showtimeId = intParam(request, "showtimeId", intParam(request, "id", 0));
        ShowtimeChangeImpact impact = adminService.previewShowtimeChangeImpact(showtimeId, actor);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(Json.createObjectBuilder()
                .add("showtimeId", impact.showtimeId())
                .add("orderCount", impact.orderCount())
                .add("customerCount", impact.customerCount())
                .add("occupiedSeatCount", impact.occupiedSeatCount())
                .add("requiresConfirmation", impact.requiresConfirmation())
                .build().toString());
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User actor = currentUser(request);
        try {
            switch (request.getServletPath()) {
                case "/admin/films" -> {
                    String tab = request.getParameter("tab");
                    if ("custom".equals(tab)) {
                        handleCustomContentPost(request, response, actor);
                    } else {
                        handleFilmPost(request, response, actor);
                    }
                }
                case "/admin/cinemas", "/admin/cinemas/films" -> handleCinemaPost(request, response, actor);
                case "/admin/rooms" -> handleRoomPost(request, response, actor);
                case "/admin/showtimes" -> handleShowtimePost(request, response, actor);
                case "/admin/users" -> handleUserPost(request, response, actor);
                case "/admin/staff" -> handleStaffPost(request, response, actor);
                case "/admin/comments" -> handleCommentPost(request, response, actor);
                case "/admin/appeals" -> handleAppealPost(request, response, actor);
                case "/admin/content/refund-policy" -> {
                    requireGlobalAdmin(actor);
                    handleRefundPolicyPost(request, response, actor);
                }
                case "/admin/content/about-us", "/admin/about-us" -> {
                    requireGlobalAdmin(actor);
                    handleAboutUsPost(request, response, actor);
                }
                case "/admin/content/terms-of-use", "/admin/terms-of-use" -> {
                    requireGlobalAdmin(actor);
                    handleTermsOfUsePost(request, response, actor);
                }
                case "/admin/promotions" -> {
                    if (!CinemaCapabilityPolicy.canCreatePromotion(actor)) {
                        throw new BookingException(403, "Bạn không có quyền quản lý khuyến mãi.");
                    }
                    handlePromotionPost(request, response, actor);
                }
                case "/admin/combos" -> handleComboPost(request, response, actor);
                case "/admin/orders" -> handleOrderPost(request, response, actor);
                case "/admin/notifications" -> handleNotificationPost(request, response, actor);
                default -> response.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        } catch (BookingException ex) {
            if (ex.getStatusCode() == HttpServletResponse.SC_FORBIDDEN) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, ex.getMessage());
                return;
            }
            String accept = request.getHeader("Accept");
            boolean wantsJson = accept != null && accept.toLowerCase().contains("application/json");
            if (wantsJson || "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"))) {
                String traceId = java.util.UUID.randomUUID().toString();
                int status = ex.getStatusCode() >= 500 ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                        : ex.getStatusCode();
                String message = status >= 500 ? "Hệ thống đang gặp lỗi vận hành. Vui lòng thử lại sau." : ex.getMessage();
                response.setStatus(status);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(Json.createObjectBuilder()
                        .add("error", message == null ? "Yêu cầu không hợp lệ." : message)
                        .add("traceId", traceId)
                        .build().toString());
                return;
            }
            ServletUtil.flashError(request, ex.getStatusCode() >= 500
                    ? "Hệ thống đang gặp lỗi vận hành. Vui lòng thử lại sau."
                    : ex.getMessage());
            redirectBack(request, response, "/admin/dashboard");
        }
    }

    private void handleFilmPost(HttpServletRequest request, HttpServletResponse response, User actor) throws ServletException, IOException {
        CinemaCapabilityPolicy.requireAdmin(actor);
        String action = ServletUtil.param(request, "action");
        if ("delete".equals(action)) {
            FilmDeletionMode mode;
            try {
                mode = FilmDeletionMode.valueOf(ServletUtil.param(request, "deletionMode").toUpperCase());
            } catch (RuntimeException ex) {
                throw new BookingException(400, "Vui lòng chọn cách xử lý bình luận.");
            }
            FilmDeletionOutcome outcome = adminService.deleteFilm(intParam(request, "id", -1), mode,
                    ServletUtil.param(request, "confirmationTitle"), actor);
            ServletUtil.flashSuccess(request, outcome == FilmDeletionOutcome.HARD_DELETED
                    ? "Đã xóa phim vì chưa có dữ liệu tham chiếu."
                    : "Phim có dữ liệu lịch sử nên đã được chuyển vào mục Đã xóa.");
        } else if ("extend".equals(action)) {
            // EX-01: gia han chi doi EndDate; moi rang buoc khac do AdminService kiem.
            LocalDate newEndDate = nullableDateParam(request, "endDate");
            if (newEndDate == null) {
                ServletUtil.flashError(request, "Vui lòng chọn ngày kết thúc chiếu mới.");
            } else {
                adminService.extendFilmEndDate(intParam(request, "id", -1), newEndDate, actor);
                ServletUtil.flashSuccess(request, "Đã gia hạn lịch chiếu đến ngày " + newEndDate + ".");
            }
        } else {
            Film film = new Film();
            film.setId(intParam(request, "id", 0));
            film.setTitle(ServletUtil.param(request, "title"));
            film.setOtherTitles(ServletUtil.param(request, "otherTitles"));
            film.setActors(ServletUtil.param(request, "actors"));
            film.setDirectors(ServletUtil.param(request, "directors"));
            film.setRating(nullableDoubleParam(request, "rating"));
            film.setReleaseDate(nullableDateParam(request, "releaseDate"));
            // EX-01: rong = chua gioi han thoi gian chieu.
            film.setEndDate(nullableDateParam(request, "endDate"));
            film.setDurationMinutes(nullableIntParam(request, "durationMinutes"));
            film.setAgeRating(ServletUtil.param(request, "ageRating"));
            film.setTrailerUrl(ServletUtil.param(request, "trailerUrl"));
            film.setCountry(ServletUtil.param(request, "country"));
            film.setFormat(ServletUtil.param(request, "format"));
            film.setStatus(ServletUtil.param(request, "status"));
            film.setCategories(ServletUtil.param(request, "categories"));
            
            // Handle Poster upload
            String uploadedThumbnail = handleFileUpload(request, "thumbnailFile");
            String thumbnail = uploadedThumbnail;
            if (thumbnail == null || thumbnail.isBlank()) {
                thumbnail = ServletUtil.param(request, "thumbnail");
            }
            film.setThumbnail(thumbnail);

            // Handle Banner upload
            String uploadedBanner = handleFileUpload(request, "bannerFile");
            String banner = uploadedBanner;
            if (banner == null || banner.isBlank()) {
                banner = ServletUtil.param(request, "banner");
            }
            film.setBanner(banner);

            film.setLanguage(ServletUtil.param(request, "language"));
            film.setSubtitles(ServletUtil.param(request, "subtitles"));
            film.setDescription(ServletUtil.param(request, "description"));

            // ST-01: gan rap ngay trong cung transaction voi luu phim.
            // null = form khong gui truong nay (goi tu noi khac) -> giu nguyen mapping cu.
            List<Integer> cinemaIds = parseCinemaIds(request);
            try {
                adminService.saveFilm(new com.mycompany.website.ban.ve.xem.phim.service.FilmFormCommand(film, cinemaIds), actor);
            } catch (BookingException ex) {
                cleanupUploadedMedia(uploadedThumbnail);
                cleanupUploadedMedia(uploadedBanner);
                // Keep the command on the same form so field-level validation
                // does not discard the user's text/selections after a 400/409.
                request.setAttribute("film", film);
                request.setAttribute("cinemas", adminService.listCinemas(actor));
                request.setAttribute("assignedCinemaIds", cinemaIds == null ? List.of() : cinemaIds);
                ServletUtil.flashError(request, ex.getMessage());
                forward(request, response, "/WEB-INF/views/admin/film-form.jsp");
                return;
            } catch (RuntimeException ex) {
                cleanupUploadedMedia(uploadedThumbnail);
                cleanupUploadedMedia(uploadedBanner);
                throw ex;
            }
            ServletUtil.flashSuccess(request, "Đã lưu thông tin phim thành công.");
        }
        redirectBack(request, response, "/admin/films");
    }

    /**
     * Phong da xoa mem van con nguyen dong trong DB de giu doanh thu, nen mot URL cu hoac mot
     * request sua tay van mo duoc form sua va so do ghe cua no. Chot lai o day (D-03).
     */
    private Room requireEditableRoom(int roomId, User actor) {
        Room room = adminService.findRoomById(roomId, actor)
                .orElseThrow(() -> new BookingException(404, "Không tìm thấy phòng chiếu."));
        assertNotDeleted(room.getStatus(), "Phòng chiếu này đã bị xóa nên không sửa được."
                + " Hãy xem ở mục \"Đã bị xóa\".");
        return room;
    }

    private void assertNotDeleted(String status, String message) {
        if (AdminService.LIFECYCLE_DELETED.equalsIgnoreCase(status)) {
            throw new BookingException(409, message);
        }
    }

    private void handleCinemaPost(HttpServletRequest request, HttpServletResponse response, User actor) throws ServletException, IOException {
        String action = ServletUtil.param(request, "action");
        String servletPath = request.getServletPath();
        String pathInfo = request.getPathInfo();
        boolean isSaveCinemaFilms = "/admin/cinemas/films".equals(servletPath)
                || "/films".equals(pathInfo)
                || "save_cinema_films".equals(action);

        if (isSaveCinemaFilms) {
            CinemaCapabilityPolicy.requireAdmin(actor);
            int cinemaId = intParam(request, "cinemaId", intParam(request, "id", 0));
            String[] rawFilmIds = request.getParameterValues("filmIds");
            List<Integer> filmIds = new java.util.ArrayList<>();
            if (rawFilmIds != null) {
                for (String fid : rawFilmIds) {
                    try {
                        filmIds.add(Integer.parseInt(fid));
                    } catch (NumberFormatException invalidFilmId) {
                        throw new BookingException(400, "Mã phim không hợp lệ: " + fid);
                    }
                }
            }
            adminService.saveCinemaFilms(cinemaId, filmIds, actor);
            ServletUtil.flashSuccess(request, "Đã cập nhật danh sách phim chiếu tại rạp thành công.");
            response.sendRedirect(request.getContextPath() + "/admin/cinemas");
            return;
        }

        if ("delete".equals(action)) {
            CinemaCapabilityPolicy.requireAdmin(actor);
            adminService.deleteCinema(intParam(request, "id", -1), actor);
            ServletUtil.flashSuccess(request, "Đã xóa rạp chiếu thành công.");
            response.sendRedirect(request.getContextPath() + "/admin/cinemas");
        } else {
            Cinema cinema = new Cinema();
            int id = intParam(request, "id", 0);
            cinema.setId(id);
            boolean isNew = id == 0;
            if (isNew) {
                CinemaCapabilityPolicy.requireAdmin(actor);
            }

            Cinema existingForManager = null;
            int cityId;
            if (!isNew && CinemaCapabilityPolicy.isManager(actor)) {
                existingForManager = adminService.findCinemaById(id, actor)
                        .orElseThrow(() -> new BookingException(404, "Không tìm thấy rạp được gán."));
                cityId = existingForManager.getCityId();
            } else {
                String rawCityId = request.getParameter("cityId");
                String newCityName = ServletUtil.param(request, "newCityName");
                if ("other".equalsIgnoreCase(rawCityId)
                        || (newCityName != null && !newCityName.isBlank())) {
                    cityId = adminService.findOrCreateCity(newCityName);
                } else {
                    cityId = intParam(request, "cityId", 0);
                }
            }

            if (cityId <= 0) {
                throw new BookingException(400, "Vui lòng chọn hoặc nhập Tỉnh / Thành phố cho rạp chiếu.");
            }

            cinema.setCityId(cityId);
            cinema.setName(existingForManager == null
                    ? ServletUtil.param(request, "name") : existingForManager.getName());
            cinema.setAddress(ServletUtil.param(request, "address"));
            cinema.setPhone(ServletUtil.param(request, "phone"));
            cinema.setStatus(existingForManager == null
                    ? ServletUtil.param(request, "status") : existingForManager.getStatus());
            cinema.setCinemaType(existingForManager == null
                    ? ServletUtil.param(request, "cinemaType") : existingForManager.getCinemaType());
            
            // Handle image upload with automatic retention for existing cinema
            String avatar = handleFileUpload(request, "avatarFile");
            if (avatar == null || avatar.isBlank()) {
                avatar = ServletUtil.param(request, "avatar");
            }
            
            String bannerUrl = handleFileUpload(request, "bannerFile");
            if (bannerUrl == null || bannerUrl.isBlank()) {
                bannerUrl = ServletUtil.param(request, "bannerUrl");
            }

            // Automatic preservation fallback for edit mode
            if (!isNew) {
                java.util.Optional<Cinema> existingOpt = adminService.findCinemaById(id, actor);
                if (existingOpt.isPresent()) {
                    Cinema existing = existingOpt.get();
                    if (avatar == null || avatar.isBlank()) {
                        avatar = existing.getAvatar();
                    }
                    if (bannerUrl == null || bannerUrl.isBlank()) {
                        bannerUrl = existing.getBannerUrl();
                    }
                }
            }

            cinema.setAvatar(avatar);
            cinema.setBannerUrl(bannerUrl);

            cinema.setDescription(ServletUtil.param(request, "description"));
            
            String[] rawFilmIds = CinemaCapabilityPolicy.isAdmin(actor)
                    ? request.getParameterValues("filmIds") : null;
            List<Integer> filmIds = rawFilmIds != null ? new java.util.ArrayList<>() : null;
            if (rawFilmIds != null) {
                for (String fid : rawFilmIds) {
                    try {
                        filmIds.add(Integer.parseInt(fid));
                    } catch (NumberFormatException invalidFilmId) {
                        throw new BookingException(400, "Mã phim không hợp lệ: " + fid);
                    }
                }
            }

            adminService.saveCinema(cinema, filmIds, actor);

            if (isGlobalAdmin(actor)) {
                boolean isSpecial = "true".equalsIgnoreCase(request.getParameter("isSpecial"));
                adminService.syncSpecialCinema(cinema.getName(), cinema.getAddress(), cinema.getAvatar(),
                        cinema.getDescription(), isSpecial, actor);
            }

            if (isNew) {
                ServletUtil.flashSuccess(request, "Đã tạo rạp chiếu mới. Vui lòng chọn các bộ phim chiếu tại rạp bên dưới.");
                response.sendRedirect(request.getContextPath() + "/admin/cinemas/films?cinemaId=" + cinema.getId());
            } else {
                ServletUtil.flashSuccess(request, "Đã cập nhật thông tin rạp/cơ sở chiếu thành công.");
                response.sendRedirect(request.getContextPath() + "/admin/cinemas");
            }
        }
    }

    private void handleRoomPost(HttpServletRequest request, HttpServletResponse response, User actor) throws IOException {
        String action = ServletUtil.param(request, "action");
        String servletPath = request.getServletPath();
        String pathInfo = request.getPathInfo();
        boolean isSaveSeats = "/admin/rooms/seats".equals(servletPath)
                || "/seats".equals(pathInfo)
                || "save_seats".equals(action);

        if ("delete".equals(action)) {
            adminService.deleteRoom(intParam(request, "id", -1), actor);
            ServletUtil.flashSuccess(request, "Đã cập nhật trạng thái/xóa phòng chiếu thành công.");
            response.sendRedirect(request.getContextPath() + "/admin/rooms");
        } else if ("deactivate".equals(action)) {
            adminService.deactivateRoom(intParam(request, "id", -1), actor);
            ServletUtil.flashSuccess(request, "Đã tạm ngưng phòng chiếu thành công.");
            response.sendRedirect(request.getContextPath() + "/admin/rooms");
        } else if ("activate".equals(action)) {
            adminService.activateRoom(intParam(request, "id", -1), actor);
            ServletUtil.flashSuccess(request, "Đã khôi phục hoạt động phòng chiếu thành công.");
            response.sendRedirect(request.getContextPath() + "/admin/rooms");
        } else if ("preview".equals(action)) {
            int roomId = intParam(request, "roomId", intParam(request, "id", 0));
            List<Seat> seats = parseSeatJson(roomId, ServletUtil.param(request, "seatsJson"));
            SeatLayoutPreview preview = adminService.previewRoomSeats(roomId, seats, actor);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(Json.createObjectBuilder()
                    .add("roomId", preview.roomId())
                    .add("currentSeatCount", preview.currentSeatCount())
                    .add("requestedSeatCount", preview.requestedSeatCount())
                    .add("addedSeatKeys", Json.createArrayBuilder(preview.addedSeatKeys()))
                    .add("removedSeatKeys", Json.createArrayBuilder(preview.removedSeatKeys()))
                    .add("referencedSeatKeys", Json.createArrayBuilder(preview.referencedSeatKeys()))
                    .add("nextLayoutVersion", preview.nextLayoutVersion())
                    .build().toString());
        } else if (isSaveSeats) {
            int roomId = intParam(request, "roomId", intParam(request, "id", 0));
            String seatsJson = ServletUtil.param(request, "seatsJson");
            List<Seat> seats = parseSeatJson(roomId, seatsJson);
            adminService.saveCustomRoomSeats(roomId, seats, actor);
            ServletUtil.flashSuccess(request, "Đã lưu sơ đồ ghế phòng chiếu thành công (" + seats.size() + " ghế).");
            response.sendRedirect(request.getContextPath() + "/admin/rooms");
        } else {
            Room room = new Room();
            int id = intParam(request, "id", 0);
            if (id == 0 && CinemaCapabilityPolicy.isManager(actor)) {
                throw new BookingException(403,
                        "Manager cần gửi yêu cầu tạo phòng để admin phê duyệt.");
            }
            room.setId(id);
            room.setCinemaId(intParam(request, "cinemaId", 0));
            room.setName(ServletUtil.param(request, "name"));
            room.setRoomType(ServletUtil.param(request, "roomType"));

            boolean isNew = id == 0;
            boolean regenerateLayout = "true".equalsIgnoreCase(ServletUtil.param(request, "regenerateLayout"));

            adminService.saveRoom(
                    room,
                    intParam(request, "rowCount", isNew ? 10 : 0),
                    intParam(request, "seatsPerRow", isNew ? 12 : 0),
                    ServletUtil.param(request, "vipRows"),
                    regenerateLayout,
                    actor
            );

            if (isNew) {
                ServletUtil.flashSuccess(request, "Đã tạo phòng chiếu mới. Hãy thiết kế sơ đồ ghế bên dưới.");
                response.sendRedirect(request.getContextPath() + "/admin/rooms/seats?roomId=" + room.getId());
            } else {
                ServletUtil.flashSuccess(request, "Đã cập nhật thông tin phòng chiếu.");
                response.sendRedirect(request.getContextPath() + "/admin/rooms");
            }
        }
    }

    private List<Seat> parseSeatJson(int roomId, String seatsJson) {
        List<Seat> seats = new java.util.ArrayList<>();
        if (seatsJson == null || seatsJson.isBlank()) {
            return seats;
        }
        try (JsonReader reader = Json.createReader(new StringReader(seatsJson))) {
            JsonArray jsonArray = reader.readArray();
            for (int i = 0; i < jsonArray.size(); i++) {
                JsonObject obj = jsonArray.getJsonObject(i);
                Seat seat = new Seat();
                seat.setRoomId(roomId);
                seat.setRowLabel(obj.getString("rowLabel"));
                seat.setSeatNumber(obj.getInt("seatNumber"));
                seat.setSeatType(obj.getString("seatType"));
                seat.setSeatKey(obj.getString("seatKey"));
                if (obj.containsKey("priceSurcharge") && !obj.isNull("priceSurcharge")) {
                    try {
                        seat.setPriceSurcharge(new BigDecimal(obj.getJsonNumber("priceSurcharge").toString()));
                    } catch (Exception ex) {
                        throw new BookingException(400, "Phần phí ghế không phải là số hợp lệ.");
                    }
                }
                String seatKey = seat.getSeatKey();
                if ("couple".equalsIgnoreCase(seat.getSeatType())
                        && seatKey != null && seatKey.contains("-")) {
                    String[] pair = seatKey.split("-", 2);
                    if (pair.length != 2 || !pair[0].matches(".+\\d+") || !pair[1].matches("\\d+")) {
                        throw new BookingException(400, "Mã ghế đôi không hợp lệ: " + seatKey);
                    }
                    int first = Integer.parseInt(pair[0].replaceAll("^.*?(\\d+)$", "$1"));
                    int second = Integer.parseInt(pair[1]);
                    seat.setSeatNumber(first);
                    seat.setSeatKey(seat.getRowLabel() + first);
                    seats.add(seat);
                    Seat partner = new Seat();
                    partner.setRoomId(roomId);
                    partner.setRowLabel(seat.getRowLabel());
                    partner.setSeatNumber(second);
                    partner.setSeatType("couple");
                    partner.setSeatKey(seat.getRowLabel() + second);
                    partner.setPriceSurcharge(seat.getPriceSurcharge());
                    seats.add(partner);
                } else {
                    seats.add(seat);
                }
            }
            return seats;
        } catch (BookingException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BookingException(400, "JSON sơ đồ ghế không hợp lệ.", ex);
        }
    }

    private void handleShowtimePost(HttpServletRequest request, HttpServletResponse response, User actor) throws IOException {
        String action = ServletUtil.param(request, "action");
        int showtimeId = intParam(request, "id", -1);
        if ("requestDelete".equals(action)) {
            adminService.requestShowtimeDeletion(showtimeId, actor);
            ServletUtil.flashSuccess(request, "Đã ngưng bán. Có thể xác nhận xóa sau 5 phút.");
        } else if ("resumeSale".equals(action)) {
            adminService.resumeShowtimeSale(showtimeId, actor);
            ServletUtil.flashSuccess(request, "Đã hủy yêu cầu xóa và mở bán lại.");
        } else if ("confirmDelete".equals(action) || "delete".equals(action)) {
            boolean hardDeleted = adminService.confirmShowtimeDeletion(showtimeId, actor);
            ServletUtil.flashSuccess(request, hardDeleted
                    ? "Đã xóa vĩnh viễn suất chiếu chưa có lịch sử."
                    : "Đã xóa suất chiếu khỏi vận hành và giữ dữ liệu lịch sử.");
        } else {
            int filmId = intParam(request, "filmId", 0);
            int cinemaId = intParam(request, "cinemaId", 0);
            int roomId = intParam(request, "roomId", 0);

            var roomOpt = adminService.findRoomById(roomId, actor);
            if (roomOpt.isPresent() && "inactive".equalsIgnoreCase(roomOpt.get().getStatus())) {
                ServletUtil.flashError(request, "Không thể tạo suất chiếu cho phòng chiếu đang tạm ngưng hoạt động.");
                response.sendRedirect(request.getContextPath() + "/admin/showtimes?action=create");
                return;
            }

            BigDecimal basePrice = decimalParam(request, "basePrice", BigDecimal.ZERO);
            // N-08: repeatDays truoc day chi bi chan phia duoi bang Math.max(1, ...).
            // Mot POST repeatDays=100000 sinh 100.000 suat + ~3,6 trieu dong ShowtimeSeats
            // trong mot transaction — lock escalation, pool 20 connection can, ca site treo.
            // Chot that nam o AdminService.saveShowtimes(); cho nay chi de bao loi ro rang
            // thay vi de nguoi dung nhan mot thong bao ky thuat.
            int repeatDays = intParam(request, "repeatDays", 1);
            if (repeatDays > AdminService.MAX_SHOWTIME_BATCH_SIZE) {
                throw new BookingException(400, "Số ngày lặp tối đa là "
                        + AdminService.MAX_SHOWTIME_BATCH_SIZE + " ngày (bạn nhập " + repeatDays + ").");
            }

            // ST-02: KHONG con gia tri mac dinh khi thieu input.
            // Ban cu roi ve LocalDateTime.now().plusDays(1).withHour(14) — vua che loi nhap
            // lieu (form gui thieu van "thanh cong", tao ra suat 14h ngay mai khong ai dat),
            // vua lay gio cua JVM lam moc nghiep vu. Thieu du lieu phai bao thieu.
            LocalDateTime startDateTime = parseShowtimeStart(request);
            if (startDateTime == null) {
                ServletUtil.flashError(request, "Vui lòng chọn ngày chiếu và giờ bắt đầu.");
                redirectBack(request, response, "/admin/showtimes");
                return;
            }

            // ST-02: EndTime do SERVER tinh tu thoi luong phim, khong nhan tu client.
            // Client tu tinh EndTime bang modulo 24 gio nhung giu nguyen ngay, nen ca
            // 24:00 -> 02:00 sinh ra EndTime TRUOC StartTime. Tinh o server vua dung ca qua
            // nua dem (plusMinutes tu dong sang ngay hom sau), vua khong tin duoc gia tri sua tay.
            Integer durationMinutes = adminService.findFilmById(filmId, actor)
                    .map(Film::getDurationMinutes).orElse(null);
            if (durationMinutes == null || durationMinutes <= 0) {
                ServletUtil.flashError(request,
                        "Phim chưa khai báo thời lượng nên không thể tính giờ kết thúc. "
                        + "Hãy cập nhật thời lượng phim trước.");
                redirectBack(request, response, "/admin/showtimes");
                return;
            }
            LocalDateTime endDateTime = startDateTime.plusMinutes(durationMinutes);

            String format = ServletUtil.param(request, "format");
            String version = ServletUtil.param(request, "version");
            String language = ServletUtil.param(request, "language");

            int existingId = intParam(request, "id", 0);
            int totalDays = Math.max(1, repeatDays);
            List<Showtime> batch = new java.util.ArrayList<>(totalDays);
            for (int i = 0; i < totalDays; i++) {
                Showtime showtime = new Showtime();
                showtime.setId(i == 0 ? existingId : 0);
                showtime.setFilmId(filmId);
                showtime.setCinemaId(cinemaId);
                showtime.setRoomId(roomId);
                showtime.setStartTime(startDateTime.plusDays(i));
                showtime.setEndTime(endDateTime.plusDays(i));
                showtime.setBasePrice(basePrice);
                showtime.setFormat(format);
                showtime.setVersion(version);
                showtime.setLanguage(language);
                batch.add(showtime);
            }
            // Ca loat nam trong MOT transaction: ngay nao vuong thi khong ngay nao duoc tao.
            // BUG-06: doi tham so trong yeu cua mot suat DA BAN VE bi chan, tru khi quan ly tich
            // o xac nhan anh huong. Khi do he thong bat buoc gui thong bao cho moi nguoi giu ve.
            boolean confirmImpact = "true".equalsIgnoreCase(ServletUtil.param(request, "confirmImpact"))
                    || "on".equalsIgnoreCase(ServletUtil.param(request, "confirmImpact"));
            int count = adminService.saveShowtimes(batch, actor, confirmImpact);
            if (count > 1) {
                ServletUtil.flashSuccess(request, "Đã tạo thành công " + count + " suất chiếu lặp lại theo lịch.");
            } else {
                ServletUtil.flashSuccess(request, "Đã lưu suất chiếu thành công.");
            }
        }
        redirectBack(request, response, "/admin/showtimes");
    }

    private void handleUserPost(HttpServletRequest request, HttpServletResponse response, User actor) throws IOException {
        String action = ServletUtil.param(request, "action");
        int userId = intParam(request, "id", -1);
        if ("lock".equals(action)) {
            String reason = ServletUtil.param(request, "lockReason");
            adminService.lockMemberDirectly(userId, reason, actor);
            ServletUtil.flashSuccess(request, "Đã khóa tài khoản thành công.");
        } else if ("unlock".equals(action)) {
            adminService.unlockMember(userId, actor);
            ServletUtil.flashSuccess(request, "Đã mở khóa tài khoản thành công.");
        } else if ("changeTier".equals(action)) {
            String newTier = ServletUtil.param(request, "membershipTier");
            adminService.updateUserMembershipTier(userId, newTier, actor);
            ServletUtil.flashSuccess(request, "Đã nâng/cập nhật hạng thành viên.");
        } else {
            User user = new User();
            user.setUsername(ServletUtil.param(request, "username"));
            user.setFullName(ServletUtil.param(request, "fullName"));
            user.setEmail(ServletUtil.param(request, "email"));
            user.setPasswordHash(ServletUtil.param(request, "password"));
            user.setPhone(ServletUtil.param(request, "phone"));
            user.setAddress(ServletUtil.param(request, "address"));
            adminService.createMember(user, actor);
            ServletUtil.flashSuccess(request, "Đã tạo member mới.");
        }
        redirectBack(request, response, "/admin/users");
    }

    /**
     * Quan ly tai khoan nhan vien quay ve.
     *
     * <p>Dung thao tac staff-only (cot {@code IsLocked}) giong trang thanh vien,
     * chu KHONG dung {@code setUserDeleted}: co che {@code Deleted} khien nguoi bi
     * khoa nhan thong bao "sai mat khau" va khong khang cao duoc.</p>
     */
    private void handleStaffPost(HttpServletRequest request, HttpServletResponse response, User actor) throws IOException {
        String action = ServletUtil.param(request, "action");
        int userId = intParam(request, "id", -1);
        if ("lock".equals(action)) {
            adminService.lockStaffDirectly(userId, ServletUtil.param(request, "lockReason"), actor);
            ServletUtil.flashSuccess(request, "Đã khóa tài khoản nhân viên.");
        } else if ("unlock".equals(action)) {
            adminService.unlockStaff(userId, actor);
            ServletUtil.flashSuccess(request, "Đã mở khóa tài khoản nhân viên.");
        } else if ("delete".equals(action)) {
            adminService.deleteStaff(userId, actor);
            ServletUtil.flashSuccess(request, "Đã xóa tài khoản nhân viên thành công.");
        } else if ("reassign".equals(action)) {
            adminService.reassignCinemaScopedUser(userId, intParam(request, "cinemaId", 0), actor);
            ServletUtil.flashSuccess(request, "Đã điều chuyển nhân viên và thu hồi phiên đăng nhập cũ.");
        } else {
            User staff = new User();
            staff.setUsername(ServletUtil.param(request, "username"));
            staff.setFullName(ServletUtil.param(request, "fullName"));
            staff.setEmail(ServletUtil.param(request, "email"));
            staff.setPasswordHash(ServletUtil.param(request, "password"));
            staff.setPhone(ServletUtil.param(request, "phone"));
            staff.setAddress(ServletUtil.param(request, "address"));
            int cinemaId = intParam(request, "cinemaId", 0);
            if (cinemaId > 0) {
                staff.setCinemaId(cinemaId);
            }
            adminService.createStaff(staff, actor);
            ServletUtil.flashSuccess(request, "Đã tạo tài khoản nhân viên quầy vé mới.");
        }
        redirectBack(request, response, "/admin/staff");
    }

    private void handleCommentPost(HttpServletRequest request, HttpServletResponse response, User actor) throws IOException {
        String action = ServletUtil.param(request, "action");
        int commentId = intParam(request, "id", -1);
        if ("warn".equals(action)) {
            adminService.warnUserForComment(commentId, actor);
            ServletUtil.flashSuccess(request, "Đã xử lý cảnh cáo người dùng vi phạm bình luận.");
        } else if ("lock".equals(action)) {
            String reason = ServletUtil.param(request, "lockReason");
            adminService.lockUserForComment(commentId, reason, actor);
            ServletUtil.flashSuccess(request, "Đã khóa tài khoản người dùng vi phạm.");
        } else if ("clear".equals(action)) {
            adminService.clearCommentReport(commentId, actor);
            ServletUtil.flashSuccess(request, "Đã gỡ báo cáo bình luận.");
        } else if ("delete".equals(action)) {
            adminService.deleteComment(commentId, actor);
            ServletUtil.flashSuccess(request, "Đã xóa bình luận.");
        }
        redirectBack(request, response, "/admin/comments");
    }

    private void handleAppealPost(HttpServletRequest request, HttpServletResponse response, User actor) throws IOException {
        String action = ServletUtil.param(request, "action");
        int appealId = intParam(request, "id", -1);
        String adminResponse = ServletUtil.param(request, "adminResponse");
        AppealResolutionResult result;
        if ("approve".equals(action)) {
            result = adminService.resolveAppeal(appealId, true, adminResponse, actor);
            ServletUtil.flashSuccess(request, "Đã phê duyệt đơn kháng cáo.");
        } else if ("reject".equals(action)) {
            result = adminService.resolveAppeal(appealId, false, adminResponse, actor);
            ServletUtil.flashSuccess(request, "Đã từ chối đơn kháng cáo.");
        } else {
            throw new BookingException(400, "Hành động xử lý đơn kháng cáo không hợp lệ.");
        }
        if (result.requiresRefundWorkflow()) {
            ServletUtil.flashError(request,
                    "Yêu cầu hoàn tiền phải được xử lý tại tab Hoàn tiền trong Quản lý đơn.");
            String encodedTicket = URLEncoder.encode(
                    result.ticketCode(), StandardCharsets.UTF_8);
            response.setStatus(HttpServletResponse.SC_SEE_OTHER);
            response.setHeader("Location", request.getContextPath()
                    + "/admin/orders?tab=refund&ticketCode=" + encodedTicket);
            return;
        }
        redirectBack(request, response, "/admin/appeals");
    }

    private void handleComboPost(HttpServletRequest request, HttpServletResponse response, User actor)
            throws ServletException, IOException {
        String action = ServletUtil.param(request, "action");
        switch (action) {
            case "delete" -> {
                adminService.deleteCombo(intParam(request, "id", -1), actor);
                ServletUtil.flashSuccess(request, "Đã xóa combo.");
            }
            case "toggleStatus" -> {
                String status = ServletUtil.param(request, "status");
                adminService.updateComboStatus(intParam(request, "id", -1), status, actor);
                ServletUtil.flashSuccess(request, "active".equals(status)
                        ? "Đã mở bán combo trở lại."
                        : "Đã ngừng bán combo. Combo không còn hiện ở trang đặt vé.");
            }
            default -> {
                ComboFood combo = new ComboFood();
                combo.setId(intParam(request, "id", 0));
                combo.setName(ServletUtil.param(request, "name"));
                combo.setPrice(decimalParam(request, "price", BigDecimal.ZERO));
                combo.setDescription(ServletUtil.param(request, "description"));
                combo.setStatus(ServletUtil.param(request, "status"));
                // CB-01: rong = combo dung chung toan he thong (chi admin chon duoc).
                // Voi manager, AdminService.saveCombo ghi de bang cum rap cua chinh ho.
                combo.setCinemaId(nullablePositiveInt(request.getParameter("cinemaId")));

                // Giu anh cu neu khong upload anh moi (giong luong Phim/Rap)
                String image = handleFileUpload(request, "imageFile");
                if (image == null || image.isBlank()) {
                    image = ServletUtil.param(request, "image");
                }
                combo.setImage(image);

                adminService.saveCombo(combo, actor);
                ServletUtil.flashSuccess(request, "Đã lưu combo thành công.");
            }
        }
        redirectBack(request, response, "/admin/combos");
    }

    private void handlePromotionPost(HttpServletRequest request, HttpServletResponse response, User actor) throws IOException {
        String action = ServletUtil.param(request, "action");
        if ("delete".equals(action)) {
            boolean hardDeleted = adminService.deletePromotion(intParam(request, "id", -1), actor);
            ServletUtil.flashSuccess(request, hardDeleted
                    ? "Đã xóa khuyến mãi vì chưa có dữ liệu phụ thuộc."
                    : "Khuyến mãi đã được chuyển sang Inactive để bảo toàn lịch sử phát hành/sử dụng.");
        } else {
            Promotion promotion = new Promotion();
            promotion.setId(intParam(request, "id", 0));
            promotion.setCode(ServletUtil.param(request, "code").toUpperCase());
            promotion.setDescription(ServletUtil.param(request, "description"));
            promotion.setDiscountPercent(nullableDoubleParam(request, "discountPercent"));
            promotion.setMaxDiscount(decimalParam(request, "maxDiscount", null));
            promotion.setStartDate(nullableDateParam(request, "startDate"));
            promotion.setEndDate(nullableDateParam(request, "endDate"));
            promotion.setConditionsJson(ServletUtil.param(request, "conditionsJson"));
            promotion.setUsageLimit(nullableIntParam(request, "usageLimit"));
            promotion.setStatus(ServletUtil.param(request, "status"));
            promotion.setVoucherType(ServletUtil.param(request, "voucherType"));
            promotion.setTargetTier(ServletUtil.param(request, "targetTier"));
            promotion.setPointsRequired(intParam(request, "pointsRequired", 0));
            promotion.setPerUserLimit(intParam(request, "perUserLimit", 0));
            adminService.savePromotion(promotion, actor);
            ServletUtil.flashSuccess(request, "Đã lưu khuyến mãi.");
        }
        redirectBack(request, response, "/admin/promotions");
    }

    /**
     * Ly do bat buoc cho mot quyet dinh dung tien (BUG-11, BUG-10).
     *
     * <p>Ca man hinh lan tang service deu chan: form co {@code required} de bao nguoi dung som,
     * con day va {@code AdminService} chan that de mot request gui thang khong lot qua.</p>
     */
    private String requiredReason(HttpServletRequest request, String message) {
        String reason = ServletUtil.param(request, "refundReason");
        if (reason == null || reason.isBlank()) {
            throw new BookingException(400, message);
        }
        return reason.trim();
    }

    /**
     * Checkbox HTML chi gui gia tri khi duoc tick; vang mat nghia la khong tick.
     *
     * <p>Doc tuong minh o day de mot request gui thang (khong qua form) cung phai neu ro y dinh
     * bo qua dieu kien, thay vi mac dinh duoc bo qua.</p>
     */
    private boolean checkboxTicked(HttpServletRequest request, String name) {
        String raw = ServletUtil.param(request, name);
        return "on".equalsIgnoreCase(raw) || "true".equalsIgnoreCase(raw) || "1".equals(raw);
    }

    private void requireGlobalAdmin(User actor) {
        if (!isGlobalAdmin(actor)) {
            throw new BookingException(403, "Chức năng toàn hệ thống chỉ dành cho Admin.");
        }
    }

    private boolean isGlobalAdmin(User actor) {
        if (actor == null) return false;
        String role = actor.getRole();
        return AppConstants.ROLE_ADMIN.equalsIgnoreCase(role);
    }

    private void handleOrderPost(HttpServletRequest request, HttpServletResponse response, User actor) throws IOException {
        String action = ServletUtil.param(request, "action");
        if ("redeem".equals(action)) {
            adminService.redeemTicket(ServletUtil.param(request, "ticketCode"), actor);
            ServletUtil.flashSuccess(request, "Đã check-in vé thành công.");
        } else if ("markPaid".equals(action)) {
            adminService.markCounterOrderPaid(intParam(request, "id", -1), actor);
            ServletUtil.flashSuccess(request, "Đã xác nhận thu tiền tại quầy. Khách đã được cộng điểm loyalty.");
        } else if ("cancel".equals(action)) {
            adminService.cancelOrder(intParam(request, "id", -1), actor);
            ServletUtil.flashSuccess(request, "Đã hủy đơn.");
        } else if ("approveRefund".equals(action)) {
            int orderId = intParam(request, "id", -1);
            OrderRecord order = adminService.findOrderById(orderId)
                    .orElseThrow(() -> new BookingException(404, "Không tìm thấy đơn hàng."));
            String refundReason = requiredReason(request,
                    "Vui lòng nhập lý do duyệt hoàn tiền.");
            // A.1 (BUG-11, INV-8): mac dinh KHONG bo qua dieu kien. Truoc day cho nay truyen `true`
            // CUNG cho moi lan duyet, nen hai luat trong AdminService.refundOrder — chan ve da
            // check-in va chan khi qua cutoff — chua bao gio chay trong ung dung that; chung chi
            // xanh vi Bug11RefundPolicyIT goi thang service. Chi khi quan ly tick "Bo qua dieu kien
            // hoan tien" moi truyen true, va khi do service bat buoc phai co ly do.
            boolean overrideRestrictions = checkboxTicked(request, "overrideRefundRestrictions");
            adminService.refundOrder(orderId, order.getTotalAmount(), refundReason, actor, overrideRestrictions);
            ServletUtil.flashSuccess(request, overrideRestrictions
                    ? "Đã duyệt hoàn tiền cho đơn vé #" + orderId + " (đã bỏ qua điều kiện hoàn tiền)."
                    : "Đã duyệt hoàn tiền và hủy đơn vé #" + orderId + ".");
        } else if ("rejectRefund".equals(action)) {
            int orderId = intParam(request, "id", -1);
            String rejectReason = requiredReason(request,
                    "Vui lòng nhập lý do từ chối hoàn tiền.");
            adminService.rejectRefund(orderId, rejectReason, actor);
            ServletUtil.flashSuccess(request, "Đã từ chối hoàn tiền cho đơn #" + orderId + ".");
        }
        redirectBack(request, response, "/admin/orders");
    }

    private void handleCustomContentPost(HttpServletRequest request, HttpServletResponse response, User actor) throws ServletException, IOException {
        try {
            int contentCinemaId = contentCinemaForWrite(request, actor);
            String type = ServletUtil.param(request, "type"); // "cinetag", "corner", "event", "special"
            String action = ServletUtil.param(request, "action"); // "save", "delete"
            
            String settingKey = switch (type) {
                case "cinetag" -> "cinetags_data";
                case "corner" -> "corner_items_data";
                case "event" -> "events_data";
                case "special" -> "special_cinemas_data";
                default -> throw new BookingException(400, "Loại nội dung không hợp lệ");
            };
            
            String existingJson = cinemaContentService.getContent(contentCinemaId, settingKey);
            
            JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
            if (existingJson != null && !existingJson.isBlank()) {
                try (JsonReader reader = Json.createReader(new StringReader(existingJson))) {
                    JsonArray array = reader.readArray();
                    for (int i = 0; i < array.size(); i++) {
                        arrayBuilder.add(array.getJsonObject(i));
                    }
                } catch (RuntimeException ex) {
                    LOGGER.log(Level.WARNING, "Noi dung JSON cu khong hop le; tu choi ghi de tranh mat du lieu", ex);
                    throw new BookingException(400, "Nội dung hiện tại không phải JSON hợp lệ.");
                }
            }
            
            JsonArray currentArray = arrayBuilder.build();
            JsonArrayBuilder newArrayBuilder = Json.createArrayBuilder();
            
            if ("delete".equals(action)) {
                int index = intParam(request, "index", -1);
                if (index >= 0 && index < currentArray.size()) {
                    for (int i = 0; i < currentArray.size(); i++) {
                        if (i != index) {
                            newArrayBuilder.add(currentArray.getJsonObject(i));
                        }
                    }
                } else {
                    newArrayBuilder = arrayBuilder;
                }
            } else {
                int index = intParam(request, "index", -1);
                
                // File upload logic
                String imageUrl = handleFileUpload(request, "imageFile");
                if (imageUrl == null || imageUrl.isBlank()) {
                    imageUrl = ServletUtil.param(request, "imageUrl");
                }
                
                JsonObjectBuilder objBuilder = Json.createObjectBuilder();
                if (type.equals("cinetag")) {
                    Double priceObj = nullableDoubleParam(request, "price");
                    double priceVal = priceObj != null ? priceObj : 0.0;
                    objBuilder.add("tag", ServletUtil.param(request, "tag"))
                              .add("name", ServletUtil.param(request, "name"))
                              .add("price", priceVal)
                              .add("imageUrl", imageUrl != null ? imageUrl : "");
                } else if (type.equals("corner")) {
                    String section = ServletUtil.param(request, "section");
                    String title = ServletUtil.param(request, "title");
                    String prefix = ServletUtil.param(request, "prefix");
                    String subtitle = ServletUtil.param(request, "subtitle");
                    if (prefix == null || prefix.isBlank()) {
                        prefix = subtitle;
                    }
                    String description = ServletUtil.param(request, "description");

                    objBuilder.add("section", section != null ? section : "")
                              .add("title", title != null ? title : "")
                              .add("subtitle", prefix != null ? prefix : "")
                              .add("prefix", prefix != null ? prefix : "")
                              .add("imageUrl", imageUrl != null ? imageUrl : "")
                              .add("description", description != null ? description : "")
                              .add("likes", intParam(request, "likes", 0))
                              .add("views", intParam(request, "views", 0));
                } else if (type.equals("event")) {
                    String title = ServletUtil.param(request, "title");
                    String targetUrl = ServletUtil.param(request, "targetUrl");
                    String description = ServletUtil.param(request, "description");
                    int filmId = intParam(request, "filmId", 0);

                    if (filmId > 0) {
                        var filmOpt = adminService.findFilmById(filmId,
                                CinemaContextResolver.scopedReadActor(actor, contentCinemaId));
                        if (filmOpt.isPresent()) {
                            Film f = filmOpt.get();
                            if (title == null || title.isBlank()) {
                                title = f.getTitle();
                            }
                            if (imageUrl == null || imageUrl.isBlank()) {
                                imageUrl = (f.getBanner() != null && !f.getBanner().isBlank()) ? f.getBanner() : f.getThumbnail();
                            }
                            if (description == null || description.isBlank()) {
                                description = f.getDescription();
                            }
                        }
                    }

                    objBuilder.add("section", ServletUtil.param(request, "section"))
                              .add("title", title != null ? title : "")
                              .add("imageUrl", imageUrl != null ? imageUrl : "")
                              .add("targetUrl", targetUrl != null ? targetUrl : "")
                              .add("description", description != null ? description : "");
                    if (filmId > 0) {
                        objBuilder.add("filmId", filmId);
                    }
                } else if (type.equals("special")) {
                    objBuilder.add("title", ServletUtil.param(request, "title"))
                              .add("address", ServletUtil.param(request, "address"))
                              .add("imageUrl", imageUrl != null ? imageUrl : "")
                              .add("description", ServletUtil.param(request, "description"));
                }
                
                JsonObject newItem = objBuilder.build();
                
                if (index >= 0 && index < currentArray.size()) {
                    for (int i = 0; i < currentArray.size(); i++) {
                        if (i == index) {
                            newArrayBuilder.add(newItem);
                        } else {
                            newArrayBuilder.add(currentArray.getJsonObject(i));
                        }
                    }
                } else {
                    for (int i = 0; i < currentArray.size(); i++) {
                        newArrayBuilder.add(currentArray.getJsonObject(i));
                    }
                    newArrayBuilder.add(newItem);
                }
            }
            
            StringWriter sw = new StringWriter();
            try (JsonWriter writer = Json.createWriter(sw)) {
                writer.writeArray(newArrayBuilder.build());
            }
            
            cinemaContentService.saveContent(contentCinemaId, settingKey, sw.toString(), actor);
            ServletUtil.flashSuccess(request, "Đã cập nhật nội dung thành công.");
            
            String sub = ServletUtil.param(request, "sub");
            if (sub == null || sub.isBlank()) {
                sub = type;
            }
            String section = ServletUtil.param(request, "section");
            String redirectTarget = "/admin/films?tab=custom&sub=" + sub;
            if (section != null && !section.isBlank()) {
                redirectTarget += "&section=" + section;
            }
            redirectBack(request, response, redirectTarget);
        } catch (BookingException ex) {
            ServletUtil.flashError(request, ex.getMessage());
            redirectBack(request, response, "/admin/films?tab=custom");
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Loi khi cap nhat noi dung custom", ex);
            ServletUtil.flashError(request, "Không thể lưu nội dung: " + ex.getMessage());
            redirectBack(request, response, "/admin/films?tab=custom");
        }
    }

    private int contentCinemaForWrite(HttpServletRequest request, User actor) {
        if (CinemaCapabilityPolicy.isManager(actor)) {
            return CinemaCapabilityPolicy.requireManagerCinema(actor);
        }
        CinemaCapabilityPolicy.requireAdmin(actor);
        Object selected = request.getSession().getAttribute(CinemaContextResolver.SESSION_ADMIN_CINEMA_ID);
        if (!(selected instanceof Integer cinemaId) || cinemaId <= 0) {
            throw new BookingException(400, "Admin cần chọn một rạp cụ thể trước khi chỉnh sửa nội dung.");
        }
        return cinemaId;
    }

    private void handleRefundPolicyPost(HttpServletRequest request, HttpServletResponse response, User actor)
            throws IOException {
        String title = ServletUtil.param(request, "title");
        String body = request.getParameter("bodyText");
        if (body == null) body = "";
        String action = ServletUtil.param(request, "action");
        if ("publish".equals(action)) {
            policyService.publish(title, body, actor.getId());
            ServletUtil.flashSuccess(request, "Đã xuất bản điều kiện hoàn tiền.");
        } else {
            policyService.saveDraft(title, body, actor.getId());
            ServletUtil.flashSuccess(request, "Đã lưu bản nháp điều kiện hoàn tiền.");
        }
        response.sendRedirect(request.getContextPath() + "/admin/content/refund-policy");
    }

    private void handleTermsOfUsePost(HttpServletRequest request, HttpServletResponse response, User actor)
            throws IOException {
        String title = ServletUtil.param(request, "title");
        String body = request.getParameter("bodyText");
        if (body == null) body = "";
        String action = ServletUtil.param(request, "action");
        if ("publish".equals(action)) {
            policyService.publishTerms(title, body, actor.getId());
            ServletUtil.flashSuccess(request, "Đã xuất bản thỏa thuận sử dụng.");
        } else {
            policyService.saveDraftTerms(title, body, actor.getId());
            ServletUtil.flashSuccess(request, "Đã lưu bản nháp thỏa thuận sử dụng.");
        }
        response.sendRedirect(request.getContextPath() + "/admin/content/terms-of-use");
    }

    private void handleAboutUsPost(HttpServletRequest request, HttpServletResponse response, User actor)
            throws ServletException, IOException {
        // 1. Doc thong tin 4 thanh vien
        javax.json.JsonArrayBuilder membersBuilder = javax.json.Json.createArrayBuilder();
        for (int i = 1; i <= 4; i++) {
            String name = ServletUtil.param(request, "memberName_" + i);
            String role = ServletUtil.param(request, "memberRole_" + i);
            String customUrl = ServletUtil.param(request, "memberImageUrl_" + i);
            String uploadedUrl = handleFileUpload(request, "memberImageFile_" + i);

            String finalUrl = (uploadedUrl != null && !uploadedUrl.isBlank()) ? uploadedUrl
                    : ((customUrl != null && !customUrl.isBlank()) ? customUrl : ("/images/" + i + ".jpg"));

            membersBuilder.add(javax.json.Json.createObjectBuilder()
                    .add("id", i)
                    .add("name", name.isBlank() ? ("Thành viên " + i) : name)
                    .add("role", role.isBlank() ? "Thành viên CineBook" : role)
                    .add("imageUrl", finalUrl));
        }
        adminService.saveSetting("about_us_members_data", membersBuilder.build().toString(), actor);

        // 2. Doc thong tin 3 khung gia tri cot loi (Su manh, Trai nghiem dat ve, He thong rap)
        javax.json.JsonArrayBuilder featuresBuilder = javax.json.Json.createArrayBuilder();
        String[] defaultKeys = {"mission", "experience", "cinemas"};
        String[] defaultTitles = {"Sứ mệnh", "Trải nghiệm đặt vé", "Hệ thống rạp"};
        String[] defaultIcons = {"target", "ticket", "screen"};

        for (int i = 0; i < 3; i++) {
            String title = ServletUtil.param(request, "featureTitle_" + (i + 1));
            String desc = ServletUtil.param(request, "featureDesc_" + (i + 1));
            String icon = ServletUtil.param(request, "featureIcon_" + (i + 1));

            featuresBuilder.add(javax.json.Json.createObjectBuilder()
                    .add("key", defaultKeys[i])
                    .add("title", title.isBlank() ? defaultTitles[i] : title)
                    .add("description", desc.isBlank() ? "" : desc)
                    .add("icon", icon.isBlank() ? defaultIcons[i] : icon));
        }
        adminService.saveSetting("about_us_features_data", featuresBuilder.build().toString(), actor);

        ServletUtil.flashSuccess(request, "Đã cập nhật nội dung trang Về Chúng Tôi thành công.");
        response.sendRedirect(request.getContextPath() + "/admin/content/about-us");
    }

    private String handleFileUpload(HttpServletRequest request, String partName) throws ServletException, IOException {
        if (request.getContentType() != null && request.getContentType().toLowerCase().startsWith("multipart/")) {
            Part part = request.getPart(partName);
            if (part != null && part.getSize() > 0) {
                try (java.io.InputStream input = part.getInputStream()) {
                    String fileName = ImageUploadUtil.validateAndStore(
                            input, part.getSubmittedFileName(), part.getSize(), UploadServlet.uploadDirectory());
                    // Store a deployment-independent path. JSPs resolve it in the current
                    // context and REST/Next clients prepend their configured asset base.
                    return "/uploads/" + fileName;
                }
            }
        }
        return null;
    }

    private void cleanupUploadedMedia(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return;
        }
        try {
            java.nio.file.Path directory = UploadServlet.uploadDirectory().toAbsolutePath().normalize();
            java.nio.file.Path file = directory.resolve(java.nio.file.Path.of(storedPath).getFileName()).normalize();
            if (file.getParent().equals(directory)) {
                java.nio.file.Files.deleteIfExists(file);
            }
        } catch (IOException | RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Khong the don file upload sau khi luu phim that bai", ex);
        }
    }

    private void handleNotificationPost(HttpServletRequest request, HttpServletResponse response, User actor)
            throws IOException {
        String action = ServletUtil.param(request, "action");
        if ("readAll".equals(action)) {
            adminService.markAllNotificationsRead(actor);
            ServletUtil.flashSuccess(request, "Đã đánh dấu tất cả thông báo là đã đọc.");
        } else if ("read".equals(action)) {
            int id = intParam(request, "id", 0);
            if (id > 0) adminService.markNotificationRead(id, actor);
        } else if ("deleteRoom".equals(action)) {
            int roomId = intParam(request, "roomId", 0);
            if (roomId > 0) {
                adminService.deleteRoom(roomId, actor, true);
                ServletUtil.flashSuccess(request, "Đã xóa vĩnh viễn phòng chiếu thành công.");
            }
        }
        redirectBack(request, response, "/admin/notifications");
    }

    /**
     * Doc moc bat dau cua suat chieu tu form (ST-02).
     *
     * <p>Chap nhan hai dang: cap {@code showDate} + {@code startTimeOnly} (form hien tai), hoac
     * {@code startTime} dang ISO (form cu / goi truc tiep). Tra {@code null} khi thieu du lieu —
     * <b>khong</b> tu bia ra gio mac dinh.</p>
     *
     * <p>Gio nhap phai theo he 24 gio {@code HH:mm}. Rieng {@code 24:00} — mot cach viet nua dem
     * ma nguoi dung Viet Nam hay go — duoc chuyen thanh {@code 00:00} cua ngay ke tiep thay vi
     * bi tu choi, vi do dung la y dinh cua nguoi nhap. Khong bao gio luu chuoi "24:00".</p>
     */
    private LocalDateTime parseShowtimeStart(HttpServletRequest request) {
        String showDate = ServletUtil.param(request, "showDate");
        String startTime = ServletUtil.param(request, "startTimeOnly");
        if (!showDate.isBlank() && !startTime.isBlank()) {
            try {
                LocalDate date = LocalDate.parse(showDate.trim());
                String normalized = startTime.trim();
                boolean rollToNextDay = normalized.startsWith("24:");
                if (rollToNextDay) {
                    normalized = "00:" + normalized.substring(3);
                }
                java.time.LocalTime time = java.time.LocalTime.parse(normalized);
                LocalDateTime parsed = LocalDateTime.of(date, time);
                return rollToNextDay ? parsed.plusDays(1) : parsed;
            } catch (java.time.format.DateTimeParseException ex) {
                LOGGER.log(Level.FINE, "Ngay/gio suat chieu khong hop le: {0} {1}",
                        new Object[] {showDate, startTime});
                return null;
            }
        }
        return nullableDateTimeParam(request, "startTime");
    }

    /**
     * Danh sach rap ma form phim muon gan, phan biet duoc "bo chon het" voi "khong gui".
     *
     * <p>
     * <b>Loi da sua (N-14).</b> Voi {@code <input type="checkbox" name="cinemaIds">},
     * khi admin bo chon het thi trinh duyet <b>khong gui</b> tham so {@code cinemaIds}
     * nao ca. {@code parseIntList(null)} tra {@code null}, va {@code null} o tang service
     * nghia la "khong dong toi mapping" — nen admin bam Luu thay flash "Da luu" trong khi
     * mapping cu con nguyen. Su phan biet null/rong ma tang service viet chu thich rat ky
     * thi form khong co cach nao dien dat.
     * </p>
     *
     * <p>
     * Hidden sentinel {@code cinemaIdsPresent} la thu duy nhat phan biet duoc: no luon
     * duoc gui cung form, nen "co sentinel ma khong co cinemaIds" = <i>bo chon het</i>,
     * con "khong co sentinel" = form khac khong quan ly mapping rap.
     * </p>
     */
    private List<Integer> parseCinemaIds(HttpServletRequest request) {
        List<Integer> parsed = parseIntList(request.getParameterValues("cinemaIds"));
        if (parsed != null) {
            return parsed;
        }
        return "1".equals(request.getParameter("cinemaIdsPresent")) ? List.of() : null;
    }

    /**
     * Doc mot danh sach id tu tham so lap. Tra {@code null} khi form khong gui truong do —
     * nguoi goi phan biet duoc "khong dong toi" voi "chon rong".
     */
    private List<Integer> parseIntList(String[] raw) {
        if (raw == null) {
            return null;
        }
        List<Integer> values = new java.util.ArrayList<>(raw.length);
        for (String value : raw) {
            Integer parsed = nullablePositiveInt(value);
            if (parsed != null) {
                values.add(parsed);
            }
        }
        return values;
    }

    private Integer nullablePositiveInt(String raw) {
        try {
            int value = Integer.parseInt(raw == null ? "" : raw);
            return value > 0 ? value : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static String showtimesJsonForHtml(List<Showtime> showtimes) {
        JsonArrayBuilder array = Json.createArrayBuilder();
        for (Showtime showtime : showtimes) {
            array.add(Json.createObjectBuilder()
                    .add("id", showtime.getId())
                    .add("filmId", showtime.getFilmId())
                    .add("cinemaId", showtime.getCinemaId())
                    .add("roomId", showtime.getRoomId())
                    .add("filmTitle", safeString(showtime.getFilmTitle()))
                    .add("cinemaName", safeString(showtime.getCinemaName()))
                    .add("roomName", safeString(showtime.getRoomName()))
                    .add("startTime", showtime.getStartTime() == null ? "" : showtime.getStartTime().toString())
                    .add("endTime", showtime.getEndTime() == null ? "" : showtime.getEndTime().toString())
                    .add("basePrice", showtime.getBasePrice() == null ? BigDecimal.ZERO : showtime.getBasePrice())
                    .add("saleStatus", showtime.getSaleStatus())
                    .add("availableSeats", showtime.getAvailableSeats())
                    .add("totalSeats", showtime.getTotalSeats())
                    .add("deleteNotBefore", showtime.getDeleteNotBefore() == null
                            ? "" : showtime.getDeleteNotBefore().toString()));
        }
        // The result is embedded in a raw script-data element. These escapes stop
        // user-entered titles such as </script> from terminating that element.
        return array.build().toString()
                .replace("&", "\\u0026")
                .replace("<", "\\u003c")
                .replace(">", "\\u003e")
                .replace("\u2028", "\\u2028")
                .replace("\u2029", "\\u2029");
    }

    private static String safeString(String value) {
        return value == null ? "" : value;
    }

    private LocalDate nullableDate(String raw) {
        try {
            return raw == null || raw.isBlank() ? null : LocalDate.parse(raw);
        } catch (java.time.format.DateTimeParseException ex) {
            return null;
        }
    }
}
