package com.mycompany.website.ban.ve.xem.phim.controller.member;

import com.mycompany.website.ban.ve.xem.phim.config.AppConstants;
import com.mycompany.website.ban.ve.xem.phim.dao.UserDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcUserDAO;
import com.mycompany.website.ban.ve.xem.phim.model.Promotion;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import com.mycompany.website.ban.ve.xem.phim.service.LoyaltyService;
import com.mycompany.website.ban.ve.xem.phim.util.ServletUtil;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class LoyaltyServlet extends HttpServlet {
    private final LoyaltyService loyaltyService = new LoyaltyService();
    private final UserDAO userDAO = new JdbcUserDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User currentUser = currentUser(request);
        if (currentUser == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        // Fetch fresh user data
        User user = userDAO.findById(currentUser.getId()).orElse(currentUser);
        request.getSession().setAttribute(AppConstants.SESSION_USER, user);

        ServletUtil.consumeFlash(request);

        List<Promotion> redeemablePromos = loyaltyService.listRedeemablePromotionsForUser(user.getId());

        request.setAttribute("user", user);
        request.setAttribute("redeemablePromos", redeemablePromos);
        request.setAttribute("redemptionKey", UUID.randomUUID().toString());
        request.setAttribute("myVouchers", loyaltyService.getUserVouchers(user.getId()));
        request.setAttribute("pointHistory", loyaltyService.getUserPointTransactions(user.getId()));

        request.getRequestDispatcher("/WEB-INF/views/member/loyalty.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User currentUser = currentUser(request);
        if (currentUser == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        String action = ServletUtil.param(request, "action");
        if (!"redeem".equals(action)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Thao tac loyalty khong hop le.");
            return;
        }

        try {
            int promotionId = Integer.parseInt(ServletUtil.param(request, "promotionId"));
            if (promotionId <= 0) {
                throw new BookingException(400, "Ma voucher doi diem khong hop le.");
            }
            String redemptionKey = ServletUtil.param(request, "redemptionKey");
            loyaltyService.redeemVoucherWithPoints(currentUser.getId(), promotionId, redemptionKey);
            ServletUtil.flashSuccess(request,
                    "Đã đổi điểm thành công! Voucher cá nhân đã được thêm vào kho của bạn.");
        } catch (NumberFormatException ex) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Ma voucher doi diem khong hop le.");
            return;
        } catch (BookingException ex) {
            response.sendError(ex.getStatusCode(), ex.getMessage());
            return;
        }
        response.sendRedirect(request.getContextPath() + "/member/loyalty");
    }

    private User currentUser(HttpServletRequest request) {
        return (User) request.getSession().getAttribute(AppConstants.SESSION_USER);
    }
}
