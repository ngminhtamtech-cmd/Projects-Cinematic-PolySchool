package com.mycompany.website.ban.ve.xem.phim.controller.booking;

import com.mycompany.website.ban.ve.xem.phim.config.AppConstants;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.service.BookingStateMachine;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.BookingException;
import com.mycompany.website.ban.ve.xem.phim.service.BookingService;
import com.mycompany.website.ban.ve.xem.phim.util.JsonUtil;
import com.mycompany.website.ban.ve.xem.phim.util.ServletUtil;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class OrderServlet extends HttpServlet {
    private final BookingService bookingService = new BookingService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User currentUser = currentUser(request);
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || "/".equals(pathInfo) || "/history".equalsIgnoreCase(pathInfo)) {
            ServletUtil.consumeFlash(request);
            List<OrderRecord> allOrders = bookingService.findOrderHistory(currentUser.getId());
            List<OrderRecord> currentOrders = allOrders.stream()
                    .filter(OrderRecord::isCurrentTicket)
                    .collect(java.util.stream.Collectors.toList());
            List<OrderRecord> pastOrders = allOrders.stream()
                    .filter(OrderRecord::isUsedOrExpired)
                    .collect(java.util.stream.Collectors.toList());
            request.setAttribute("orders", allOrders);
            request.setAttribute("currentOrders", currentOrders);
            request.setAttribute("pastOrders", pastOrders);
            request.getRequestDispatcher("/WEB-INF/views/booking/history.jsp").forward(request, response);
            return;
        }
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User currentUser = currentUser(request);
        String pathInfo = request.getPathInfo();
        if (pathInfo == null) {
            createOrder(request, response, currentUser);
            return;
        }
        if (pathInfo.matches("/\\d+/pay")) {
            payOrder(request, response, currentUser, parseOrderId(pathInfo));
            return;
        }
        if (pathInfo.matches("/\\d+/cancel")) {
            cancelOrder(request, response, currentUser, parseOrderId(pathInfo));
            return;
        }
        if (pathInfo.matches("/\\d+/hide-history")) {
            hideHistoryOrder(request, response, currentUser, parseOrderId(pathInfo));
            return;
        }
        if ("/batch-hide-history".equalsIgnoreCase(pathInfo)) {
            batchHideHistoryOrders(request, response, currentUser);
            return;
        }
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    private void batchHideHistoryOrders(HttpServletRequest request, HttpServletResponse response, User currentUser) throws IOException {
        String action = request.getParameter("action");
        if ("all".equalsIgnoreCase(action)) {
            bookingService.hideAllHistoryOrdersFromUserHistory(currentUser.getId());
            ServletUtil.flashSuccess(request, "Đã dọn dẹp sạch toàn bộ lịch sử vé thành công.");
        } else {
            String[] orderIdStrs = request.getParameterValues("orderIds");
            if (orderIdStrs != null && orderIdStrs.length > 0) {
                List<Integer> orderIds = new java.util.ArrayList<>();
                for (String s : orderIdStrs) {
                    try {
                        orderIds.add(Integer.parseInt(s));
                    } catch (NumberFormatException ex) {
                        if (ex != null) {
                            // skip invalid id
                        }
                    }
                }
                if (!orderIds.isEmpty()) {
                    bookingService.hideBatchOrdersFromUserHistory(orderIds, currentUser.getId());
                    ServletUtil.flashSuccess(request, "Đã xóa " + orderIds.size() + " vé khỏi lịch sử hiển thị.");
                } else {
                    ServletUtil.flashError(request, "Vui lòng chọn ít nhất 1 vé để xóa.");
                }
            } else {
                ServletUtil.flashError(request, "Vui lòng chọn ít nhất 1 vé để xóa.");
            }
        }
        response.sendRedirect(request.getContextPath() + "/booking/history");
    }

    private void hideHistoryOrder(HttpServletRequest request, HttpServletResponse response, User currentUser, int orderId) throws IOException {
        try {
            bookingService.hideOrderFromUserHistory(orderId, currentUser.getId());
            ServletUtil.flashSuccess(request, "Đã xóa vé khỏi lịch sử hiển thị của bạn thành công.");
            response.sendRedirect(request.getContextPath() + "/booking/history");
        } catch (BookingException ex) {
            ServletUtil.flashError(request, ex.getMessage());
            response.sendRedirect(request.getContextPath() + "/booking/history");
        }
    }

    private void createOrder(HttpServletRequest request, HttpServletResponse response, User currentUser) throws IOException {
        try {
            int showtimeId = Integer.parseInt(ServletUtil.param(request, "showtimeId"));
            List<Integer> seatIds = BookingService.parseSeatIds(ServletUtil.param(request, "seatIds"));
            Map<Integer, Integer> comboSelections = BookingService.parseComboSelections(ServletUtil.param(request, "comboItems"));
            String promotionCode = ServletUtil.param(request, "promotionCode");
            String paymentMethod = ServletUtil.param(request, "paymentMethod");
            String idempotencyKey = getIdempotencyKey(request);
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                throw new com.mycompany.website.ban.ve.xem.phim.service.BookingException(
                        400, "IDEMPOTENCY_KEY_REQUIRED");
            }

            OrderRecord order = bookingService.createDraftOrder(
                    currentUser.getId(),
                    showtimeId,
                    seatIds,
                    comboSelections,
                    promotionCode,
                    paymentMethod,
                    idempotencyKey
            );
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", false);
            body.put("orderId", order.getId());
            body.put("totalAmount", order.getTotalAmount());
            body.put("ticketCode", order.getTicketCode());
            body.put("lifecycleState", BookingStateMachine.classify(
                    order.getOrderStatus(), order.getPaymentStatus(), order.getPaymentMethod()).code());
            body.put("stateVersion", order.getStateVersion());
            body.put("replayed", order.isReplayed());
            body.put("message", "Da tao don tam giu ghe trong " + BookingService.holdMinutes() + " phut.");
            JsonUtil.write(response, body);
        } catch (BookingException ex) {
            JsonUtil.error(response, ex.getStatusCode(), ex.getMessage());
        } catch (Exception ex) {
            String msg = (ex.getMessage() != null && !ex.getMessage().isBlank()) ? ex.getMessage() : "Không thể tạo đơn đặt vé lúc này.";
            JsonUtil.error(response, 500, msg);
        }
    }

    private void payOrder(HttpServletRequest request, HttpServletResponse response, User currentUser, int orderId) throws IOException {
        try {
            String comboItems = request.getParameter("comboItems");
            String promotionCode = request.getParameter("promotionCode");
            String paymentMethod = request.getParameter("paymentMethod");
            String idempotencyKey = getIdempotencyKey(request);
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                throw new com.mycompany.website.ban.ve.xem.phim.service.BookingException(
                        400, "IDEMPOTENCY_KEY_REQUIRED");
            }

            java.util.Map<Integer, Integer> comboSelections = BookingService.parseComboSelections(comboItems);

            OrderRecord order = bookingService.payOrder(currentUser.getId(), orderId, comboSelections, promotionCode, paymentMethod, idempotencyKey);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", false);
            body.put("orderId", order.getId());
            body.put("ticketCode", order.getTicketCode());
            body.put("lifecycleState", BookingStateMachine.classify(
                    order.getOrderStatus(), order.getPaymentStatus(), order.getPaymentMethod()).code());
            body.put("stateVersion", order.getStateVersion());
            body.put("replayed", order.isReplayed());
            body.put("historyUrl", request.getContextPath() + "/orders/history");
            body.put("message", "Thanh toan thanh cong.");
            JsonUtil.write(response, body);
        } catch (BookingException ex) {
            JsonUtil.error(response, ex.getStatusCode(), ex.getMessage());
        } catch (RuntimeException ex) {
            JsonUtil.error(response, 500, "Khong the xac nhan thanh toan.");
        }
    }

    private void cancelOrder(HttpServletRequest request, HttpServletResponse response, User currentUser, int orderId) throws IOException {
        try {
            bookingService.cancelUserDraftOrder(currentUser.getId(), orderId);
            ServletUtil.flashSuccess(request, "Đã hủy đơn giữ chỗ thành công.");
        } catch (BookingException ex) {
            ServletUtil.flashError(request, ex.getMessage());
        } catch (Exception ex) {
            ServletUtil.flashError(request, "Không thể hủy đơn giữ chỗ lúc này.");
        }
        response.sendRedirect(request.getContextPath() + "/orders/history");
    }

    private String getIdempotencyKey(HttpServletRequest request) {
        String key = request.getHeader("X-Idempotency-Key");
        if (key == null || key.isBlank()) {
            key = request.getParameter("idempotencyKey");
        }
        return key;
    }

    private int parseOrderId(String pathInfo) {
        try {
            return Integer.parseInt(pathInfo.split("/")[1]);
        } catch (RuntimeException ex) {
            return -1;
        }
    }

    private User currentUser(HttpServletRequest request) {
        return (User) request.getSession().getAttribute(AppConstants.SESSION_USER);
    }
}
