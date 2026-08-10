package com.mycompany.website.ban.ve.xem.phim.controller.admin;

import com.mycompany.website.ban.ve.xem.phim.config.AppConstants;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
import com.mycompany.website.ban.ve.xem.phim.service.CinemaContextResolver;
import com.mycompany.website.ban.ve.xem.phim.util.ServletUtil;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class AdminDashboardServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(AdminDashboardServlet.class.getName());

    /** Hien thi khi khong doc duoc so lieu — de nguoi dung khong doc nham thanh so 0. */
    private static final String UNKNOWN_COUNT = "—";
    private static final String[] COUNT_KEYS = {
        "filmCount", "cinemaCount", "roomCount", "showtimeCount", "memberCount",
        "promotionCount", "orderCount", "managerCount", "settingCount", "auditCount",
    };

    private final AdminService adminService;

    public AdminDashboardServlet() {
        this(new AdminService());
    }

    /** Chi dung cho test: thay service de kiem tra hanh vi khi khong doc duoc so lieu. */
    AdminDashboardServlet(AdminService adminService) {
        this.adminService = adminService;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        ServletUtil.consumeFlash(request);
        User user = (User) request.getSession().getAttribute(AppConstants.SESSION_USER);
        User operationalActor = user;
        Integer cinemaContextId = null;
        try {
            cinemaContextId = CinemaContextResolver.prepare(request, user, adminService.listCinemas());
            operationalActor = CinemaContextResolver.scopedReadActor(user, cinemaContextId);
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Khong khoi tao duoc bo loc rap cho dashboard.", ex);
            request.setAttribute("cinemaContextOptions", List.of());
        }

        try {
            // F-003: dem bang COUNT_BIG(*) trong AdminService. Truoc day trang nay lay .size() cua
            // cac danh sach da bi cat (don 50 dong, audit 50, phim/thanh vien 200, suat chieu 500)
            // nen khi vuot nguong, o so lieu dung yen o dung con so nguong.
            Map<String, Object> counts = new LinkedHashMap<>(adminService.dashboardCounts(operationalActor));
            if (cinemaContextId != null && user != null
                    && AppConstants.ROLE_ADMIN.equalsIgnoreCase(user.getRole())) {
                Map<String, Long> globalCounts = adminService.dashboardCounts(user);
                counts.put("managerCount", globalCounts.get("managerCount"));
                counts.put("settingCount", globalCounts.get("settingCount"));
                counts.put("auditCount", globalCounts.get("auditCount"));
            }
            counts.forEach(request::setAttribute);
            request.setAttribute("countsUnavailable", false);
        } catch (RuntimeException ex) {
            // Khong dat 0 cho tat ca: so 0 gia khien nguoi quan tri tin rang he thong dang trong.
            LOGGER.log(Level.SEVERE, "Khong doc duoc so lieu tong quan cho dashboard admin.", ex);
            for (String key : COUNT_KEYS) {
                request.setAttribute(key, UNKNOWN_COUNT);
            }
            request.setAttribute("countsUnavailable", true);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }

        loadOperationalData(request, operationalActor, user);
        request.getRequestDispatcher("/WEB-INF/views/admin/dashboard.jsp").forward(request, response);
    }

    /**
     * Chỉ bind dữ liệu báo cáo đã có sẵn trong AdminService. Mỗi khối được tải
     * độc lập để một truy vấn lỗi không làm mất các số liệu thật còn lại và tuyệt
     * đối không thay lỗi bằng dữ liệu mẫu hay số 0 giả.
     */
    private void loadOperationalData(HttpServletRequest request, User operationalActor, User sessionUser) {
        try {
            request.setAttribute("dailyRevenue", adminService.dailyRevenueRows(operationalActor));
            request.setAttribute("revenueUnavailable", false);
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Khong doc duoc doanh thu theo ngay cho dashboard.", ex);
            request.setAttribute("dailyRevenue", Collections.emptyList());
            request.setAttribute("revenueUnavailable", true);
        }

        try {
            request.setAttribute("topFilms", adminService.topFilms(operationalActor));
            request.setAttribute("topFilmsUnavailable", false);
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Khong doc duoc top phim cho dashboard.", ex);
            request.setAttribute("topFilms", Collections.emptyList());
            request.setAttribute("topFilmsUnavailable", true);
        }

        if (sessionUser != null && AppConstants.ROLE_ADMIN.equalsIgnoreCase(sessionUser.getRole())) {
            try {
                request.setAttribute("recentAuditLogs", adminService.listAuditLogs(1, 5).items());
                request.setAttribute("auditLogsUnavailable", false);
            } catch (RuntimeException ex) {
                LOGGER.log(Level.WARNING, "Khong doc duoc audit log gan day cho dashboard.", ex);
                request.setAttribute("recentAuditLogs", Collections.emptyList());
                request.setAttribute("auditLogsUnavailable", true);
            }
        } else {
            request.setAttribute("recentAuditLogs", Collections.emptyList());
            request.setAttribute("auditLogsUnavailable", false);
        }
    }
}
