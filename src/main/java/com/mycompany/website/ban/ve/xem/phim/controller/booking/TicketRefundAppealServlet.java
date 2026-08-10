package com.mycompany.website.ban.ve.xem.phim.controller.booking;

import com.mycompany.website.ban.ve.xem.phim.controller.BasePortalServlet;
import com.mycompany.website.ban.ve.xem.phim.dao.AdminNotificationDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.OrderDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.UserAppealDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcAdminNotificationDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcOrderDAO;
import com.mycompany.website.ban.ve.xem.phim.dao.impl.JdbcUserAppealDAO;
import com.mycompany.website.ban.ve.xem.phim.model.AdminNotification;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.model.UserAppeal;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import com.mycompany.website.ban.ve.xem.phim.service.RefundAppealPolicy;
import com.mycompany.website.ban.ve.xem.phim.util.JsonUtil;
import com.mycompany.website.ban.ve.xem.phim.util.ServletUtil;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Man hinh xin hoan tien ve (khang cao) cho member.
 *
 * <p>Dang ky URL nam trong {@code web.xml} theo quy uoc du an — khong dung
 * {@code @WebServlet} o day, vi khai bao ca hai noi se tao mapping trung
 * cho cung mot url-pattern va lam Tomcat tu choi deploy.</p>
 */
public class TicketRefundAppealServlet extends BasePortalServlet {
    private static final long serialVersionUID = 1L;
    private final OrderDAO orderDAO;
    private final UserAppealDAO userAppealDAO;
    private final AdminNotificationDAO notificationDAO;

    public TicketRefundAppealServlet() {
        this(new JdbcOrderDAO(), new JdbcUserAppealDAO(), new JdbcAdminNotificationDAO());
    }

    TicketRefundAppealServlet(OrderDAO orderDAO, UserAppealDAO userAppealDAO,
            AdminNotificationDAO notificationDAO) {
        this.orderDAO = orderDAO;
        this.userAppealDAO = userAppealDAO;
        this.notificationDAO = notificationDAO;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        ServletUtil.consumeFlash(request);

        User currentUser = currentUser(request);
        if (currentUser == null) {
            String ticketCode = ServletUtil.param(request, "ticketCode");
            response.sendRedirect(request.getContextPath() + "/login?redirect=/ticket-refund-appeal" + (ticketCode.isBlank() ? "" : "?ticketCode=" + ticketCode));
            return;
        }

        String ticketCode = ServletUtil.param(request, "ticketCode");
        if (!ticketCode.isBlank()) {
            Optional<OrderRecord> optOrder = findOwnedOrder(ticketCode, currentUser);
            if (optOrder.isEmpty()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            OrderRecord order = optOrder.get();
            order.setPendingRefundAppeal(
                    userAppealDAO.findPendingByTicketCode(order.getTicketCode()).isPresent());
            RefundAppealPolicy.Evaluation eligibility = RefundAppealPolicy.evaluate(order);
            if (!eligibility.eligible()) {
                if (writeConflict(request, response, HttpServletResponse.SC_CONFLICT,
                        "REFUND_APPEAL_NOT_ELIGIBLE", eligibility.message())) {
                    return;
                }
                ServletUtil.flashError(request, eligibility.message());
                response.sendRedirect(request.getContextPath() + "/orders?tab=past");
                return;
            }
            request.setAttribute("targetOrder", order);
            request.setAttribute("ticketCode", order.getTicketCode());
        }

        List<OrderRecord> allUserOrders = orderDAO.findHistoryByUserId(currentUser.getId());
        List<OrderRecord> missedOrders = allUserOrders.stream()
                .filter(order -> RefundAppealPolicy.evaluate(order).eligible())
                .toList();
        request.setAttribute("missedOrders", missedOrders);

        forward(request, response, "/WEB-INF/views/booking/ticket-refund-appeal.jsp");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            User currentUser = currentUser(request);
            if (currentUser == null) {
                response.sendRedirect(request.getContextPath() + "/login");
                return;
            }

            String ticketCode = ServletUtil.param(request, "ticketCode");
            String reason = ServletUtil.param(request, "reason");
            String bankAccountInfo = ServletUtil.param(request, "bankAccountInfo");
            String contactPhone = ServletUtil.param(request, "contactPhone");

            if (ticketCode.isBlank()) {
                ServletUtil.flashError(request, "Vui lòng nhập đầy đủ Mã vé và Lý do xin hoàn tiền.");
                response.sendRedirect(request.getContextPath()
                        + "/ticket-refund-appeal?ticketCode=" + ticketCode);
                return;
            }

            Optional<OrderRecord> optOrder = findOwnedOrder(ticketCode, currentUser);
            if (optOrder.isEmpty()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            OrderRecord order = optOrder.get();
            order.setPendingRefundAppeal(
                    userAppealDAO.findPendingByTicketCode(order.getTicketCode()).isPresent());
            RefundAppealPolicy.Evaluation eligibility = RefundAppealPolicy.evaluate(order);
            if (!eligibility.eligible()) {
                if (writeConflict(request, response, HttpServletResponse.SC_CONFLICT,
                        "REFUND_APPEAL_NOT_ELIGIBLE", eligibility.message())) {
                    return;
                }
                ServletUtil.flashError(request, eligibility.message());
                response.sendRedirect(request.getContextPath() + "/orders?tab=past");
                return;
            }
            if (reason.isBlank()) {
                ServletUtil.flashError(request, "Vui lòng nhập đầy đủ Mã vé và Lý do xin hoàn tiền.");
                response.sendRedirect(request.getContextPath()
                        + "/ticket-refund-appeal?ticketCode=" + ticketCode);
                return;
            }

            String ownedTicketCode = order.getTicketCode();
            if (userAppealDAO.findPendingByTicketCode(ownedTicketCode).isPresent()) {
                throw new BookingException(HttpServletResponse.SC_CONFLICT,
                        "Mã vé này đã có yêu cầu hoàn tiền đang chờ xử lý.");
            }

            UserAppeal appeal = new UserAppeal();
            appeal.setUserId(order.getUserId());
            appeal.setEmail(currentUser.getEmail());
            appeal.setTicketCode(ownedTicketCode);
            appeal.setBankAccountInfo(bankAccountInfo
                    + (contactPhone.isBlank() ? "" : " | SĐT: " + contactPhone));
            appeal.setReason("Xin hoàn tiền cho vé phim \"" + order.getFilmTitle()
                    + "\" (Mã vé: #" + ownedTicketCode + ") — Lý do: " + reason);
            int appealId = userAppealDAO.createRefundAppeal(appeal);

            AdminNotification note = new AdminNotification();
            note.setTitle("YÊU CẦU HOÀN TIỀN VÉ #" + ownedTicketCode + " từ " + currentUser.getEmail());
            note.setMessage("Khách hàng " + currentUser.getFullName() + " ("
                    + currentUser.getEmail() + ") gửi yêu cầu hoàn tiền cho suất chiếu "
                    + order.getFilmTitle() + ". Lý do: \"" + reason + "\"");
            note.setCategory("order");
            note.setSeverity("warning");
            note.setTargetType("UserAppeal");
            note.setTargetId(String.valueOf(appealId));
            note.setActionUrl("/admin/orders?tab=refund");
            note.setCinemaId(appeal.getCinemaId());
            notificationDAO.createNotification(note);

            ServletUtil.flashSuccess(request, "✅ Đã gửi yêu cầu hoàn tiền cho mã vé #"
                    + ownedTicketCode
                    + ". Ban quản lý CineBook sẽ xem xét và phản hồi trong 24h-48h.");
            response.sendRedirect(request.getContextPath() + "/orders");
        } catch (BookingException ex) {
            int status = ex.getStatusCode() >= 400 && ex.getStatusCode() <= 599
                    ? ex.getStatusCode()
                    : HttpServletResponse.SC_CONFLICT;
            if (writeConflict(request, response, status, "REFUND_APPEAL_DUPLICATE", ex.getMessage())) {
                return;
            }
            ServletUtil.flashError(request, ex.getMessage());
            response.sendRedirect(request.getContextPath() + "/orders?tab=past");
        }
    }

    private Optional<OrderRecord> findOwnedOrder(String ticketCode, User currentUser) {
        return orderDAO.findByTicketCodeAndUserId(ticketCode, currentUser.getId())
                .filter(order -> order.getUserId() == currentUser.getId());
    }

    /**
     * Phan biet client API voi dieu huong cua trinh duyet.
     *
     * <p>Thiet ke 05/08/2026 chot ro hai chieu: <i>"User navigation must not end on a raw
     * Tomcat 409 page. The HTTP status may remain 409 for API clients, but its error code and
     * message must be specific."</i> Nen cung mot xung dot nghiep vu phai tra hai dang khac
     * nhau — khong phai chon mot trong hai.</p>
     */
    private boolean wantsJson(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.toLowerCase().contains("application/json")) {
            return true;
        }
        return "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"));
    }

    /**
     * Xung dot mien nghiep vu: JSON kem ma loi cu the cho API, flash + redirect cho trinh duyet.
     *
     * @return {@code true} neu da tra loi bang JSON — caller khong duoc redirect them lan nua
     */
    private boolean writeConflict(HttpServletRequest request, HttpServletResponse response,
            int status, String code, String message) throws IOException {
        if (!wantsJson(request)) {
            return false;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", true);
        body.put("code", code);
        body.put("message", message == null ? "" : message);
        response.setStatus(status);
        JsonUtil.write(response, body);
        return true;
    }
}
