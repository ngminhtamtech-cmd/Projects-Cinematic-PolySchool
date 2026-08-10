package com.mycompany.website.ban.ve.xem.phim.controller.auth;

import com.mycompany.website.ban.ve.xem.phim.config.AppConstants;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import com.mycompany.website.ban.ve.xem.phim.util.ServletUtil;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class AppealServlet extends HttpServlet {
    private final AdminService adminService = new AdminService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        ServletUtil.consumeFlash(request);
        String email = request.getParameter("email");
        if (email != null && !email.isBlank()) {
            request.setAttribute("email", email);
        }
        String ticketCode = request.getParameter("ticketCode");
        if (ticketCode != null && !ticketCode.isBlank()) {
            request.setAttribute("ticketCode", ticketCode);
            request.setAttribute("reason", "Yêu cầu hoàn tiền cho mã vé: " + ticketCode.trim() + ". Lý do giải trình: ");
        }
        request.getRequestDispatcher("/WEB-INF/views/auth/appeal.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String email = ServletUtil.param(request, "email");
        String reason = ServletUtil.param(request, "reason");

        if (email.isBlank() || reason.isBlank()) {
            request.setAttribute(AppConstants.FLASH_ERROR, "Vui lòng nhập đầy đủ Email và Lý do kháng cáo.");
            request.setAttribute("email", email);
            request.setAttribute("reason", reason);
            request.getRequestDispatcher("/WEB-INF/views/auth/appeal.jsp").forward(request, response);
            return;
        }

        try {
            adminService.submitAppeal(email, reason);
            ServletUtil.flashSuccess(request, "Đơn kháng cáo của bạn đã được gửi thành công. Quản trị viên sẽ xem xét và phản hồi sớm nhất.");
            response.sendRedirect(request.getContextPath() + "/login");
        } catch (BookingException ex) {
            request.setAttribute(AppConstants.FLASH_ERROR, ex.getMessage());
            request.setAttribute("email", email);
            request.setAttribute("reason", reason);
            request.getRequestDispatcher("/WEB-INF/views/auth/appeal.jsp").forward(request, response);
        }
    }
}
