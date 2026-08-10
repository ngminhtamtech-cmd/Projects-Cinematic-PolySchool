package com.mycompany.website.ban.ve.xem.phim.controller;

import com.mycompany.website.ban.ve.xem.phim.service.PolicyDocumentService;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Public, escaped renderer for the currently published refund policy. */
public class RefundPolicyServlet extends BasePortalServlet {
    private final PolicyDocumentService policyService = new PolicyDocumentService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            request.setAttribute("policy", policyService.publishedRefundPolicy());
            forward(request, response, "/WEB-INF/views/refund-policy.jsp");
        } catch (RuntimeException ex) {
            throw new ServletException("Refund policy is unavailable", ex);
        }
    }
}
