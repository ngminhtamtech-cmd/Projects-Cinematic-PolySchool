package com.mycompany.website.ban.ve.xem.phim.controller;

import com.mycompany.website.ban.ve.xem.phim.config.AppConstants;
import com.mycompany.website.ban.ve.xem.phim.model.Invoice;
import com.mycompany.website.ban.ve.xem.phim.model.OrderRecord;
import com.mycompany.website.ban.ve.xem.phim.model.User;
import com.mycompany.website.ban.ve.xem.phim.service.InvoiceService;
import com.mycompany.website.ban.ve.xem.phim.service.AdminService;
import java.io.IOException;
import java.io.OutputStream;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class InvoiceServlet extends HttpServlet {
    private final AdminService adminService = new AdminService();
    private final InvoiceService invoiceService = new InvoiceService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User actor = (User) request.getSession().getAttribute(AppConstants.SESSION_USER);
        if (actor == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        int orderId;
        try {
            String path = request.getPathInfo();
            orderId = Integer.parseInt(path == null ? "" : path.replaceFirst("^/", ""));
        } catch (NumberFormatException ex) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Mã đơn không hợp lệ.");
            return;
        }
        OrderRecord order = adminService.findOrderById(orderId, actor).orElse(null);
        if (order == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        boolean operator = "admin".equalsIgnoreCase(actor.getRole())
                || "manager".equalsIgnoreCase(actor.getRole());
        if (!operator && order.getUserId() != actor.getId()) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        Invoice invoice = invoiceService.issueSale(orderId);
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + invoice.getInvoiceNo() + ".pdf\"");
        try (OutputStream out = response.getOutputStream()) {
            invoiceService.renderPdf(invoice, order, out);
        }
    }
}
