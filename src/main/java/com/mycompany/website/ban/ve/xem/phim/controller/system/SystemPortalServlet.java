package com.mycompany.website.ban.ve.xem.phim.controller.system;

import com.mycompany.website.ban.ve.xem.phim.controller.BasePortalServlet;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import com.mycompany.website.ban.ve.xem.phim.service.HoldSweeper;
import com.mycompany.website.ban.ve.xem.phim.util.ServletUtil;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class SystemPortalServlet extends BasePortalServlet {
    private static final java.util.logging.Logger LOGGER =
            java.util.logging.Logger.getLogger(SystemPortalServlet.class.getName());
    private final AdminService adminService = new AdminService();
    private final HoldSweeper holdSweeper = new HoldSweeper();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            switch (request.getServletPath()) {
                case "/system/managers" -> {
                    request.setAttribute("managers", adminService.listUsers("manager"));
                    request.setAttribute("cinemas", adminService.listCinemas());
                    forward(request, response, "/WEB-INF/views/system/managers.jsp");
                }
                case "/system/config" -> {
                    request.setAttribute("settings", adminService.listSettings());
                    forward(request, response, "/WEB-INF/views/system/config.jsp");
                }
                case "/system/audit-logs" -> {
                    int page = intParam(request, "page", 1);
                    var auditPage = adminService.listAuditLogs(page, 50);
                    request.setAttribute("logs", auditPage.items());
                    request.setAttribute("auditPage", auditPage);
                    forward(request, response, "/WEB-INF/views/system/audit-logs.jsp");
                }
                case "/system/backup" ->
                    response.sendRedirect(request.getContextPath() + "/system/config");
                default -> response.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        } catch (BookingException ex) {
            renderError(request, response, ex, LOGGER);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User actor = currentUser(request);
        try {
            switch (request.getServletPath()) {
                case "/system/managers" -> handleManagersPost(request, response, actor);
                case "/system/config" -> handleConfigPost(request, response, actor);
                case "/system/audit-logs" -> handleAuditLogPost(request, response, actor);
                case "/system/backup" -> handleBackupPost(request, response, actor);
                default -> response.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        } catch (BookingException ex) {
            ServletUtil.flashError(request, ex.getMessage());
            redirectBack(request, response, "/system/dashboard");
        }
    }

    private void handleManagersPost(HttpServletRequest request, HttpServletResponse response, User actor) throws IOException {
        String action = ServletUtil.param(request, "action");
        if ("lock".equals(action)) {
            adminService.setUserDeleted(intParam(request, "id", -1), true, actor, "LOCK_MANAGER");
            ServletUtil.flashSuccess(request, "Da khoa tai khoan manager.");
        } else if ("unlock".equals(action)) {
            adminService.setUserDeleted(intParam(request, "id", -1), false, actor, "UNLOCK_MANAGER");
            ServletUtil.flashSuccess(request, "Da mo khoa tai khoan manager.");
        } else if ("reassign".equals(action)) {
            adminService.reassignCinemaScopedUser(intParam(request, "id", -1),
                    intParam(request, "cinemaId", 0), actor);
            ServletUtil.flashSuccess(request, "Đã đổi rạp phụ trách và thu hồi phiên đăng nhập cũ.");
        } else {
            User user = new User();
            user.setUsername(ServletUtil.param(request, "username"));
            user.setFullName(ServletUtil.param(request, "fullName"));
            user.setEmail(ServletUtil.param(request, "email"));
            user.setPasswordHash(ServletUtil.param(request, "password"));
            user.setPhone(ServletUtil.param(request, "phone"));
            user.setAddress(ServletUtil.param(request, "address"));
            int cinemaId = intParam(request, "cinemaId", -1);
            user.setCinemaId(cinemaId > 0 ? cinemaId : null);
            adminService.createManager(user, actor);
            ServletUtil.flashSuccess(request, "Da tao manager moi.");
        }
        redirectBack(request, response, "/system/managers");
    }

    private void handleConfigPost(HttpServletRequest request, HttpServletResponse response, User actor) throws IOException {
        String action = ServletUtil.param(request, "action");
        if ("releaseHeldSeats".equals(action)) {
            int affected = adminService.releaseStaleHeldSeats(actor);
            ServletUtil.flashSuccess(request, "Da giai phong " + affected + " ghe bi giu qua han.");
            redirectBack(request, response, "/system/config");
            return;
        }
        if ("runSweeper".equals(action)) {
            // Chay tay dung lop sweeper nen — mot duong ma, khong co ban sao logic thu hai.
            HoldSweeper.SweepResult result = holdSweeper.sweepOnce();
            if (result.isSkipped()) {
                ServletUtil.flashError(request, "Sweeper khong chay duoc vong nay (dang bi tat hoac gap loi DB)."
                        + " Xem catalina log de biet chi tiet.");
            } else {
                ServletUtil.flashSuccess(request, "Sweeper da chay: " + result + ".");
            }
            redirectBack(request, response, "/system/config");
            return;
        }
        adminService.saveSetting(ServletUtil.param(request, "settingKey"), ServletUtil.param(request, "settingValue"), actor);
        ServletUtil.flashSuccess(request, "Da luu cau hinh.");
        redirectBack(request, response, "/system/config");
    }

    private void handleAuditLogPost(HttpServletRequest request, HttpServletResponse response, User actor) throws IOException {
        int olderThanDays = intParam(request, "olderThanDays", 90);
        int affected = adminService.clearAuditLogs(olderThanDays, actor);
        ServletUtil.flashSuccess(request, "Da don dep " + affected + " audit log cu hon " + olderThanDays + " ngay.");
        redirectBack(request, response, "/system/audit-logs");
    }

    private void handleBackupPost(HttpServletRequest request, HttpServletResponse response, User actor) throws IOException {
        String backupFile = adminService.backupDatabase(actor);
        ServletUtil.flashSuccess(request, "Da backup DB ra file: " + backupFile);
        response.sendRedirect(request.getContextPath() + "/system/audit-logs");
    }
}
